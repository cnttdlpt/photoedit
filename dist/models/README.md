# IRO AI Neural Models

Thư mục lưu trữ các mô hình Neural Network định dạng ONNX Runtime Web cho IRO Studio.

## Tệp mô hình:
- `iro_ai_enhancer.onnx`: Mô hình MobileNetV3-Small (IRO-EnhanceNet) huấn luyện trên Google Colab.
- Tự động nạp bởi `app.mjs` khi người dùng bấm nút **✨ AI Auto**.
- Nếu tệp chưa được tải về, hệ thống tự động kích hoạt bộ xử lý thông minh Heuristic Auto-Balance (phân tích 256 bins histogram và color cast) làm dự phòng mượt mà 100%.

Huấn luyện mô hình tại: `ai_training/iro_ai_photo_enhancer.ipynb` hoặc `ai_training/train.py`.
