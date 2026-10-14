# Kiểm tra bản chuyển iOS

## Kiểm tra bổ sung: Auto làm tối chủ thể — 13/10/2026

Phản ánh tiếp theo của người dùng là có cơ sở. Bộ kiểm tra trước đã bỏ sót cảnh có nền sáng và chủ thể tối. Ngưỡng high-key dựa P5 chỉ bảo vệ ảnh gần như toàn trắng; ở ảnh hỗn hợp, thuật toán vẫn kéo median về 0.28 linear và cho EV âm tới −1.2. Gemini còn được phép −0.3 EV quanh baseline 0 và cộng dồn DR/highlight/shadow, làm ảnh tối thêm.

Đã sửa:

- Auto nội bộ không tự giảm EV hay làm sâu shadow. Chỉ giảm highlight khi có clipping thực, giữ nền trắng bình thường và nâng nhẹ vùng tối.
- Thêm `AutoBalanceGuard`: render tối đa 8.192 pixel mẫu bằng chính ColorEngine, đo độ sáng theo các nhóm pixel được chọn từ **ảnh gốc**, kiểm tra vùng tối/trung gian, độ sáng tổng thể và tỷ lệ clipping. Bù EV tối thiểu nếu cần; giảm cường độ đề xuất hoặc trở về trung tính nếu không đạt. Cho phép giảm vùng trắng gắt nhưng không kéo chủ thể tối theo.
- Cả AI thành công và Auto dự phòng đều qua kiểm tra này trên ImageWorker, sau đó kiểm lại revision/cancellation trước khi áp. Preview và xuất file dùng cùng settings đã kiểm chứng.
- Prompt yêu cầu không giảm EV toàn ảnh/không dùng shadow dương và không cộng dồn giảm sáng tùy ý. Việc bảo vệ không phụ thuộc model tuân thủ prompt.

### Bằng chứng pixel thật (thang 0–255)

| Fixture | Ảnh gốc | Trước sửa bổ sung | Sau sửa |
|---|---:|---:|---:|
| Chủ thể / nền sáng | 60 / 210 | 35 / 144 | 60 / 210 |
| Chủ thể / nền trắng | 25 / 250 | 14 / 169 | 29 / 250 |
| Ảnh hỗn hợp — độ sáng trung bình, AI | 133,10 | 116,35 | 132,81 |
| Vùng trung gian trong ảnh có trắng gắt, AI | 127,56 | 125,25 | 127,28 |
| Vùng trắng gắt, AI | 248,33 | 239,67 | 243,33 |

Đã chứng minh test pixel thất bại với guard no-op trước khi chạy lại và đạt với guard thật; không nới tolerance để làm test xanh. Hai test editor mới chạy 4 fixture mỗi test (tối, trung tính, sáng, ngược sáng), kiểm tra **cả preview và PNG xuất**, bao gồm Gemini thành công và fallback lỗi mạng.

### Kết quả xác minh

- **121 portable core checks đạt**, 9 fixture Android RGBA giữ nguyên.
- **18/18 Gemini tests đạt** trên native macOS, toàn bộ API response giả lập.
- **89/89 XCTest đạt trên Mac Catalyst**, `build/QA-Brightness-Mac-20261013.xcresult`; sau cập nhật assertion Undo/Redo, **20/20 EditorModelTests** chạy lại đạt tại `build/QA-Brightness-Mac-Editor-Final.xcresult`.
- **89/89 XCTest đạt trên iPhone 16 Pro thật**, `build/QA-Brightness-iPhone-20261013.xcresult`. Xcode đã cài bản Debug mới để chạy tests; không thay tài khoản/ảnh người dùng.
- **89/89 XCTest đạt trên iPhone 18 Pro Simulator**, `build/QA-Brightness-iOS-20261013.xcresult`. Thời gian khởi động/cài app khoảng 5 phút; tests chạy khoảng 7 giây.

Bản chạy cho người dùng: đã cập nhật `/Applications/IRO.app` từ `build/Verified-Mac-Brightness-20261013/Build/Products/Debug-maccatalyst/IRO.app`; xác minh chữ ký và SHA-256 executable khớp bản build mới. App cũ được giữ ở `build/Backups/IRO-before-brightness-1791877348.app`. Bản Debug mới đã được Xcode cài lên iPhone 16 Pro trong lượt chạy tests. Trên Mac cần thoát app đang chạy và mở lại; trên cả hai nền tảng bấm Auto lại vì các thông số tối đã lưu trước đó không tự đổi khi cập nhật app.

Giới hạn: fixture pixel kiểm chứng lỗi làm tối của pipeline; chưa có ảnh thực tế người dùng gửi và không gọi API Gemini thật trong các test này. Không khẳng định mọi đánh giá thẩm mỹ/nhận diện cảnh đều chính xác. API key/quota không được xác minh. Ảnh SDR sRGB và HEIC/HDR hiển thị trong Photos có thể khác cách trình bày; lượt này đo trên cùng ảnh nguồn trong editor.

## Đợt kiểm tra AI màu — 13/10/2026

- Xcode 27.0, Mac Catalyst arm64: **81/81 XCTest đạt**, 0 lỗi/0 bỏ qua. Báo cáo `build/QA-Mac-20261013-R3.xcresult`. Chạy bằng chữ ký Apple Development đã cấu hình; ký ad-hoc gặp lỗi nạp test bundle do library validation.
- Swift core portable: **111 kiểm tra đạt**, gồm **9 fixture RGBA khớp Android**.
- Riêng Gemini: **14 test** dùng URLProtocol giả lập, bao phủ request/header, JSON chia phần, loại thought, response bị cắt, sai số kênh HSL, NaN/Infinity, lỗi xác thực, cancellation và retry. Đã chạy cả native macOS độc lập và trong bộ Mac Catalyst.
- iPhone 18 Pro Simulator, iOS 27.0 arm64: **81/81 XCTest đạt**, 0 lỗi/0 bỏ qua. Báo cáo `build/QA-iOS-20261013-R2.xcresult`. Simulator mất khoảng 3 phút khởi động/cài app; thời gian chạy tests khoảng 2 giây.

Các lỗi đã sửa:

1. Auto phân tích ảnh gốc nhưng giữ LUT/recipe/mask cũ, khiến màu bị chồng. Auto nay bắt đầu từ ảnh gốc với thông số màu trung tính; một bước Hoàn tác phục hồi trạng thái trước đó.
2. Đo EV từ chênh lệch byte sRGB dễ làm cháy sáng. Đã dùng log2 trong ánh sáng tuyến tính và giới hạn theo vùng sáng từng kênh. Giữ ảnh trung tính và cảnh sáng chủ đạo, không mặc định tăng saturation/sharpness/clarity.
3. Cân bằng trắng từ trung bình toàn ảnh nhầm màu vật thể với ám màu. Chỉ ước lượng nhiệt độ khi có đủ pixel gần trung tính.
4. AI trả thông số tuyệt đối nhưng bị trộn lại với baseline và các lớp màu phụ. EV nay được giới hạn đúng phạm vi quanh baseline; Auto tự nhiên không thêm curve/grading/mask. Prompt khớp thông số thực được áp.
5. Phản hồi AI cũ có thể ghi đè thao tác mới, hủy request vẫn chạy fallback. Đã kiểm tra revision/request, bỏ cả kết quả và fallback quá hạn; Undo/Redo giữ cả phần mô tả AI.
6. Film gợi ý từng tìm theo từ đầu và áp recipe đầu tiên, thay toàn bộ thông số Auto. Nay khớp film chính xác, chỉ áp LUT tương ứng; nếu thiếu LUT thì báo rõ.
7. Thumbnail khung bỏ qua LightCraft. Đã dùng cùng màu với preview/export và không vẽ lớp phủ mask vào thumbnail/xuất ảnh.
8. Curve nhiều điểm bị coi là identity khi hai đầu không còn ở (0,0)/(1,1). Đã sửa điều kiện và kiểm tra pixel đầu ra.
9. Script tạo project làm mất Mac Catalyst; đường dẫn test host chưa thích ứng cấu trúc app Mac. Đã cập nhật và giữ thiết lập ký/optimization đang có. Form Gemini chỉ áp min-width 520 trên Mac.
10. Đọc Gemini JSON từng lấy riêng đoạn text cuối và có thể dùng thought như kết quả. Đã nối answer parts, yêu cầu STOP và kiểm tra dữ liệu; không retry lỗi xác thực/hủy. Xóa key rồi lưu thực sự tắt AI, không quay lại key mặc định.

Giới hạn: không gọi Gemini thật, không gửi ảnh của người dùng, chưa đối chiếu ảnh gốc/kết quả mà người dùng báo sai; chưa kiểm thử thao tác trực quan hoặc cài bản sửa lên iPhone thật. Test mạng dùng dữ liệu giả lập nên không xác nhận hạn mức/API key hoặc chất lượng nhận diện cảnh ngoài thực tế. Repo vẫn có key tích hợp sẵn từ cấu hình phát triển hiện có; lượt này giữ tương thích và không kiểm tra key qua mạng.

Tham khảo API chính thức: [GenerateContent và finishReason](https://ai.google.dev/api/generate-content), [danh sách model](https://ai.google.dev/gemini-api/docs/models), [vòng đời model](https://ai.google.dev/gemini-api/docs/deprecations). Các model 3.x hiện có được kiểm tra qua tài liệu; không suy ra model nào hoạt động được với key hiện tại.

Lệnh chạy lại:

```sh
bash ios/Tests/run-core-checks.sh
xcodebuild -project ios/IRO.xcodeproj -scheme IRO \
  -destination 'platform=macOS,variant=Mac Catalyst' \
  -derivedDataPath ios/build/QA-Mac CODE_SIGN_IDENTITY='Apple Development' test
xcodebuild -project ios/IRO.xcodeproj -scheme IRO \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' \
  -derivedDataPath ios/build/QA-iOS CODE_SIGNING_ALLOWED=NO test
```

## Kết quả trước đợt kiểm tra này

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
