# IRO Studio 3.0 — Hệ sinh thái phòng tối & giả lập màu phim đa nền tảng

Dự án **IRO Studio** được tích hợp trọn vẹn trong hệ sinh thái sáng tạo ảnh nguồn mở [PhotoCraft](https://github.com/cnttdlpt/photocraft):
- **Desktop (Rust/wgpu):** [PhotoCraft](https://github.com/cnttdlpt/photocraft) — Phần mềm chỉnh sửa ảnh chuyên nghiệp thay thế Photoshop viết bằng 100% Rust cho macOS, Windows, Linux.
- **Mobile Android:** [`android/`](android/README.md) — Ứng dụng native Android (Material Design 3 Expressive, Camera2 API, GPU preview, Darkroom 3.0).
- **Mobile iOS:** [`ios/`](ios/README.md) — Ứng dụng native iOS (Swift/SwiftUI + Metal Compute Shaders).
- **Web Studio:** [`web/`](web/README.md) — Phòng tối trực tuyến độc lập (Standalone Web), nạp 392 công thức Fuji X Weekly, 30 mẫu khung ảnh triển lãm và bộ công cụ LightCraft.

---

## 🚀 Các tính năng cốt lõi đồng bộ trên mọi nền tảng

1. **Thư viện 392 công thức màu phim Fuji X Weekly (`recipes.json`):**
   - Hỗ trợ đầy đủ cảm biến: `BAYER`, `X-Trans IV`, `X-Trans V`, `X-Trans III`, `X-Trans II`, `GFX`.
   - Tìm kiếm công thức, lọc theo cảm biến và nạp thông số chỉ với 1 chạm.
2. **30 Mẫu khung ảnh triển lãm (PhotoFrame):**
   - Đầy đủ 30 phong cách từ cơ bản (*Classic, White, Glass, Dark, Centered*), mở rộng (*Gallery, Noir, Instant Polaroid, Vintage, Film 35mm Sprocket, Cinema, Blur, Palette 5 màu, Postcard, Editorial, Double, Date LED cam, Signature*) đến nghệ thuật (*Float, Soft, Torn giấy xé, Tape băng dính, Wood vân gỗ, Gold mạ vàng, Neon, Notebook, Ticket, Blueprint, Arch, Corners*).
   - Tự động điền và định dạng thông số EXIF (Máy ảnh, Ống kính, Khẩu độ, Màn trập, ISO, Ngày chụp).
3. **LightCraft Suite:**
   - **Tone Curve:** Đường cong nội suy đơn điệu bậc ba Fritsch–Carlson không vọt lố với 5 điểm điều khiển.
   - **3-Way Color Wheels:** Bộ 3 bánh xe màu điện ảnh (Shadows, Midtones, Highlights) trong không gian cảm nhận OkLab/OkLCh.
4. **Phòng tối quang học 3.0:**
   - Black Pro-Mist bloom, Halation quầng đỏ CineStill 800T, Bleach Bypass giữ bạc, Dehaze khử sương mù, Texture tái tạo da, Swirly Bokeh Helios 44-2, 3D Depth Fog, Light Leak 35mm, Hạt muối bạc Dual-Band và Chuyển động Living Film 24fps.
5. **Xuất ảnh & Xuất bảng màu 3D LUT:**
   - Hỗ trợ xuất ảnh JPEG, PNG (nhúng ẩn công thức LSB) và WebP tỉ lệ 1x / 2x (Ultra HD 4K Print Quality).
   - Xuất bảng màu 3D LUT `.cube` (33x33x33) dùng cho DaVinci Resolve, Premiere Pro, Final Cut Pro và CapCut.

