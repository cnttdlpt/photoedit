import Foundation

/// Determines the decoded source size before the optional export upscale pass.
public enum ExportSizing {
    public static func dimensions(width w: Int, height h: Int, scale: Int, fullSize: Bool,
                                  pixelBudget: Int) throws -> (width: Int, height: Int) {
        guard w > 0, h > 0, (scale == 1 || scale == 2), pixelBudget > 0, w <= Int.max / h else {
            throw CoreError.invalidValue("Kích thước xuất không hợp lệ")
        }
        let pixels = w * h
        if fullSize {
            // Match the Android 32-bit image-array bound as well as the memory budget.
            let limit = Int(Int32.max)
            guard pixels <= pixelBudget, w <= limit / scale, h <= limit / scale,
                  pixels <= limit / (scale * scale) else {
                throw CoreError.invalidValue("Không đủ bộ nhớ cho độ phân giải này. Chọn kích thước gốc hoặc tiêu chuẩn; app không tự giảm kích thước ở chế độ 2×.")
            }
            return (w, h)
        }
        let ratio = min(1, sqrt(Double(min(12_000_000, pixelBudget)) / Double(pixels)))
        return (max(1, Int(floor(Double(w) * ratio))), max(1, Int(floor(Double(h) * ratio))))
    }
}
