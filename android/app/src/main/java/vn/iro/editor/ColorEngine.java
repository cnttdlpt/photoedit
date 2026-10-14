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
     * Nhận diện vùng màu da người mẫu dựa trên không gian YCbCr theo ITU-R BT.601.
     * Trả về trọng số [0.0 .. 1.0] để bảo vệ da khỏi bị ám màu nặng khi grading hoặc áp LUT.
     */
    private static double detectSkinWeight(double r, double g, double b) {
        if (r < 60.0 || g < 40.0 || b < 20.0 || r <= g || g <= b * 0.85) return 0.0;
        double maxC = Math.max(r, Math.max(g, b));
        double minC = Math.min(r, Math.min(g, b));
        if (maxC - minC < 15.0) return 0.0;
        double cr = 128.0 + 0.5 * r - 0.418688 * g - 0.081312 * b;
        double cb = 128.0 - 0.168736 * r - 0.331264 * g + 0.5 * b;
        if (cr >= 135.0 && cr <= 180.0 && cb >= 85.0 && cb <= 135.0) {
            double dist = Math.hypot((cr - 154.0) / 22.0, (cb - 112.0) / 22.0);
            return Math.max(0.0, 1.0 - dist);
        }
        return 0.0;
    }

    /** Hàm sinh số nhiễu giả ngẫu nhiên có hạt giống cho phân tầng hạt phim hữu cơ. */
    private static double hashNoise(int x, int y, int seed) {
        int h = (x * 374761393 ^ y * 668265263 ^ seed * 912384749) + 0x5a1f1234;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 12);
        return ((h & 0x7fffffff) % 1024) / 1023.0 - 0.5;
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
        double tonHigh = s.highlight * 0.035;
        double tonShad = s.shadow * 0.035;
        double tonDR = (s.dr - 100) / 300.0 * 0.10;
        double ccFactor = s.colorChrome * 0.012;
        double cbFactor = s.chromeBlue * 0.04;
        int grainSize = (int) Math.max(1.0, s.grainSize);

        double cx = w * 0.5;
        double cy = h * 0.5;
        double maxRad = Math.hypot(cx, cy);
        boolean hasBokeh = (s.swirlyBokeh > 0.0 && w > 4 && h > 4);

        // =========================================================================
        // BƯỚC 1: XỬ LÝ MÀU SẮC TRÊN TỪNG PIXEL (BOKEH, EV, WB, DEHAZE, LUT, TONE, CHROME, BLEACH, GRAIN)
        // =========================================================================
        for (int i = 0; i < pixels.length; i++) {
            // Kiểm tra cờ hủy luồng mỗi 16.384 pixel để phản hồi tức thì khi người dùng kéo slider
            if ((i & 16383) == 0 && Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Đã hủy luồng render tại bước 1");
            }

            int sampleX = i % w;
            int sampleY = i / w;
            if (hasBokeh) {
                double dx = sampleX - cx;
                double dy = sampleY - cy;
                double rNorm = Math.hypot(dx, dy) / maxRad;
                if (rNorm > 0.35) {
                    double angle = Math.pow(rNorm - 0.35, 2.0) * (s.swirlyBokeh / 100.0) * 0.65;
                    double cosA = Math.cos(angle);
                    double sinA = Math.sin(angle);
                    sampleX = (int) Math.max(0, Math.min(w - 1, Math.round(cx + dx * cosA - dy * sinA)));
                    sampleY = (int) Math.max(0, Math.min(h - 1, Math.round(cy + dx * sinA + dy * cosA)));
                }
            }
            int p = hasBokeh ? pixels[sampleY * w + sampleX] : pixels[i];

            int rByte = (p >>> 16) & 255;
            int gByte = (p >>> 8) & 255;
            int bByte = p & 255;

            // 1.1 & 1.2: Tuyến tính hóa qua bảng tra + áp dụng EV và dịch chuyển WB tương đối
            rgb[0] = gamma(Math.max(0.0, LINEAR_LUT[rByte] * ev * rExpFactor));
            rgb[1] = gamma(Math.max(0.0, LINEAR_LUT[gByte] * ev));
            rgb[2] = gamma(Math.max(0.0, LINEAR_LUT[bByte] * ev * bExpFactor));

            // Dehaze (Khử mù sương bằng thuật toán Dark Channel Prior)
            if (s.dehaze != 0.0) {
                double dh = s.dehaze / 100.0;
                double darkChannel = Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
                if (dh > 0.0) {
                    double trans = Math.max(0.22, 1.0 - dh * 0.72 * darkChannel);
                    double atm = 235.0 / 255.0;
                    rgb[0] = clamp((rgb[0] - atm * (1.0 - trans)) / trans);
                    rgb[1] = clamp((rgb[1] - atm * (1.0 - trans)) / trans);
                    rgb[2] = clamp((rgb[2] - atm * (1.0 - trans)) / trans);
                } else {
                    double mist = -dh * 0.55;
                    rgb[0] = rgb[0] * (1.0 - mist) + (230.0 / 255.0) * mist;
                    rgb[1] = rgb[1] * (1.0 - mist) + (235.0 / 255.0) * mist;
                    rgb[2] = rgb[2] * (1.0 - mist) + (242.0 / 255.0) * mist;
                }
            }

            double skinW = s.skinProtect > 0.0 ? detectSkinWeight(rByte, gByte, bByte) * (s.skinProtect / 100.0) : 0.0;

            // 1.3: Nội suy LUT 3D (nếu có) và pha trộn theo cường độ lutAmount (%) có tính skinProtect
            if (lut != null) {
                lut.sample(rgb[0], rgb[1], rgb[2], mapped);
                double effectiveLut = (s.lutAmount / 100.0) * (1.0 - skinW * 0.45);
                for (int k = 0; k < 3; k++) {
                    rgb[k] += (mapped[k] - rgb[k]) * effectiveLut;
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

            // 1.5: Tạo hạt phim (Film Grain) bằng hàm băm không gian tất định với mặt nạ midtone hữu cơ
            double noise = 0.0;
            if (s.grain > 0.0) {
                int gx = (i % w) / grainSize;
                int gy = (i / w) / grainSize;
                int hash = (gx * 374761393 + gy * 668265263) ^ 0x5a1f1234;
                hash = (hash ^ (hash >>> 13)) * 1274126177;
                double fine = (((hash >>> 8) & 65535) / 65535.0 - 0.5);
                double coarse = s.grainSize > 1.0 ? hashNoise(gx / 2, gy / 2, 77) : fine;
                double midtoneMask = 0.35 + 0.65 * (4.0 * clampedLum * (1.0 - clampedLum));
                noise = (fine * 0.65 + coarse * 0.35) * (s.grain / 255.0) * midtoneMask;
            }

            // 1.6: Tổng hợp kênh màu (Color có giảm trên da khi skinProtect > 0)
            double effectiveColor = 1.0 + s.color * 0.08 * (1.0 - skinW * 0.5);
            for (int k = 0; k < 3; k++) {
                rgb[k] = lum
                        + (rgb[k] - lum) * effectiveColor
                        + tone
                        - ccFactor * chroma
                        - cbFactor * blueWeight
                        + noise;
            }

            // Bleach Bypass (Giữ bạc phòng tối)
            if (s.bleachBypass > 0.0) {
                double bb = s.bleachBypass / 100.0;
                double curLum = clamp(luma(rgb[0], rgb[1], rgb[2]));
                double silver = curLum < 0.5
                        ? 2.0 * curLum * curLum
                        : 1.0 - 2.0 * (1.0 - curLum) * (1.0 - curLum);
                for (int k = 0; k < 3; k++) {
                    double desat = rgb[k] * (1.0 - 0.55 * bb) + curLum * (0.55 * bb);
                    rgb[k] = desat * (1.0 - 0.65 * bb) + silver * (0.65 * bb);
                }
            }

            // 3D Depth Fog (Sương mù hậu cảnh)
            if (s.depthFog > 0.0) {
                double ny = (double) (i / w) / h;
                double distWeight = Math.max(0.0, 1.0 - ny * 0.85) * (1.0 - clamp(lum) * 0.45);
                double fog = distWeight * (s.depthFog / 100.0) * 0.65;
                rgb[0] = rgb[0] * (1.0 - fog) + (225.0 / 255.0) * fog;
                rgb[1] = rgb[1] * (1.0 - fog) + (232.0 / 255.0) * fog;
                rgb[2] = rgb[2] * (1.0 - fog) + (240.0 / 255.0) * fog;
            }

            // Vệt lọt sáng phim 35mm
            if (s.lightLeak > 0.0) {
                double nx = (double) (i % w) / w;
                double ny = (double) (i / w) / h;
                double leakLeft = Math.max(0.0, 1.0 - nx * 3.2) * (0.6 + 0.4 * Math.sin(ny * 4.5));
                double leakCorner = Math.max(0.0, 1.0 - Math.hypot(1.0 - nx, ny * 0.8) * 2.1);
                double leak = (leakLeft + leakCorner * 0.75) * (s.lightLeak / 100.0);
                if (leak > 0.01) {
                    rgb[0] = 1.0 - (1.0 - clamp(rgb[0])) * Math.max(0.0, 1.0 - leak * 0.95);
                    rgb[1] = 1.0 - (1.0 - clamp(rgb[1])) * Math.max(0.0, 1.0 - leak * 0.48);
                    rgb[2] = 1.0 - (1.0 - clamp(rgb[2])) * Math.max(0.0, 1.0 - leak * 0.12);
                }
            }

            out[i] = (pixels[i] & 0xff000000)
                    | (byteValue(rgb[0]) << 16)
                    | (byteValue(rgb[1]) << 8)
                    | byteValue(rgb[2]);
        }

        // =========================================================================
        // BƯỚC 2: QUANG HỌC KHUẾCH TÁN (BLACK PRO-MIST & HALATION CINESTILL 800T)
        // =========================================================================
        if ((s.proMist > 0.0 || s.halation > 0.0) && w > 2 && h > 2) {
            int[] src = out.clone();
            double mistStrength = (s.proMist / 100.0) * 0.55;
            double halStrength = (s.halation / 100.0) * 0.45;

            for (int y = 0; y < h; y++) {
                int y0 = Math.max(0, y - 2), y1 = Math.min(h - 1, y + 2);
                for (int x = 0; x < w; x++) {
                    int x0 = Math.max(0, x - 2), x1 = Math.min(w - 1, x + 2);
                    int p = y * w + x;
                    int p1 = y0 * w + x0, p2 = y0 * w + x1;
                    int p3 = y1 * w + x0, p4 = y1 * w + x1;

                    int pVal = out[p];
                    int r = (pVal >>> 16) & 255;
                    int g = (pVal >>> 8) & 255;
                    int b = pVal & 255;

                    if (mistStrength > 0.0) {
                        double avgR = (((src[p1] >>> 16) & 255) + ((src[p2] >>> 16) & 255) + ((src[p3] >>> 16) & 255) + ((src[p4] >>> 16) & 255)) * 0.25;
                        double avgG = (((src[p1] >>> 8) & 255) + ((src[p2] >>> 8) & 255) + ((src[p3] >>> 8) & 255) + ((src[p4] >>> 8) & 255)) * 0.25;
                        double avgB = ((src[p1] & 255) + (src[p2] & 255) + (src[p3] & 255) + (src[p4] & 255)) * 0.25;
                        double avgLum = avgR * 0.2126 + avgG * 0.7152 + avgB * 0.0722;
                        if (avgLum > 135.0) {
                            double bloom = ((avgLum - 135.0) / 120.0) * mistStrength;
                            r = byteValue((255.0 - (255.0 - r) * (1.0 - bloom * 0.65)) / 255.0);
                            g = byteValue((255.0 - (255.0 - g) * (1.0 - bloom * 0.62)) / 255.0);
                            b = byteValue((255.0 - (255.0 - b) * (1.0 - bloom * 0.58)) / 255.0);
                        }
                    }

                    if (halStrength > 0.0) {
                        double l1 = (((src[p1] >>> 16) & 255) * 0.2126 + ((src[p1] >>> 8) & 255) * 0.7152 + (src[p1] & 255) * 0.0722);
                        double l2 = (((src[p2] >>> 16) & 255) * 0.2126 + ((src[p2] >>> 8) & 255) * 0.7152 + (src[p2] & 255) * 0.0722);
                        double l3 = (((src[p3] >>> 16) & 255) * 0.2126 + ((src[p3] >>> 8) & 255) * 0.7152 + (src[p3] & 255) * 0.0722);
                        double l4 = (((src[p4] >>> 16) & 255) * 0.2126 + ((src[p4] >>> 8) & 255) * 0.7152 + (src[p4] & 255) * 0.0722);
                        double maxLum = Math.max(Math.max(l1, l2), Math.max(l3, l4));
                        if (maxLum > 205.0) {
                            double glow = (maxLum - 205.0) * halStrength;
                            r = byteValue((r + glow * 1.15) / 255.0);
                            g = byteValue((g + glow * 0.38) / 255.0);
                        }
                    }

                    out[p] = (pVal & 0xff000000) | (r << 16) | (g << 8) | b;
                }
            }
        }

        // =========================================================================
        // BƯỚC 3: LỌC KHÔNG GIAN (KHỬ NHIỄU NR, ĐỘ NÉT SHARPNESS, ĐỘ TRONG CLARITY, MỊN DA TEXTURE)
        // =========================================================================
        if (s.nr > -4.0 || s.sharpness != 0.0 || s.clarity != 0.0 || s.texture != 0.0) {
            int[] src = out.clone();
            double noiseAmount = (s.nr + 4.0) / 8.0 * 0.65;
            double texAmt = (s.texture / 100.0) * 0.55;

            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    if ((x & 255) == 0 && Thread.currentThread().isInterrupted()) {
                        throw new CancellationException("Đã hủy luồng render tại bước 3");
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

                        // Pha trộn giữa làm mịn (NR), tăng nét viền (Sharpness) và chi tiết vi mô / mịn da (Texture)
                        double filtered = v + (avg - v) * noiseAmount + (v - avg) * (s.sharpness * 0.18 + texAmt);

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
