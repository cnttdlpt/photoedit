#!/bin/bash
set -e

DEVICE_ID="00008140-0016614E0281401C"
APP_PATH="/Users/nguyennam/Library/Developer/Xcode/DerivedData/IRO-hbdylwvjfzayowafiszmabxbujrj/Build/Products/Debug-iphoneos/IRO.app"

echo "Đang kiểm tra kết nối thiết bị iPhone ($DEVICE_ID)..."
if [ ! -d "$APP_PATH" ]; then
    echo "Đang build gói ứng dụng iOS..."
    xcodebuild -project ios/IRO.xcodeproj -scheme IRO -destination 'generic/platform=iOS' -configuration Debug -allowProvisioningUpdates build
fi

echo "Đang cài đặt IRO lên iPhone..."
xcrun devicectl device install app --device "$DEVICE_ID" "$APP_PATH"
echo "✅ Cài đặt thành công IRO lên iPhone!"
