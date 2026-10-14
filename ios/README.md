# IRO cho iPhone, iPad và Mac

Bản iOS native bằng **SwiftUI + Swift**, chuyển từ phần cốt lõi Java của `android/`. Yêu cầu iOS 17 trở lên hoặc macOS 14 trở lên qua Mac Catalyst. Không dùng WebView, CocoaPods hay thư viện bên ngoài.

## Mở và chạy

1. Mở `ios/IRO.xcodeproj` trong Xcode.
2. Chọn scheme **IRO** và một **iPhone Simulator** hoặc **My Mac (Mac Catalyst)**, bấm **Run** (`⌘R`).
3. Để cài lên iPhone thật: kết nối iPhone, chọn thiết bị đó, vào target **IRO → Signing & Capabilities**, chọn **Team** Apple của bạn; đổi Bundle Identifier nếu cần. Bật Developer Mode trên iPhone nếu Xcode yêu cầu, rồi bấm Run.
4. Phát hành qua TestFlight/App Store cần tài khoản và cấu hình ký của chủ ứng dụng. Dự án chưa được ký phân phối hoặc phát hành.

Xcode phải hoàn tất bước cài đặt lần đầu, chấp nhận điều khoản Apple và cài iOS Simulator runtime. Chỉnh ảnh và Auto nội bộ chạy offline. Auto AI dùng cấu hình Gemini trong ứng dụng. Xcode project đã có sẵn, không cần chạy script sinh project để mở.

### Debug trên iPhone đã kết nối

Đã cấu hình Automatic Signing với Personal Team và cài bản Debug lên iPhone 16 Pro ngày 09/10/2026. Trong Xcode, chọn scheme **IRO**, chọn thiết bị **HYN-DPT-NAMNV**, đặt breakpoint rồi bấm **Run** (`⌘R`). Xcode sẽ cài bản mới và kết nối LLDB; xem log trong Debug Area (`⇧⌘Y`).

Nếu app đang mở sẵn, dùng **Debug → Attach to Process → IRO**. Developer Mode và tin cậy nhà phát triển đã được bật trên thiết bị hiện tại. Với iPhone khác, cần hoàn tất hai bước này cho máy đó. Profile Personal Team của lần cài này hết hạn **16/10/2026**; build và Run lại từ Xcode để cấp lại khi cần.

## Những phần đã chuyển

- Giao diện studio tiếng Việt, nền xanh than và điểm nhấn xanh kem theo bản Android; hỗ trợ iPhone/iPad.
- Mở ảnh bằng bộ chọn Ảnh của iOS hoặc Tệp; giữ một bản sao ảnh nguồn trong vùng riêng của ứng dụng, giữ ảnh và thông số gần nhất khi mở lại.
- **Auto màu:** cân bằng tự nhiên từ ảnh gốc, thay các lớp LUT/recipe/LightCraft trước đó trong một bước Hoàn tác. AI trả thông số tuyệt đối; kết quả được render mẫu để kiểm tra độ sáng chủ thể và clipping trước khi áp. Auto không tự kéo toàn ảnh tối đi; kết quả cũ bị bỏ nếu bạn đổi ảnh hoặc chỉnh tiếp. Film gợi ý chỉ áp LUT đã gắn đúng film và giữ phần cân bằng, không chọn recipe tùy ý.
- Bộ xử lý màu Swift chuyển trực tiếp từ Java: EV tuyến tính, WB Red/Blue và nhiệt độ tương đối, LUT, tone/color, DR, Color Chrome/Blue, grain, NR, sharpness, clarity. Công việc xử lý chạy ngoài luồng giao diện và hủy preview cũ khi kéo thanh chỉnh.
- Thư viện **392 recipe**: dữ liệu `recipes.json` (391 mục) và `sample-recipe.json` được sao chép nguyên từ Android. Tìm theo tên/film, lọc cảm biến/IR/video, xem nguồn và ghi chú, sửa bản riêng, nhập/xuất JSON tương thích Android.
- LUT 3D `.cube` kích thước 2–65, DOMAIN_MIN/MAX, nội suy trilinear, chỉnh cường độ, gắn theo film và lưu offline. Thumbnail chạy trên ảnh đang mở; recipe chưa có LUT hiện ảnh gốc và nhãn báo rõ.
- Kho LUT GitHub công khai: lấy catalog, tìm kiếm, tải theo commit bất biến, kiểm Git blob SHA-1 và định dạng trước khi lưu. Tối đa 35 MB/file, 1000 LUT/kho. Tự kiểm tra mặc định tắt; khi bật chỉ kiểm tra lúc mở app, tối đa mỗi ngày (lỗi thử lại sau một giờ), tối đa 20 bản mới. Bản mới lưu riêng, không thay LUT đang gắn.
- **30 mẫu khung** giữ ID và tỷ lệ bố cục của Android. Đọc EXIF thật, sửa chữ và tác giả; Hủy không áp thay đổi. Dùng cùng bộ dựng cho thumbnail, preview và ảnh xuất.
- Xuất **JPEG 100% / 95% hoặc PNG**, kích thước gốc, **2×** hoặc tiêu chuẩn tối đa 12 MP. Luôn xử lý từ ảnh nguồn. Gốc/2× báo lỗi khi thiếu bộ nhớ, không tự thu nhỏ. Chia sẻ, lưu vào Tệp hoặc thêm vào Ảnh bằng quyền add-only.

## Phạm vi và giới hạn

Recipe là thông số máy ảnh, không phải LUT hoặc mô phỏng chính thức của Fujifilm. Không tự tạo profile Fuji. Thiếu LUT màu nền được báo rõ; WB tuyệt đối/ISO và trường chưa hỗ trợ được giữ để tham khảo. Màu nền phụ thuộc LUT sRGB và ảnh đầu vào.

Ảnh tĩnh JPEG, PNG, HEIC và định dạng ImageIO hỗ trợ; **không hỗ trợ RAW**, chỉnh Live Photo/video hoặc xuất HDR. Preview tối đa 1280 px. Đầu ra sRGB 8-bit, không chép EXIF sang file xuất; các chữ trong khung được vẽ vào ảnh. Grain/sharpness phụ thuộc độ phân giải. Phóng 2× là nội suy có lọc, không phải AI tăng chi tiết. Xử lý CPU ảnh lớn có thể mất thời gian và bị giới hạn bởi RAM thực tế của thiết bị.

Khung chuyển từ Canvas Android sang CoreGraphics/UIKit; bố cục/ID được giữ nhưng font hệ thống, khử răng cưa và nội suy có thể khác giữa hai nền tảng. Không khẳng định toàn bộ file ảnh xuất giống từng byte với Android.

Chỉnh ảnh thủ công và Auto nội bộ xử lý trên thiết bị. Khi dùng **Auto AI**, bản JPEG thu nhỏ tối đa 384 px cùng số liệu RGB được gửi tới Google Gemini. Trong Cài đặt Gemini, xóa key và bấm Lưu để dùng Auto nội bộ. Phần LUT dùng GitHub HTTPS. Dữ liệu app nằm trong Application Support; có thể nằm trong bản sao lưu hệ thống theo cài đặt iOS của người dùng. Bộ chọn Ảnh không yêu cầu đọc toàn bộ thư viện; quyền thêm ảnh chỉ hỏi khi bấm Lưu vào Ảnh.

## Mã nguồn

| Thư mục | Nội dung |
|---|---|
| `IRO/Core` | Settings, Recipe, CubeLut, ColorEngine, ExportSizing; Foundation thuần |
| `IRO/Imaging` | ImageIO, EXIF, khung, đổi sRGB, xuất ảnh |
| `IRO/Library` | LUT offline, catalog GitHub, JSON và lưu phiên bản |
| `IRO/App` | App và trạng thái editor; xử lý nền, phiên làm việc |
| `IRO/Views` | Giao diện SwiftUI tiếng Việt |
| `IRO/Resources` | Recipe gốc, icon, Info.plist và khai báo quyền riêng tư |
| `Tests` | XCTest, kiểm tra cốt lõi độc lập, fixture đối chiếu Java |

Sau khi thêm hoặc bỏ file Swift, chạy `python3 ios/tools/generate_project.py` từ thư mục gốc để cập nhật project. Script giữ Team, Bundle Identifier và các thiết lập ký cơ bản hiện có; các tùy chỉnh build khác cần lưu riêng trước khi tái tạo project. Icon dùng lại hình học Android; sinh lại bằng `python3 ios/tools/create_icon.py`.

## Kiểm tra

Chạy bộ XCTest trong Xcode bằng `⌘U` hoặc chọn một simulator có trên máy:

```sh
xcodebuild -project ios/IRO.xcodeproj -scheme IRO \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' \
  -derivedDataPath ios/build CODE_SIGNING_ALLOWED=NO test
```

Bộ cốt lõi có thể chạy riêng bằng Command Line Tools, không cần simulator:

```sh
bash ios/Tests/run-core-checks.sh
```

Bộ kiểm tra riêng có các regression đo sáng, cân bằng trắng và LightCraft; xem `VALIDATION.md` để biết số kiểm tra gần nhất. Bộ ban đầu đạt **45 checks**, gồm **8 fixture pixel khớp mã Java Android**, giải mã/áp dụng toàn bộ 391 recipe, biên giá trị, nội suy LUT, kích thước xuất và hủy xử lý. Fixture tham chiếu được sinh từ `AndroidParityFixture.java`. 20 kiểm tra Java gốc cũng đạt. Xem `VALIDATION.md` để biết kết quả build và XCTest gần nhất.
