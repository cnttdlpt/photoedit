package vn.iro.editor;

import android.graphics.Bitmap;

/**
 * Lớp bảo vệ chống tối ảnh và cháy sáng khi Tự động cân bằng (Auto-Balance Guard).
 * Chuyển đổi trực tiếp và đồng bộ 100% từ {@code ios/IRO/Core/AutoBalanceGuard.swift}.
 *
 * <p>Nguyên tắc:
 * <ul>
 *     <li>Cân bằng tự động tuyệt đối không được phép làm tối một khung cảnh sáng tự nhiên để biến nó thành xám xịt.</li>
 *     <li>Kiểm tra đánh giá qua {@link Meter}: đo độ chói trước và sau trên các dải màu (Bands),
 *         đảm bảo không bị suy giảm độ chói ({@code brightnessOK}) và không làm clipping vùng sáng mới ({@code highlightsOK}).</li>
 *     <li>Nếu bị nén tone quá mức, tự động tìm kiếm nhị phân bù sáng tối ưu (tối đa +0.6 EV, trần 1.5 EV).</li>
 *     <li>Nếu sau các mức suy giảm [1.0, 0.75, 0.5, 0.25, 0.125] mà vẫn không đạt, trả về {@link Settings} trung tính
 *         thay vì đưa ra kết quả làm ảnh xấu hơn bản gốc.</li>
 * </ul>
 */
public final class AutoBalanceGuard {

    private AutoBalanceGuard() {
    }

    public static Settings protect(Bitmap bitmap, Settings proposed) {
        if (bitmap == null || bitmap.getWidth() < 1 || bitmap.getHeight() < 1) {
            return proposed != null ? proposed.copy() : new Settings();
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        return protect(pixels, w, h, proposed);
    }

    public static Settings protect(int[] imagePixels, int width, int height, Settings proposed) {
        if (imagePixels == null || imagePixels.length < 1 || proposed == null) {
            return proposed != null ? proposed.copy() : new Settings();
        }
        int total = width * height;
        int count = Math.min(8192, total);
        int[] sample = new int[count];
        int sampleCount = 0;

        for (int i = 0; i < count; i++) {
            int offset = (int) (((long) i * total) / count);
            if (offset < imagePixels.length) {
                int p = imagePixels[offset];
                int a = (p >>> 24) & 255;
                if (a >= 16) {
                    sample[sampleCount++] = p;
                }
            }
        }

        if (sampleCount == 0) {
            return new Settings();
        }

        int[] cleanSample = new int[sampleCount];
        System.arraycopy(sample, 0, cleanSample, 0, sampleCount);

        Meter reference = new Meter(cleanSample);

        Settings candidate = proposed.copy();
        candidate.exposure = Math.max(0.0, candidate.exposure);
        candidate.shadow = Math.min(0.0, candidate.shadow);
        candidate.grain = 0.0;
        candidate.nr = -4.0;
        candidate.sharpness = 0.0;
        candidate.clarity = 0.0;

        double[] amounts = new double[]{1.0, 0.75, 0.5, 0.25, 0.125, 0.0};
        for (double amount : amounts) {
            Settings settings = attenuated(candidate, amount);
            int[] rendered = ColorEngine.process(cleanSample, sampleCount, 1, settings, null);
            Meter.Assessment assessment = reference.assess(rendered);

            if (!assessment.brightnessOK) {
                double lower = settings.exposure;
                double upper = Math.min(1.5, lower + 0.6);
                Settings brightened = settings.copy();
                brightened.exposure = upper;
                int[] brightRendered = ColorEngine.process(cleanSample, sampleCount, 1, brightened, null);

                if (reference.assess(brightRendered).brightnessOK) {
                    for (int step = 0; step < 10; step++) {
                        double middle = (lower + upper) / 2.0;
                        brightened.exposure = middle;
                        int[] midRendered = ColorEngine.process(cleanSample, sampleCount, 1, brightened, null);
                        if (reference.assess(midRendered).brightnessOK) {
                            upper = middle;
                        } else {
                            lower = middle;
                        }
                    }
                    settings.exposure = upper;
                    rendered = ColorEngine.process(cleanSample, sampleCount, 1, settings, null);
                    assessment = reference.assess(rendered);
                }
            }

            if (assessment.brightnessOK && assessment.highlightsOK) {
                return settings;
            }
        }

        return new Settings();
    }

    private static Settings attenuated(Settings input, double amount) {
        Settings s = input.copy();
        s.exposure *= amount;
        s.highlight *= amount;
        s.shadow *= amount;
        s.temperature *= amount;
        s.red *= amount;
        s.blue *= amount;
        s.color *= amount;
        s.colorChrome *= amount;
        s.chromeBlue *= amount;
        if (amount < 0.75) {
            s.dr = 100;
        }
        return s;
    }

    private static final class Meter {
        private final int[] sourcePixels;
        private final double[] luminances;
        private final double[] alpha;
        private final boolean hasBrightHighlights;

        static final class Assessment {
            final boolean brightnessOK;
            final boolean highlightsOK;

            Assessment(boolean brightnessOK, boolean highlightsOK) {
                this.brightnessOK = brightnessOK;
                this.highlightsOK = highlightsOK;
            }
        }

        Meter(int[] source) {
            this.sourcePixels = source;
            int n = source.length;
            this.luminances = new double[n];
            this.alpha = new double[n];

            double totalAlpha = 0.0;
            double highlightAlpha = 0.0;

            for (int i = 0; i < n; i++) {
                int p = source[i];
                int a = (p >>> 24) & 255;
                int r = (p >>> 16) & 255;
                int g = (p >>> 8) & 255;
                int b = p & 255;

                double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                double al = a / 255.0;

                luminances[i] = lum;
                alpha[i] = al;
                totalAlpha += al;

                if (lum >= 245.0) {
                    highlightAlpha += al;
                }
            }

            this.hasBrightHighlights = totalAlpha > 0.0 && highlightAlpha >= totalAlpha * 0.10;
        }

        Assessment assess(int[] rendered) {
            int n = luminances.length;
            double[] weights = new double[5];
            double[] differences = new double[5];
            double newClipping = 0.0;
            double highlightGain = 0.0;
            double highlightWeight = 0.0;

            for (int i = 0; i < n; i++) {
                double before = luminances[i];
                int rp = rendered[i];
                int rr = (rp >>> 16) & 255;
                int rg = (rp >>> 8) & 255;
                int rb = rp & 255;
                double after = 0.2126 * rr + 0.7152 * rg + 0.0722 * rb;

                double weight = alpha[i];
                double[] groups = new double[]{1.0, 0.0, 0.0, 0.0, 0.0};
                if (before >= 16.0 && before < 64.0) groups[1] = 1.0;
                if (before >= 64.0 && before < 128.0) groups[2] = 1.0;
                if (before >= 128.0 && before <= 220.0) groups[3] = 1.0;
                if (before >= 45.0 && before <= 210.0) {
                    groups[4] = Math.max(0.0, 1.0 - Math.abs(before - 128.0) / 83.0);
                }

                for (int band = 0; band < 5; band++) {
                    weights[band] += weight * groups[band];
                    differences[band] += (after - before) * weight * groups[band];
                }

                int sp = sourcePixels[i];
                int sr = (sp >>> 16) & 255;
                int sg = (sp >>> 8) & 255;
                int sb = sp & 255;
                int sourcePeak = Math.max(sr, Math.max(sg, sb));
                int outputPeak = Math.max(rr, Math.max(rg, rb));

                if (sourcePeak < 254 && outputPeak >= 254) {
                    newClipping += weight;
                }
                if (before >= 245.0) {
                    highlightGain += (after - before) * weight;
                    highlightWeight += weight;
                }
            }

            boolean brightnessOK = true;
            for (int band = 0; band < 5; band++) {
                if (band == 0 && hasBrightHighlights) continue;
                if (weights[band] == 0.0) continue;
                if (differences[band] / weights[band] < -0.75) {
                    brightnessOK = false;
                    break;
                }
            }

            boolean highlightsOK = (newClipping <= weights[0] * 0.01)
                    && (highlightWeight == 0.0 || (highlightGain / highlightWeight <= 0.75));

            return new Assessment(brightnessOK, highlightsOK);
        }
    }
}
