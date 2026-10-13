package vn.iro.editor;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Lõi xử lý hình ảnh & màu sắc chuyên sâu <b>LightCraft Engine</b> ({@code LightCraftEngine}).
 * Chuyển đổi nguyên bản từ các crate Rust của dự án <b>LightCraft</b> ({@code https://github.com/cnttdlpt/lightcraft.git}):
 *
 * <ul>
 *     <li><b>{@code crates/color/src/spline.rs} ({@link MonotoneCurve}):</b> Nội suy đường cong bậc ba đơn điệu
 *         Fritsch–Carlson (Monotone Cubic Hermite Spline) giúp chỉnh Tone Curve (RGB, Red, Green, Blue)
 *         không bao giờ bị vọt lố (overshoot) hay đảo ngược sắc độ ngoài ý muốn.</li>
 *     <li><b>{@code crates/pipeline/src/finish.rs} ({@code curve_luts}):</b> Kết hợp Đường cong Tham số 4 vùng
 *         (Parametric Curve: Shadows, Darks, Lights, Highlights với cửa sổ cosin nâng) và Đường cong Điểm (Point Curve).</li>
 *     <li><b>{@code crates/color/src/perceptual.rs} & {@code crates/pipeline/src/colorops.rs}:</b>
 *         Không gian màu cảm nhận đồng nhất <b>OkLab / OkLCh</b> (Björn Ottosson) phục vụ:
 *         <ul>
 *             <li><b>3-Way Color Grading Wheels:</b> Pha màu điện ảnh độc lập cho Vùng tối (Shadows),
 *                 Trung tính (Midtones) và Vùng sáng (Highlights) kèm thanh trượt {@code Blending} & {@code Balance}.</li>
 *             <li><b>8-Band Color Mixer (HSL):</b> Tinh chỉnh Hue / Saturation / Luminance độc lập trên 8 dải màu
 *                 (Đỏ, Cam, Vàng, Lục, Lam ngọc, Lam, Tím, Hồng sẫm) với trọng số phân hoạch cosin mượt ({@code bandWeights}).</li>
 *         </ul>
 *     </li>
 *     <li><b>{@code crates/pipeline/src/masks.rs}:</b> Mặt nạ cục bộ tương tác (Local Masking):
 *         <b>Radial Gradient</b> (Elip tỏa tròn), <b>Linear Gradient</b> (Chuyển sắc bầu trời/tiền cảnh),
 *         <b>AI Sky Heuristic</b> (Tự động nhận diện bầu trời) và <b>Subject Saliency</b> (Chủ thể trung tâm)
 *         với các thanh chỉnh Phơi sáng, Nhiệt độ màu, Độ tương phản & Độ bão hòa cục bộ.</li>
 *     <li><b>{@code crates/engine/src/presets.rs}:</b> 12 Preset phòng tối đặc trưng của LightCraft
 *         (<i>Teal & Orange, Golden Hour, Warm Glow, Cool Morning, Faded Matte, Warm Film, Selenium Tone,
 *         Crisp Landscape, Blue Hour Boost, Soft Skin, Bright & Airy, Cinematic</i>).</li>
 * </ul>
 */
public final class LightCraftEngine {

    /** 8 tâm góc sắc độ (độ HSV) của bộ trộn màu 8 kênh trong {@code crates/develop/src/lib.rs}. */
    public static final float[] MIXER_HUES_DEG = {0f, 30f, 60f, 120f, 180f, 225f, 275f, 320f};

    /** Tên tiếng Việt và mã màu hiển thị chuẩn từ {@code BAND_COLORS} trong {@code widgets.rs}. */
    public static final String[] MIXER_BAND_NAMES = {
            "Đỏ", "Cam", "Vàng", "Lục", "Lam ngọc", "Lam", "Tím", "Hồng"
    };
    public static final int[] MIXER_BAND_COLORS = {
            0xffdd3333, // Red (#aa0000 -> sáng rõ trên UI)
            0xffe67e22, // Orange
            0xffd4ac0d, // Yellow
            0xff27ae60, // Green
            0xff16a085, // Aqua
            0xff2980b9, // Blue
            0xff8e44ad, // Purple
            0xffc2185b  // Magenta
    };

    /**
     * Đường cong bậc ba đơn điệu Fritsch–Carlson ({@code MonotoneCurve} trong {@code crates/color/src/spline.rs}).
     * Đảm bảo đường cong đi qua mọi điểm điều khiển {@code (x, y)} trong miền {@code [0..1]} mà không bị vọt lố.
     */
    public static final class MonotoneCurve {
        private final double[] xs;
        private final double[] ys;
        private final double[] ms;

        public MonotoneCurve(List<float[]> points) {
            List<double[]> clean = new ArrayList<>();
            if (points != null) {
                for (float[] pt : points) {
                    if (pt != null && pt.length >= 2 && Float.isFinite(pt[0]) && Float.isFinite(pt[1])) {
                        clean.add(new double[]{
                                Math.max(0.0, Math.min(1.0, pt[0])),
                                Math.max(0.0, Math.min(1.0, pt[1]))
                        });
                    }
                }
            }
            Collections.sort(clean, Comparator.comparingDouble(a -> a[0]));

            // Loại bỏ các điểm trùng hoành độ x (giống dedup_by trong spline.rs)
            List<double[]> dedup = new ArrayList<>();
            for (double[] pt : clean) {
                if (dedup.isEmpty() || Math.abs(pt[0] - dedup.get(dedup.size() - 1)[0]) >= 1e-6) {
                    dedup.add(pt);
                }
            }
            if (dedup.size() < 2) {
                dedup.clear();
                dedup.add(new double[]{0.0, 0.0});
                dedup.add(new double[]{1.0, 1.0});
            }

            int n = dedup.size();
            this.xs = new double[n];
            this.ys = new double[n];
            this.ms = new double[n];
            for (int i = 0; i < n; i++) {
                xs[i] = dedup.get(i)[0];
                ys[i] = dedup.get(i)[1];
            }

            double[] d = new double[n - 1];
            for (int i = 0; i < n - 1; i++) {
                d[i] = (ys[i + 1] - ys[i]) / (xs[i + 1] - xs[i]);
            }

            ms[0] = d[0];
            ms[n - 1] = d[n - 2];
            for (int i = 1; i < n - 1; i++) {
                ms[i] = (d[i - 1] * d[i] <= 0.0) ? 0.0 : (d[i - 1] + d[i]) * 0.5;
            }

            // Bộ giới hạn Fritsch–Carlson (Fritsch–Carlson limiter) giữ tính đơn điệu trên từng đoạn
            for (int i = 0; i < n - 1; i++) {
                if (d[i] == 0.0) {
                    ms[i] = 0.0;
                    ms[i + 1] = 0.0;
                    continue;
                }
                double a = ms[i] / d[i];
                double b = ms[i + 1] / d[i];
                double s = a * a + b * b;
                if (s > 9.0) {
                    double t = 3.0 / Math.sqrt(s);
                    ms[i] = t * a * d[i];
                    ms[i + 1] = t * b * d[i];
                }
            }
        }

        /** Tính giá trị tung độ {@code y} tại hoành độ {@code x} trong miền {@code [0..1]}. */
        public double eval(double x) {
            int n = xs.length;
            if (x <= xs[0]) return ys[0];
            if (x >= xs[n - 1]) return ys[n - 1];

            int lo = 0, hi = n - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                double v = xs[mid];
                if (v < x) {
                    lo = mid + 1;
                } else if (v > x) {
                    hi = mid - 1;
                } else {
                    return ys[mid];
                }
            }
            int i = Math.max(0, lo - 1);
            double h = xs[i + 1] - xs[i];
            double t = (x - xs[i]) / h;
            double t2 = t * t;
            double t3 = t2 * t;
            double out = (2.0 * t3 - 3.0 * t2 + 1.0) * ys[i]
                    + (t3 - 2.0 * t2 + t) * h * ms[i]
                    + (-2.0 * t3 + 3.0 * t2) * ys[i + 1]
                    + (t3 - t2) * h * ms[i + 1];
            return Math.max(0.0, Math.min(1.0, out));
        }

        /** Xuất bảng tra cứu 1 chiều {@code n} phần tử trên miền {@code [0..1]}. */
        public float[] toLut(int n) {
            float[] lut = new float[n];
            for (int i = 0; i < n; i++) {
                lut[i] = (float) eval((double) i / (n - 1));
            }
            return lut;
        }
    }

    /** Một bánh xe màu trong hệ thống 3-Way Color Grading (Shadows / Midtones / Highlights). */
    public static final class GradingWheel {
        /** Góc màu Hue (0..360 độ). */
        public float hue = 0f;
        /** Độ bão hòa Saturation (0..100%). */
        public float sat = 0f;
        /** Độ sáng vùng Luminance (-100..+100). */
        public float lum = 0f;

        public void reset() {
            hue = 0f;
            sat = 0f;
            lum = 0f;
        }

        public boolean isNeutral() {
            return Math.abs(sat) < 0.5f && Math.abs(lum) < 0.5f;
        }

        public GradingWheel copy() {
            GradingWheel w = new GradingWheel();
            w.hue = this.hue;
            w.sat = this.sat;
            w.lum = this.lum;
            return w;
        }
    }

    /** Chế độ Mặt nạ cục bộ (Local Masking) từ {@code crates/pipeline/src/masks.rs}. */
    public enum MaskMode {
        NONE("Tắt Mask"),
        RADIAL("Radial (Elip)"),
        LINEAR("Linear (Chuyển sắc)"),
        SKY("AI Bầu trời"),
        SUBJECT("AI Chủ thể");

        public final String label;

        MaskMode(String label) {
            this.label = label;
        }
    }

    /**
     * Toàn bộ thông số chỉnh sửa chuyên sâu của LightCraft gắn với phiên làm việc hiện tại.
     */
    public static final class State {
        // 1. Tone Curve (Đường cong Điểm 4 kênh + Đường cong Tham số 4 vùng)
        public final List<float[]> masterCurve = defaultCurvePoints();
        public final List<float[]> redCurve = defaultCurvePoints();
        public final List<float[]> greenCurve = defaultCurvePoints();
        public final List<float[]> blueCurve = defaultCurvePoints();
        public float paramShadows = 0f;    // -100..+100
        public float paramDarks = 0f;      // -100..+100
        public float paramLights = 0f;     // -100..+100
        public float paramHighlights = 0f; // -100..+100

        // 2. 3-Way Color Grading Wheels (Shadows, Midtones, Highlights trong không gian OkLab)
        public final GradingWheel shadowsWheel = new GradingWheel();
        public final GradingWheel midtonesWheel = new GradingWheel();
        public final GradingWheel highlightsWheel = new GradingWheel();
        public float gradingBlending = 50f; // 0..100 (mặc định 50 giống LightCraft)
        public float gradingBalance = 0f;   // -100..+100

        // 3. 8-Band Color Mixer (Red, Orange, Yellow, Green, Aqua, Blue, Purple, Magenta)
        public final float[] mixerHue = new float[8]; // -100..+100
        public final float[] mixerSat = new float[8]; // -100..+100
        public final float[] mixerLum = new float[8]; // -100..+100

        // 4. Local Masking (Radial, Linear, Sky, Subject)
        public MaskMode maskMode = MaskMode.NONE;
        public boolean showMaskOverlay = false;
        public boolean maskInvert = false;
        // Tọa độ chuẩn hóa (0..1) cho Radial Mask
        public float radialCenterX = 0.5f;
        public float radialCenterY = 0.45f;
        public float radialRx = 0.30f;
        public float radialRy = 0.24f;
        public float radialFeather = 65f; // 0..100
        // Tọa độ chuẩn hóa (0..1) cho Linear Gradient Mask
        public float linearStartX = 0.5f;
        public float linearStartY = 0.18f;
        public float linearEndX = 0.5f;
        public float linearEndY = 0.58f;
        // Thông số điều chỉnh cục bộ bên trong Mask
        public float maskExposure = 0f;   // -2.0..+2.0 EV
        public float maskTemp = 0f;       // -100..+100
        public float maskClarity = 0f;    // -100..+100
        public float maskSaturation = 0f; // -100..+100

        // Preset LightCraft đang chọn (nếu có)
        public String activePresetId = "";

        public static List<float[]> defaultCurvePoints() {
            List<float[]> list = new ArrayList<>();
            list.add(new float[]{0f, 0f});
            list.add(new float[]{1f, 1f});
            return list;
        }

        public static boolean isCurveIdentity(List<float[]> pts) {
            if (pts == null || pts.size() < 2) return true;
            if (pts.size() == 2) {
                float[] a = pts.get(0);
                float[] b = pts.get(1);
                return Math.abs(a[0]) < 1e-3f && Math.abs(a[1]) < 1e-3f
                        && Math.abs(b[0] - 1f) < 1e-3f && Math.abs(b[1] - 1f) < 1e-3f;
            }
            for (float[] p : pts) {
                if (Math.abs(p[0] - p[1]) > 1e-3f) return false;
            }
            return true;
        }

        public boolean hasCurveEdits() {
            return Math.abs(paramShadows) > 0.5f
                    || Math.abs(paramDarks) > 0.5f
                    || Math.abs(paramLights) > 0.5f
                    || Math.abs(paramHighlights) > 0.5f
                    || !isCurveIdentity(masterCurve)
                    || !isCurveIdentity(redCurve)
                    || !isCurveIdentity(greenCurve)
                    || !isCurveIdentity(blueCurve);
        }

        public boolean hasGradingEdits() {
            return !shadowsWheel.isNeutral()
                    || !midtonesWheel.isNeutral()
                    || !highlightsWheel.isNeutral();
        }

        public boolean hasMixerEdits() {
            for (int i = 0; i < 8; i++) {
                if (Math.abs(mixerHue[i]) > 0.5f || Math.abs(mixerSat[i]) > 0.5f || Math.abs(mixerLum[i]) > 0.5f) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasMaskEdits() {
            if (maskMode == MaskMode.NONE) return false;
            return showMaskOverlay
                    || Math.abs(maskExposure) > 0.02f
                    || Math.abs(maskTemp) > 0.5f
                    || Math.abs(maskClarity) > 0.5f
                    || Math.abs(maskSaturation) > 0.5f;
        }

        public boolean isIdentity() {
            return !hasCurveEdits() && !hasGradingEdits() && !hasMixerEdits() && !hasMaskEdits();
        }

        public void resetCurves() {
            masterCurve.clear();
            masterCurve.addAll(defaultCurvePoints());
            redCurve.clear();
            redCurve.addAll(defaultCurvePoints());
            greenCurve.clear();
            greenCurve.addAll(defaultCurvePoints());
            blueCurve.clear();
            blueCurve.addAll(defaultCurvePoints());
            paramShadows = 0f;
            paramDarks = 0f;
            paramLights = 0f;
            paramHighlights = 0f;
        }

        public void resetGrading() {
            shadowsWheel.reset();
            midtonesWheel.reset();
            highlightsWheel.reset();
            gradingBlending = 50f;
            gradingBalance = 0f;
        }

        public void resetMixer() {
            Arrays.fill(mixerHue, 0f);
            Arrays.fill(mixerSat, 0f);
            Arrays.fill(mixerLum, 0f);
        }

        public void resetMask() {
            maskMode = MaskMode.NONE;
            showMaskOverlay = false;
            maskInvert = false;
            radialCenterX = 0.5f;
            radialCenterY = 0.45f;
            radialRx = 0.30f;
            radialRy = 0.24f;
            radialFeather = 65f;
            linearStartX = 0.5f;
            linearStartY = 0.18f;
            linearEndX = 0.5f;
            linearEndY = 0.58f;
            maskExposure = 0f;
            maskTemp = 0f;
            maskClarity = 0f;
            maskSaturation = 0f;
        }

        public void resetAll() {
            resetCurves();
            resetGrading();
            resetMixer();
            resetMask();
            activePresetId = "";
        }
    }

    /** Định nghĩa một Preset phòng tối tích hợp sẵn từ {@code crates/engine/src/presets.rs}. */
    public static final class BuiltinPreset {
        public final String id;
        public final String group;
        public final String name;
        public final String subtitle;
        public final int badgeColor;
        private final Runnable2 applier;

        private interface Runnable2 {
            void apply(State lc, Settings baseSettings);
        }

        BuiltinPreset(String id, String group, String name, String subtitle, int badgeColor, Runnable2 applier) {
            this.id = id;
            this.group = group;
            this.name = name;
            this.subtitle = subtitle;
            this.badgeColor = badgeColor;
            this.applier = applier;
        }

        public void applyTo(State lc) {
            applyTo(lc, null);
        }

        public void applyTo(State lc, Settings baseSettings) {
            lc.resetCurves();
            lc.resetGrading();
            lc.resetMixer();
            lc.activePresetId = this.id;
            Settings s = baseSettings != null ? baseSettings : new Settings();
            applier.apply(lc, s);
        }
    }

    /** Danh sách 12 Preset đặc trưng chuyển thể từ {@code crates/engine/src/presets.rs} của LightCraft. */
    public static final List<BuiltinPreset> PRESETS = buildBuiltinPresets();

    private static List<BuiltinPreset> buildBuiltinPresets() {
        List<BuiltinPreset> list = new ArrayList<>();

        list.add(new BuiltinPreset("lc.teal-orange", "Color", "Teal & Orange", "Shadows 195° · Highlights 35°", 0xff1a73e8, (lc, s) -> {
            lc.shadowsWheel.hue = 195f;
            lc.shadowsWheel.sat = 28f;
            lc.highlightsWheel.hue = 35f;
            lc.highlightsWheel.sat = 22f;
            lc.gradingBalance = 10f;
            lc.paramDarks = -12f;
            lc.paramLights = 12f;
            s.color = 1.2;
        }));

        list.add(new BuiltinPreset("lc.golden-hour", "Landscape", "Golden Hour", "Nắng vàng ấm · Highlights 38°", 0xfff29900, (lc, s) -> {
            lc.highlightsWheel.hue = 38f;
            lc.highlightsWheel.sat = 26f;
            lc.highlightsWheel.lum = 6f;
            lc.paramHighlights = -25f;
            lc.paramShadows = 18f;
            s.red = 2.0;
            s.blue = -2.0;
            s.highlight = -1.5;
            s.shadow = -1.0;
        }));

        list.add(new BuiltinPreset("lc.cinematic", "Style", "Cinematic", "Màn ảnh rộng · S-Curve & Split Grade", 0xffea4335, (lc, s) -> {
            lc.paramShadows = 20f;
            lc.paramDarks = -14f;
            lc.paramLights = 14f;
            lc.paramHighlights = -12f;
            lc.shadowsWheel.hue = 190f;
            lc.shadowsWheel.sat = 25f;
            lc.highlightsWheel.hue = 30f;
            lc.highlightsWheel.sat = 18f;
            s.clarity = 1.5;
        }));

        list.add(new BuiltinPreset("lc.faded-matte", "Film", "Faded Matte", "Mờ sương cổ điển · Nâng vùng tối", 0xff8e44ad, (lc, s) -> {
            lc.paramShadows = 40f;
            lc.paramHighlights = -15f;
            lc.masterCurve.clear();
            lc.masterCurve.add(new float[]{0.0f, 0.09f});
            lc.masterCurve.add(new float[]{0.25f, 0.24f});
            lc.masterCurve.add(new float[]{0.75f, 0.78f});
            lc.masterCurve.add(new float[]{1.0f, 0.94f});
            s.color = -1.2;
            s.grain = 8.0;
        }));

        list.add(new BuiltinPreset("lc.warm-film", "Film", "Warm Film", "Phim nhựa ấm · Hạt mịn", 0xffe67e22, (lc, s) -> {
            lc.paramShadows = 22f;
            lc.highlightsWheel.hue = 45f;
            lc.highlightsWheel.sat = 16f;
            s.red = 2.0;
            s.grain = 10.0;
        }));

        list.add(new BuiltinPreset("lc.crisp-landscape", "Landscape", "Crisp Landscape", "Phong cảnh sắc nét · Tách mây", 0xff34a853, (lc, s) -> {
            lc.paramHighlights = -35f;
            lc.paramShadows = 24f;
            lc.mixerSat[3] = 18f; // Lục
            lc.mixerSat[5] = 22f; // Lam
            lc.mixerLum[5] = -14f;
            s.clarity = 2.2;
            s.color = 2.0;
        }));

        list.add(new BuiltinPreset("lc.blue-hour", "Landscape", "Blue Hour Boost", "Chạng vạng xanh thẫm", 0xff2980b9, (lc, s) -> {
            lc.mixerSat[5] = 28f; // Blue
            lc.mixerSat[6] = 18f; // Purple
            lc.shadowsWheel.hue = 220f;
            lc.shadowsWheel.sat = 16f;
            s.blue = 2.0;
            s.shadow = -1.5;
        }));

        list.add(new BuiltinPreset("lc.soft-skin", "Portrait", "Soft Skin", "Chân dung da mịn màng", 0xffc2185b, (lc, s) -> {
            lc.mixerSat[1] = -10f; // Orange
            lc.mixerLum[1] = 14f;  // Làm sáng nhẹ tông da cam
            lc.paramShadows = 14f;
            s.clarity = -1.2;
            s.shadow = -1.0;
        }));

        list.add(new BuiltinPreset("lc.bright-airy", "Portrait", "Bright & Airy", "Trong trẻo & Thoáng sáng", 0xff16a085, (lc, s) -> {
            lc.paramShadows = 32f;
            lc.paramLights = 16f;
            lc.paramHighlights = -25f;
            s.exposure = 0.4;
            s.color = -0.6;
        }));

        list.add(new BuiltinPreset("lc.cool-morning", "Color", "Cool Morning", "Sương sớm lạnh · Shadows 215°", 0xff3b6fe0, (lc, s) -> {
            lc.shadowsWheel.hue = 215f;
            lc.shadowsWheel.sat = 15f;
            lc.paramHighlights = -20f;
            s.blue = 1.0;
        }));

        list.add(new BuiltinPreset("lc.selenium", "B&W", "Selenium Tone", "Đơn sắc ánh tím Selen", 0xff6e7480, (lc, s) -> {
            lc.shadowsWheel.hue = 285f;
            lc.shadowsWheel.sat = 20f;
            lc.highlightsWheel.hue = 40f;
            lc.highlightsWheel.sat = 10f;
            s.color = -4.0;
            s.clarity = 2.0;
        }));

        list.add(new BuiltinPreset("lc.moody", "Style", "Moody Darkroom", "Trầm mặc · Tương phản chiều sâu", 0xff1b1c1e, (lc, s) -> {
            lc.shadowsWheel.hue = 200f;
            lc.shadowsWheel.sat = 16f;
            lc.shadowsWheel.lum = -8f;
            lc.paramHighlights = -28f;
            lc.paramDarks = -16f;
            s.exposure = -0.25;
            s.color = -1.8;
        }));

        return Collections.unmodifiableList(list);
    }

    // =========================================================================
    // TOÁN HỌC ĐƯỜNG CONG TONE CURVE (từ crates/pipeline/src/finish.rs)
    // =========================================================================

    /**
     * Xây dựng 3 bảng tra cứu LUT 256 mức cho 3 kênh R, G, B bằng cách hợp nhất:
     * 1. Đường cong Tham số 4 vùng ({@code paramShadows, paramDarks, paramLights, paramHighlights})
     * 2. Đường cong Điểm tổng ({@code masterCurve})
     * 3. Đường cong Điểm từng kênh ({@code redCurve, greenCurve, blueCurve})
     */
    public static float[][] buildToneCurveLuts(State lc, int lutSize) {
        float[] base = new float[lutSize];
        float s1 = 0.25f, s2 = 0.50f, s3 = 0.75f;
        float[][] regions = {
                {0.0f, s1, lc.paramShadows},
                {s1, s2, lc.paramDarks},
                {s2, s3, lc.paramLights},
                {s3, 1.0f, lc.paramHighlights}
        };

        for (int i = 0; i < lutSize; i++) {
            float x = (float) i / (lutSize - 1);
            float d = 0f;
            for (float[] reg : regions) {
                float amt = reg[2];
                if (Math.abs(amt) < 1e-4f) continue;
                float a = reg[0], b = reg[1];
                float ctr = (a + b) * 0.5f;
                float half = (b - a) * 0.75f + 0.05f;
                float t = clamp((x - ctr) / half, -1.0f, 1.0f);
                float win = 0.5f + 0.5f * (float) Math.cos(t * Math.PI);
                d += (amt / 100.0f) * 0.22f * win;
            }
            base[i] = clamp(x + d * 4.0f * x * (1.0f - x), 0.0f, 1.0f);
        }
        // Đảm bảo tính đơn điệu (keep monotone) giống dòng 48-50 trong finish.rs
        for (int i = 1; i < lutSize; i++) {
            if (base[i] < base[i - 1]) {
                base[i] = base[i - 1];
            }
        }

        if (!State.isCurveIdentity(lc.masterCurve)) {
            float[] masterLut = new MonotoneCurve(lc.masterCurve).toLut(lutSize);
            base = composeLut(masterLut, base);
        }

        float[] rLut = !State.isCurveIdentity(lc.redCurve)
                ? composeLut(new MonotoneCurve(lc.redCurve).toLut(lutSize), base)
                : base.clone();
        float[] gLut = !State.isCurveIdentity(lc.greenCurve)
                ? composeLut(new MonotoneCurve(lc.greenCurve).toLut(lutSize), base)
                : base.clone();
        float[] bLut = !State.isCurveIdentity(lc.blueCurve)
                ? composeLut(new MonotoneCurve(lc.blueCurve).toLut(lutSize), base)
                : base.clone();

        return new float[][]{rLut, gLut, bLut, base};
    }

    private static float[] composeLut(float[] outer, float[] inner) {
        int n = inner.length;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            float x = clamp(inner[i], 0f, 1f) * (n - 1);
            int idx = Math.min(n - 2, (int) x);
            float t = x - idx;
            out[i] = outer[idx] + (outer[idx + 1] - outer[idx]) * t;
        }
        return out;
    }

    // =========================================================================
    // KHÔNG GIAN MÀU CẢM NHẬN OKLAB / OKLCH (từ crates/color/src/perceptual.rs)
    // =========================================================================

    private static void srgbLinearToOklab(float r, float g, float b, float[] outLab) {
        float l = 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b;
        float m = 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b;
        float s = 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b;

        float l_ = (float) Math.cbrt(Math.max(0f, l));
        float m_ = (float) Math.cbrt(Math.max(0f, m));
        float s_ = (float) Math.cbrt(Math.max(0f, s));

        outLab[0] = 0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_;
        outLab[1] = 1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_;
        outLab[2] = 0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_;
    }

    private static void oklabToSrgbLinear(float L, float a, float b, float[] outRgb) {
        float l_ = L + 0.3963377774f * a + 0.2158037573f * b;
        float m_ = L - 0.1055613458f * a - 0.0638541728f * b;
        float s_ = L - 0.0894841775f * a - 1.2914855480f * b;

        float l = l_ * l_ * l_;
        float m = m_ * m_ * m_;
        float s = s_ * s_ * s_;

        outRgb[0] = +4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s;
        outRgb[1] = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s;
        outRgb[2] = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s;
    }

    private static float oklchHueOfSrgbHue(float deg) {
        float[] rgb = hsvToRgb(deg, 1f, 1f);
        float[] lab = new float[3];
        srgbLinearToOklab(srgbToLinear(rgb[0]), srgbToLinear(rgb[1]), srgbToLinear(rgb[2]), lab);
        return (float) Math.atan2(lab[2], lab[1]);
    }

    private static final float[] BAND_OKLCH_HUES = new float[8];
    static {
        for (int i = 0; i < 8; i++) {
            BAND_OKLCH_HUES[i] = oklchHueOfSrgbHue(MIXER_HUES_DEG[i]);
        }
    }

    private static float wrapRad(float a) {
        float tau = (float) (Math.PI * 2.0);
        float r = (a + (float) Math.PI) % tau;
        if (r < 0f) r += tau;
        return r - (float) Math.PI;
    }

    private static float modTau(float a) {
        float tau = (float) (Math.PI * 2.0);
        float r = a % tau;
        return r < 0f ? r + tau : r;
    }

    /** Trọng số cosin phân hoạch đơn vị qua 8 dải màu ({@code band_weights} trong {@code colorops.rs}). */
    private static void computeBandWeights(float hRad, float[] outW) {
        Arrays.fill(outW, 0f);
        for (int i = 0; i < 8; i++) {
            float a = BAND_OKLCH_HUES[i];
            float b = BAND_OKLCH_HUES[(i + 1) % 8];
            float span = modTau(wrapRad(b - a));
            float d = modTau(wrapRad(hRad - a));
            if (d <= span && span > 1e-5f) {
                float t = d / span;
                float s = 0.5f - 0.5f * (float) Math.cos(t * Math.PI);
                outW[i] += 1.0f - s;
                outW[(i + 1) % 8] += s;
                break;
            }
        }
    }

    public static float[] hsvToRgb(float hDeg, float s, float v) {
        float c = v * s;
        float hp = ((hDeg % 360f) + 360f) % 360f / 60f;
        float x = c * (1f - Math.abs((hp % 2f) - 1f));
        float r = 0f, g = 0f, b = 0f;
        int sector = (int) hp;
        switch (sector) {
            case 0: r = c; g = x; b = 0f; break;
            case 1: r = x; g = c; b = 0f; break;
            case 2: r = 0f; g = c; b = x; break;
            case 3: r = 0f; g = x; b = c; break;
            case 4: r = x; g = 0f; b = c; break;
            default: r = c; g = 0f; b = x; break;
        }
        float m = v - c;
        return new float[]{r + m, g + m, b + m};
    }

    private static float srgbToLinear(float c) {
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    private static float linearToSrgb(float c) {
        c = Math.max(0f, c);
        return c <= 0.0031308f ? c * 12.92f : (float) (1.055 * Math.pow(c, 1.0 / 2.4) - 0.055);
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // =========================================================================
    // KẾT XUẤT TOÀN BỘ PIPELINE LIGHTCRAFT LÊN BITMAP
    // =========================================================================

    /**
     * Áp dụng toàn bộ các chỉnh sửa của LightCraft (Tone Curve, 3-Way OkLab Color Grading,
     * 8-Band Color Mixer và Local Masking) lên {@link Bitmap} nguồn.
     */
    public static Bitmap processBitmap(Bitmap src, State lc) {
        if (src == null || lc == null || lc.isIdentity()) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) return src;

        int[] pixels = new int[w * h];
        src.getPixels(pixels, 0, w, 0, 0, w, h);
        int[] outPixels = processPixels(pixels, w, h, lc);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(outPixels, 0, w, 0, 0, w, h);
        return out;
    }

    /**
     * Áp dụng toàn bộ pipeline LightCraft trực tiếp trên mảng điểm ảnh {@code ARGB_8888}
     * (dùng chung cho cả Android {@link Bitmap} và phiên bản máy tính Windows Desktop).
     */
    public static int[] processPixels(int[] srcPixels, int w, int h, State lc) {
        if (srcPixels == null || w <= 0 || h <= 0 || srcPixels.length < w * h) {
            return srcPixels;
        }
        int[] pixels = srcPixels.clone();
        if (lc == null || lc.isIdentity()) {
            return pixels;
        }

        boolean hasCurves = lc.hasCurveEdits();
        boolean hasGrading = lc.hasGradingEdits();
        boolean hasMixer = lc.hasMixerEdits();
        boolean hasMask = lc.hasMaskEdits();

        // 1. Chuẩn bị LUT 256 mức cho Tone Curve
        float[][] curveLuts = hasCurves ? buildToneCurveLuts(lc, 256) : null;

        // 2. Chuẩn bị tham số 3-Way Color Grading Wheels trong OkLab
        float shA = 0f, shB = 0f, shLum = 0f;
        float midA = 0f, midB = 0f, midLum = 0f;
        float hiA = 0f, hiB = 0f, hiLum = 0f;
        float blendWidth = 0.15f + (lc.gradingBlending / 100f) * 0.5f;
        float balanceMid = 0.5f - (lc.gradingBalance / 100f) * 0.25f;
        if (hasGrading) {
            float hSh = oklchHueOfSrgbHue(lc.shadowsWheel.hue);
            float sSh = (lc.shadowsWheel.sat / 100f) * 0.09f;
            shA = sSh * (float) Math.cos(hSh);
            shB = sSh * (float) Math.sin(hSh);
            shLum = (lc.shadowsWheel.lum / 100f) * 0.12f;

            float hMid = oklchHueOfSrgbHue(lc.midtonesWheel.hue);
            float sMid = (lc.midtonesWheel.sat / 100f) * 0.09f;
            midA = sMid * (float) Math.cos(hMid);
            midB = sMid * (float) Math.sin(hMid);
            midLum = (lc.midtonesWheel.lum / 100f) * 0.12f;

            float hHi = oklchHueOfSrgbHue(lc.highlightsWheel.hue);
            float sHi = (lc.highlightsWheel.sat / 100f) * 0.09f;
            hiA = sHi * (float) Math.cos(hHi);
            hiB = sHi * (float) Math.sin(hHi);
            hiLum = (lc.highlightsWheel.lum / 100f) * 0.12f;
        }

        // 3. Chuẩn bị tham số 8-Band Color Mixer
        float[] mixHue = new float[8];
        float[] mixSat = new float[8];
        float[] mixLum = new float[8];
        if (hasMixer) {
            for (int i = 0; i < 8; i++) {
                mixHue[i] = (lc.mixerHue[i] / 100f) * 0.5f;
                mixSat[i] = (lc.mixerSat[i] / 100f);
                mixLum[i] = (lc.mixerLum[i] / 100f) * 0.18f;
            }
        }

        // 4. Chuẩn bị tham số Local Masking (Radial, Linear, Sky, Subject)
        float maskExpGain = (float) Math.pow(2.0, lc.maskExposure);
        float maskTempShift = lc.maskTemp / 100f;
        float maskClarShift = lc.maskClarity / 100f;
        float maskSatShift = lc.maskSaturation / 100f;
        float ldx = lc.linearEndX - lc.linearStartX;
        float ldy = lc.linearEndY - lc.linearStartY;
        float lLen2 = Math.max(1e-6f, ldx * ldx + ldy * ldy);
        float radFeather = clamp(lc.radialFeather / 100f, 0.05f, 1.0f);
        float rx = Math.max(0.02f, lc.radialRx);
        float ry = Math.max(0.02f, lc.radialRy);

        float[] lab = new float[3];
        float[] rgbLin = new float[3];
        float[] bandW = new float[8];

        float invW = 1.0f / Math.max(1, w - 1);
        float invH = 1.0f / Math.max(1, h - 1);

        for (int y = 0; y < h; y++) {
            float ny = y * invH;
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                float nx = x * invW;
                int argb = pixels[rowOffset + x];
                int alpha = argb & 0xFF000000;
                float sr = ((argb >> 16) & 0xFF) / 255.0f;
                float sg = ((argb >> 8) & 0xFF) / 255.0f;
                float sb = (argb & 0xFF) / 255.0f;

                // A. Xử lý Local Masking nếu đang bật
                float maskAlpha = 0f;
                if (hasMask) {
                    switch (lc.maskMode) {
                        case RADIAL: {
                            float dx = (nx - lc.radialCenterX) / rx;
                            float dy = (ny - lc.radialCenterY) / ry;
                            float dist = (float) Math.sqrt(dx * dx + dy * dy);
                            maskAlpha = 1.0f - smoothstep(1.0f - radFeather, 1.0f, dist);
                            break;
                        }
                        case LINEAR: {
                            float t = ((nx - lc.linearStartX) * ldx + (ny - lc.linearStartY) * ldy) / lLen2;
                            maskAlpha = 1.0f - smoothstep(0.0f, 1.0f, t);
                            break;
                        }
                        case SKY: {
                            // Heuristic bầu trời từ masks.rs (dòng 231-243): vùng cao, sáng và giàu sắc xanh lam/trắng
                            float luma = 0.2126f * sr + 0.7152f * sg + 0.0722f * sb;
                            float blueness = Math.max(0f, sb - sr) / (sb + 1e-4f);
                            float topWeight = 1.0f - smoothstep(0.22f, 0.68f, ny);
                            maskAlpha = topWeight * smoothstep(0.20f, 0.55f, luma)
                                    * (0.4f + 0.6f * Math.max(smoothstep(0.0f, 0.28f, blueness), smoothstep(0.55f, 0.90f, luma)));
                            break;
                        }
                        case SUBJECT: {
                            // Heuristic chủ thể trung tâm từ masks.rs (dòng 260-269)
                            float dx = (nx - 0.5f) / 0.36f;
                            float dy = (ny - 0.52f) / 0.40f;
                            float d = (float) Math.sqrt(dx * dx + dy * dy);
                            maskAlpha = 1.0f - smoothstep(0.55f, 1.05f, d);
                            break;
                        }
                        default:
                            break;
                    }
                    if (lc.maskInvert) {
                        maskAlpha = 1.0f - maskAlpha;
                    }
                    maskAlpha = clamp(maskAlpha, 0f, 1f);

                    if (maskAlpha > 1e-3f) {
                        float gain = 1.0f + maskAlpha * (maskExpGain - 1.0f);
                        sr = clamp(sr * gain, 0f, 1f);
                        sg = clamp(sg * gain, 0f, 1f);
                        sb = clamp(sb * gain, 0f, 1f);

                        if (Math.abs(maskTempShift) > 1e-3f) {
                            sr = clamp(sr + maskAlpha * maskTempShift * 0.14f, 0f, 1f);
                            sb = clamp(sb - maskAlpha * maskTempShift * 0.14f, 0f, 1f);
                        }
                        if (Math.abs(maskClarShift) > 1e-3f) {
                            float luma = 0.299f * sr + 0.587f * sg + 0.114f * sb;
                            float midFactor = 4.0f * luma * (1.0f - luma);
                            float cDelta = maskAlpha * maskClarShift * 0.28f * (luma - 0.5f) * midFactor;
                            sr = clamp(sr + cDelta, 0f, 1f);
                            sg = clamp(sg + cDelta, 0f, 1f);
                            sb = clamp(sb + cDelta, 0f, 1f);
                        }
                    }
                }

                // B. Xử lý 8-Band Color Mixer & 3-Way Color Grading trong không gian OkLab / OkLCh
                float localSat = hasMask ? (maskAlpha * maskSatShift) : 0f;
                if (hasMixer || hasGrading || Math.abs(localSat) > 1e-3f) {
                    float lr = srgbToLinear(sr);
                    float lg = srgbToLinear(sg);
                    float lb = srgbToLinear(sb);
                    srgbLinearToOklab(lr, lg, lb, lab);

                    if (hasMixer || Math.abs(localSat) > 1e-3f) {
                        float L = lab[0];
                        float C = (float) Math.hypot(lab[1], lab[2]);
                        float H = (float) Math.atan2(lab[2], lab[1]);

                        if (hasMixer && C > 1e-4f) {
                            computeBandWeights(H, bandW);
                            float dh = 0f, ds = 0f, dl = 0f;
                            for (int i = 0; i < 8; i++) {
                                dh += bandW[i] * mixHue[i];
                                ds += bandW[i] * mixSat[i];
                                dl += bandW[i] * mixLum[i];
                            }
                            float chromaW = Math.min(1.0f, C / 0.12f);
                            H += dh * chromaW;
                            C = Math.max(0f, C * (1.0f + ds));
                            L = clamp(L + dl * chromaW * (float) Math.sqrt(Math.max(0.05f, L)), 0f, 1f);
                        }
                        if (Math.abs(localSat) > 1e-3f) {
                            C = Math.max(0f, C * (1.0f + localSat));
                        }
                        lab[0] = L;
                        lab[1] = C * (float) Math.cos(H);
                        lab[2] = C * (float) Math.sin(H);
                    }

                    if (hasGrading) {
                        float ws = 1.0f - smoothstep(balanceMid - blendWidth, balanceMid + blendWidth * 0.25f, lab[0]);
                        float wh = smoothstep(balanceMid - blendWidth * 0.25f, balanceMid + blendWidth, lab[0]);
                        float wm = Math.max(0f, 1.0f - ws - wh);

                        lab[1] += shA * ws + midA * wm + hiA * wh;
                        lab[2] += shB * ws + midB * wm + hiB * wh;
                        lab[0] = clamp(lab[0] + shLum * ws + midLum * wm + hiLum * wh, 0f, 1f);
                    }

                    oklabToSrgbLinear(lab[0], lab[1], lab[2], rgbLin);
                    sr = clamp(linearToSrgb(rgbLin[0]), 0f, 1f);
                    sg = clamp(linearToSrgb(rgbLin[1]), 0f, 1f);
                    sb = clamp(linearToSrgb(rgbLin[2]), 0f, 1f);
                }

                // C. Áp dụng bảng tra cứu Tone Curve (Parametric ∘ Master ∘ R/G/B)
                if (curveLuts != null) {
                    sr = evalLut256(curveLuts[0], sr);
                    sg = evalLut256(curveLuts[1], sg);
                    sb = evalLut256(curveLuts[2], sb);
                }

                // D. Nếu người dùng bật "Hiển thị vùng Mask đỏ" (giống lớp phủ đỏ của LightCraft masking.jpg)
                if (hasMask && lc.showMaskOverlay && maskAlpha > 0.01f) {
                    float overlayStrength = maskAlpha * 0.48f;
                    sr = sr * (1f - overlayStrength) + 0.95f * overlayStrength;
                    sg = sg * (1f - overlayStrength) + 0.18f * overlayStrength;
                    sb = sb * (1f - overlayStrength) + 0.22f * overlayStrength;
                }

                int ir = Math.min(255, Math.max(0, Math.round(sr * 255f)));
                int ig = Math.min(255, Math.max(0, Math.round(sg * 255f)));
                int ib = Math.min(255, Math.max(0, Math.round(sb * 255f)));
                pixels[rowOffset + x] = alpha | (ir << 16) | (ig << 8) | ib;
            }
        }

        return pixels;
    }

    private static float evalLut256(float[] lut, float x) {
        float f = clamp(x, 0f, 1f) * 255f;
        int i = Math.min(254, (int) f);
        float t = f - i;
        return lut[i] + (lut[i + 1] - lut[i]) * t;
    }
}
