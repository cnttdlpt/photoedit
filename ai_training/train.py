"""
IRO AI Photo Enhancer — Standalone Training & ONNX Export Script
Huấn luyện mô hình Neural Network dự đoán thông số chỉnh ảnh và film simulation cho IRO Studio.
"""

import os
import argparse
import time
import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
import torchvision.transforms as T
import torchvision.models as models
from PIL import Image, ImageEnhance

# -----------------------------------------------------------------------------
# Danh mục phim mô phỏng Fujifilm trong IRO Studio
# -----------------------------------------------------------------------------
FILM_CLASSES = [
    'Classic Chrome',
    'Velvia',
    'Astia',
    'Classic Neg.',
    'PROVIA',
    'ETERNA',
    'Acros',
    'Nostalgic Neg.'
]

# 10 Thông số thanh trượt chính trong IRO Studio (min, max)
PARAM_RANGES = [
    ('exposure', -2.0, 2.0),
    ('contrast', -50.0, 50.0),
    ('highlights', -100.0, 100.0),
    ('shadows', -100.0, 100.0),
    ('whites', -100.0, 100.0),
    ('blacks', -100.0, 100.0),
    ('temp', -50.0, 50.0),
    ('tint', -50.0, 50.0),
    ('saturation', -50.0, 50.0),
    ('clarity', 0.0, 50.0),
]


class IROEnhanceNet(nn.Module):
    """
    Kiến trúc mạng nơ-ron đa nhiệm (Multi-Head) dựa trên MobileNetV3-Small:
    - Head 1: Hồi quy 10 thông số thanh trượt (Normalized [-1, 1])
    - Head 2: Phân loại dòng phim Fujifilm phù hợp nhất
    """
    def __init__(self, num_params=10, num_films=8, pretrained=True):
        super().__init__()
        weights = models.MobileNet_V3_Small_Weights.DEFAULT if pretrained else None
        base = models.mobilenet_v3_small(weights=weights)
        
        # Trích xuất phần backbone trích xuất đặc trưng hình ảnh
        self.features = base.features
        self.avgpool = nn.AdaptiveAvgPool2d((1, 1))
        
        # Kích thước vector đặc trưng từ MobileNetV3-Small là 576
        in_dim = 576
        
        self.shared_fc = nn.Sequential(
            nn.Linear(in_dim, 256),
            nn.BatchNorm1d(256),
            nn.Hardswish(inplace=True),
            nn.Dropout(p=0.2)
        )
        
        # Head 1: Dự đoán thông số thanh trượt (Đầu ra dạng Tanh trong dải [-1, 1])
        self.param_head = nn.Sequential(
            nn.Linear(256, 128),
            nn.Hardswish(inplace=True),
            nn.Linear(128, num_params),
            nn.Tanh()
        )
        
        # Head 2: Dự đoán loại phim Fujifilm tối ưu
        self.film_head = nn.Sequential(
            nn.Linear(256, 64),
            nn.Hardswish(inplace=True),
            nn.Linear(64, num_films)
        )

    def forward(self, x):
        feat = self.features(x)
        feat = self.avgpool(feat)
        feat = torch.flatten(feat, 1)
        shared = self.shared_fc(feat)
        
        params = self.param_head(shared)
        film_logits = self.film_head(shared)
        return params, film_logits


class SyntheticPhotoDataset(Dataset):
    """
    Dataset tự sinh (Self-Synthesizing) dựa trên các nguyên lý quang học và công thức màu IRO Studio.
    Cho phép huấn luyện ngay lập tức mà không cần tải hàng trăm GB ảnh mẫu.
    """
    def __init__(self, size=4000, img_size=224):
        self.size = size
        self.img_size = img_size
        self.transform = T.Compose([
            T.ToTensor(),
            T.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
        ])

    def __len__(self):
        return self.size

    def __getitem__(self, idx):
        # Tạo canvas ảnh nền ngẫu nhiên với gradient hoặc các mảng sáng tối
        np_img = np.zeros((self.img_size, self.img_size, 3), dtype=np.uint8)
        
        # Gradient bầu trời / phong cảnh / chân dung mô phỏng
        base_lum = np.random.randint(50, 200)
        grad_dir = np.random.choice(['horiz', 'vert', 'radial'])
        if grad_dir == 'vert':
            col1 = np.random.randint(20, 240, size=3)
            col2 = np.random.randint(20, 240, size=3)
            for c in range(3):
                np_img[:, :, c] = np.linspace(col1[c], col2[c], self.img_size, dtype=np.uint8)
        elif grad_dir == 'horiz':
            col1 = np.random.randint(20, 240, size=3)
            col2 = np.random.randint(20, 240, size=3)
            for c in range(3):
                np_img[:, :, c] = np.linspace(col1[c], col2[c], self.img_size, dtype=np.uint8)[None, :]
        else:
            np_img[:] = np.random.randint(40, 220, size=3, dtype=np.uint8)

        # Thêm các khối sáng tạo điểm nhấn
        for _ in range(np.random.randint(2, 6)):
            x = np.random.randint(0, self.img_size)
            y = np.random.randint(0, self.img_size)
            r = np.random.randint(20, 80)
            col = np.random.randint(0, 255, size=3)
            yy, xx = np.ogrid[:self.img_size, :self.img_size]
            mask = (xx - x)**2 + (yy - y)**2 <= r**2
            np_img[mask] = col

        pil_img = Image.fromarray(np_img)
        
        # Phân tích độ sáng và độ lệch màu của ảnh gốc để tính thông số ground-truth
        r_mean = np.mean(np_img[:, :, 0])
        g_mean = np.mean(np_img[:, :, 1])
        b_mean = np.mean(np_img[:, :, 2])
        lum = 0.299 * r_mean + 0.587 * g_mean + 0.114 * b_mean
        
        # Tính toán thông số cân chỉnh mục tiêu (Ground Truth)
        # Mục tiêu kéo ánh sáng về dải trung tính 128
        target_exposure = np.clip((128.0 - lum) / 64.0, -2.0, 2.0)
        target_contrast = np.clip((lum - 128.0) * 0.3, -30.0, 30.0)
        target_highlights = np.clip((180.0 - np.percentile(np_img, 95)) * 0.5, -80.0, 80.0)
        target_shadows = np.clip((np.percentile(np_img, 10) - 40.0) * 0.5, -80.0, 80.0)
        target_whites = np.clip((240.0 - np.max(np_img)) * 0.4, -60.0, 60.0)
        target_blacks = np.clip((np.min(np_img) - 15.0) * 0.4, -60.0, 60.0)
        
        # Cân bằng trắng nhiệt độ K (Color Cast)
        target_temp = np.clip((b_mean - r_mean) * 0.6, -45.0, 45.0)
        target_tint = np.clip(((r_mean + b_mean)/2.0 - g_mean) * 0.5, -45.0, 45.0)
        
        # Độ bão hòa & Trong khối
        color_std = np.std(np_img)
        target_saturation = np.clip(15.0 - color_std * 0.2, -30.0, 40.0)
        target_clarity = np.clip(20.0, 5.0, 40.0)
        
        # Lựa chọn dòng phim tối ưu dựa trên sắc thái
        if target_temp > 10:
            target_film = 0 # Classic Chrome (ấm, hoài niệm)
        elif b_mean > r_mean + 20:
            target_film = 3 # Classic Neg. (tương phản cao, lạnh)
        elif color_std > 60:
            target_film = 1 # Velvia (rực rỡ)
        elif lum < 80:
            target_film = 5 # ETERNA (điện ảnh, dịu)
        else:
            target_film = 4 # PROVIA (chuẩn trung tính)

        raw_params = np.array([
            target_exposure, target_contrast, target_highlights, target_shadows,
            target_whites, target_blacks, target_temp, target_tint,
            target_saturation, target_clarity
        ], dtype=np.float32)

        # Chuẩn hóa về [-1, 1]
        norm_params = np.zeros(10, dtype=np.float32)
        for i, (_, p_min, p_max) in enumerate(PARAM_RANGES):
            norm_params[i] = 2.0 * (raw_params[i] - p_min) / (p_max - p_min) - 1.0

        tensor_img = self.transform(pil_img)
        return tensor_img, torch.tensor(norm_params, dtype=torch.float32), torch.tensor(target_film, dtype=torch.long)


def train_model(epochs=15, batch_size=32, lr=1e-3, device='cuda', save_dir='models'):
    os.makedirs(save_dir, exist_ok=True)
    if device == 'cuda' and not torch.cuda.is_available():
        print("[!] Không tìm thấy CUDA, chuyển sang chạy trên CPU.")
        device = 'cpu'

    print(f"[*] Bắt đầu huấn luyện IRO-EnhanceNet trên {device.upper()}...")
    print(f"[*] Số epochs: {epochs} | Batch size: {batch_size} | Learning rate: {lr}")

    dataset = SyntheticPhotoDataset(size=4000)
    train_size = int(0.85 * len(dataset))
    val_size = len(dataset) - train_size
    train_ds, val_ds = torch.utils.data.random_split(dataset, [train_size, val_size])

    train_loader = DataLoader(train_ds, batch_size=batch_size, shuffle=True, num_workers=2, pin_memory=True if device=='cuda' else False)
    val_loader = DataLoader(val_ds, batch_size=batch_size, shuffle=False)

    model = IROEnhanceNet(pretrained=True).to(device)
    optimizer = optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs, eta_min=1e-5)

    criterion_params = nn.SmoothL1Loss()
    criterion_film = nn.CrossEntropyLoss()

    best_loss = float('inf')

    for epoch in range(1, epochs + 1):
        t0 = time.time()
        model.train()
        train_loss = 0.0
        for imgs, params, films in train_loader:
            imgs = imgs.to(device)
            params = params.to(device)
            films = films.to(device)

            optimizer.zero_grad()
            pred_params, pred_films = model(imgs)

            loss_p = criterion_params(pred_params, params)
            loss_f = criterion_film(pred_films, films)
            loss = loss_p + 0.3 * loss_f

            loss.backward()
            optimizer.step()
            train_loss += loss.item() * len(imgs)

        train_loss /= len(train_ds)
        scheduler.step()

        # Validation
        model.eval()
        val_loss = 0.0
        val_acc = 0.0
        with torch.no_grad():
            for imgs, params, films in val_loader:
                imgs = imgs.to(device)
                params = params.to(device)
                films = films.to(device)

                pred_params, pred_films = model(imgs)
                loss_p = criterion_params(pred_params, params)
                loss_f = criterion_film(pred_films, films)
                val_loss += (loss_p + 0.3 * loss_f).item() * len(imgs)
                val_acc += (pred_films.argmax(dim=1) == films).sum().item()

        val_loss /= len(val_ds)
        val_acc /= len(val_ds)
        elapsed = time.time() - t0

        print(f"Epoch [{epoch:02d}/{epochs:02d}] ({elapsed:.1f}s) - Train Loss: {train_loss:.4f} | Val Loss: {val_loss:.4f} | Film Acc: {val_acc*100:.1f}%")

        if val_loss < best_loss:
            best_loss = val_loss
            torch.save(model.state_dict(), os.path.join(save_dir, 'best_model.pt'))

    print("\n[+] Huấn luyện hoàn tất! Best Val Loss:", best_loss)
    return model


def export_onnx(model, save_path='models/iro_ai_enhancer.onnx'):
    model.eval()
    model.cpu()
    dummy_input = torch.randn(1, 3, 224, 224, dtype=torch.float32)

    os.makedirs(os.path.dirname(save_path), exist_ok=True)
    print(f"[*] Đang xuất mô hình sang định dạng ONNX: {save_path} ...")
    torch.onnx.export(
        model,
        dummy_input,
        save_path,
        export_params=True,
        opset_version=14,
        do_constant_folding=True,
        input_names=['input'],
        output_names=['sliders', 'film_logits'],
        dynamic_axes={'input': {0: 'batch_size'}, 'sliders': {0: 'batch_size'}, 'film_logits': {0: 'batch_size'}}
    )
    file_size_mb = os.path.getsize(save_path) / (1024 * 1024)
    print(f"[✓] Đã xuất thành công: {save_path} ({file_size_mb:.2f} MB)")

    # Kiểm tra thử nghiệm suy luận bằng ONNX Runtime
    try:
        import onnxruntime as ort
        sess = ort.InferenceSession(save_path)
        out = sess.run(None, {'input': dummy_input.numpy()})
        print(f"[✓] ONNX Runtime Verification: Output Sliders shape={out[0].shape}, Film shape={out[1].shape}")
    except Exception as e:
        print("[!] Không thể kiểm thử ONNX Runtime:", e)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Huấn luyện AI IRO Photo Enhancer')
    parser.add_argument('--epochs', type=int, default=15, help='Số epoch huấn luyện')
    parser.add_argument('--batch-size', type=int, default=32, help='Kích thước batch')
    parser.add_argument('--lr', type=float, default=1e-3, help='Tốc độ học (learning rate)')
    parser.add_argument('--device', type=str, default='cuda', help='cuda hoặc cpu')
    parser.add_argument('--output', type=str, default='models/iro_ai_enhancer.onnx', help='Đường dẫn xuất file ONNX')
    args = parser.parse_args()

    model = train_model(epochs=args.epochs, batch_size=args.batch_size, lr=args.lr, device=args.device)
    export_onnx(model, save_path=args.output)
