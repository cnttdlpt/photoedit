package vn.iro.editor;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Bộ phân tích và tự động cân bằng ảnh thông minh (Conservative Natural Auto-Balancer).
 * Đồng bộ chính xác 100% với thuật toán mới nhất từ {@code ios/IRO/Core/ColorEngine.swift}.
 *
 * <p>Nguyên tắc thiết kế:
 * <ul>
 *     <li><b>Đo sáng tuyến tính (Linear Light):</b> Tính độ chói chuẩn Rec. 709 trong không gian ánh sáng tuyến tính
 *         thay vì gamma phi tuyến, giúp dự đoán độ bù sáng chính xác mà không làm bệt chi tiết.</li>
 *     <li><b>Bảo vệ vùng sáng (Highlight Headroom):</b> Giới hạn bù sáng dựa theo phân vị 95% đỉnh kênh màu
 *         để không làm cháy sáng các vùng mây trời hay đèn chiếu.</li>
 *     <li><b>Nhận diện High-Key:</b> Nếu bức ảnh có nền trắng chủ đạo (High-Key, phân vị 5% độ chói >= 180),
 *         giữ nguyên Highlight = 0 để tránh kéo xỉn phông nền trắng.</li>
 *     <li><b>Cân bằng trắng theo mẫu trung tính (Neutral Midtone Log-Ratio):</b> Chỉ lấy mẫu độ lệch R/B
 *         từ các điểm ảnh gần trung tính (độ bão hòa <= 0.18 và độ chói 0.06..0.8), loại bỏ hoàn toàn
 *         nhiễu từ vật thể màu rực rỡ như hoa đỏ hay bầu trời xanh.</li>
 *     <li><b>Bảo toàn ý đồ sáng tạo:</b> Không tự ý tăng bão hòa màu, độ nét hay độ trong (Color, Sharpness, Clarity)
 *         nhằm tôn trọng tối đa màu sắc nguyên bản của người chụp.</li>
 * </ul>
 */
public final class AutoBalancer {

    private static final double[] LINEAR_LUT = new double[256];

    static {
        for (int i = 0; i < 256; i++) {
            double v = i / 255.0;
            LINEAR_LUT[i] = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
    }

    private AutoBalancer() {
    }

    private static double linear(int byteVal) {
        return LINEAR_LUT[byteVal & 255];
    }

    /**
     * Phân tích trực tiếp trên đối tượng {@link Bitmap} của Android và trả về bộ {@link Settings} đã tối ưu.
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
     * Tương đương 100% với hàm {@code ColorEngine.analyze(_:baseSettings:)} trên iOS.
     */
    public static Settings analyze(int[] pixels, int width, int height, Settings baseSettings) {
        Settings adjusted = baseSettings != null ? baseSettings.copy() : new Settings();
        int totalPixels = width * height;
        if (pixels == null || totalPixels <= 0 || pixels.length != totalPixels) {
            return adjusted;
        }

        int sampleStep = Math.max(1, totalPixels / 10000);
        int maxSamples = totalPixels / sampleStep + 2;

        double[] luminances = new double[maxSamples];
        double[] channelPeaks = new double[maxSamples];
        double[] encodedLuminances = new double[maxSamples];
        List<Double> neutralLogRatios = new ArrayList<>();

        int sampleCount = 0;

        for (int idx = 0; idx < totalPixels; idx += sampleStep) {
            int p = pixels[idx];
            int alpha = (p >>> 24) & 255;
            if (alpha < 16) {
                continue; // Bỏ qua viền đệm trong suốt
            }

            int r = (p >>> 16) & 255;
            int g = (p >>> 8) & 255;
            int b = p & 255;

            double lr = linear(r);
            double lg = linear(g);
            double lb = linear(b);

            double luminance = 0.2126 * lr + 0.7152 * lg + 0.0722 * lb;
            luminances[sampleCount] = luminance;
            channelPeaks[sampleCount] = Math.max(lr, Math.max(lg, lb));
            encodedLuminances[sampleCount] = 0.2126 * r + 0.7152 * g + 0.0722 * b;
            sampleCount++;

            int maxC = Math.max(r, Math.max(g, b));
            int minC = Math.min(r, Math.min(g, b));
            double saturation = maxC > 0 ? (double) (maxC - minC) / maxC : 0.0;

            // Điểm ảnh gần trung tính, vùng trung tính giúp đo cân bằng trắng không bị ảnh hưởng bởi hoa/trời
            if (saturation <= 0.18 && luminance > 0.06 && luminance < 0.8 && lr > 0.0 && lb > 0.0) {
                neutralLogRatios.add(Math.log(lr / lb));
            }
        }

        if (sampleCount == 0) {
            return adjusted;
        }

        Arrays.sort(luminances, 0, sampleCount);
        Arrays.sort(channelPeaks, 0, sampleCount);
        Arrays.sort(encodedLuminances, 0, sampleCount);

        double median = luminances[sampleCount / 2];
        double p95 = channelPeaks[(int) (sampleCount * 0.95)];
        double encodedMedian = encodedLuminances[sampleCount / 2];
        double encodedP5 = encodedLuminances[(int) (sampleCount * 0.05)];
        double encodedP95 = encodedLuminances[(int) (sampleCount * 0.95)];
        boolean isHighKey = encodedP5 >= 180.0;

        // 1. Phơi sáng trong không gian tuyến tính: EV = log2(target / median)
        double ln2 = Math.log(2.0);
        double targetL = 0.14;
        double exposure = Math.max(0.0, Math.min(1.2, Math.log(targetL / Math.max(median, 0.0001)) / ln2));
        if (exposure > 0.0 && p95 > 0.0) {
            // Bảo vệ không để vùng sáng vượt quá mức 0.90 trong ánh sáng tuyến tính
            exposure = Math.min(exposure, Math.max(0.0, Math.log(0.90 / p95) / ln2));
        }
        // Làm tròn về số thập phân 1 chữ số
        adjusted.exposure = Math.floor(exposure * 10.0) / 10.0;

        // 2. Vùng sáng (Highlight)
        if (isHighKey) {
            adjusted.highlight = 0.0;
        } else if (encodedP95 >= 254.0) {
            adjusted.highlight = -0.5;
        } else if (encodedP95 < 180.0 && encodedMedian < 110.0) {
            adjusted.highlight = 0.5;
        } else {
            adjusted.highlight = 0.0;
        }

        // 3. Vùng tối (Shadow): nâng sáng vùng tối bị chìm sâu (giá trị âm mở sáng trong ColorEngine)
        adjusted.shadow = encodedP5 < 40.0 ? -0.5 : 0.0;

        // 4. Nhiệt độ màu (Temperature): tỉ lệ R/B dựa trên log ratio mẫu trung tính
        if (neutralLogRatios.size() >= Math.max(1, sampleCount / 20)) {
            Collections.sort(neutralLogRatios);
            double ratio = neutralLogRatios.get(neutralLogRatios.size() / 2);
            adjusted.temperature = Math.max(-15.0, Math.min(15.0, Math.round(-ratio / 0.004)));
        }

        DebugLogger.d(DebugLogger.TAG_AUTOBALANCE, String.format(Locale.ROOT,
                "AutoBalancer: %d mẫu (highKey=%b) -> EV=%+.1f, Hi=%+.1f, Sh=%+.1f, Temp=%+.0f",
                sampleCount, isHighKey, adjusted.exposure, adjusted.highlight, adjusted.shadow, adjusted.temperature));

        return adjusted;
    }
}
