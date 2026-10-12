package vn.iro.editor;

import android.graphics.Bitmap;
import java.util.Arrays;
import java.util.Locale;

/**
 * Bộ phân tích và tự động cân bằng ảnh thông minh (Auto-Balance Image Analysis).
 * Chuyển đổi trực tiếp từ {@code AutoBalancer} trong {@code ios/IRO/Core/ColorEngine.swift} (dòng 148–255).
 *
 * <p>Cơ chế hoạt động (để theo dõi và debug):
 * <ol>
 *     <li><b>Lấy mẫu thống kê (Sub-sampling):</b> Lấy tối đa ~10.000 điểm ảnh không trong suốt
 *         ({@code alpha > 0}) trải đều trên toàn bức ảnh để phân tích tức thì mà không gây trễ giao diện.</li>
 *     <li><b>Phân vị độ chói (Luminance Percentiles):</b> Tính độ chói cảm nhận Rec.709 cho từng mẫu,
 *         sắp xếp mảng để tìm trung vị ({@code medianL}), phân vị 5% vùng tối ({@code p5}) và phân vị 95% vùng sáng ({@code p95}).</li>
 *     <li><b>Tự động Phơi sáng (Exposure):</b> Đưa độ chói trung vị về mức mục tiêu {@code 120.0} (midtone hài hòa),
 *         giới hạn an toàn trong đoạn {@code [-1.8, +1.8]} EV.</li>
 *     <li><b>Tối ưu Tương phản động (Highlight & Shadow):</b>
 *         <ul>
 *             <li>Nếu vùng sáng bị gắt ({@code p95 > 240}): hạ {@code highlight = -1.0} để cứu chi tiết bầu trời/vùng sáng.</li>
 *             <li>Nếu ảnh thiếu sáng phẳng ({@code p95 < 180 && medianL < 110}): nâng {@code highlight = +0.5}.</li>
 *             <li>Nếu vùng tối bị bệt ({@code p5 < 20 && medianL < 130}): đặt {@code shadow = -1.0} (trong {@link ColorEngine},
 *                 giá trị âm nâng chi tiết vùng tối).</li>
 *             <li>Nếu vùng tối bị mờ đục/thiếu chiều sâu ({@code p5 > 50}): đặt {@code shadow = +0.5} để làm sâu sắc đen.</li>
 *         </ul>
 *     </li>
 *     <li><b>Cân bằng trắng tương đối (Temperature):</b> So sánh độ lệch trung bình kênh Đỏ và Xanh dương
 *         ({@code rbDelta = avgR - avgB}). Nếu ảnh quá ám vàng/đỏ ({@code > 14}) thì giảm nhiệt độ;
 *         nếu quá ám xanh lạnh ({@code < -14}) thì tăng nhiệt độ ấm lên.</li>
 *     <li><b>Độ đậm màu & Độ nét:</b> Tăng {@code color = +1.0} nếu ảnh gốc nhợt nhạt ({@code avgSat < 0.22}),
 *         đồng thời thêm độ trong trẻo nhẹ ({@code clarity = 1.0}, {@code sharpness = 1.0}).</li>
 * </ol>
 */
public final class AutoBalancer {

    private AutoBalancer() {
    }

    /**
     * Phân tích trực tiếp trên đối tượng {@link Bitmap} của Android và trả về bộ {@link Settings} đã tối ưu.
     *
     * @param bitmap       Ảnh nguồn đang mở (thường là ảnh preview).
     * @param baseSettings Thông số hiện tại (để giữ lại tên {@code film}, {@code lutAmount}, {@code dr}...).
     * @return Bản sao {@link Settings} mới với các thông số đã được cân bằng tự động.
     */
    public static Settings analyze(Bitmap bitmap, Settings baseSettings) {
        if (bitmap == null || bitmap.getWidth() < 1 || bitmap.getHeight() < 1) {
            return baseSettings != null ? baseSettings.copy() : new Settings();
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        return analyze(pixels, w, h, baseSettings);
    }

    /**
     * Phân tích mảng điểm ảnh ARGB_8888 và tính toán bộ thông số {@link Settings} cân bằng tự động.
     * Tương đương 100% với hàm {@code AutoBalancer.analyze(_:baseSettings:)} trên iOS.
     *
     * @param pixels       Mảng điểm ảnh {@code 0xAARRGGBB}.
     * @param width        Chiều rộng ảnh.
     * @param height       Chiều cao ảnh.
     * @param baseSettings Bộ thông số nền (nếu {@code null} sẽ khởi tạo {@link Settings} mặc định).
     * @return Bộ {@link Settings} đã được tối ưu hóa.
     */
    public static Settings analyze(int[] pixels, int width, int height, Settings baseSettings) {
        Settings adjusted = baseSettings != null ? baseSettings.copy() : new Settings();
        int totalPixels = width * height;
        if (pixels == null || totalPixels <= 0 || pixels.length != totalPixels) {
            return adjusted;
        }

        // Bước nhảy lấy mẫu để thu thập tối đa khoảng 10.000 điểm ảnh
        int sampleStep = Math.max(1, totalPixels / 10000);
        double[] luminanceBuffer = new double[totalPixels / sampleStep + 2];
        int validCount = 0;

        double sumR = 0.0;
        double sumB = 0.0;
        double sumSat = 0.0;

        for (int idx = 0; idx < totalPixels; idx += sampleStep) {
            int p = pixels[idx];
            int alpha = (p >>> 24) & 255;
            // Bỏ qua các điểm ảnh trong suốt (viền đệm không chứa nội dung ảnh)
            if (alpha == 0) {
                continue;
            }

            double r = (p >>> 16) & 255;
            double g = (p >>> 8) & 255;
            double b = p & 255;

            // Độ chói cảm nhận theo chuẩn Rec. 709
            double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
            luminanceBuffer[validCount++] = lum;

            sumR += r;
            sumB += b;

            double maxC = Math.max(r, Math.max(g, b));
            double minC = Math.min(r, Math.min(g, b));
            if (maxC > 0.0) {
                sumSat += (maxC - minC) / maxC;
            }
        }

        if (validCount == 0) {
            return adjusted;
        }

        // Sắp xếp mảng độ chói để lấy trung vị (median), phân vị 5% (p5) và phân vị 95% (p95)
        Arrays.sort(luminanceBuffer, 0, validCount);
        double medianL = luminanceBuffer[validCount / 2];
        double p5 = luminanceBuffer[(int) (validCount * 0.05)];
        double p95 = luminanceBuffer[(int) (validCount * 0.95)];

        double avgR = sumR / validCount;
        double avgB = sumB / validCount;
        double avgSat = sumSat / validCount;

        // 1. Phơi sáng (Exposure): đưa độ chói trung vị về khoảng 120.0
        double targetMedian = 120.0;
        double expDiff = (targetMedian - medianL) / 48.0;
        adjusted.exposure = Math.round(Math.max(-1.8, Math.min(1.8, expDiff)) * 10.0) / 10.0;

        // 2. Vùng sáng (Highlight) & Vùng tối (Shadow)
        if (p95 > 240.0) {
            adjusted.highlight = -1.0; // Cứu chi tiết vùng sáng gắt / bầu trời cháy sáng
        } else if (p95 < 180.0 && medianL < 110.0) {
            adjusted.highlight = 0.5;  // Làm tươi vùng sáng bị xỉn
        } else {
            adjusted.highlight = 0.0;
        }

        if (p5 < 20.0 && medianL < 130.0) {
            adjusted.shadow = -1.0;    // Giá trị âm nâng vùng tối bị bệt trong ColorEngine
        } else if (p5 > 50.0) {
            adjusted.shadow = 0.5;     // Giá trị dương làm sâu lại vùng tối bị bạc màu
        } else {
            adjusted.shadow = 0.0;
        }

        // 3. Cân bằng trắng / Nhiệt độ màu tương đối (Temperature)
        double rbDelta = avgR - avgB;
        if (rbDelta > 14.0) {
            double tempShift = -Math.min(30.0, (rbDelta - 14.0) * 0.8);
            adjusted.temperature = Math.round(tempShift);
        } else if (rbDelta < -14.0) {
            double tempShift = Math.min(30.0, (-rbDelta - 14.0) * 0.8);
            adjusted.temperature = Math.round(tempShift);
        } else {
            adjusted.temperature = 0.0;
        }

        // 4. Độ đậm màu (Color / Saturation)
        if (avgSat < 0.22) {
            adjusted.color = 1.0;
        } else {
            adjusted.color = 0.0;
        }

        // 5. Độ trong (Clarity) & Độ sắc nét (Sharpness): tăng nhẹ độ chi tiết (+1.0)
        adjusted.clarity = 1.0;
        adjusted.sharpness = 1.0;

        DebugLogger.d(DebugLogger.TAG_AUTOBALANCE, String.format(Locale.ROOT,
                "Phân tích %d mẫu: medianL=%.1f, p5=%.1f, p95=%.1f, rbDelta=%.1f, avgSat=%.2f -> EV=%+.1f, Hi=%+.1f, Sh=%+.1f, Temp=%+.0f, Col=%+.1f",
                validCount, medianL, p5, p95, rbDelta, avgSat,
                adjusted.exposure, adjusted.highlight, adjusted.shadow, adjusted.temperature, adjusted.color));

        return adjusted;
    }
}
