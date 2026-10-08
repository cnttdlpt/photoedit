# IRO — Phòng màu ảnh

App chỉnh màu ảnh chạy trong trình duyệt; ảnh không gửi lên máy chủ. Không cần cài thư viện.

Chạy `npm start`, mở http://127.0.0.1:4173. Kiểm tra bộ xử lý bằng `npm test`.

- Nhập JPEG, PNG, WebP, AVIF; kéo thả ảnh vào khung xem trước.
- Chín lựa chọn màu, gồm Original và tám mô phỏng lấy cảm hứng từ Fuji. Đây là mô phỏng gần đúng, không phải thuật toán Fujifilm chính thức.
- Nhập LUT 3D .cube 2–65 điểm mỗi chiều, hỗ trợ DOMAIN_MIN/MAX và nội suy trilinear. LUT lưu trong IndexedDB của trình duyệt.
- Recipe mẫu đúng các giá trị trên ảnh tham khảo. DR và WB Auto được mô phỏng trên ảnh đã giải mã; Auto giữ cân bằng trắng sẵn có. ISO là thông số tham khảo lúc chụp.
- Xuất JPEG/PNG/WebP ở kích thước nguồn, giới hạn 24 MP. Preview có cạnh dài tối đa 1400 px. Hạt và nét có thể khác nhẹ giữa preview và ảnh xuất do độ phân giải.
- Lưu/nhập recipe JSON. LUT được nhập riêng, recipe tham chiếu LUT bằng tên.

Ảnh RAW/HEIC, LUT 1D, profile ICC nâng cao và metadata EXIF đầu ra chưa hỗ trợ. Grain và NR là hiệu ứng hậu kỳ gần đúng. Xuất ảnh dùng không gian màu canvas của trình duyệt. Phông chữ Google Fonts cần mạng; khi offline dùng sans-serif hệ thống.
