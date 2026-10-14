package vn.iro.editor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.Slide;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Hệ thống chuyển động và tương tác vật lý chuẩn Material Design 3 Expressive.
 * Tích hợp đầy đủ các đường cong gia tốc M3, tương tác chạm Morphing Shape,
 * hiệu ứng chuyển cảnh Shared-Axis, Living Film 24fps và dấu ngày tháng Quartz Date.
 */
public final class MotionStudio {
    private MotionStudio() {}

    public static final Interpolator EMPHASIZED_DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f);
    public static final Interpolator EMPHASIZED_ACCELERATE = new PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f);
    public static final Interpolator STANDARD = new PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f);
    public static final Interpolator EXPRESSIVE_SPRING = new OvershootInterpolator(1.45f);
    public static final Interpolator EXPRESSIVE_GENTLE_SPRING = new OvershootInterpolator(1.15f);

    public static final long DURATION_SHORT = 200L;
    public static final long DURATION_MEDIUM = 350L;
    public static final long DURATION_LONG = 500L;

    /**
     * Gắn hiệu ứng phản hồi chạm đàn hồi chuẩn M3 Expressive.
     */
    public static void attachExpressiveTouch(View view) {
        attachExpressiveTouch(view, 0.95f);
    }

    /**
     * Gắn hiệu ứng phản hồi chạm đàn hồi kèm tùy chỉnh tỷ lệ thu nhỏ.
     */
    public static void attachExpressiveTouch(final View view, final float pressedScale) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(pressedScale)
                            .scaleY(pressedScale)
                            .setDuration(95)
                            .setInterpolator(STANDARD)
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(260)
                            .setInterpolator(EXPRESSIVE_SPRING)
                            .start();
                    break;
            }
            return false;
        });
    }

    /**
     * Phản hồi chạm M3 Expressive kết hợp biến đổi hình học (Shape Morphing: đổi bán kính bo góc khi nhấn).
     */
    public static void attachMorphingShapeTouch(
            View view,
            GradientDrawable shapeDrawable,
            float restingCornerPx,
            float pressedCornerPx
    ) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(110L).setInterpolator(EMPHASIZED_DECELERATE).start();
                    if (shapeDrawable != null) {
                        animateCornerRadius(shapeDrawable, restingCornerPx, pressedCornerPx, 120L);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(240L).setInterpolator(EXPRESSIVE_SPRING).start();
                    if (shapeDrawable != null) {
                        animateCornerRadius(shapeDrawable, pressedCornerPx, restingCornerPx, 240L);
                    }
                    break;
            }
            return false;
        });
    }

    public static void animateCornerRadius(GradientDrawable drawable, float fromPx, float toPx, long duration) {
        if (drawable == null) return;
        ValueAnimator va = ValueAnimator.ofFloat(fromPx, toPx);
        va.setDuration(duration);
        va.setInterpolator(EMPHASIZED_DECELERATE);
        va.addUpdateListener(anim -> drawable.setCornerRadius((Float) anim.getAnimatedValue()));
        va.start();
    }

    public static void animateContainerTransition(ViewGroup sceneRoot) {
        if (sceneRoot == null) return;
        TransitionSet transition = new TransitionSet();
        transition.addTransition(new ChangeBounds().setDuration(DURATION_MEDIUM).setInterpolator(EMPHASIZED_DECELERATE));
        transition.addTransition(new Fade(Fade.IN).setDuration(DURATION_SHORT).setInterpolator(EMPHASIZED_DECELERATE));
        transition.addTransition(new Fade(Fade.OUT).setDuration(150L).setInterpolator(EMPHASIZED_ACCELERATE));
        TransitionManager.beginDelayedTransition(sceneRoot, transition);
    }

    /**
     * Chuyển cảnh trục ngang Material Design 3 (Shared Axis X) khi người dùng đổi Tab công cụ.
     */
    public static void animateSharedAxisX(ViewGroup container, boolean forward) {
        if (container == null) return;
        TransitionSet set = new TransitionSet();
        Slide slide = new Slide(forward ? Gravity.END : Gravity.START);
        slide.setDuration(DURATION_MEDIUM);
        slide.setInterpolator(EMPHASIZED_DECELERATE);
        Fade fade = new Fade();
        fade.setDuration(DURATION_SHORT);
        set.addTransition(slide).addTransition(fade).addTransition(new ChangeBounds());
        TransitionManager.beginDelayedTransition(container, set);
    }

    /**
     * Chuyển đổi giữa 2 màn hình lớn bằng hiệu ứng Shared Axis Z / Container Transform.
     */
    public static void transitionScreens(final View outgoing, final View incoming, boolean forward) {
        if (incoming == null) return;
        if (outgoing != null && outgoing.getVisibility() == View.VISIBLE && outgoing != incoming) {
            final float exitScale = forward ? 1.04f : 0.94f;
            outgoing.animate().cancel();
            outgoing.animate()
                    .alpha(0f)
                    .scaleX(exitScale)
                    .scaleY(exitScale)
                    .setDuration(190)
                    .setInterpolator(EMPHASIZED_ACCELERATE)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            outgoing.setVisibility(View.GONE);
                            outgoing.setAlpha(1f);
                            outgoing.setScaleX(1f);
                            outgoing.setScaleY(1f);
                            outgoing.animate().setListener(null);
                        }
                    })
                    .start();
        } else if (outgoing != null && outgoing != incoming) {
            outgoing.setVisibility(View.GONE);
        }

        if (incoming.getVisibility() != View.VISIBLE || incoming.getAlpha() < 0.99f) {
            float startScale = forward ? 0.94f : 1.03f;
            incoming.animate().cancel();
            incoming.setVisibility(View.VISIBLE);
            incoming.setAlpha(0f);
            incoming.setScaleX(startScale);
            incoming.setScaleY(startScale);
            incoming.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(330)
                    .setInterpolator(EMPHASIZED_DECELERATE)
                    .setListener(null)
                    .start();
        }
    }

    /**
     * Hiệu ứng chuyển Tab công cụ theo chuẩn Material 3 Shared-Axis.
     */
    public static void animateSharedAxisTab(final View targetPanel, boolean verticalAxis, int direction) {
        if (targetPanel == null) return;
        float density = targetPanel.getResources().getDisplayMetrics().density;
        float offsetPx = (direction >= 0 ? 1f : -1f) * 18f * density;

        targetPanel.animate().cancel();
        targetPanel.setVisibility(View.VISIBLE);
        targetPanel.setAlpha(0f);
        if (verticalAxis) {
            targetPanel.setTranslationY(offsetPx);
            targetPanel.setTranslationX(0f);
        } else {
            targetPanel.setTranslationX(offsetPx);
            targetPanel.setTranslationY(0f);
        }

        targetPanel.animate()
                .alpha(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(280)
                .setInterpolator(EMPHASIZED_DECELERATE)
                .setListener(null)
                .start();
    }

    /**
     * Hiệu ứng xuất hiện nối tiếp dạng thác đổ (Staggered Cascade).
     */
    public static void animateCascade(ViewGroup group, int baseDelayMs, int stepDelayMs) {
        if (group == null) return;
        float density = group.getResources().getDisplayMetrics().density;
        float slidePx = 16f * density;
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;
            child.setAlpha(0f);
            child.setTranslationY(slidePx);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(baseDelayMs + (long) i * stepDelayMs)
                    .setDuration(360)
                    .setInterpolator(EMPHASIZED_DECELERATE)
                    .start();
        }
    }

    /**
     * Hiệu ứng xuất hiện tuần tự dạng thác đổ cho danh sách thanh trượt hoặc thẻ màu phim.
     */
    public static void animateStaggeredCascade(ViewGroup group, long stepDelayMs) {
        if (group == null) return;
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            child.setAlpha(0f);
            child.setTranslationY(24f);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * stepDelayMs)
                    .setDuration(280L)
                    .setInterpolator(EMPHASIZED_DECELERATE)
                    .start();
        }
    }

    /**
     * Hiệu ứng nảy nhẹ khi huy hiệu hoặc nút trạng thái vừa cập nhật giá trị.
     */
    public static void popBadge(View badge) {
        pulseBadge(badge);
    }

    /**
     * Nhịp nảy đàn hồi (Micro-pulse) cho nhãn hiển thị thông số khi chạm mốc 0 hoặc hoàn tất AI Auto-Balance.
     */
    public static void pulseBadge(View badge) {
        if (badge == null) return;
        badge.animate().cancel();
        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(
                badge,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.18f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.18f, 1f)
        );
        pulse.setDuration(260L);
        pulse.setInterpolator(EXPRESSIVE_GENTLE_SPRING);
        pulse.start();
    }

    /**
     * Hiệu ứng hiển thị/ẩn huy hiệu "ĐANG XEM ẢNH GỐC" kèm co giãn nhẹ khung ảnh chính khi nhấn giữ so sánh.
     */
    public static void animateCompareMode(View imageStage, final View badge, boolean comparing) {
        if (imageStage != null) {
            float targetScale = comparing ? 0.982f : 1.0f;
            imageStage.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .setDuration(comparing ? 150 : 240)
                    .setInterpolator(comparing ? STANDARD : EXPRESSIVE_SPRING)
                    .start();
        }
        if (badge != null) {
            float density = badge.getResources().getDisplayMetrics().density;
            badge.animate().cancel();
            if (comparing) {
                badge.setVisibility(View.VISIBLE);
                badge.setAlpha(0f);
                badge.setTranslationY(-10f * density);
                badge.setScaleX(0.9f);
                badge.setScaleY(0.9f);
                badge.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .setInterpolator(EXPRESSIVE_SPRING)
                        .setListener(null)
                        .start();
            } else {
                badge.animate()
                        .alpha(0f)
                        .translationY(-8f * density)
                        .setDuration(150)
                        .setInterpolator(EMPHASIZED_ACCELERATE)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                badge.setVisibility(View.GONE);
                                badge.animate().setListener(null);
                            }
                        })
                        .start();
            }
        }
    }

    public static List<Bitmap> generateLivingFilmFrames(
            Bitmap source,
            Settings baseSettings,
            ColorEngine engine,
            int totalFrames
    ) {
        return generateLivingFilmFrames(source, baseSettings, totalFrames);
    }

    public static List<Bitmap> generateLivingFilmFrames(
            Bitmap source,
            Settings baseSettings,
            int totalFrames
    ) {
        List<Bitmap> frames = new ArrayList<>(totalFrames);
        if (source == null || source.getWidth() < 1 || source.getHeight() < 1) return frames;
        int w = source.getWidth();
        int h = source.getHeight();
        int[] inPixels = new int[w * h];
        source.getPixels(inPixels, 0, w, 0, 0, w, h);
        Settings s = baseSettings != null ? baseSettings.copy() : new Settings();
        if (s.grain <= 0) s.grain = 8;
        for (int f = 0; f < totalFrames; f++) {
            int[] outPixels = ColorEngine.process(inPixels, w, h, s, null);
            Bitmap rendered = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            rendered.setPixels(outPixels, 0, w, 0, 0, w, h);
            drawQuartzDateStamp(rendered);
            frames.add(rendered);
        }
        return frames;
    }

    public static void drawQuartzDateStamp(Bitmap target) {
        if (target == null || !target.isMutable()) return;
        Canvas canvas = new Canvas(target);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float fontSize = Math.max(18f, Math.min(target.getWidth(), target.getHeight()) * 0.032f);
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(fontSize);
        paint.setFakeBoldText(true);
        paint.setColor(Color.rgb(255, 123, 26));
        paint.setShadowLayer(fontSize * 0.45f, 0f, 0f, Color.argb(215, 255, 68, 0));
        String stamp = new SimpleDateFormat("''yy MM dd", Locale.US).format(new Date());
        canvas.drawText(stamp, target.getWidth() - fontSize * 6.4f, target.getHeight() - fontSize * 1.1f, paint);
    }
}
