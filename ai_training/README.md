# 🧠 IRO AI Photo Enhancer — Huấn luyện AI Chỉnh Ảnh trên Google Colab

Tài liệu và mã nguồn hướng dẫn huấn luyện mô hình **AI Neural Network chuyên gia chỉnh màu ảnh** trên **Google Colab (GPU miễn phí)** và tích hợp trực tiếp vào **IRO Studio Web** thông qua **ONNX Runtime Web**.

---

## 🌟 Kiến Trúc Mô Hình (IRO-EnhanceNet)

Mô hình được thiết kế theo tiêu chuẩn công nghiệp:
- **Backbone**: `MobileNetV3-Small` (siêu nhẹ, chỉ ~2.5M tham số, suy luận < 25ms trên trình duyệt).
- **Multi-Head Output**:
  1. **Head 1 (Parameter Regression - 10 thông số cơ bản)**:
     - `exposure` (Độ phơi sáng EV): `-2.0` đến `+2.0`
     - `contrast` (Độ tương phản): `-50` đến `+50`
     - `highlights` (Vùng sáng): `-100` đến `+100`
     - `shadows` (Vùng tối): `-100` đến `+100`
     - `whites` (Điểm trắng): `-100` đến `+100`
     - `blacks` (Điểm đen): `-100` đến `+100`
     - `temp` (Nhiệt độ K): `-50` đến `+50`
     - `tint` (Sắc độ Tint): `-50` đến `+50`
     - `saturation` (Độ bão hòa): `-50` đến `+50`
     - `clarity` (Độ trong khối): `0` đến `+50`
  2. **Head 2 (Film Simulation Classification - 8 dòng phim Fujifilm)**:
     - `Classic Chrome`, `Velvia`, `Astia`, `Classic Neg.`, `PROVIA`, `ETERNA`, `Acros`, `Nostalgic Neg.`

- **Định dạng xuất**:
  - `iro_ai_enhancer.onnx` (FP32 ~ 5MB)
  - `iro_ai_enhancer_quant.onnx` (INT8 Quantized ~ 1.8MB — siêu nhẹ cho Web!)

---

## 🚀 Hướng Dẫn Huấn Luyện Trên Google Colab (Chỉ 4 Bước)

### Bước 1: Mở Notebook trên Google Colab
1. Truy cập [Google Colab](https://colab.research.google.com/).
2. Chọn thẻ **Upload (Tải lên)** và chọn tệp `iro_ai_photo_enhancer.ipynb` trong thư mục này.

### Bước 2: Bật GPU Miễn Phí (Tesla T4)
- Trên thanh menu của Colab, chọn: **Runtime (Thời gian chạy)** $\rightarrow$ **Change runtime type (Thay đổi loại thời gian chạy)**.
- Mục **Hardware accelerator (Phần cứng tăng tốc)**: Chọn **T4 GPU** $\rightarrow$ bấm **Save (Lưu)**.

### Bước 3: Chạy Huấn Luyện (Run All)
- Bấm **Runtime** $\rightarrow$ **Run all (Chạy tất cả)** (hoặc phím tắt `Ctrl + F9`).
- Notebook sẽ tự động:
  1. Cài đặt các thư viện PyTorch, ONNX, ONNX Runtime.
  2. Khởi tạo bộ dữ liệu mẫu (hoặc tự sinh dựa trên biến đổi màu phim IRO).
  3. Huấn luyện mạng `IROEnhanceNet` trong 15-20 epochs (~10 phút trên T4 GPU).
  4. Hiển thị kết quả Before / After và đồ thị đánh giá.
  5. Xuất file `iro_ai_enhancer.onnx` và `iro_ai_enhancer_quant.onnx`.

### Bước 4: Tải Model về và tích hợp vào IRO Studio
- Ở ô lệnh cuối cùng của Notebook, tệp `iro_ai_enhancer.onnx` sẽ tự động được tải về máy của bạn.
- Bạn chỉ cần copy tệp `iro_ai_enhancer.onnx` vào thư mục:
  ```text
  photoedit/web/models/iro_ai_enhancer.onnx
  ```
- Mở website IRO Studio: Bấm nút **✨ AI Auto** trên thanh công cụ — Trình duyệt sẽ tự động kích hoạt mô hình Neural AI vừa train để chỉnh ảnh trong 20ms!

---

## 💻 Huấn luyện cục bộ (Local GPU NVIDIA)

Nếu máy tính của bạn có card đồ họa NVIDIA (RTX / GTX):
```bash
cd ai_training
pip install torch torchvision onnx onnxruntime pillow numpy matplotlib
python train.py --epochs 25 --batch-size 32 --device cuda
```
Mô hình `iro_ai_enhancer.onnx` sẽ được tạo ngay trong thư mục `models/`.
