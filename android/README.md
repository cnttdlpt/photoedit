# IRO Android — mã nguồn để chỉnh sửa

Đây là dự án **Android native bằng Java**, không phải WebView bọc website. Chưa tạo APK, chưa ký hay phát hành. Bản web cũ nằm ngoài thư mục này.

## Mở dự án

1. Mở thư mục `android` bằng Android Studio. Trên Windows, nên chép thư mục này sang đường dẫn không dấu như `C:\Dev\IroAndroid` để tránh kiểm tra đường dẫn của Android Gradle Plugin.
2. Chọn **Gradle JDK = jbr-21** đi kèm Android Studio, hoặc JDK 17. Không chọn Java 26 của hệ thống với phiên bản Gradle này.
3. SDK: Android 35; min Android 9 / API 28. Gradle Wrapper 8.11.1 và Android Gradle Plugin 8.9.2. Android Studio sẽ đề nghị tải dependency còn thiếu khi Sync.
4. Sửa source bình thường. Chỉ khi muốn tạo APK mới dùng Build > Build APK(s). Không có APK được tạo trong lần bàn giao này.

Gradle Wrapper là bản chính thức từ `https://github.com/gradle/gradle/tree/v8.11.1`. SHA-256 của wrapper JAR: `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.

## Những phần đã làm

Giao diện gọn: ảnh chiếm phần chính, Mở ảnh / Chọn màu / Chỉnh tay ở dưới và Xuất ảnh ở trên. Chỉnh tay mặc định thu gọn; khi mở chỉ có bốn thanh cơ bản, các thông số khác nằm trong Nâng cao. LUT, nhập/lưu công thức, thông số nguồn và đặt lại nằm trong menu ⋮. Chạm recipe để áp dụng ngay, giữ một mục để xem thông số và ghi chú áp dụng. Nút Xem gốc nằm trên khung ảnh.

Thiết kế studio: nền xanh than, chữ trắng kem, điểm nhấn xanh nhạt; khung ảnh bo góc, thẻ recipe và nút có phản hồi chạm. `StudioStyle.java` quản lý màu, hình nền và icon vẽ native. Hình minh họa khi chưa mở ảnh chỉ là trang trí, không phải preview màu của LUT. Recipe đang chọn được đánh dấu trong thư viện.

Bản compact: bỏ slogan và tiêu đề phụ; tên màu và nút LUT nằm chung một hàng; thanh công cụ dùng icon cạnh chữ, cao 48 dp; bảng chỉnh tay tối đa 220 dp, ảnh thu nhỏ trong thư viện 72 × 76 dp. Các nút chính vẫn giữ vùng chạm tối thiểu 48 dp.

Icon launcher riêng: sáu lá khẩu độ bao quanh chữ “i”, nền xanh than và các sắc xanh kem. Icon vector thích ứng với mặt nạ launcher, có bản monochrome cho Android 13+. Manifest đã trỏ tới `@mipmap/ic_launcher`. Sửa thiết kế tại `design/create-icon.mjs`, chạy lại bằng Node để sinh vector Android và `design/iro-icon.svg`; `design/iro-icon.png` là bản xem trước. Không cần thư viện hình ảnh hay truy cập mạng.

Danh sách Chọn màu có ảnh thu nhỏ lấy từ ảnh đang mở. Với film đã gắn LUT, thumbnail chạy LUT và thông số recipe thực tế; với film chưa gắn LUT, hiển thị ảnh gốc và nhãn “Chưa có LUT”, không giả làm preview màu Fuji. Mục LUT của tôi có ảnh trước/sau (LUT + chỉnh tay hiện tại). Nếu chưa mở ảnh, các ô nhắc mở ảnh để xem trước. `ThumbnailSession.java` tạo ảnh nền, cache tối đa 64 thumbnail / 2 LUT và hủy công việc khi đóng hộp thoại. Thumbnail giới hạn cạnh dài 192 px, nên độ hạt/nét có thể khác ảnh xuất.

- Thư viện offline: **391 bộ thông số** trích từ **359 bài có recipe**, trong tổng số 364 đường dẫn bài được liên kết từ 10 danh mục công khai của Fuji X Weekly. Thêm 1 recipe từ ảnh người dùng gửi, tổng 392 mục.
- Tìm kiếm theo tên / film simulation; lọc nhóm cảm biến, IR và video. Nhóm cảm biến là nhóm trên trang chỉ mục, **không phải danh sách tương thích chính xác của từng thân máy**.
- Xem thông số nguồn, mở bài gốc, sửa bản riêng của recipe và nhập/xuất JSON.
- Nhập LUT 3D `.cube` 2–65 điểm mỗi chiều; nội suy trilinear, hỗ trợ DOMAIN_MIN/MAX, lưu LUT trong bộ nhớ riêng của app và gắn theo tên film simulation.
- Chỉnh EV, highlight, shadow, color, WB Red/Blue, nhiệt độ tương đối, sharpness, NR, grain strength/size, clarity, Color Chrome / Blue và DR hậu kỳ.
- Chọn ảnh bằng Android document picker; giải mã EXIF orientation bằng ImageDecoder, chuyển ảnh vào sRGB; xem ảnh gốc / ảnh chỉnh; xuất JPEG 95% hoặc PNG bằng trình chọn nơi lưu của hệ thống.
- Giữ thông số và ảnh gần nhất nếu quyền truy cập file còn hiệu lực. LUT lưu lâu dài trong app. Recipe tùy chỉnh được lưu bằng file JSON; thư viện JSON nhập hàng loạt hiện thêm vào phiên làm việc, không tự ghi đè thư viện nguồn.
- Có quyền Internet để lấy danh sách và tải LUT khi bạn yêu cầu. Không tải ảnh lên server. Tự cập nhật mặc định tắt.

## Vì sao bản trước lệch màu và bản này sửa gì?

**Recipe không phải LUT.** Recipe của Fuji X Weekly là các thông số dành cho pipeline JPEG của máy Fujifilm. Tên Classic Chrome không mô tả một phép tăng contrast hoặc giảm saturation đơn giản. Bản trước đã dùng các phép chỉnh tổng quát và vì vậy không có cơ sở để đảm bảo giống Fuji.

Bản này **loại bỏ các công thức giả lập màu nền tự đặt**. Khi chọn recipe mà chưa gắn LUT, app báo `Chưa gắn LUT màu nền`. Chỉ các chỉnh sửa hậu kỳ được áp dụng. App không kèm LUT chính thức hay LUT đã được đo/hiệu chuẩn của Fuji.

Để có màu gần đúng với mục tiêu, chọn recipe rồi nhập LUT nhận đầu vào sRGB phù hợp film simulation và ảnh nguồn. LUT Log/RAW không dùng trực tiếp cho JPEG. LUT do người dùng nhập cũng không tự đảm bảo khớp Fujifilm. Muốn xác nhận độ giống cần ảnh gốc, JPEG Fuji đối chiếu và một profile được hiệu chuẩn trên cùng đầu vào.

Pipeline hiện tại: ảnh giải mã sRGB → EV trong ánh sáng tuyến tính + WB shift tương đối → LUT màu nền → tone/color/Color Chrome → grain → NR/sharpness/clarity. Đây là hậu kỳ gần đúng; không phải thuật toán của Fujifilm.

### Các giá trị được giữ nguyên nhưng không thể áp dụng như trên máy

- ISO là thông số lúc chụp, giữ để tham khảo.
- WB Auto / Kelvin / Daylight được giữ nguyên trong dữ liệu. Chưa biết white point của ảnh nguồn nên không tự áp lại WB tuyệt đối; chỉ Red/Blue và thanh nhiệt độ tương đối tác động lên ảnh. App hiển thị lời giải thích khi áp recipe.
- DR Auto dùng DR100 trong editor; DR200/400 là nén highlight hậu kỳ, không phục hồi chi tiết đã cháy sáng.
- Bù sáng dạng khoảng không tự chọn đầu khoảng; EV giữ 0 để người dùng chọn. Các trường khác có tùy chọn trong ngoặc dùng giá trị đầu được nêu, kèm thông báo.
- D-Range Priority, flash, các dạng B&W toning và các thông số chưa hỗ trợ vẫn hiển thị trong recipe và được liệt kê là chưa áp dụng.
- Grain/NR/sharpness/clarity/Color Chrome chỉ mô phỏng hiệu ứng trên JPEG, không khớp thang máy ảnh tuyệt đối.

## Phạm vi dữ liệu, không phải “toàn bộ app Fuji X Weekly”

Nguồn: https://fujixweekly.com/recipes/ và các bài được liên kết từ 8 nhóm cảm biến, Full Spectrum IR, Video. Thu thập ngày 08/10/2026.

- `app/src/main/assets/recipes.json`: dữ liệu thực tế, nguyên giá trị thông số trong từng block, URL nguồn, nhóm cảm biến, trạng thái cần đối chiếu.
- `IMPORT-REPORT.json`: 16 recipe không có WB trong block nguồn; **không tự bịa WB**. Nhiều mục là B&W cũ.
- 5 liên kết không có block recipe: 3 bài thông tin IR, 2 bài giới thiệu video recipe Early Access cho Patron. Không trích thông số ẩn, không truy cập dữ liệu riêng của app.
- Tên một số biến thể trong bài nhiều recipe được gắn hậu tố “biến thể” để phân biệt. Cùng tên trên cảm biến khác vẫn giữ riêng.
- Chỉ đưa tên và thông số kỹ thuật vào thư viện. Không đóng gói ảnh mẫu hay nội dung bài viết.
- Đây là trích xuất tự động có kiểm tra cấu trúc và một số mẫu đối chiếu, chưa xác minh thủ công từng thông số của 391 recipe. Có URL để người dùng đối chiếu hoặc sửa.
- Tham khảo trang chính thức: https://fujixweekly.com/app/ — thư viện hơn 400 recipe; một số recipe chỉ dành cho Patron. Không đồng nhất 391 mục đã thu thập với toàn bộ cơ sở dữ liệu của app.

## Sửa ở đâu?

- `app/src/main/java/vn/iro/editor/MainActivity.java`: giao diện native, thư viện, file picker, lưu LUT, xuất ảnh.
- `ColorEngine.java`: toàn bộ pipeline xử lý màu, dễ thay bằng OpenGL hay profile đo thực tế.
- `CubeLut.java`: đọc `.cube` và nội suy màu.
- `Recipe.java`: giữ dữ liệu gốc và ánh xạ sang các chỉnh sửa hậu kỳ; thông số chưa hỗ trợ được báo rõ.
- `Settings.java` và `SettingsCodec.java`: các giá trị chỉnh, lưu/nhập/kiểm tra JSON.
- `app/src/main/assets/recipes.json`: thêm/sửa thư viện recipe.
- `app/src/main/assets/sample-recipe.json`: thông số chính xác từ ảnh tham khảo người dùng gửi.

## Cập nhật dữ liệu công khai

Chạy trong thư mục `android`, cần Node.js và PowerShell 7. Script chỉ đọc HTTPS của `fujixweekly.com`, 3 kết nối đồng thời, không tải ảnh. Nếu website đổi cấu trúc HTML cần cập nhật parser và xem báo cáo.

```powershell
New-Item -ItemType Directory -Path tools/cache -Force
Invoke-WebRequest https://fujixweekly.com/recipes/ -OutFile tools/cache/index.html
node tools/discover.mjs
./tools/fetch-public.ps1 -ListPath tools/categories.json
node tools/catalog.mjs discover
./tools/fetch-public.ps1 -ListPath tools/urls.json
node tools/catalog.mjs extract
```

Ngày `retrievedOn` trong script cần đổi theo lần cập nhật. Cache HTML chỉ phục vụ trích xuất và không nằm trong source control hay app.

## Kiểm tra và giới hạn

Đã kiểm tra biên dịch toàn bộ Java với Android API 35 (`javac --release 17`); 14 kiểm tra bộ xử lý ảnh và 5 kiểm tra thư viện/parser đạt. Chưa chạy Gradle build đầy đủ hoặc cài lên điện thoại, vì lần này chỉ bàn giao source, không đóng gói APK.

Chạy lại bằng `./tools/check.ps1` trong thư mục Android, có thể truyền `-Sdk` và `-JavaHome` nếu khác đường dẫn mặc định. Lệnh chỉ tạo `.class` tạm trong `tools/build`, không tạo APK.

Không hỗ trợ RAW, không giữ EXIF trong file xuất. Preview tối đa 1280 px, xuất tối đa 12 MP hoặc thấp hơn theo bộ nhớ; độ hạt/nét thay đổi theo độ phân giải. LUT CPU trên ảnh lớn có thể chậm. Giao diện dùng Android framework widgets để dễ sửa và giảm dependency. Không chứa telemetry, tài khoản hay API key.

Cấu hình AGP tham khảo: https://developer.android.com/build/releases/agp-8-9-0-release-notes


## LUT trực tuyến

Mở **⋮ → LUT trực tuyến**, bấm **Tìm / cập nhật**, tìm tên rồi chọn **Tải / dùng LUT**. LUT được kiểm tra định dạng và checksum trước khi lưu, gắn riêng cho film đang chọn. Ảnh xem trước chỉ hiện sau khi tải, dùng ảnh bạn đang chỉnh.

Nguồn mẫu: [t0saki/lumix-original-looks](https://github.com/t0saki/lumix-original-looks), giấy phép MIT; tác giả thiết kế cho LUMIX Standard/sRGB. Đây là màu sáng tạo, không phải bộ Fuji chính thức; kết quả phụ thuộc ảnh đầu vào. **Đổi nguồn** nhận kho GitHub công khai dạng owner/repository hoặc URL kho. App tìm file .cube trong kho đã chọn, không tìm toàn bộ Internet. Kiểm tra hướng dẫn đầu vào và giấy phép của nguồn trước khi dùng.

Bật **Tự kiểm tra / tải bản mới mỗi ngày** để kiểm tra lúc mở app, tối đa một lần/ngày (thử lỗi không quá một lần/giờ). Chỉ tải phiên bản mới của LUT đã lưu, tối đa 20 file mỗi đợt. Không chạy dịch vụ nền khi app đóng. Bản mới được lưu riêng, không tự thay LUT đang gắn với ảnh; chọn dùng bản mới trong danh sách khi muốn. Tắt tùy chọn để chỉ kiểm tra thủ công.

LUT đã tải dùng offline. Mất mạng hoặc tải lỗi giữ bản cũ. Tải qua HTTPS, ghim theo commit và đối chiếu Git blob SHA-1; giới hạn 35 MB/file, 1000 LUT/kho và hỗ trợ 3D .cube kích thước 2–65. Không hỗ trợ LUT 1D, ZIP, nguồn cần đăng nhập hoặc Git LFS. GitHub có giới hạn số lần gọi API. Ảnh không gửi lên mạng. Phiên bản cũ được giữ để không phá liên kết film hiện tại.


## Chọn ảnh từ Bộ sưu tập

Nút **Mở ảnh** và vùng ảnh trống gọi ứng dụng **Bộ sưu tập/Gallery** bằng ACTION_PICK. Nếu có nhiều ứng dụng phù hợp, Android có thể hỏi chọn ứng dụng. Không ép mở Google Photos hay Photo Picker. Nếu máy không có ứng dụng xử lý thao tác này, app dùng trình chọn tài liệu lọc ảnh.

Ảnh được sao chép nguyên bản vào vùng riêng của app (tối đa 100 MB/ảnh) để vẫn đọc được khi mở lại hoặc xuất ảnh, không phụ thuộc quyền tạm thời của Gallery. Không sửa ảnh gốc và không yêu cầu quyền đọc toàn bộ thư viện. Các bản sao cũ được dọn khi mở ảnh thành công, giữ ảnh hiện tại và ảnh đang xuất. Hủy chọn hoặc đọc ảnh lỗi giữ ảnh đang chỉnh.


## Xuất ảnh độ phân giải cao

Trong **Xuất ảnh**, chọn **Phóng lớn 2×** để tăng gấp đôi chiều rộng và chiều cao từ ảnh nguồn (2000 × 1500 → 4000 × 3000), hoặc **Giữ kích thước gốc**. Mặc định hộp xuất chọn 2× và JPEG 100%; có thêm JPEG 95% và PNG nén không mất dữ liệu. Ảnh được chỉnh màu ở kích thước nguồn rồi phóng bằng nội suy có lọc, không phải AI tái tạo chi tiết. PNG/JPEG xuất từ ảnh đã xử lý sRGB 8-bit.

Chế độ gốc/2× kiểm tra bộ nhớ và báo lỗi nếu không đủ, không âm thầm thu nhỏ. **Tiêu chuẩn** giữ giới hạn 12 MP và giảm theo bộ nhớ có thể dùng. Ảnh preview vẫn tối đa 1280 px; không dùng preview để xuất. Kích thước thực tế hiện khi lưu thành công. File lớn có thể cần thời gian và dung lượng hơn; nếu xử lý lỗi sau khi chọn đích, trình cung cấp tài liệu có thể để lại file rỗng.


## Khung thông tin ảnh

Chức năng Camera đã được gỡ, bao gồm nút, activity, mã GPU và quyền Camera/lưu thư viện cũ. App tiếp tục nhập từ Bộ sưu tập.

Mở ảnh rồi chọn **Khung** ở thanh trên (hoặc **⋮ → Khung thông tin ảnh**). Có ba mẫu:
- **Classic**: giữ nguyên ảnh, thêm dải trắng dưới ảnh với tên máy bên trái, thông số và ngày chụp bên phải.
- **White**: viền trắng và cột thông tin bên phải, không crop ảnh.
- **Glass**: chữ trên dải tối chuyển sắc ở dưới ảnh, giữ kích thước và chèn đè lên phần ảnh dưới.

Đọc EXIF từ ảnh gốc: hãng/model, tiêu cự, khẩu độ, thời gian phơi sáng, ISO, thời gian chụp. Ưu tiên tiêu cự quy đổi 35 mm nếu có và ghi (eq.). Không có EXIF thì để trống, không lấy model điện thoại đang chạy app hoặc thông số recipe để đoán. Có thể sửa mọi trường và thêm tên tác giả/chữ ký. Chưa hỗ trợ logo hãng hay logo tùy chỉnh; tên máy là chữ.

**Áp dụng** cập nhật ảnh xem trước và lưu nội dung chỉnh tay cho ảnh hiện tại; **Hủy** bỏ thay đổi; **Bỏ khung** tắt chèn. Đổi ảnh sẽ đọc lại thông tin của ảnh mới. Không sửa file gốc. Khi xuất JPEG/PNG (kể cả 2×), khung và chữ được vẽ vào file kết quả từ cùng bộ dựng dùng cho preview. Hai mẫu trắng làm tăng kích thước canvas; màn hình báo kích thước cuối cùng sau khi lưu. Hộp xuất ghi rõ khung đang chọn. Chữ quá dài được co/thu gọn để không tràn cột.


## Thư viện 18 mẫu khung

Trong **Khung → Chọn mẫu**, chọn nhóm và chạm ảnh minh họa. Tất cả thumbnail dùng ảnh và thông tin hiện tại, dựng từ cùng mã với ảnh xuất. Bộ chọn chia nhóm Thông số máy, Tối giản, Film/hoài cổ, Sáng tạo. Mẫu số 1–3 giữ nguyên ID để tương thích cài đặt cũ.

Các mẫu: Classic, White, Glass, Dark, Centered, Gallery, Noir, Instant, Vintage, Film 35, Cinema, Blur, Palette, Postcard, Editorial, Double, Date, Signature. Date chỉ dùng ngày chụp; Signature dùng chữ ký (hoặc tên máy khi trống). Không tự tạo ngày chụp nếu thiếu EXIF. Blur dùng ảnh thu nhỏ rồi nội suy làm nền mềm; ảnh chính giữ nguyên. Palette lấy 5 mẫu màu dọc hàng giữa ảnh, không phải phân tích màu chủ đạo. Các viền phim được vẽ bằng hình học, không phải scan phim thật.

Đây là bộ mẫu tự thiết kế theo các nhóm bố cục phổ biến, không phải toàn bộ mẫu tồn tại trên Internet và không sao chép gói template/logo trả phí. Tham khảo nhóm kiểu từ [Fotor](https://www.fotor.com/features/frames), [Framio](https://framio.org/), [Porder](https://porder.io.vn/) và [FrameGen](https://framegen.vercel.app/photo-frame). Không tải ảnh của người dùng hoặc ảnh minh họa của các nguồn này vào app; thư viện dùng offline.


### Bổ sung 12 mẫu — tổng 30 khung

Vào **Khung → Chọn mẫu → Mẫu mới**: Float (đổ bóng), Soft (bo tròn phần giấy ngoài), Torn (mép giấy xé), Tape (scrapbook băng dính), Wood (vân gỗ), Gold (viền vàng), Neon (ánh sáng viền), Notebook (giấy kẻ ô), Ticket (vé kỷ niệm), Blueprint (bản vẽ xanh), Arch (giấy dạng vòm), Corners (góc giữ ảnh). ID 1–18 giữ nguyên. Các mẫu mới vẫn dùng ảnh/thông tin hiện tại và cùng renderer khi xuất. Tape/Corners có trang trí che một phần nhỏ góc ảnh; các mẫu giấy bo/vòm giữ ảnh chính hình chữ nhật.

Tham khảo nhóm ý tưởng mới từ [Canva — ripped paper](https://www.canva.com/templates/s/ripped-paper/) và [Fotor — frames](https://www.fotor.com/features/frames). Đây là các mẫu vẽ bằng mã riêng, không tải hoặc sao chép template trả phí. Đã kiểm tra biên dịch; mã kiểm thử thiết bị duyệt toàn bộ 30 mẫu, chưa xác minh render bộ mới trên Android thực tế.
