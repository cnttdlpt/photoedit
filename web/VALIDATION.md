# Kiểm tra lỗi nút chức năng — 16/10/2026

Nguyên nhân chính: `app.mjs` import ba tên không được `engine.mjs` export. ES module dừng ngay khi liên kết, trước khi đăng ký các nút. Các kiểm thử pixel cũ vẫn đạt vì không chạy phần khởi tạo giao diện.

Đã đồng bộ import/preset/khung, cách đọc catalog, trường histogram, Chrome, HSL, bánh xe sáng, tone curve, xoay, hoàn tác và xuất ảnh. Giữ sửa đổi mở ảnh Safari và API đồng bộ hiện có. JavaScript/CSS/HTML được yêu cầu kiểm tra lại cache; HTML và module dùng mã phiên bản `20261016-fix1` để bỏ qua bản cũ đã cache một tháng.

## Kiểm thử

- 19/19 engine và hợp đồng app/engine: bao gồm 12 preset khác nhau, HSL/wheels/vignette, recipe, LUT và dữ liệu mặc định giữ nguyên pixel.
- 7/7 tích hợp DOM + Canvas trong Node: khởi động thật từ index, tạo ảnh mẫu, nút chọn ảnh/LUT, chỉnh sáng/undo, xoay, tone curve, khung, thư viện/tìm kiếm/yêu thích, bảng so sánh, PNG/LUT, LightCraft/reset và localStorage lỗi.
- 3/3 HTTP: MIME JavaScript, cache với URL có phiên bản, gzip và Vary. Chạy trên cổng loopback tạm; lượt đầu bị sandbox chặn mở cổng, lượt chạy được cấp quyền đã đạt.
- 8/8 bộ kiểm thử bản `dist` tại thư mục gốc đạt sau khi đồng bộ.
- `git diff --check` đạt.

Các bài DOM dùng LinkeDOM và Canvas trong Node, mọi fetch đều là fixture cục bộ. Chưa kiểm thử trực quan trên trình duyệt: công cụ trình duyệt bị từ chối truy cập website. Không xem các bài DOM là kiểm thử tương thích Safari/Chrome thực tế.

## Triển khai

Máy chủ: `nguyennam@192.168.88.230`, Apache, document root `/var/www/html`.
Bản sao trước cập nhật: `/home/nguyennam/iro-backups/web-controls-20261016-1/` (`live-before.tgz`, `source-before.tgz`).
Chỉ thay các tệp mã/config liên quan; không sửa dữ liệu trong `data` và `uploads`.
Cập nhật hoàn tất lúc 22:24 ngày 16/10/2026 (Asia/Ho_Chi_Minh). Kiểm tra trước khi ghi xác nhận các tệp trên máy chủ chưa bị thay đổi kể từ bản sao lưu. SHA-256 của năm tệp live khớp bản đã kiểm thử; các tệp nguồn và bốn tài sản dist cũng khớp. Apache trả HTTP 200 cho HTML, app, engine, worker và CSS, với Cache-Control: no-cache và MIME đúng (text/javascript cho module). Không cần khởi động lại Apache.
