package vn.iro.editor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * Bộ xử lý màu hậu kỳ sRGB cốt lõi (Color Pipeline) của IRO Studio.
 * Đồng bộ chuẩn xác 100% với Web Engine ({@code engine.mjs}) và thuật toán giả lập màu film Fujifilm.
 *
 * <p>Sơ đồ luồng xử lý (Color Pipeline):
 * <ol>
 *     <li><b>Ma trận giả lập màu film (Film Simulation Matrix):</b> Áp dụng hệ số tương phản, độ bão hòa
 *         và độ lệch màu đặc trưng của từng dòng film kinh điển (Classic Chrome, Classic Neg., Velvia, Acros, Eterna...).</li>
 *     <li><b>Cân bằng trắng (White Balance & Shift):</b> Bù trừ nhiệt độ theo loại ánh sáng (Daylight, Cloudy, Tungsten)
 *         và dịch chuyển sắc thái kênh Red / Blue / Temperature.</li>
 *     <li><b>Phơi sáng quang học (EV in Linear Light):</b> Tăng giảm bù trừ sáng trong không gian ánh sáng tuyến tính (Linear Light).</li>
 *     <li><b>Đường cong tương phản (Highlight, Shadow, DR):</b> Tinh chỉnh vùng sáng, vùng tối và nén cháy sáng DR200/400.</li>
 *     <li><b>Màu sắc & Chiều sâu (Color Boost, Color Chrome, FX Blue):</b> Làm sâu màu sắc rực rỡ và bầu trời xanh ngọc.</li>
 *     <li><b>Hiệu ứng phòng tối & Hoài niệm:</b> Bleach Bypass (giữ bạc), Sương mù 3D (Depth Fog), Lọt sáng 35mm (Light Leak).</li>
 *     <li><b>Áp dụng LUT 3D (.cube):</b> Nếu công thức hoặc người dùng đính kèm LUT 3D, nội suy tam tuyến tính (trilinear).</li>
 *     <li><b>Hạt phim hữu cơ (Film Grain):</b> Sinh nhiễu giả ngẫu nhiên tất định không nhấp nháy với mặt nạ trung tính (midtone mask).</li>
 *     <li><b>Lọc quang học & Không gian:</b> Khuếch tán Black Pro-Mist, quầng sáng đỏ Halation, Khử nhiễu NR, Độ nét Sharpness & Độ trong Clarity.</li>
 * </ol>
 */
public final class ColorEngine {

    /**
     * Bảng ma trận màu phim Fujifilm chuẩn xác từ {@code engine.mjs}:
     * [0]: Độ no màu (Saturation multiplier)
     * [1]: Độ tương phản (Contrast curve multiplier)
     * [2]: Độ lệch kênh Đỏ (+/- Red shift)
     * [3]: Độ lệch kênh Lục (+/- Green shift)
     * [4]: Độ lệch kênh Lam (+/- Blue shift)
     */
    public static final Map<String, double[]> FILMS = new HashMap<>();
    static {
        FILMS.put("original", new double[]{1.0, 1.0, 0.0, 0.0, 0.0});
        FILMS.put("classic chrome", new double[]{0.77, 1.12, 3.0, 0.0, -3.0});
        FILMS.put("classic neg.", new double[]{0.85, 1.22, 5.0, -3.0, -5.0});
        FILMS.put("provia", new double[]{1.05, 1.05, 0.0, 0.0, 0.0});
        FILMS.put("velvia", new double[]{1.40, 1.15, 2.0, 0.0, -2.0});
        FILMS.put("astia", new double[]{0.95, 0.91, 4.0, 0.0, 1.0});
        FILMS.put("eterna", new double[]{0.70, 0.82, 1.0, 2.0, 4.0});
        FILMS.put("acros", new double[]{0.0, 1.17, 0.0, 0.0, 0.0});
        FILMS.put("nostalgic neg.", new double[]{0.87, 0.96, 9.0, 3.0, -5.0});
        FILMS.put("pro neg. hi", new double[]{0.90, 1.08, 2.0, 0.0, -1.0});
        FILMS.put("pro neg. std", new double[]{0.90, 0.93, 2.0, 0.0, -1.0});
        FILMS.put("eterna bleach bypass", new double[]{0.50, 1.18, 0.0, 0.0, 0.0});
        FILMS.put("monochrome", new double[]{0.0, 1.0, 0.0, 0.0, 0.0});
        FILMS.put("sepia", new double[]{0.0, 1.0, 20.0, 6.0, -12.0});
    }

    /**
     * Tra cứu thông số mô phỏng màu phim dựa trên tên gọi hoặc bí danh (aliases).
     */
    public static double[] resolveFilmProfile(String name) {
        if (name == null || name.trim().isEmpty()) {
            return FILMS.get("original");
        }
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.equals("original") || n.equals("gốc")) return FILMS.get("original");
        if (n.equals("provia / standard") || n.equals("standard") || n.equals("provia")) return FILMS.get("provia");
        if (n.contains("classic chrome") || n.equals("cc")) return FILMS.get("classic chrome");
        if (n.contains("classic neg") || n.contains("classic negative") || n.equals("nc")) return FILMS.get("classic neg.");
        if (n.contains("nostalgic neg") || n.contains("nostalgic negative") || n.equals("nn")) return FILMS.get("nostalgic neg.");
        if (n.contains("velvia")) return FILMS.get("velvia");
        if (n.contains("astia")) return FILMS.get("astia");
        if (n.contains("bleach bypass")) return FILMS.get("eterna bleach bypass");
        if (n.contains("eterna")) return FILMS.get("eterna");
        if (n.contains("acros")) return FILMS.get("acros");
        if (n.contains("pro neg. hi") || n.contains("pro neg hi")) return FILMS.get("pro neg. hi");
        if (n.contains("pro neg. std") || n.contains("pro neg std") || n.contains("pro neg")) return FILMS.get("pro neg. std");
        if (n.contains("sepia")) return FILMS.get("sepia");
        if (n.contains("monochrome") || n.contains("black & white") || n.contains("b&w")) return FILMS.get("monochrome");

        for (Map.Entry<String, double[]> entry : FILMS.entrySet()) {
            if (n.contains(entry.getKey())) return entry.getValue();
        }
        return FILMS.get("original");
    }

    /**
     * Bảng tra cứu chuyển đổi sRGB (0..255) sang ánh sáng tuyến tính [0.0 .. 1.0].
     */
    private static final double[] LINEAR_LUT = new double[256];

    static {
        for (int b = 0; b < 256; b++) {
            LINEAR_LUT[b] = linear(b / 255.0);
        }
    }

    private ColorEngine() {
    }

    private static double clamp(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    private static double clamp255(double x) {
        return Math.max(0.0, Math.min(255.0, x));
    }

    private static double clamp255Norm(double x) {
        return Math.max(0.0, Math.min(1.0, x / 255.0));
    }

    private static int clampByte(double x) {
        return (int) Math.round(Math.max(0.0, Math.min(255.0, x)));
    }

    private static double linear(double x) {
        return x <= 0.04045 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
    }

    private static double gamma(double x) {
        return x <= 0.0031308 ? 12.92 * x : 1.055 * Math.pow(x, 1.0 / 2.4) - 0.055;
    }

    private static int byteValue(double x) {
        return (int) Math.round(clamp(x) * 255.0);
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
     * Đồng bộ chính xác thuật toán pixel với Web Engine.
     */
    public static int[] process(int[] pixels, int w, int h, Settings s, CubeLut lut) {
        if (w < 1 || h < 1 || (long) w * h != pixels.length) {
            throw new IllegalArgumentException("Invalid image dimensions: " + w + "x" + h);
        }

        long startTimeNs = System.nanoTime();
        int[] out = new int[pixels.length];
        double[] mapped = new double[3];

        // 1. Phân tích ma trận màu film được chọn
        double[] film = resolveFilmProfile(s.film);
        double film0 = film[0], film1 = film[1];
        double film2 = film[2], film3 = film[3], film4 = film[4];

        // 2. Cân bằng trắng & dịch chuyển sắc thái tương tự Web
        double wbOffset = 0.0;
        if (s.wb != null) {
            String wbLower = s.wb.toLowerCase(Locale.ROOT);
            if (wbLower.contains("daylight") || wbLower.contains("fine")) wbOffset = 3.0;
            else if (wbLower.contains("cloudy")) wbOffset = 9.0;
            else if (wbLower.contains("tungsten") || wbLower.contains("incandescent")) wbOffset = -15.0;
        }

        double redOffset = s.red * 2.0 + s.temperature * 0.15 + wbOffset;
        double blueOffset = s.blue * 2.0 - s.temperature * 0.15 - wbOffset;
        double highlightMult = s.highlight * 10.0;
        double shadowMult = s.shadow * 10.0;
        double drOffset = ((s.dr - 100) / 300.0) * 28.0;
        double baseEffectiveAmount = (s.amount > 0.0 ? s.amount : 100.0) / 100.0;

        int grainSize = (int) Math.max(1.0, s.grainSize);
        double cx = w * 0.5;
        double cy = h * 0.5;
        double maxRad = Math.hypot(cx, cy);
        boolean hasBokeh = (s.swirlyBokeh > 0.0 && w > 4 && h > 4);

        double dh = (s.dehaze != 0.0) ? (s.dehaze / 100.0) : 0.0;
        double dhMist = -dh * 0.55;

        double bb = (s.bleachBypass > 0.0) ? (s.bleachBypass / 100.0) : 0.0;
        double bbDesat1 = 1.0 - 0.55 * bb, bbDesat2 = 0.55 * bb;
        double bbSilver1 = 1.0 - 0.65 * bb, bbSilver2 = 0.65 * bb;

        boolean hasExposure = (s.exposure != 0.0);
        double ev = hasExposure ? Math.pow(2.0, s.exposure) : 1.0;

        // =========================================================================
        // BƯỚC 1: XỬ LÝ MÀU SẮC TRÊN TỪNG PIXEL (FILM SIMULATION, WB, EV, TONE, GRAIN)
        // =========================================================================
        for (int i = 0; i < pixels.length; i++) {
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

            double rIn = (p >>> 16) & 255;
            double gIn = (p >>> 8) & 255;
            double bIn = p & 255;

            double a0 = rIn, a1 = gIn, a2 = bIn;
            double skinW = s.skinProtect > 0.0 ? detectSkinWeight(rIn, gIn, bIn) * (s.skinProtect / 100.0) : 0.0;

            // Dehaze (Khử mù sương bằng Dark Channel Prior)
            if (dh != 0.0) {
                double darkChannel = Math.min(a0, Math.min(a1, a2)) / 255.0;
                if (dh > 0.0) {
                    double trans = Math.max(0.22, 1.0 - dh * 0.72 * darkChannel);
                    double atm = 235.0;
                    double invTrans = 1.0 / trans;
                    double atmSub = atm * (1.0 - trans);
                    a0 = (a0 - atmSub) * invTrans;
                    a1 = (a1 - atmSub) * invTrans;
                    a2 = (a2 - atmSub) * invTrans;
                } else {
                    a0 = a0 * (1.0 - dhMist) + 230.0 * dhMist;
                    a1 = a1 * (1.0 - dhMist) + 235.0 * dhMist;
                    a2 = a2 * (1.0 - dhMist) + 242.0 * dhMist;
                }
            }

            // Dịch chuyển Cân bằng trắng
            a0 += redOffset;
            a2 += blueOffset;

            // Độ chói cảm nhận Rec.709
            double l = a0 * 0.2126 + a1 * 0.7152 + a2 * 0.0722;
            double effectiveAmount = baseEffectiveAmount * (1.0 - skinW * 0.45);

            // Giả lập ma trận màu film Fujifilm chuẩn xác
            double filmc0 = ((l + (a0 - l) * film0) - 128.0) * film1 + 128.0 + film2;
            double filmc1 = ((l + (a1 - l) * film0) - 128.0) * film1 + 128.0 + film3;
            double filmc2 = ((l + (a2 - l) * film0) - 128.0) * film1 + 128.0 + film4;

            double v0 = a0 + (filmc0 - a0) * effectiveAmount;
            double v1 = a1 + (filmc1 - a1) * effectiveAmount;
            double v2 = a2 + (filmc2 - a2) * effectiveAmount;

            // Bù sáng trong miền ánh sáng tuyến tính (Linear light EV)
            if (hasExposure) {
                double lin0 = LINEAR_LUT[clampByte(v0)] * ev;
                double lin1 = LINEAR_LUT[clampByte(v1)] * ev;
                double lin2 = LINEAR_LUT[clampByte(v2)] * ev;
                v0 = gamma(Math.max(0.0, lin0)) * 255.0;
                v1 = gamma(Math.max(0.0, lin1)) * 255.0;
                v2 = gamma(Math.max(0.0, lin2)) * 255.0;
            }

            // Đường cong sắc độ (Highlights, Shadows & Dynamic Range)
            double lum0 = clamp255Norm(v0);
            double lum1 = clamp255Norm(v1);
            double lum2 = clamp255Norm(v2);

            v0 += highlightMult * lum0 * lum0 - shadowMult * Math.pow(1.0 - lum0, 2.0) - drOffset * Math.pow(lum0, 3.0);
            v1 += highlightMult * lum1 * lum1 - shadowMult * Math.pow(1.0 - lum1, 2.0) - drOffset * Math.pow(lum1, 3.0);
            v2 += highlightMult * lum2 * lum2 - shadowMult * Math.pow(1.0 - lum2, 2.0) - drOffset * Math.pow(lum2, 3.0);

            // Tăng cường độ đậm màu (Color boost quanh trục độ chói)
            double curLum = v0 * 0.2126 + v1 * 0.7152 + v2 * 0.0722;
            double colorFac = 1.0 + s.color * 0.09 * (1.0 - skinW * 0.5);
            v0 = curLum + (v0 - curLum) * colorFac;
            v1 = curLum + (v1 - curLum) * colorFac;
            v2 = curLum + (v2 - curLum) * colorFac;

            // Color Chrome Effect (giữ chi tiết các màu bão hòa cao)
            double colorChromeVal = (s.colorChrome > 0.0) ? s.colorChrome : 0.0;
            if (colorChromeVal > 0.0 && film0 > 0.0) {
                double maxC = Math.max(v0, Math.max(v1, v2));
                double minC = Math.min(v0, Math.min(v1, v2));
                double sat = maxC > 1.0 ? (maxC - minC) / maxC : 0.0;
                if (sat > 0.45) {
                    double factor = 1.0 - (sat - 0.45) * 0.14 * colorChromeVal;
                    v0 *= factor;
                    v1 *= factor;
                    v2 *= factor;
                }
            }

            // Color Chrome FX Blue (làm sâu màu xanh dương bầu trời)
            double chromeBlueVal = (s.chromeBlue > 0.0) ? s.chromeBlue : 0.0;
            if (chromeBlueVal > 0.0 && film0 > 0.0) {
                double blueDom = Math.max(0.0, (v2 - Math.max(v0, v1 * 0.85)) / 255.0);
                if (blueDom > 0.08) {
                    double depth = 1.0 - blueDom * 0.16 * chromeBlueVal;
                    v0 *= depth;
                    v1 *= depth;
                    v2 = clamp255(v2 * (1.0 + blueDom * 0.06 * chromeBlueVal));
                }
            }

            // Bleach Bypass (hiệu ứng giữ bạc)
            if (bb > 0.0) {
                double lNorm = clamp255Norm(v0 * 0.2126 + v1 * 0.7152 + v2 * 0.0722);
                double silver = (lNorm < 0.5
                        ? 2.0 * lNorm * lNorm
                        : 1.0 - 2.0 * (1.0 - lNorm) * (1.0 - lNorm)) * 255.0;
                double desatR = v0 * bbDesat1 + (lNorm * 255.0) * bbDesat2;
                double desatG = v1 * bbDesat1 + (lNorm * 255.0) * bbDesat2;
                double desatB = v2 * bbDesat1 + (lNorm * 255.0) * bbDesat2;
                v0 = desatR * bbSilver1 + silver * bbSilver2;
                v1 = desatG * bbSilver1 + silver * bbSilver2;
                v2 = desatB * bbSilver1 + silver * bbSilver2;
            }

            // 3D Depth Fog (Sương mù hậu cảnh)
            if (s.depthFog > 0.0) {
                double ny = (double) (i / w) / Math.max(1, h);
                double distWeight = Math.max(0.0, 1.0 - ny * 0.85) * (1.0 - clamp255Norm(curLum) * 0.45);
                double fog = distWeight * (s.depthFog / 100.0) * 0.65;
                v0 = v0 * (1.0 - fog) + 225.0 * fog;
                v1 = v1 * (1.0 - fog) + 232.0 * fog;
                v2 = v2 * (1.0 - fog) + 240.0 * fog;
            }

            // Nội suy LUT 3D (.cube) nếu có gắn kèm
            if (lut != null) {
                lut.sample(clamp255Norm(v0), clamp255Norm(v1), clamp255Norm(v2), mapped);
                double effectiveLut = (s.lutAmount / 100.0) * (1.0 - skinW * 0.45);
                v0 = v0 + (mapped[0] * 255.0 - v0) * effectiveLut;
                v1 = v1 + (mapped[1] * 255.0 - v1) * effectiveLut;
                v2 = v2 + (mapped[2] * 255.0 - v2) * effectiveLut;
            }

            // Vệt lọt sáng phim 35mm (Vintage Light Leak)
            if (s.lightLeak > 0.0) {
                double nx = (double) (i % w) / Math.max(1, w);
                double ny = (double) (i / w) / Math.max(1, h);
                double leakLeft = Math.max(0.0, 1.0 - nx * 3.2) * (0.6 + 0.4 * Math.sin(ny * 4.5));
                double leakCorner = Math.max(0.0, 1.0 - Math.hypot(1.0 - nx, ny * 0.8) * 2.1);
                double leak = (leakLeft + leakCorner * 0.75) * (s.lightLeak / 100.0);
                if (leak > 0.01) {
                    v0 = 255.0 - (255.0 - clamp255(v0)) * Math.max(0.0, 1.0 - leak * 0.95);
                    v1 = 255.0 - (255.0 - clamp255(v1)) * Math.max(0.0, 1.0 - leak * 0.48);
                    v2 = 255.0 - (255.0 - clamp255(v2)) * Math.max(0.0, 1.0 - leak * 0.12);
                }
            }

            // Tạo hạt phim hữu cơ (Film Grain)
            double noise = 0.0;
            if (s.grain > 0.0) {
                int gx = (i % w) / grainSize;
                int gy = (i / w) / grainSize;
                int hash = (gx * 374761393 + gy * 668265263) ^ 0x5a1f1234;
                hash = (hash ^ (hash >>> 13)) * 1274126177;
                double fine = (((hash >>> 8) & 65535) / 65535.0 - 0.5);
                double coarse = s.grainSize > 1.0 ? hashNoise(gx / 2, gy / 2, 77) : fine;
                double midtoneMask = 0.35 + 0.65 * (4.0 * clamp255Norm(curLum) * (1.0 - clamp255Norm(curLum)));
                noise = (fine * 0.65 + coarse * 0.35) * s.grain * midtoneMask;
            }

            out[i] = (pixels[i] & 0xff000000)
                    | (clampByte(v0 + noise) << 16)
                    | (clampByte(v1 + noise) << 8)
                    | clampByte(v2 + noise);
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

                    for (int shift = 0; shift <= 16; shift += 8) {
                        double v = (p >>> shift) & 255;
                        double avg = (((src[i - 1] >>> shift) & 255)
                                + ((src[i + 1] >>> shift) & 255)
                                + ((src[i - w] >>> shift) & 255)
                                + ((src[i + w] >>> shift) & 255)) / 4.0;

                        double filtered = v + (avg - v) * noiseAmount + (v - avg) * (s.sharpness * 0.18 + texAmt);

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
