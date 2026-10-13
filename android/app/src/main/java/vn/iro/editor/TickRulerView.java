package vn.iro.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Thanh trượt thước chia vạch độ chính xác cao (Precision Tick Ruler Slider).
 * Chuyển đổi trực tiếp từ {@code struct TickRulerSlider} trong {@code ios/IRO/Views/EditorView.swift} (dòng 53–149).
 *
 * <p>Cơ chế hiển thị và tương tác (để theo dõi và debug):
 * <ul>
 *     <li>Kim chỉ thị màu Cam Hổ Phách ({@link StudioStyle#WARM_AMBER}) cố định ở chính giữa phía trên ({@code centerX}).</li>
 *     <li>Khi người dùng vuốt ngang sang trái/phải, dải vạch thước sẽ dịch chuyển dưới kim chỉ thị:
 *         {@code stepsDelta = -(touchX - dragStartX) / tickSpacing}.</li>
 *     <li>Mỗi 5 vạch phụ có 1 vạch chính cao hơn ({@code majorTickInterval = 5}); vạch giá trị {@code 0.0}
 *         được tô màu Cam Hổ Phách nổi bật để dễ căn về trung tâm.</li>
 *     <li>Mỗi khi giá trị nhảy qua một nấc ({@code step}), phát phản hồi xúc giác (Haptic Feedback) giống iOS.</li>
 * </ul>
 */
public final class TickRulerView extends View {

    /** Giao diện lắng nghe thay đổi giá trị và trạng thái bắt đầu/kết thúc kéo thước. */
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
    private Listener listener;

    private final float density;
    private final float tickSpacingPx;
    private static final int MAJOR_TICK_INTERVAL = 5;

    private int accentColor = StudioStyle.INK;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF boundsRect = new RectF();
    private final Path clipPath = new Path();
    private final Path trianglePath = new Path();

    public TickRulerView(Context context) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        this.tickSpacingPx = 8.5f * density;

        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(StudioStyle.SURFACE);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.0f * density);
        borderPaint.setColor(StudioStyle.LINE);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        needlePaint.setStyle(Paint.Style.FILL);
        needlePaint.setColor(accentColor);
    }

    /**
     * Đổi màu cho kim chỉ thị và các vạch đang kích hoạt.
     */
    public void setAccentColor(int color) {
        this.accentColor = color;
        this.needlePaint.setColor(color);
        invalidate();
    }

    /**
     * Cấu hình dải giá trị và bước nhảy cho thông số đang chọn (ví dụ EV: [-3, 3] step 0.1).
     */
    public void configure(double currentValue, double min, double max, double stepSize) {
        this.minValue = min;
        this.maxValue = max;
        this.step = stepSize > 0 ? stepSize : 1.0;
        this.value = Math.max(min, Math.min(max, currentValue));
        invalidate();
    }

    public void setValue(double currentValue) {
        this.value = Math.max(minValue, Math.min(maxValue, currentValue));
        invalidate();
    }

    public double getValue() {
        return value;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        float cornerRadius = 12f * density;
        boundsRect.set(0, 0, width, height);

        // 1. Vẽ nền phẳng tối giản
        canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, bgPaint);

        // 2. Cắt vùng vẽ bên trong khung bo góc và vẽ vạch thước
        canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(boundsRect, cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.clipPath(clipPath);

        double span = maxValue - minValue;
        if (span > 0 && step > 0) {
            float centerX = width / 2f;
            double totalSteps = span / step;
            double currentStepIndex = (value - minValue) / step;
            float centerOffset = (float) currentStepIndex * tickSpacingPx;

            int minStep = Math.max(0, (int) ((centerOffset - centerX) / tickSpacingPx) - 2);
            int maxStep = Math.min((int) Math.round(totalSteps), (int) ((centerOffset + centerX) / tickSpacingPx) + 2);

            double zeroVal = (minValue <= 0 && maxValue >= 0) ? 0.0 : minValue;
            double rangeLow = Math.min(zeroVal, value);
            double rangeHigh = Math.max(zeroVal, value);

            for (int i = minStep; i <= maxStep; i++) {
                float x = centerX + (i * tickSpacingPx - centerOffset);
                double tickVal = minValue + i * step;
                boolean isMajor = (i % MAJOR_TICK_INTERVAL == 0);
                boolean isZero = Math.abs(tickVal) < (step * 0.4);
                boolean inActiveSpan = (tickVal >= rangeLow - step * 0.25 && tickVal <= rangeHigh + step * 0.25)
                        && Math.abs(value - zeroVal) >= step * 0.5;

                float tickHeight = (isMajor ? 16f : 9.5f) * density;
                float startY = (isMajor ? 11.5f : 15f) * density;

                if (isZero) {
                    tickPaint.setColor(accentColor);
                    tickPaint.setStrokeWidth(2.1f * density);
                } else if (inActiveSpan) {
                    tickPaint.setColor(accentColor);
                    tickPaint.setStrokeWidth((isMajor ? 1.6f : 1.15f) * density);
                } else if (isMajor) {
                    tickPaint.setColor(0xa61b1c1e);
                    tickPaint.setStrokeWidth(1.3f * density);
                } else {
                    tickPaint.setColor(0x666e7480);
                    tickPaint.setStrokeWidth(0.9f * density);
                }

                canvas.drawLine(x, startY, x, startY + tickHeight, tickPaint);
            }
        }
        canvas.restore();

        // 3. Vẽ viền ngoài
        canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, borderPaint);

        // 4. Vẽ kim chỉ thị theo màu kênh RGB đang chọn ở chính giữa phía trên (Tam giác ngược ▼ + vạch đứng)
        float centerX = width / 2f;
        float triHalfW = 4.5f * density;
        float triTopY = 2.2f * density;
        float triBotY = 8.5f * density;

        trianglePath.reset();
        trianglePath.moveTo(centerX - triHalfW, triTopY);
        trianglePath.lineTo(centerX + triHalfW, triTopY);
        trianglePath.lineTo(centerX, triBotY);
        trianglePath.close();
        canvas.drawPath(trianglePath, needlePaint);

        float stemHalfW = 0.95f * density;
        canvas.drawRect(centerX - stemHalfW, triBotY, centerX + stemHalfW, triBotY + 7.0f * density, needlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                isDragging = true;
                dragStartX = event.getX();
                dragStartValue = value;
                if (listener != null) {
                    listener.onEditingChanged(true);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!isDragging) {
                    return false;
                }
                float dx = event.getX() - dragStartX;
                double stepsDelta = -dx / tickSpacingPx;
                double newVal = dragStartValue + stepsDelta * step;
                newVal = Math.max(minValue, Math.min(maxValue, newVal));
                newVal = Math.round(newVal / step) * step;

                if (Math.abs(newVal - value) >= step * 0.8) {
                    value = newVal;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    invalidate();
                    if (listener != null) {
                        listener.onValueChanged(value);
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;
                    if (listener != null) {
                        listener.onEditingChanged(false);
                    }
                }
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }
}
