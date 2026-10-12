package vn.iro.editor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

/**
 * Hệ thống chuyển động và tương tác vật lý chuẩn Google Material 3 Expressive (Android 15/16).
 *
 * <p>Cung cấp các đường cong nội suy (Motion Tokens) và hiệu ứng chuyển cảnh mượt mà:
 * <ul>
 *     <li><b>Emphasized Decelerate ({@link #EMPHASIZED_DECELERATE}):</b> Đường cong giảm tốc đặc trưng
 *         của Google Material 3 ({@code cubic-bezier(0.05, 0.7, 0.1, 1.0)}) cho các chuyển động mở rộng,
 *         chuyển tab Shared-Axis và xuất hiện thẻ.</li>
 *     <li><b>Expressive Spring ({@link #EXPRESSIVE_SPRING}):</b> Hiệu ứng đàn hồi vật lý (Spring Overshoot)
 *         khi người dùng chạm nhả nút, chọn công cụ trên Navigation Rail hoặc bật huy hiệu.</li>
 *     <li><b>Container & Shared-Axis Transitions:</b> Chuyển cảnh mượt giữa màn hình chào và phòng tối chỉnh ảnh,
 *         cũng như khi chuyển đổi giữa 3 phân hệ {@code Tinh chỉnh}, {@code Màu phim}, {@code Khung ảnh}.</li>
 * </ul>
 */
public final class MotionStudio {

    /** Đường cong giảm tốc nhấn mạnh chuẩn Google Material 3 (dùng cho phần tử đi vào màn hình). */
    public static final Interpolator EMPHASIZED_DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f);

    /** Đường cong tăng tốc chuẩn Google Material 3 (dùng cho phần tử rời khỏi màn hình). */
    public static final Interpolator EMPHASIZED_ACCELERATE = new PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f);

    /** Đường cong tiêu chuẩn Material 3 cho các biến đổi kích thước/màu sắc trên màn hình. */
    public static final Interpolator STANDARD = new PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f);

    /** Đường cong đàn hồi (Spring Physics) đặc trưng của Material 3 Expressive. */
    public static final Interpolator EXPRESSIVE_SPRING = new OvershootInterpolator(1.45f);

    private MotionStudio() {
    }

    /**
     * Gắn hiệu ứng phản hồi chạm đàn hồi (Tactile Spring Press) chuẩn Material 3 Expressive cho một {@link View}.
     * Khi chạm xuống sẽ thu nhỏ nhẹ ({@code pressedScale}) và khi nhả tay sẽ bật nảy mềm mại về {@code 1.0f}.
     *
     * @param view         Thành phần giao diện cần gắn hiệu ứng.
     * @param pressedScale Tỷ lệ thu nhỏ khi nhấn giữ (ví dụ {@code 0.94f} cho nút nhỏ, {@code 0.97f} cho thẻ lớn).
     */
    public static void attachExpressiveTouch(final View view, final float pressedScale) {
        if (view == null) {
            return;
        }
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
            // Trả về false để vẫn cho phép RippleDrawable, onClick và onLongClick hoạt động bình thường
            return false;
        });
    }

    /**
     * Chuyển đổi giữa 2 màn hình lớn (ví dụ: Màn hình chào {@code EmptyPhoto} <-> Phòng tối {@code EditorWorkspace})
     * bằng hiệu ứng phóng nhẹ kèm mờ dần (Material 3 Shared Axis Z / Container Transform).
     *
     * @param outgoing Màn hình sắp ẩn đi.
     * @param incoming Màn hình sắp hiện lên.
     * @param forward  {@code true} nếu đi sâu vào trình chỉnh sửa, {@code false} nếu quay lại màn hình chào.
     */
    public static void transitionScreens(final View outgoing, final View incoming, boolean forward) {
        if (incoming == null) {
            return;
        }
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
     * Hiệu ứng chuyển Tab công cụ theo chuẩn Material 3 Shared-Axis (trục Y trên Tablet/Landscape Navigation Rail,
     * hoặc trục X trên điện thoại dọc).
     *
     * @param targetPanel  Khung nội dung của tab vừa được chọn.
     * @param verticalAxis {@code true} nếu trượt theo chiều dọc (Tablet/Landscape), {@code false} nếu trượt ngang.
     * @param direction    Hướng chuyển ({@code >= 0} là tiến tới, {@code < 0} là lùi lại).
     */
    public static void animateSharedAxisTab(final View targetPanel, boolean verticalAxis, int direction) {
        if (targetPanel == null) {
            return;
        }
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
     * Hiệu ứng xuất hiện nối tiếp (Staggered Cascade) cho các phần tử con trong một {@link ViewGroup}.
     * Giúp giao diện khổ ngang / máy tính bảng có chiều sâu chuyển động mượt mà như Google Photos.
     */
    public static void animateCascade(ViewGroup group, int baseDelayMs, int stepDelayMs) {
        if (group == null) {
            return;
        }
        float density = group.getResources().getDisplayMetrics().density;
        float slidePx = 16f * density;
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) {
                continue;
            }
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
     * Hiệu ứng nảy nhẹ (Pop Micro-Animation) khi một huy hiệu hoặc nút trạng thái vừa cập nhật giá trị.
     */
    public static void popBadge(View badge) {
        if (badge == null) {
            return;
        }
        badge.animate().cancel();
        badge.setScaleX(0.86f);
        badge.setScaleY(0.86f);
        badge.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240)
                .setInterpolator(EXPRESSIVE_SPRING)
                .start();
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
}
