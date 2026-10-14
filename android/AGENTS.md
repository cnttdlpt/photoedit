# Quy tắc Workspace (IRO Android — Đồng bộ Git với iOS & Web)

## 1. Tự động đồng bộ và đẩy code lên GitHub sau mỗi lần chỉnh sửa
- **Bắt buộc**: Mỗi khi hoàn tất việc chỉnh sửa mã nguồn trong workspace `c:\Users\NamNV.dpt\Documents\chỉnh sửa ảnh`, bạn **phải** tự động đồng bộ và đẩy (`git push`) lên nhánh `main` của kho lưu trữ GitHub `https://github.com/cnttdlpt/photoedit.git`.
- Thư mục clone Git chính thức của dự án trên máy này là: `C:\Users\NamNV.dpt\Documents\photoedit`.
- Quy trình chuẩn sau mỗi lần sửa code:
  1. Chạy kiểm thử nhanh: `powershell -ExecutionPolicy Bypass -File tools/check.ps1` (và xóa `tools/cache` hoặc `tools/build` tạm nếu có).
  2. Chạy lệnh đồng bộ và đẩy Git tự động: `powershell -ExecutionPolicy Bypass -File tools/sync-git.ps1 -Push -Message "..."`.
  Hoặc kéo code mới từ Git: `powershell -ExecutionPolicy Bypass -File tools/sync-git.ps1 -Pull`.

## 2. Môi trường Build & Gradle
- Khi chạy `./gradlew.bat` trên máy này, luôn đặt `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` (JDK 21 của Android Studio) để tránh lỗi `jlink.exe` với JDK 26 của hệ thống.
