package vn.iro.editor;

import java.util.concurrent.CancellationException;

/**
 * Bộ xử lý màu hậu kỳ sRGB cốt lõi (Color Pipeline).
 * Chuyển đổi và đồng bộ tối ưu hóa từ {@code ios/IRO/Core/ColorEngine.swift}.
 *
 * <p>Sơ đồ luồng xử lý (Pipeline) để theo dõi khi debug:
 * <ol>
 *     <li><b>Tuyến tính hóa sRGB (Linearization):</b> Tra bảng {@link #LINEAR_LUT} đã tính sẵn cho 256 mức byte
 *         nhằm loại bỏ lệnh {@code Math.pow(..., 2.4)} đắt đỏ trên từng điểm ảnh.</li>
 *     <li><b>Phơi sáng (EV) & Cân bằng trắng tương đối (WB Shift):</b> Nhân hệ số {@code 2^EV} và hàm mũ
 *         kênh Đỏ/Xanh dương ({@code red}, {@code blue}, {@code temperature}) trong miền ánh sáng tuyến tính,
 *         sau đó mã hóa ngược về gamma sRGB.</li>
 *     <li><b>Áp dụng LUT 3D (.cube):</b> Nếu người dùng đã gắn LUT cho film đang chọn, lấy mẫu nội suy
 *         tam tuyến tính (trilinear) và pha trộn theo tỷ lệ {@code lutAmount} (%).</li>
 *     <li><b>Đường cong Tone (Highlight / Shadow / DR):</b> Tính độ chói cảm nhận Rec.709
 *         ({@code Y = 0.2126*R + 0.7152*G + 0.0722*B}) và điều chỉnh vùng sáng, vùng tối cùng nén vùng cháy sáng DR200/DR400.</li>
 *     <li><b>Độ đậm màu (Color), Color Chrome & Chrome FX Blue:</b> Điều chỉnh độ bão hòa quanh trục độ chói {@code Y},
 *         làm sâu các vùng màu có sắc độ cao ({@code chroma}) và vùng xanh dương ({@code blueWeight}).</li>
 *     <li><b>Hạt phim (Film Grain):</b> Sinh nhiễu giả ngẫu nhiên tất định (deterministic spatial hash)
 *         theo tọa độ {@code (x / grainSize, y / grainSize)} để hạt phim không bị nhấp nháy khi kéo thanh trượt.</li>
 *     <li><b>Lọc không gian (Spatial Pass - NR, Sharpness, Clarity):</b> Thực hiện lượt quét thứ hai với lân cận
 *         bán kính 1 pixel (cho Khử nhiễu & Độ nét) và bán kính 4 pixel (cho Độ trong / Clarity).</li>
 * </ol>
 */
public final class ColorEngine {

    /**
     * Bảng tra cứu chuyển đổi sRGB (0..255) sang ánh sáng tuyến tính [0.0 .. 1.0].
     * Tương đương {@code linearLUT} trong {@code ColorEngine.swift}, giúp tăng tốc vòng lặp chính.
     */
    private static final double[] LINEAR_LUT = new double[256];

    static {
        for (int b = 0; b < 256; b++) {
            LINEAR_LUT[b] = linear(b / 255.0);
        }
    }

    private ColorEngine() {
    }

    /** Giới hạn giá trị thực trong đoạn [0.0, 1.0]. */
    private static double clamp(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    /** Chuyển giá trị sRGB chuẩn hóa [0..1] sang ánh sáng tuyến tính (chuẩn IEC 61966-2-1). */
    private static double linear(double x) {
        return x <= 0.04045 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
    }

    /** Chuyển giá trị ánh sáng tuyến tính [0..1] ngược về không gian gamma sRGB. */
    private static double gamma(double x) {
        return x <= 0.0031308 ? 12.92 * x : 1.055 * Math.pow(x, 1.0 / 2.4) - 0.055;
    }

    /** Quy đổi giá trị thực [0..1] sang byte nguyên [0..255] có làm tròn. */
    private static int byteValue(double x) {
        return (int) Math.round(clamp(x) * 255.0);
    }

    /** Tính độ chói cảm nhận (Perceived Luminance) theo chuẩn ITU-R BT.709. */
    private static double luma(double r, double g, double b) {
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /**
     * Xử lý toàn bộ mảng điểm ảnh ARGB_8888 theo thông số {@link Settings} và {@link CubeLut}.
     *
     * @param pixels Mảng pixel đầu vào dạng {@code 0xAARRGGBB}, độ dài phải đúng bằng {@code w * h}.
     * @param w      Chiều rộng ảnh (pixel).
     * @param h      Chiều cao ảnh (pixel).
     * @param s      Bộ thông số chỉnh sửa màu sắc.
     * @param lut    Bảng LUT 3D (truyền {@code null} nếu chưa gắn LUT).
     * @return Mảng pixel mới đã qua xử lý hậu kỳ.
     * @throws CancellationException Nếu luồng xử lý hiện tại bị đánh dấu hủy ({@code Thread.interrupted()}) khi người dùng kéo thanh trượt mới.
     */
    public static int[] process(int[] pixels, int w, int h, Settings s, CubeLut lut) {
        if (w < 1 || h < 1 || (long) w * h != pixels.length) {
            throw new IllegalArgumentException("Invalid image dimensions: " + w + "x" + h);
        }

        long startTimeNs = System.nanoTime();
        int[] out = new int[pixels.length];
        double[] rgb = new double[3];
        double[] mapped = new double[3];

        // Tính trước các hệ số hằng số cho toàn bộ bức ảnh (giống ColorEngine.swift dòng 54-65)
        double ev = Math.pow(2.0, s.exposure);
        double rExpFactor = Math.exp(s.red * 0.023 + s.temperature * 0.002);
        double bExpFactor = Math.exp(s.blue * 0.023 - s.temperature * 0.002);
        double colorFactor = 1.0 + s.color * 0.08;
        double tonHigh = s.highlight * 0.035;
        double tonShad = s.shadow * 0.035;
        double tonDR = (s.dr - 100) / 300.0 * 0.10;
        double ccFactor = s.colorChrome * 0.012;
        double cbFactor = s.chromeBlue * 0.04;
        int grainSize = (int) Math.max(1.0, s.grainSize);

        // =========================================================================
        // BƯỚC 1: XỬ LÝ MÀU SẮC TRÊN TỪNG PIXEL (EV, WB, LUT, TONE, CHROME, GRAIN)
        // =========================================================================
        for (int i = 0; i < pixels.length; i++) {
            // Kiểm tra cờ hủy luồng mỗi 16.384 pixel để phản hồi tức thì khi người dùng kéo slider
            if ((i & 16383) == 0 && Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Đã hủy luồng render tại bước 1");
            }

            int p = pixels[i];
            int rByte = (p >>> 16) & 255;
            int gByte = (p >>> 8) & 255;
            int bByte = p & 255;

            // 1.1 & 1.2: Tuyến tính hóa qua bảng tra + áp dụng EV và dịch chuyển WB tương đối
            rgb[0] = gamma(Math.max(0.0, LINEAR_LUT[rByte] * ev * rExpFactor));
            rgb[1] = gamma(Math.max(0.0, LINEAR_LUT[gByte] * ev));
            rgb[2] = gamma(Math.max(0.0, LINEAR_LUT[bByte] * ev * bExpFactor));

            // 1.3: Nội suy LUT 3D (nếu có) và pha trộn theo cường độ lutAmount (%)
            if (lut != null) {
                lut.sample(rgb[0], rgb[1], rgb[2], mapped);
                for (int k = 0; k < 3; k++) {
                    rgb[k] += (mapped[k] - rgb[k]) * s.lutAmount / 100.0;
                }
            }

            // 1.4: Tính độ chói (luma), trọng số vùng tối (shadow), vùng sáng (high) và độ bão hòa cục bộ (chroma)
            double lum = luma(rgb[0], rgb[1], rgb[2]);
            double clampedLum = clamp(lum);
            double shadow = Math.pow(1.0 - clampedLum, 2.0);
            double high = Math.pow(clampedLum, 2.0);

            double maxChannel = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
            double minChannel = Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
            double chroma = maxChannel - minChannel;
            double blueWeight = Math.max(0.0, rgb[2] - Math.max(rgb[0], rgb[1]));

            // DR ở đây là nén vùng sáng cao (highlight compression) hậu kỳ
            double tone = tonHigh * high - tonShad * shadow - tonDR * Math.pow(clampedLum, 3.0);

            // 1.5: Tạo hạt phim (Film Grain) bằng hàm băm không gian tất định (deterministic spatial hash)
            int gx = (i % w) / grainSize;
            int gy = (i / w) / grainSize;
            int hash = (gx * 374761393 + gy * 668265263) ^ 0x5a1f1234;
            hash = (hash ^ (hash >>> 13)) * 1274126177;
            double noise = (((hash >>> 8) & 65535) / 65535.0 - 0.5) * s.grain / 255.0;

            // 1.6: Tổng hợp kênh màu và đóng gói lại vào số nguyên 32-bit ARGB (giữ nguyên kênh Alpha gốc)
            for (int k = 0; k < 3; k++) {
                rgb[k] = lum
                        + (rgb[k] - lum) * colorFactor
                        + tone
                        - ccFactor * chroma
                        - cbFactor * blueWeight
                        + noise;
            }

            out[i] = (p & 0xff000000)
                    | (byteValue(rgb[0]) << 16)
                    | (byteValue(rgb[1]) << 8)
                    | byteValue(rgb[2]);
        }

        // =========================================================================
        // BƯỚC 2: LỌC KHÔNG GIAN (KHỬ NHIỄU NR, ĐỘ NÉT SHARPNESS, ĐỘ TRONG CLARITY)
        // =========================================================================
        if (s.nr > -4.0 || s.sharpness != 0.0 || s.clarity != 0.0) {
            int[] src = out.clone();
            double noiseAmount = (s.nr + 4.0) / 8.0 * 0.65;

            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    if ((x & 255) == 0 && Thread.currentThread().isInterrupted()) {
                        throw new CancellationException("Đã hủy luồng render tại bước 2");
                    }

                    int i = y * w + x;
                    int p = src[i];
                    int result = p & 0xff000000;

                    // Xử lý lần lượt 3 kênh B (shift=0), G (shift=8), R (shift=16)
                    for (int shift = 0; shift <= 16; shift += 8) {
                        double v = (p >>> shift) & 255;
                        // Trung bình 4 điểm ảnh lân cận sát cạnh (trái, phải, trên, dưới)
                        double avg = (((src[i - 1] >>> shift) & 255)
                                + ((src[i + 1] >>> shift) & 255)
                                + ((src[i - w] >>> shift) & 255)
                                + ((src[i + w] >>> shift) & 255)) / 4.0;

                        // Pha trộn giữa làm mịn (NR) và tăng nét viền (Sharpness)
                        double filtered = v + (avg - v) * noiseAmount + (v - avg) * s.sharpness * 0.18;

                        // Clarity: tăng tương phản cục bộ với bán kính rộng hơn (cách 4 pixel)
                        if (s.clarity != 0.0 && x >= 4 && x < w - 4 && y >= 4 && y < h - 4) {
                            double broad = (((src[i - 4] >>> shift) & 255)
                                    + ((src[i + 4] >>> shift) & 255)
                                    + ((src[i - 4 * w] >>> shift) & 255)
                                    + ((src[i + 4 * w] >>> shift) & 255)) / 4.0;
                            filtered += (v - broad) * s.clarity * 0.06;
                        }

                        result |= byteValue(filtered / 255.0) << shift;
                    }
                    out[i] = result;
                }
            }
        }

        long elapsedMs = (System.nanoTime() - startTimeNs) / 1_000_000L;
        DebugLogger.d(DebugLogger.TAG_ENGINE,
                "Render hoàn tất: " + w + "x" + h + " (" + pixels.length + " px) trong " + elapsedMs + " ms");
        return out;
    }
}
