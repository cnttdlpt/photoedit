# Kiểm tra bản chuyển iOS

- Project Xcode, Info.plist và PrivacyInfo: `plutil -lint` đạt.
- Ngày 10/10/2026: build Debug iOS Simulator arm64 và bản Debug-iphoneos có ký thành công, Xcode 27.0. Bản Simulator x86_64 từng build thành công ở lượt 09/10, chưa chạy lại kiến trúc này với bản vá mới.
- Swift core độc lập bằng Command Line Tools: **62 checks đạt**, gồm 9 fixture checksum RGBA khớp mã Java, toàn bộ 391 recipe trong catalog và hủy tác vụ.
- Android Java gốc: 14 EngineTest + 6 ExportSizingTest đạt; catalog/parser Node: 5/5 đạt. Chưa build APK trong lượt này.
- Ngày 10/10/2026, XCTest trên iPhone 18 Pro Simulator, iOS 27.0: **53/53 đạt, 0 lỗi, 0 bỏ qua, không có runtime warning** (19 Core, 15 Library, 9 Imaging, 10 EditorModel).
- Imaging kiểm tra đủ 8 EXIF orientation, alpha PNG/JPEG, thông tin nguồn, toàn bộ 30 mẫu khung, xuất từ nguồn gốc/2×, RAW và tệp lỗi, giới hạn bộ nhớ và chữ trên khung khớp giữa preview/export.
- EditorModel kiểm tra reset chỉ tạo một bước undo, khôi phục metadata/ghi chú/trạng thái tự động, đóng ảnh xóa LUT cũ, xuất vẫn hoàn tất preview, giữ ảnh trước khi lưu phiên thất bại, giữ địa chỉ kho đang gõ và bỏ kết quả thao tác cũ khi người dùng đã chuyển trạng thái.
- Lỗi bộ nhớ Simulator ở lượt 09/10 đã được sửa bằng ngân sách dự phòng khi `os_proc_available_memory()` trả 0, trừ resident memory. Logic giới hạn RAM trên iPhone thật giữ nguyên.
- Giao diện đã biên dịch; chưa kiểm tra thao tác trực quan vì công cụ điều khiển giao diện máy chưa được cấp quyền. Tải GitHub được kiểm tra logic/checksum và lưu trữ bằng fixture; chưa chạy tải mạng thực tế.
- Ngày 10/10/2026: đã cài bản sửa và mở IRO thành công trên iPhone 16 Pro bằng devicectl. Chưa kiểm thử thao tác trực quan hoặc chạy XCTest trên máy thật với bản vá mới.
- Ngày 09/10/2026: build **Debug-iphoneos** thành công bằng Personal Team, xác minh chữ ký và profile chứa đúng iPhone; entitlement `get-task-allow = true`. Đã cài và mở IRO trên **iPhone 16 Pro, iOS 27.2**, sau khi người dùng xác nhận tin cậy nhà phát triển. Chưa chạy toàn bộ XCTest trên thiết bị vật lý; chưa ký phân phối hoặc gửi TestFlight/App Store.
- Đã gắn LLDB vào tiến trình IRO trên iPhone thật, xác nhận trạng thái dừng tại main thread (`SIGSTOP` do attach), rồi detach để ứng dụng tiếp tục chạy. Team đã lưu trong project cho app/test; script sinh project giữ các thiết lập ký cơ bản.

XCTest bao gồm bộ xử lý, lưu LUT/recipe, checksum, định hướng EXIF, alpha PNG/JPEG, 30 khung và xuất từ ảnh nguồn. Kiểm thử cấu trúc không thay thế đối chiếu màu với JPEG Fujifilm thực tế.

## Các lỗi đã sửa trong lượt 10/10/2026

- Auto-balance từng đẩy vùng tối theo chiều ngược với renderer và tính cả pixel trong suốt; đã sửa và kiểm tra hướng thay đổi pixel.
- Tối ưu bộ lọc không gian làm lệch làm tròn 1 byte so với Android; đã khôi phục thứ tự phép nhân. Renderer song song kiểm tra hủy giữa các nhóm pixel để không giữ công việc xem trước cũ quá lâu.
- Hoàn tác thiếu chữ trên khung, ghi chú recipe và trạng thái tự cân bằng; reset tạo thừa bước undo. Chọn lại cùng khung hoặc kéo chưa đổi giá trị không còn xóa Redo.
- Đóng ảnh không xóa LUT đang dùng; kết quả tải LUT/recipe cũ có thể ghi đè thao tác mới. Đã tách phiên bản yêu cầu thay màu và yêu cầu nạp LUT, có regression cho thao tác chồng nhau.
- Xuất ảnh trong lúc đang render từng hủy preview mà không tạo lại; kết quả xuất đã cũ do LUT nền thay đổi cũng từng được công bố. Preview được tiếp tục và đầu ra cũ bị loại bỏ.
- Chữ khung được chuẩn hóa giống nhau khi xem trước và xuất. Lưu vào Ảnh/recipe/gắn LUT trả kết quả thành công rõ ràng, tránh hiển thị thành công hoặc đóng form khi lưu thất bại.
- Nhập LUT dọn file mới nếu ghi manifest thất bại, kiểm checksum khi đọc/gắn LUT, và từ chối recipe chứa dữ liệu không thể nhập lại hoặc render.

Báo cáo tích hợp được lưu trong `build/Regression-Verified-20261010.xcresult`. Bản vá này chưa được kiểm thử thao tác trực quan hoặc XCTest trên iPhone thật.
