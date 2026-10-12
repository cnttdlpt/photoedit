package vn.iro.editor;

/**
 * Bộ tính toán kích thước giải mã ảnh nguồn trước khi xuất (Export Sizing).
 * Chuyển đổi trực tiếp từ {@code ios/IRO/Core/ExportSizing.swift}.
 *
 * <p>Quy tắc kiểm soát bộ nhớ khi xuất ảnh:
 * <ul>
 *     <li>Chế độ <b>Tiêu chuẩn ({@code fullSize = false})</b>: giới hạn tối đa 12 MP (hoặc thấp hơn nếu RAM trống ít),
 *         tự động thu tỷ lệ đồng đều theo căn bậc hai số điểm ảnh.</li>
 *     <li>Chế độ <b>Kích thước gốc ({@code fullSize = true, scale = 1})</b> và <b>Phóng lớn 2× ({@code fullSize = true, scale = 2})</b>:
 *         kiểm tra nghiêm ngặt ngân sách điểm ảnh ({@code pixelBudget}) và giới hạn mảng 32-bit ({@code Integer.MAX_VALUE}).
 *         Nếu không đủ bộ nhớ thì ném ngoại lệ ngay, tuyệt đối không âm thầm giảm độ phân giải.</li>
 * </ul>
 */
final class ExportSizing {

    private ExportSizing() {
    }

    /**
     * Tính toán kích thước giải mã ảnh nguồn {@code [width, height]} trước bước phóng 2× (nếu có).
     *
     * @param w           Chiều rộng ảnh gốc (pixel).
     * @param h           Chiều cao ảnh gốc (pixel).
     * @param scale       Hệ số nhân kích thước sau khi xử lý màu (1 hoặc 2).
     * @param fullSize    {@code true} nếu giữ kích thước gốc hoặc phóng 2×; {@code false} nếu dùng mức tiêu chuẩn 12 MP.
     * @param pixelBudget Số lượng điểm ảnh tối đa cho phép cấp phát dựa trên RAM khả dụng của thiết bị.
     * @return Mảng 2 phần tử {@code [targetWidth, targetHeight]}.
     */
    static int[] dimensions(int w, int h, int scale, boolean fullSize, long pixelBudget) {
        if (w < 1 || h < 1 || (scale != 1 && scale != 2) || pixelBudget < 1) {
            throw new IllegalArgumentException("Kích thước xuất không hợp lệ");
        }

        long pixels = (long) w * h;
        if (fullSize) {
            // Kiểm tra ngân sách RAM và chống tràn số nguyên 32-bit khi cấp phát mảng pixel
            if (pixels > pixelBudget
                    || (long) w * scale > Integer.MAX_VALUE
                    || (long) h * scale > Integer.MAX_VALUE
                    || pixels > Integer.MAX_VALUE / ((long) scale * scale)) {
                throw new IllegalArgumentException(
                        "Không đủ bộ nhớ cho độ phân giải này. Chọn kích thước gốc hoặc tiêu chuẩn; app không tự giảm kích thước ở chế độ 2×.");
            }
            return new int[]{w, h};
        }

        // Chế độ tiêu chuẩn: giới hạn tối đa 12 MP (12.000.000 điểm ảnh) hoặc theo ngân sách RAM
        double ratio = Math.min(1.0, Math.sqrt((double) Math.min(12_000_000L, pixelBudget) / pixels));
        return new int[]{
                Math.max(1, (int) Math.floor(w * ratio)),
                Math.max(1, (int) Math.floor(h * ratio))
        };
    }
}
