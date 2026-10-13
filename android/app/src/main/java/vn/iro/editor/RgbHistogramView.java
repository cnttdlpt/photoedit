package vn.iro.editor;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import java.util.Locale;

/**
 * Biểu đồ tần suất màu RGB thời gian thực (Live Interactive RGB Waveform & Histogram)
 * mang ngôn ngữ thiết kế Google Photos / Material 3 RGB Spectrum.
 *
 * <p>Tính năng cốt lõi (để theo dõi và debug):
 * <ul>
 *     <li><b>Phân tích 3 kênh Đỏ (R), Lục (G), Lam (B) & Độ chói (Luma):</b> Lấy mẫu siêu tốc trên ảnh đang hiển thị,
 *         chia thành {@link #BINS} = 64 cột tần suất và làm mượt bằng bộ lọc trung bình trượt 3 điểm.</li>
 *     <li><b>Chuyển động nội suy mượt (Smooth Animated Morphing):</b> Khi người dùng kéo thước chỉnh màu,
 *         các đường cong {@code Red (#EA4335)}, {@code Green (#34A853)}, {@code Blue (#4285F4)} uốn lượn mượt mà
 *         nhờ {@link ValueAnimator} với đường cong {@link MotionStudio#STANDARD}.</li>
 *     <li><b>Tương tác chọn kênh trực tiếp (Interactive RGB Channel Selector):</b> Chạm vào biểu đồ hoặc các nút
 *         {@code RGB}, {@code R}, {@code G}, {@code B} ở góc trên phải để cô lập từng kênh màu hoặc chuyển nhanh
 *         sang thông số chỉnh kênh màu tương ứng.</li>
 * </ul>
 */
public final class RgbHistogramView extends View {

    /** Số cột tần suất (Bins) trên trục ngang (0..255 được gom vào 64 cột để vẽ đường cong Bezier mượt). */
    private static final int BINS = 64;

    /** Chế độ hiển thị: 0 = Tất cả RGB, 1 = Chỉ kênh Đỏ (R), 2 = Chỉ kênh Lục (G), 3 = Chỉ kênh Lam (B). */
    public static final int MODE_ALL_RGB = 0;
    public static final int MODE_RED = 1;
    public static final int MODE_GREEN = 2;
    public static final int MODE_BLUE = 3;

    public interface ChannelClickListener {
        void onChannelSelected(int mode);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curvePath = new Path();
    private final Path fillPath = new Path();
    private final RectF chipRect = new RectF();

    // Dữ liệu tần suất hiện tại (đang vẽ trên màn hình, được nội suy mượt)
    private final float[] curR = new float[BINS];
    private final float[] curG = new float[BINS];
    private final float[] curB = new float[BINS];

    // Dữ liệu tần suất bắt đầu & đích cho ValueAnimator
    private final float[] startR = new float[BINS];
    private final float[] startG = new float[BINS];
    private final float[] startB = new float[BINS];
    private final float[] targetR = new float[BINS];
    private final float[] targetG = new float[BINS];
    private final float[] targetB = new float[BINS];

    private int avgR = 128;
    private int avgG = 128;
    private int avgB = 128;
    private int displayMode = MODE_ALL_RGB;
    private ValueAnimator morphAnimator;
    private ChannelClickListener listener;

    public RgbHistogramView(Context context) {
        super(context);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        setClickable(true);
    }

    public void setChannelClickListener(ChannelClickListener listener) {
        this.listener = listener;
    }

    public int getDisplayMode() {
        return displayMode;
    }

    /**
     * Phân tích ảnh {@link Bitmap} đầu vào và cập nhật biểu đồ sóng RGB với hiệu ứng chuyển động mượt.
     */
    public void updateFromBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() < 1 || bitmap.getHeight() < 1) {
            return;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int[] rawR = new int[BINS];
        int[] rawG = new int[BINS];
        int[] rawB = new int[BINS];

        // Lấy mẫu lưới khoảng ~4.000 điểm ảnh để tốc độ tính toán dưới 1 ms ngay cả khi kéo thanh trượt liên tục
        int stepX = Math.max(1, w / 64);
        int stepY = Math.max(1, h / 64);
        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        int count = 0;

        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int c = bitmap.getPixel(x, y);
                int a = (c >>> 24) & 255;
                if (a < 16) {
                    continue;
                }
                int r = (c >>> 16) & 255;
                int g = (c >>> 8) & 255;
                int b = c & 255;

                rawR[r >>> 2]++;
                rawG[g >>> 2]++;
                rawB[b >>> 2]++;

                sumR += r;
                sumG += g;
                sumB += b;
                count++;
            }
        }

        if (count == 0) {
            return;
        }

        avgR = (int) (sumR / count);
        avgG = (int) (sumG / count);
        avgB = (int) (sumB / count);

        // Làm mượt 3 điểm và tìm đỉnh cực đại (bỏ qua 2 đầu biên 0 và 63 bị cháy/bệt quá mức để đồ thị luôn cân đối)
        float maxVal = 1f;
        for (int i = 0; i < BINS; i++) {
            int prev = Math.max(0, i - 1);
            int next = Math.min(BINS - 1, i + 1);
            float sr = (rawR[prev] + rawR[i] * 2f + rawR[next]) * 0.25f;
            float sg = (rawG[prev] + rawG[i] * 2f + rawG[next]) * 0.25f;
            float sb = (rawB[prev] + rawB[i] * 2f + rawB[next]) * 0.25f;

            targetR[i] = sr;
            targetG[i] = sg;
            targetB[i] = sb;

            if (i > 1 && i < BINS - 2) {
                maxVal = Math.max(maxVal, Math.max(sr, Math.max(sg, sb)));
            }
        }

        // Chuẩn hóa về đoạn [0.0 .. 1.0]
        for (int i = 0; i < BINS; i++) {
            targetR[i] = Math.min(1f, targetR[i] / maxVal);
            targetG[i] = Math.min(1f, targetG[i] / maxVal);
            targetB[i] = Math.min(1f, targetB[i] / maxVal);
            startR[i] = curR[i];
            startG[i] = curG[i];
            startB[i] = curB[i];
        }

        if (morphAnimator != null) {
            morphAnimator.cancel();
        }
        morphAnimator = ValueAnimator.ofFloat(0f, 1f);
        morphAnimator.setDuration(180);
        morphAnimator.setInterpolator(MotionStudio.STANDARD);
        morphAnimator.addUpdateListener(anim -> {
            float t = (float) anim.getAnimatedValue();
            for (int i = 0; i < BINS; i++) {
                curR[i] = startR[i] + (targetR[i] - startR[i]) * t;
                curG[i] = startG[i] + (targetG[i] - startG[i]) * t;
                curB[i] = startB[i] + (targetB[i] - startB[i]) * t;
            }
            invalidate();
        });
        morphAnimator.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            displayMode = (displayMode + 1) % 4;
            if (listener != null) {
                listener.onChannelSelected(displayMode);
            }
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;

        // 1. Nền hộp biểu đồ phẳng tối giản (#EFF1F5) + viền mảnh
        chipRect.set(0, 0, w, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(StudioStyle.SURFACE);
        canvas.drawRoundRect(chipRect, 12f * density, 12f * density, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(StudioStyle.LINE);
        canvas.drawRoundRect(chipRect, 12f * density, 12f * density, paint);

        // Lưới chia 4 phần dọc mờ
        paint.setColor(0x14181a1f);
        float graphTop = 22f * density;
        float graphBottom = h - 6f * density;
        float graphLeft = 10f * density;
        float graphRight = w - 10f * density;
        float graphW = graphRight - graphLeft;
        float graphH = Math.max(10f, graphBottom - graphTop);

        for (int i = 1; i <= 3; i++) {
            float gx = graphLeft + (graphW * i / 4f);
            canvas.drawLine(gx, graphTop, gx, graphBottom, paint);
        }

        // 2. Vẽ đường cong tần suất dịu mắt
        if (displayMode == MODE_ALL_RGB || displayMode == MODE_BLUE) {
            drawChannelCurve(canvas, curB, graphLeft, graphTop, graphW, graphH,
                    0xaa2563eb, 0x222563eb, density);
        }
        if (displayMode == MODE_ALL_RGB || displayMode == MODE_GREEN) {
            drawChannelCurve(canvas, curG, graphLeft, graphTop, graphW, graphH,
                    0xaa2e9b52, 0x222e9b52, density);
        }
        if (displayMode == MODE_ALL_RGB || displayMode == MODE_RED) {
            drawChannelCurve(canvas, curR, graphLeft, graphTop, graphW, graphH,
                    0xaad9483b, 0x22d9483b, density);
        }

        // 3. Nhãn tối giản góc trên trái & góc trên phải
        textPaint.setTextSize(9.5f * density);
        textPaint.setColor(StudioStyle.MUTED);
        String avgLabel = String.format(Locale.ROOT, "Histogram · R %d  G %d  B %d", avgR, avgG, avgB);
        canvas.drawText(avgLabel, 10f * density, 15f * density, textPaint);

        String modeLabel = (displayMode == MODE_ALL_RGB) ? "RGB"
                : (displayMode == MODE_RED ? "Kênh R"
                : (displayMode == MODE_GREEN ? "Kênh G" : "Kênh B"));
        textPaint.setColor(StudioStyle.INK);
        float badgeW = textPaint.measureText(modeLabel);
        canvas.drawText(modeLabel, w - badgeW - 10f * density, 15f * density, textPaint);
    }

    private void drawChannelCurve(Canvas canvas,
                                  float[] values,
                                  float left,
                                  float top,
                                  float width,
                                  float height,
                                  int strokeColor,
                                  int topFillColor,
                                  float density) {
        curvePath.reset();
        fillPath.reset();

        float bottom = top + height;
        fillPath.moveTo(left, bottom);

        for (int i = 0; i < BINS; i++) {
            float x = left + (width * i / (float) (BINS - 1));
            float y = bottom - (values[i] * height * 0.92f);
            if (i == 0) {
                curvePath.moveTo(x, y);
                fillPath.lineTo(x, y);
            } else {
                float prevX = left + (width * (i - 1) / (float) (BINS - 1));
                float prevY = bottom - (values[i - 1] * height * 0.92f);
                float midX = (prevX + x) * 0.5f;
                curvePath.cubicTo(midX, prevY, midX, y, x, y);
                fillPath.cubicTo(midX, prevY, midX, y, x, y);
            }
        }

        fillPath.lineTo(left + width, bottom);
        fillPath.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(
                0, top, 0, bottom,
                topFillColor, 0x05000000,
                Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f * density);
        paint.setColor(strokeColor);
        canvas.drawPath(curvePath, paint);
    }
}
