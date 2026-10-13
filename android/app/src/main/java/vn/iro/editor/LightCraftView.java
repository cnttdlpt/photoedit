package vn.iro.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;
import java.util.Locale;

/**
 * Bộ 3 Widget tương tác phòng tối <b>LightCraft UI</b> ({@code LightCraftView}), chuyển thể trực tiếp từ
 * {@code crates/ui-egui/src/panels/edit.rs} và {@code crates/ui-egui/src/panels/masking.rs} của dự án
 * <b>LightCraft</b> ({@code https://github.com/cnttdlpt/lightcraft.git}):
 *
 * <ul>
 *     <li>{@link ToneCurveGraphView}: Đồ thị đường cong <b>Monotone Cubic Hermite Spline</b> (4×4 ô lưới,
 *         chạm/kéo điểm điều khiển cho 5 kênh {@code Parametric / RGB / Red / Green / Blue}, chạm đúp xóa điểm,
 *         hiển thị thông số {@code Input / Output 0..255}).</li>
 *     <li>{@link GradingWheelsView}: Bộ 3 bánh xe màu điện ảnh <b>3-Way Color Grading Wheels</b>
 *         ({@code Shadows · Midtones · Highlights}) tính toán trong không gian <b>OkLab / OkLCh</b>,
 *         hỗ trợ kéo 360° chọn Hue & Saturation và chạm đúp đặt lại tâm trắng.</li>
 *     <li>{@link StageMaskOverlayView}: Lớp phủ tương tác trực tiếp trên sân khấu ảnh cho
 *         <b>Radial Gradient Mask</b> (kéo tâm & bán kính elip) và <b>Linear Gradient Mask</b>
 *         (kéo điểm đầu & điểm cuối chuyển sắc bầu trời).</li>
 * </ul>
 */
public final class LightCraftView {

    private LightCraftView() {}

    // =========================================================================
    // 1. ĐỒ THỊ ĐƯỜNG CONG TONE CURVE (từ curve_editor trong panels/edit.rs)
    // =========================================================================

    public static final class ToneCurveGraphView extends View {

        /** Kênh đường cong đang hiển thị và tương tác. */
        public enum Channel {
            PARAMETRIC("Tham số", StudioStyle.INK),
            MASTER("RGB", StudioStyle.INK),
            RED("Đỏ", StudioStyle.GOOGLE_RED),
            GREEN("Lục", StudioStyle.GOOGLE_GREEN),
            BLUE("Lam", StudioStyle.GOOGLE_BLUE);

            public final String label;
            public final int color;

            Channel(String label, int color) {
                this.label = label;
                this.color = color;
            }
        }

        private final LightCraftEngine.State state;
        private final Runnable onCurveChanged;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path curvePath = new Path();
        private final Path fillPath = new Path();
        private final RectF plotRect = new RectF();
        private final DashPathEffect splitDash = new DashPathEffect(new float[]{6f, 6f}, 0f);

        private Channel activeChannel = Channel.MASTER;
        private int draggingPointIndex = -1;
        private long lastDownTimeMs = 0L;
        private float lastDownX = 0f;
        private float lastDownY = 0f;

        public ToneCurveGraphView(Context context, LightCraftEngine.State state, Runnable onCurveChanged) {
            super(context);
            this.state = state;
            this.onCurveChanged = onCurveChanged;
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            setClickable(true);
        }

        public void setChannel(Channel ch) {
            this.activeChannel = (ch == null) ? Channel.MASTER : ch;
            this.draggingPointIndex = -1;
            invalidate();
        }

        public Channel getChannel() {
            return activeChannel;
        }

        private float dp(float v) {
            return getResources().getDisplayMetrics().density * v;
        }

        private List<float[]> activePoints() {
            switch (activeChannel) {
                case RED: return state.redCurve;
                case GREEN: return state.greenCurve;
                case BLUE: return state.blueCurve;
                default: return state.masterCurve;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float pad = dp(10);
            plotRect.set(pad, pad, w - pad, h - pad);

            // 1. Nền sáng phẳng tối giản (#EFF1F5) + viền mảnh
            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(StudioStyle.SURFACE);
            canvas.drawRoundRect(0, 0, w, h, dp(12), dp(12), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.0f));
            paint.setColor(StudioStyle.LINE);
            canvas.drawRoundRect(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f), dp(12), dp(12), paint);

            // 2. Lưới 4×4 và đường chéo tham chiếu y = x
            paint.setStrokeWidth(dp(0.8f));
            paint.setColor(0x16181a1f);
            for (int i = 1; i < 4; i++) {
                float f = i / 4.0f;
                float gx = plotRect.left + plotRect.width() * f;
                float gy = plotRect.top + plotRect.height() * f;
                canvas.drawLine(gx, plotRect.top, gx, plotRect.bottom, paint);
                canvas.drawLine(plotRect.left, gy, plotRect.right, gy, paint);
            }
            if (activeChannel == Channel.PARAMETRIC) {
                paint.setPathEffect(splitDash);
                paint.setColor(0x28181a1f);
                for (float split : new float[]{0.25f, 0.50f, 0.75f}) {
                    float sx = plotRect.left + plotRect.width() * split;
                    canvas.drawLine(sx, plotRect.top, sx, plotRect.bottom, paint);
                }
                paint.setPathEffect(null);
            }

            paint.setColor(0x28181a1f);
            canvas.drawLine(plotRect.left, plotRect.bottom, plotRect.right, plotRect.top, paint);

            // 3. Vẽ mờ 3 đường cong R/G/B phía sau
            float[][] luts = LightCraftEngine.buildToneCurveLuts(state, 128);
            int[] bgColors = {0x38d9483b, 0x382e9b52, 0x382563eb};
            paint.setStrokeWidth(dp(1.1f));
            for (int ch = 0; ch < 3; ch++) {
                buildLutPath(luts[ch], curvePath);
                paint.setColor(bgColors[ch]);
                canvas.drawPath(curvePath, paint);
            }

            // 4. Vẽ đường cong chính đang chọn
            float[] mainLut;
            int mainColor = activeChannel.color;
            if (activeChannel == Channel.RED) {
                mainLut = luts[0];
            } else if (activeChannel == Channel.GREEN) {
                mainLut = luts[1];
            } else if (activeChannel == Channel.BLUE) {
                mainLut = luts[2];
            } else {
                mainLut = luts[3];
            }

            buildLutPath(mainLut, curvePath);

            fillPath.set(curvePath);
            fillPath.lineTo(plotRect.right, plotRect.bottom);
            fillPath.lineTo(plotRect.left, plotRect.bottom);
            fillPath.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(
                    0, plotRect.top, 0, plotRect.bottom,
                    (mainColor & 0x00ffffff) | 0x18000000,
                    0x00000000,
                    Shader.TileMode.CLAMP));
            canvas.drawPath(fillPath, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2.0f));
            paint.setColor(mainColor);
            canvas.drawPath(curvePath, paint);

            // 5. Vẽ các điểm điều khiển (Control Points)
            if (activeChannel != Channel.PARAMETRIC) {
                List<float[]> pts = activePoints();
                for (int i = 0; i < pts.size(); i++) {
                    float[] pt = pts.get(i);
                    float cx = plotRect.left + pt[0] * plotRect.width();
                    float cy = plotRect.bottom - pt[1] * plotRect.height();
                    boolean activePt = (i == draggingPointIndex);
                    float rad = dp(activePt ? 6.0f : 4.4f);

                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.WHITE);
                    canvas.drawCircle(cx, cy, rad, paint);

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(1.8f));
                    paint.setColor(mainColor);
                    canvas.drawCircle(cx, cy, rad, paint);
                }

                textPaint.setTextSize(dp(10f));
                textPaint.setColor(StudioStyle.MUTED);
                if (draggingPointIndex >= 0 && draggingPointIndex < pts.size()) {
                    float[] q = pts.get(draggingPointIndex);
                    String io = String.format(Locale.US, "In %d → Out %d",
                            Math.round(q[0] * 255f), Math.round(q[1] * 255f));
                    canvas.drawText(io, plotRect.left + dp(6), plotRect.top + dp(13), textPaint);
                } else {
                    canvas.drawText("Chạm thêm điểm · Chạm đúp xóa", plotRect.left + dp(6), plotRect.top + dp(13), textPaint);
                }
            } else {
                textPaint.setTextSize(dp(10f));
                textPaint.setColor(StudioStyle.MUTED);
                canvas.drawText("Đường cong 4 vùng tham số",
                        plotRect.left + dp(6), plotRect.top + dp(13), textPaint);
            }
        }

        private void buildLutPath(float[] lut, Path out) {
            out.reset();
            int n = lut.length;
            for (int i = 0; i < n; i++) {
                float x = plotRect.left + ((float) i / (n - 1)) * plotRect.width();
                float y = plotRect.bottom - lut[i] * plotRect.height();
                if (i == 0) {
                    out.moveTo(x, y);
                } else {
                    out.lineTo(x, y);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (activeChannel == Channel.PARAMETRIC) {
                return false;
            }
            if (plotRect.width() <= 1f || plotRect.height() <= 1f) {
                return false;
            }
            List<float[]> pts = activePoints();
            float tx = event.getX();
            float ty = event.getY();
            float nx = clamp((tx - plotRect.left) / plotRect.width(), 0f, 1f);
            float ny = clamp((plotRect.bottom - ty) / plotRect.height(), 0f, 1f);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    if ( getParent() != null ) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    int nearestIdx = findNearestPoint(pts, tx, ty, dp(20));
                    long now = System.currentTimeMillis();
                    // Chạm đúp vào điểm ở giữa để xóa điểm (giống dòng 893-902 trong edit.rs)
                    if (now - lastDownTimeMs < 320L && Math.hypot(tx - lastDownX, ty - lastDownY) < dp(24)) {
                        if (nearestIdx > 0 && nearestIdx < pts.size() - 1) {
                            pts.remove(nearestIdx);
                            draggingPointIndex = -1;
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            invalidate();
                            if (onCurveChanged != null) onCurveChanged.run();
                            lastDownTimeMs = 0L;
                            return true;
                        }
                    }
                    lastDownTimeMs = now;
                    lastDownX = tx;
                    lastDownY = ty;

                    if (nearestIdx >= 0) {
                        draggingPointIndex = nearestIdx;
                    } else if (pts.size() < 12) {
                        // Thêm điểm điều khiển mới theo thứ tự hoành độ x tăng dần
                        int insertAt = pts.size();
                        for (int i = 0; i < pts.size(); i++) {
                            if (pts.get(i)[0] > nx) {
                                insertAt = i;
                                break;
                            }
                        }
                        pts.add(insertAt, new float[]{nx, ny});
                        draggingPointIndex = insertAt;
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        if (onCurveChanged != null) onCurveChanged.run();
                    }
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (draggingPointIndex >= 0 && draggingPointIndex < pts.size()) {
                        float lo = (draggingPointIndex == 0) ? 0f : (pts.get(draggingPointIndex - 1)[0] + 0.02f);
                        float hi = (draggingPointIndex == pts.size() - 1) ? 1f : (pts.get(draggingPointIndex + 1)[0] - 0.02f);
                        float clampedX;
                        if (draggingPointIndex == 0) {
                            clampedX = Math.min(nx, hi);
                        } else if (draggingPointIndex == pts.size() - 1) {
                            clampedX = Math.max(nx, lo);
                        } else {
                            clampedX = clamp(nx, lo, hi);
                        }
                        pts.get(draggingPointIndex)[0] = clampedX;
                        pts.get(draggingPointIndex)[1] = ny;
                        invalidate();
                        if (onCurveChanged != null) onCurveChanged.run();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    draggingPointIndex = -1;
                    invalidate();
                    return true;
                }
            }
            return super.onTouchEvent(event);
        }

        private int findNearestPoint(List<float[]> pts, float sx, float sy, float maxDistPx) {
            int bestIdx = -1;
            float bestDist = maxDistPx;
            for (int i = 0; i < pts.size(); i++) {
                float px = plotRect.left + pts.get(i)[0] * plotRect.width();
                float py = plotRect.bottom - pts.get(i)[1] * plotRect.height();
                float d = (float) Math.hypot(sx - px, sy - py);
                if (d < bestDist) {
                    bestDist = d;
                    bestIdx = i;
                }
            }
            return bestIdx;
        }
    }

    // =========================================================================
    // 2. BỘ 3 BÁNH XE MÀU 3-WAY COLOR GRADING WHEELS (từ grading_wheel trong edit.rs)
    // =========================================================================

    public static final class GradingWheelsView extends View {

        private static final String[] WHEEL_LABELS = {"Vùng tối (Shadows)", "Trung tính (Mid)", "Vùng sáng (High)"};
        private static final int[] SWEEP_COLORS = {
                0xFFE05252, // 0° Red
                0xFFE09A52, // 30° Orange
                0xFFE0DF52, // 60° Yellow
                0xFF52E068, // 120° Green
                0xFF52DFE0, // 180° Cyan
                0xFF527CE0, // 240° Blue
                0xFFC552E0, // 300° Magenta
                0xFFE05252  // 360° Red
        };

        private final LightCraftEngine.State state;
        private final Runnable onGradingChanged;
        private final Paint wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final DashPathEffect guideDash = new DashPathEffect(new float[]{5f, 5f}, 0f);
        private final float[] cx = new float[3];
        private final float[] cy = new float[3];
        private float radius = 1f;
        private int activeWheelIndex = -1;
        private long lastTapTimeMs = 0L;
        private int lastTappedWheel = -1;

        public GradingWheelsView(Context context, LightCraftEngine.State state, Runnable onGradingChanged) {
            super(context);
            this.state = state;
            this.onGradingChanged = onGradingChanged;
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            textPaint.setTextAlign(Paint.Align.CENTER);
            setClickable(true);
        }

        private float dp(float v) {
            return getResources().getDisplayMetrics().density * v;
        }

        private LightCraftEngine.GradingWheel wheelAt(int idx) {
            if (idx == 0) return state.shadowsWheel;
            if (idx == 1) return state.midtonesWheel;
            return state.highlightsWheel;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            // Vẽ thẻ nền phẳng tối giản (#EFF1F5) + viền mảnh
            ringPaint.setPathEffect(null);
            ringPaint.setStyle(Paint.Style.FILL);
            ringPaint.setColor(StudioStyle.SURFACE);
            canvas.drawRoundRect(0, 0, w, h, dp(12), dp(12), ringPaint);

            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(1f));
            ringPaint.setColor(StudioStyle.LINE);
            canvas.drawRoundRect(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f), dp(12), dp(12), ringPaint);

            float cellW = w / 3.0f;
            float maxR = Math.min(cellW * 0.41f, (h - dp(38)) * 0.45f);
            radius = Math.max(dp(24), maxR);
            float centerY = dp(18) + radius;

            for (int i = 0; i < 3; i++) {
                cx[i] = cellW * (i + 0.5f);
                cy[i] = centerY;
                drawSingleWheel(canvas, i, cx[i], cy[i], radius);
            }
        }

        private void drawSingleWheel(Canvas canvas, int idx, float x, float y, float r) {
            LightCraftEngine.GradingWheel wh = wheelAt(idx);

            // 1. Vẽ đĩa quang phổ Hue (SweepGradient) + khử bão hòa ở tâm (RadialGradient trắng/xám)
            canvas.save();
            canvas.scale(1f, -1f, x, y);
            wheelPaint.setStyle(Paint.Style.FILL);
            wheelPaint.setShader(new SweepGradient(x, y, SWEEP_COLORS, null));
            canvas.drawCircle(x, y, r, wheelPaint);
            canvas.restore();

            wheelPaint.setShader(new RadialGradient(
                    x, y, r,
                    new int[]{0xFFF4F5F7, 0x99F4F5F7, 0x00F4F5F7},
                    new float[]{0f, 0.48f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, r, wheelPaint);
            wheelPaint.setShader(null);

            // 2. Vòng hướng dẫn độ bão hòa
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setPathEffect(guideDash);
            ringPaint.setStrokeWidth(dp(0.8f));
            ringPaint.setColor(0x28181a1f);
            canvas.drawCircle(x, y, r * 0.45f, ringPaint);
            canvas.drawCircle(x, y, r * 0.75f, ringPaint);
            ringPaint.setPathEffect(null);

            // Viền ngoài bánh xe
            ringPaint.setStrokeWidth(dp(1.2f));
            ringPaint.setColor(idx == activeWheelIndex ? StudioStyle.INK : StudioStyle.LINE);
            canvas.drawCircle(x, y, r, ringPaint);

            // 3. Điểm trung tính ở tâm và hạt điều khiển
            ringPaint.setStyle(Paint.Style.FILL);
            ringPaint.setColor(0x66181a1f);
            canvas.drawCircle(x, y, dp(2f), ringPaint);

            float radAngle = (float) Math.toRadians(wh.hue);
            float dist = (wh.sat / 100f) * r;
            float dotX = x + (float) Math.cos(radAngle) * dist;
            float dotY = y - (float) Math.sin(radAngle) * dist;

            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(1.1f));
            ringPaint.setColor(0x88181a1f);
            canvas.drawLine(x, y, dotX, dotY, ringPaint);

            ringPaint.setStyle(Paint.Style.FILL);
            ringPaint.setColor(Color.WHITE);
            canvas.drawCircle(dotX, dotY, dp(5.2f), ringPaint);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(1.6f));
            ringPaint.setColor(StudioStyle.INK);
            canvas.drawCircle(dotX, dotY, dp(5.2f), ringPaint);

            // 4. Thông số H° / S% phía trên bánh xe và tên vùng phía dưới
            textPaint.setTextSize(dp(9.5f));
            textPaint.setColor(wh.sat > 0.5f ? StudioStyle.INK : StudioStyle.MUTED);
            String readout = String.format(Locale.US, "%d° · %d%%", Math.round(wh.hue), Math.round(wh.sat));
            canvas.drawText(readout, x, y - r - dp(5), textPaint);

            textPaint.setTextSize(dp(10f));
            textPaint.setColor(StudioStyle.INK);
            canvas.drawText(WHEEL_LABELS[idx], x, y + r + dp(13), textPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float tx = event.getX();
            float ty = event.getY();

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    int hit = 0;
                    float bestD = Float.MAX_VALUE;
                    for (int i = 0; i < 3; i++) {
                        float d = (float) Math.hypot(tx - cx[i], ty - cy[i]);
                        if (d < bestD) {
                            bestD = d;
                            hit = i;
                        }
                    }
                    activeWheelIndex = hit;
                    long now = System.currentTimeMillis();
                    if (hit == lastTappedWheel && (now - lastTapTimeMs) < 320L) {
                        // Chạm đúp đặt lại bánh xe về trung tính (0°, 0%) giống dòng 1345-1347 trong edit.rs
                        wheelAt(hit).reset();
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        invalidate();
                        if (onGradingChanged != null) onGradingChanged.run();
                        lastTapTimeMs = 0L;
                        return true;
                    }
                    lastTappedWheel = hit;
                    lastTapTimeMs = now;
                    updateWheelFromTouch(hit, tx, ty);
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (activeWheelIndex >= 0 && activeWheelIndex < 3) {
                        updateWheelFromTouch(activeWheelIndex, tx, ty);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    activeWheelIndex = -1;
                    invalidate();
                    return true;
                }
            }
            return super.onTouchEvent(event);
        }

        /** Chuyển đổi tọa độ chạm sang góc Hue (0..360) và Saturation (0..100) giống {@code wheel_pick} trong edit.rs. */
        private void updateWheelFromTouch(int idx, float tx, float ty) {
            float vx = tx - cx[idx];
            float vy = ty - cy[idx];
            float deg = (float) ((Math.toDegrees(Math.atan2(-vy, vx)) + 360.0) % 360.0);
            float sat = clamp(((float) Math.hypot(vx, vy) / Math.max(1f, radius)) * 100f, 0f, 100f);
            LightCraftEngine.GradingWheel w = wheelAt(idx);
            w.hue = deg;
            w.sat = sat;
            invalidate();
            if (onGradingChanged != null) onGradingChanged.run();
        }
    }

    // =========================================================================
    // 3. LỚP PHỦ MẶT NẠ CỤC BỘ TRÊN SÂN KHẤU ẢNH (Radial & Linear Gradient Pins)
    // =========================================================================

    public static final class StageMaskOverlayView extends View {

        private final LightCraftEngine.State state;
        private final Runnable onMaskGeometryChanged;
        private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF imageBounds = new RectF();
        private final DashPathEffect ringDash = new DashPathEffect(new float[]{10f, 8f}, 0f);

        private int imageAspectW = 0;
        private int imageAspectH = 0;
        private boolean interactiveEnabled = false;
        // 0: không kéo, 1: tâm Radial / đầu Linear, 2: vành Radial / cuối Linear
        private int dragHandle = 0;

        public StageMaskOverlayView(Context context, LightCraftEngine.State state, Runnable onMaskGeometryChanged) {
            super(context);
            this.state = state;
            this.onMaskGeometryChanged = onMaskGeometryChanged;
        }

        public void setImageAspect(int w, int h) {
            if (this.imageAspectW != w || this.imageAspectH != h) {
                this.imageAspectW = w;
                this.imageAspectH = h;
                invalidate();
            }
        }

        public void setInteractiveEnabled(boolean enabled) {
            this.interactiveEnabled = enabled;
            invalidate();
        }

        private float dp(float v) {
            return getResources().getDisplayMetrics().density * v;
        }

        private void computeFitCenterBounds(int viewW, int viewH, RectF out) {
            if (imageAspectW <= 0 || imageAspectH <= 0) {
                out.set(0, 0, viewW, viewH);
                return;
            }
            float scale = Math.min((float) viewW / imageAspectW, (float) viewH / imageAspectH);
            float drawW = imageAspectW * scale;
            float drawH = imageAspectH * scale;
            float left = (viewW - drawW) * 0.5f;
            float top = (viewH - drawH) * 0.5f;
            out.set(left, top, left + drawW, top + drawH);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!interactiveEnabled || state == null || state.maskMode == LightCraftEngine.MaskMode.NONE) {
                return;
            }
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            computeFitCenterBounds(w, h, imageBounds);
            if (imageBounds.width() <= 1f || imageBounds.height() <= 1f) return;

            float bw = imageBounds.width();
            float bh = imageBounds.height();

            if (state.maskMode == LightCraftEngine.MaskMode.RADIAL) {
                float cx = imageBounds.left + state.radialCenterX * bw;
                float cy = imageBounds.top + state.radialCenterY * bh;
                float rx = state.radialRx * bw;
                float ry = state.radialRy * bh;

                // Vành elip ngoài và vành feather bên trong (giống lớp phủ Radial trong masking.jpg)
                overlayPaint.setPathEffect(null);
                overlayPaint.setStyle(Paint.Style.STROKE);
                overlayPaint.setStrokeWidth(dp(2f));
                overlayPaint.setColor(0xddea4335);
                canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, overlayPaint);

                float innerScale = 1.0f - clamp(state.radialFeather / 100f, 0.05f, 0.95f);
                overlayPaint.setPathEffect(ringDash);
                overlayPaint.setStrokeWidth(dp(1.2f));
                overlayPaint.setColor(0x99ffffff);
                canvas.drawOval(
                        cx - rx * innerScale,
                        cy - ry * innerScale,
                        cx + rx * innerScale,
                        cy + ry * innerScale,
                        overlayPaint);
                overlayPaint.setPathEffect(null);

                // Chốt tâm (Center Pin) và chốt kéo bán kính (Radius Handle)
                drawPin(canvas, cx, cy, StudioStyle.GOOGLE_RED);
                drawPin(canvas, cx + rx, cy, StudioStyle.GOOGLE_BLUE);
            } else if (state.maskMode == LightCraftEngine.MaskMode.LINEAR) {
                float sx = imageBounds.left + state.linearStartX * bw;
                float sy = imageBounds.top + state.linearStartY * bh;
                float ex = imageBounds.left + state.linearEndX * bw;
                float ey = imageBounds.top + state.linearEndY * bh;

                overlayPaint.setPathEffect(ringDash);
                overlayPaint.setStyle(Paint.Style.STROKE);
                overlayPaint.setStrokeWidth(dp(1.8f));
                overlayPaint.setColor(0xddffffff);
                canvas.drawLine(sx, sy, ex, ey, overlayPaint);
                overlayPaint.setPathEffect(null);

                // Vẽ 2 đường vuông góc qua điểm đầu và điểm cuối của dải chuyển sắc Linear Gradient
                float dx = ex - sx;
                float dy = ey - sy;
                float len = (float) Math.hypot(dx, dy);
                if (len > 1f) {
                    float px = -dy / len * dp(64);
                    float py = dx / len * dp(64);
                    overlayPaint.setColor(0xccea4335);
                    canvas.drawLine(sx - px, sy - py, sx + px, sy + py, overlayPaint);
                    overlayPaint.setColor(0xcc4285f4);
                    canvas.drawLine(ex - px, ey - py, ex + px, ey + py, overlayPaint);
                }

                drawPin(canvas, sx, sy, StudioStyle.GOOGLE_RED);
                drawPin(canvas, ex, ey, StudioStyle.GOOGLE_BLUE);
            }
        }

        private void drawPin(Canvas canvas, float x, float y, int fill) {
            overlayPaint.setStyle(Paint.Style.FILL);
            overlayPaint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, dp(7.5f), overlayPaint);
            overlayPaint.setColor(fill);
            canvas.drawCircle(x, y, dp(5.2f), overlayPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!interactiveEnabled || state == null) {
                return false;
            }
            if (state.maskMode != LightCraftEngine.MaskMode.RADIAL
                    && state.maskMode != LightCraftEngine.MaskMode.LINEAR) {
                return false;
            }
            computeFitCenterBounds(getWidth(), getHeight(), imageBounds);
            float bw = imageBounds.width();
            float bh = imageBounds.height();
            if (bw <= 1f || bh <= 1f) return false;

            float tx = event.getX();
            float ty = event.getY();
            float nx = clamp((tx - imageBounds.left) / bw, 0.02f, 0.98f);
            float ny = clamp((ty - imageBounds.top) / bh, 0.02f, 0.98f);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    if (state.maskMode == LightCraftEngine.MaskMode.RADIAL) {
                        float cx = imageBounds.left + state.radialCenterX * bw;
                        float cy = imageBounds.top + state.radialCenterY * bh;
                        float rxHandleX = cx + state.radialRx * bw;
                        if (Math.hypot(tx - rxHandleX, ty - cy) < dp(32)) {
                            dragHandle = 2;
                        } else {
                            dragHandle = 1;
                            state.radialCenterX = nx;
                            state.radialCenterY = ny;
                        }
                    } else {
                        float sx = imageBounds.left + state.linearStartX * bw;
                        float sy = imageBounds.top + state.linearStartY * bh;
                        float ex = imageBounds.left + state.linearEndX * bw;
                        float ey = imageBounds.top + state.linearEndY * bh;
                        if (Math.hypot(tx - ex, ty - ey) < Math.hypot(tx - sx, ty - sy)) {
                            dragHandle = 2;
                            state.linearEndX = nx;
                            state.linearEndY = ny;
                        } else {
                            dragHandle = 1;
                            state.linearStartX = nx;
                            state.linearStartY = ny;
                        }
                    }
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    invalidate();
                    if (onMaskGeometryChanged != null) onMaskGeometryChanged.run();
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (state.maskMode == LightCraftEngine.MaskMode.RADIAL) {
                        if (dragHandle == 2) {
                            state.radialRx = clamp(Math.abs(nx - state.radialCenterX), 0.06f, 0.65f);
                            state.radialRy = clamp(state.radialRx * 0.82f, 0.06f, 0.65f);
                        } else {
                            state.radialCenterX = nx;
                            state.radialCenterY = ny;
                        }
                    } else {
                        if (dragHandle == 2) {
                            state.linearEndX = nx;
                            state.linearEndY = ny;
                        } else {
                            state.linearStartX = nx;
                            state.linearStartY = ny;
                        }
                    }
                    invalidate();
                    if (onMaskGeometryChanged != null) onMaskGeometryChanged.run();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    dragHandle = 0;
                    invalidate();
                    return true;
                }
            }
            return false;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
