package vn.iro.editor;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Thanh trượt thước chia vạch độ chính xác cao chuẩn Material Design 3 Expressive (Precision Tick Ruler).
 * Nâng cấp:
 * - Hiệu ứng phóng đại vạch dạng sóng Gaussian (Gaussian Wave Magnification) quanh kim chỉ thị trung tâm.
 * - Nam châm hút tự động về mốc 0.0 (Magnetic Zero-Detent Snap) kèm phản hồi xúc giác 2 tầng.
 * - Dải màu Tonal Delta hiển thị trực quan độ lệch so với mốc cân bằng 0.0.
 * - Kim chỉ thị và khung viền tự biến hình đàn hồi (Dynamic Spring Morphing) khi người dùng chạm kéo.
 */
public final class TickRulerView extends View {

    public interface Listener {
        void onValueChanged(double newValue);
        void onEditingChanged(boolean isDragging);
    }

    private double value = 0.0;
    private double minValue = -3.0;
    private double maxValue = 3.0;
    private double step = 0.1;

    private float dragStartX = 0f;
    private double dragStartValue = 0.0;
    private boolean isDragging = false;
    private float dragProgress = 0f; // 0f (nghỉ) -> 1f (đang kéo)
    private Listener listener;

    private final float density;
    private final float tickSpacingPx;
    private static final int MAJOR_TICK_INTERVAL = 5;

    private int accentColor = StudioStyle.WARM_AMBER;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint deltaBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF boundsRect = new RectF();

    public TickRulerView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        tickSpacingPx = 10f * density;

        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(StudioStyle.M3_SURFACE_CONTAINER_LOW);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1f * density);
        borderPaint.setColor(StudioStyle.M3_OUTLINE_VARIANT);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        pointerPaint.setStyle(Paint.Style.FILL);
        pointerPaint.setColor(accentColor);

        deltaBarPaint.setStyle(Paint.Style.FILL);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        pointerPaint.setColor(color);
        invalidate();
    }

    public void setRange(double min, double max, double step) {
        this.minValue = min;
        this.maxValue = max;
        this.step = step > 0 ? step : 0.1;
        this.value = Math.max(minValue, Math.min(maxValue, value));
        invalidate();
    }

    public void setValue(double val) {
        this.value = Math.max(minValue, Math.min(maxValue, val));
        invalidate();
    }

    public double getValue() {
        return value;
    }

    /**
     * Tương thích ngược với các lệnh gọi configure() hiện tại trong MainActivity.
     */
    public void configure(double currentValue, double min, double max, double stepSize) {
        setRange(min, max, stepSize);
        setValue(currentValue);
    }

    private void animateDragState(boolean dragging) {
        ValueAnimator va = ValueAnimator.ofFloat(dragProgress, dragging ? 1f : 0f);
        va.setDuration(dragging ? 150L : 260L);
        va.setInterpolator(MotionStudio.EMPHASIZED_DECELERATE);
        va.addUpdateListener(a -> {
            dragProgress = (Float) a.getAnimatedValue();
            invalidate();
        });
        va.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float corner = (14f + 8f * dragProgress) * density;
        boundsRect.set(density, density, w - density, h - density);
        canvas.drawRoundRect(boundsRect, corner, corner, bgPaint);
        canvas.drawRoundRect(boundsRect, corner, corner, borderPaint);

        float centerX = w * 0.5f;
        float centerY = h * 0.56f;
        int totalSteps = (int) Math.round((maxValue - minValue) / step);
        double currentStepFloat = (value - minValue) / step;
        double zeroStepFloat = (0.0 - minValue) / step;

        // Vẽ thanh Tonal Delta nối từ mốc 0.0 đến vị trí hiện tại
        if (minValue < 0.0 && maxValue > 0.0) {
            float zeroX = centerX + (float) ((zeroStepFloat - currentStepFloat) * tickSpacingPx);
            float left = Math.max(12f * density, Math.min(zeroX, centerX));
            float right = Math.min(w - 12f * density, Math.max(zeroX, centerX));
            if (right - left > 1f) {
                deltaBarPaint.setColor(accentColor);
                deltaBarPaint.setAlpha(42);
                canvas.drawRoundRect(left, centerY - 10f * density, right, centerY + 10f * density, 4f * density, 4f * density, deltaBarPaint);
            }
        }

        // Vẽ các vạch chia kèm hiệu ứng phóng đại Gaussian quanh kim trung tâm
        float sigma = 3.2f * tickSpacingPx;
        for (int i = 0; i <= totalSteps; i++) {
            float x = centerX + (float) ((i - currentStepFloat) * tickSpacingPx);
            if (x < 8f * density || x > w - 8f * density) continue;

            float dist = Math.abs(x - centerX);
            float edgeFade = Math.max(0f, 1f - (dist / (w * 0.48f)));
            float gaussianBoost = (float) Math.exp(-(dist * dist) / (2f * sigma * sigma)) * (0.35f + 0.65f * dragProgress);

            double tickVal = minValue + i * step;
            boolean isZero = Math.abs(tickVal) < step * 0.25;
            boolean isMajor = (i % MAJOR_TICK_INTERVAL == 0) || isZero;

            float baseHalfH = (isMajor ? 8.5f : 4.8f) * density;
            float halfH = baseHalfH * (1f + 0.42f * gaussianBoost);

            tickPaint.setStrokeWidth((isZero ? 2.4f : isMajor ? 1.8f : 1.2f) * density);
            tickPaint.setColor(isZero ? accentColor : isMajor ? StudioStyle.INK : StudioStyle.MUTED);
            tickPaint.setAlpha(Math.round(255 * Math.min(1f, edgeFade * (isMajor ? 0.95f : 0.65f) + gaussianBoost * 0.3f)));

            canvas.drawLine(x, centerY - halfH, x, centerY + halfH, tickPaint);

            // Chấm tròn định vị mốc 0.0
            if (isZero) {
                pointerPaint.setColor(accentColor);
                canvas.drawCircle(x, centerY - halfH - 4f * density, 2.5f * density, pointerPaint);
            }
        }

        // Vẽ Kim chỉ thị trung tâm chuẩn M3 Expressive (tự vươn dài khi đang kéo)
        pointerPaint.setColor(accentColor);
        float pointerHalfW = (1.6f + 0.5f * dragProgress) * density;
        float pointerTop = (5f - 2f * dragProgress) * density;
        float pointerBottom = h - (5f - 2f * dragProgress) * density;
        canvas.drawRoundRect(
                centerX - pointerHalfW,
                pointerTop,
                centerX + pointerHalfW,
                pointerBottom,
                pointerHalfW,
                pointerHalfW,
                pointerPaint
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartX = event.getX();
                dragStartValue = value;
                isDragging = true;
                animateDragState(true);
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (listener != null) listener.onEditingChanged(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!isDragging) return false;
                float dx = event.getX() - dragStartX;
                double rawVal = dragStartValue - (dx / tickSpacingPx) * step;
                // Nam châm hút nhẹ về mốc 0.0 (Magnetic Zero Snap)
                if (minValue < 0.0 && maxValue > 0.0 && Math.abs(rawVal) < step * 0.35) {
                    rawVal = 0.0;
                }
                double snapped = Math.round(rawVal / step) * step;
                snapped = Math.max(minValue, Math.min(maxValue, snapped));

                int prevTick = (int) Math.round(value / step);
                int newTick = (int) Math.round(snapped / step);
                if (newTick != prevTick) {
                    value = snapped;
                    if (newTick == 0 || newTick % MAJOR_TICK_INTERVAL == 0) {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    } else {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                    if (listener != null) listener.onValueChanged(value);
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;
                    animateDragState(false);
                    if (listener != null) listener.onEditingChanged(false);
                }
                return true;
        }
        return super.onTouchEvent(event);
    }
}
