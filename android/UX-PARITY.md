# Android UX — lấy Web làm chuẩn

Chuẩn tham chiếu: `web/index.html`, `web/studio.css` và hành vi trong `web/app.mjs`.

- Mặc định sáng với nền #f4f4f2, panel trắng, điểm nhấn #365f50. Lựa chọn sáng/tối đã lưu của người dùng vẫn được giữ.
- Trang đầu tập trung chọn ảnh từ thiết bị và thử ảnh mẫu; bỏ lối vào camera ở hero/header để khớp web.
- Editor dọc: header IRO/mở ảnh/xuất/theme, hàng undo/redo/tự động/giữ so sánh/reset, ảnh xem trước, panel công cụ dưới ảnh.
- Thứ tự tab: Màu phim → Tinh chỉnh → Khung ảnh → Nâng cao. Tablet/ngang vẫn dùng rail để tận dụng chiều rộng.
- Màu phim: tìm trên toàn bộ catalog theo tên/film/sensor/raw metadata; lọc family; yêu thích lưu trên thiết bị; thẻ ảnh hai cột (ba cột trên tablet). Nạp 24 kết quả mỗi lượt, có Xem thêm; không chỉ giới hạn 25 recipe đầu như carousel cũ.
- Khung: lưới hai cột trên điện thoại và ba cột trên tablet; giữ sửa EXIF.
- Xuất: mặc định kích thước gốc, giữ JPEG/PNG và tùy chọn 2×. Android dùng picker lưu file hệ thống; web dùng tải xuống của trình duyệt.
- Reset toàn ảnh cũng đặt lại trạng thái LightCraft để không còn hiệu ứng nâng cao sau khi bấm reset.

Không chuyển Android sang WebView. Các điều khiển native như thước phơi sáng, system picker và hold-to-compare được giữ để phù hợp điện thoại. Tự động cân bằng native không được đổi nhãn thành AI để tránh nhầm với AI của web. Không tuyên bố mọi tính năng web đã có trên Android.

Kiểm tra 27/10/2026: tools/check.ps1 đạt 36 engine assertions, 6 sizing assertions, URL/checksum và 5 catalog tests. Instrumentation emulator Android 17 đạt các suite engine/sizing/LUT/EXIF/30 khung. Smoke UI: import ảnh đã lưu, bố cục mới, tìm Velvia, favorite/filter, áp recipe, lưới khung, export mặc định original. Evidence tại workspace ChatGPT: qa-20261027/android-ux-*.xml/png/log.

Source chính để mở Android Studio: C:/Users/NamNV.dpt/Documents/photoedit/android. Workspace Android cũ và nhánh origin/android không được tự động ghi đè bởi thay đổi local này.
