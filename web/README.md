# IRO Web Studio 3.0 — Phòng Tối Trực Tuyến Độc Lập

[![Platform: Web Standalone](https://img.shields.io/badge/Platform-Web%20Standalone-0969da?style=flat-square&logo=html5)](.)
[![Engine: Vanilla ES Modules](https://img.shields.io/badge/Engine-ES%20Modules%20%2B%20Web%20Workers-f58220?style=flat-square&logo=javascript)](.)
[![Presets: 392 Fuji Recipes](https://img.shields.io/badge/Presets-392%20Fuji%20Recipes-2da44e?style=flat-square)](recipes.json)
[![Frames: 30 Styles](https://img.shields.io/badge/PhotoFrames-30%20Exhibition%20Styles-8250df?style=flat-square)](engine.mjs)
[![License: MIT](https://img.shields.io/badge/License-MIT-gray?style=flat-square)](LICENSE)

**IRO Web Studio** là phiên bản Web độc lập (Standalone PWA / Web App) được chuyển giao nguyên bản từ nền tảng Android & iOS của IRO Studio, mang đến trải nghiệm phòng tối giả lập màu phim chuyên nghiệp ngay trên trình duyệt mà không cần cài đặt ứng dụng.

---

## 🌟 Điểm nổi bật về kiến trúc

1. **100% Xử lý Client-Side (Bảo mật tuyệt đối):**
   - Mọi thao tác biên tập ảnh, tính toán đường cong màu, render khung và nạp LUT diễn ra hoàn toàn trên thiết bị của người dùng qua Web Worker và HTML5 Canvas.
   - Không có bất kỳ hình ảnh nào bị tải lên máy chủ.
2. **Đồng bộ chuẩn xác với Native Engine (Android/iOS/Rust):**
   - Đầy đủ 392 công thức giả lập màu phim Fujifilm (`recipes.json`).
   - Đầy đủ 30 mẫu khung ảnh triển lãm (PhotoFrame) khớp mã và thông số EXIF 1:1 với `PhotoFrame.java` và `PhotoFrame.swift`.
   - Thuật toán đường cong đơn điệu bậc ba Fritsch–Carlson không bao giờ bị vọt lố (overshoot).
   - Bánh xe màu 3 vùng (Shadows, Midtones, Highlights) trong không gian cảm nhận đồng đều OkLab / OkLCh.
3. **Phòng tối quang học mô phỏng hóa học tương tự:**
   - Black Pro-Mist bloom, CineStill Halation quầng đỏ, Bleach Bypass giữ lại bạc, Helios 44-2 Swirly Bokeh, Light Leak 35mm rò sáng ngẫu nhiên, Hạt muối bạc Dual-Band và Chuyển động Living Film 24fps.
4. **Nhúng ẩn công thức LSB (Recipe Steganography):**
   - Tự động mã hóa công thức màu vào 2 bit thấp nhất của kênh màu ảnh PNG xuất ra. Khi kéo ảnh này ngược lại vào web, hệ thống tự động giải mã và phục hồi lại 100% thông số chỉnh màu ban đầu.
5. **Xuất bảng màu 3D LUT `.cube`:**
   - Tạo file `.cube` chuẩn 33×33×33 hoặc 64×64×64 dùng cho DaVinci Resolve, Premiere Pro, Final Cut Pro, Lightroom và CapCut.

---

## 📂 Cấu trúc thư mục gói Web

| File / Thư mục | Vai trò |
|---|---|
| `index.html` | Giao diện phòng tối MD3 Expressive Darkroom, responsive cho cả Mobile, Tablet và Desktop |
| `style.css` | Hệ thống thiết kế Material You với biến CSS theme tối hiện đại |
| `app.mjs` | Tương tác giao diện: kéo thả ảnh, điều khiển bánh xe màu tương tác, canvas tone curve, bộ lọc công thức |
| `engine.mjs` | Lõi xử lý hình ảnh toán học, thuật toán màu sắc, 30 khung ảnh và xuất file |
| `worker.mjs` | Web Worker xử lý ảnh bất đồng bộ ngoài luồng chính, tránh giật lag giao diện |
| `recipes.json` | Thư viện 392 công thức màu Fujifilm trích xuất chuẩn hóa từ Fuji X Weekly |
| `tests.mjs` | Bộ 8 kiểm thử đơn vị tự động kiểm tra tính đúng đắn toán học của Engine |
| `server.mjs` | Máy chủ Node.js hiệu năng cao, hỗ trợ nén Gzip, cache headers và CORS |
| `Dockerfile` | Cấu hình Docker container hóa ứng dụng |
| `docker-compose.yml` | Cấu hình Docker Compose triển khai 1 lệnh |
| `nginx.conf` | Cấu hình Nginx tối ưu phục vụ file tĩnh và cache |
| `.htaccess` | Cấu hình cho máy chủ Apache / Shared Host cPanel |
| `DEPLOY.md` | Sổ tay hướng dẫn triển khai lên máy chủ VPS / Debian / Cloudflare Tunnel |

---

## 🚀 Hướng dẫn khởi chạy nhanh

### 1. Khởi chạy bằng Node.js (Mặc định)
```bash
# Trong thư mục web/
node server.mjs
```
Mở trình duyệt truy cập: `http://localhost:4173`

### 2. Khởi chạy với Docker / Docker Compose
```bash
docker compose up -d
```
Truy cập tại: `http://localhost:4173`

### 3. Kiểm thử đơn vị tự động
```bash
node --test tests.mjs
```
Kết quả kiểm tra toàn diện 8/8 bài kiểm tra (100% PASS).

---

## 🌐 Triển khai lên Server (Debian / Ubuntu / Home Server)

Xem chi tiết từng bước hướng dẫn trong file [DEPLOY.md](DEPLOY.md):
- Cách triển khai với PM2 chạy nền liên tục.
- Cấu hình Nginx Reverse Proxy với HTTPS SSL (Let's Encrypt).
- Kết nối Cloudflare Tunnel an toàn không cần mở cổng NAT port forwarding.
