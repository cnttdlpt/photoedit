package vn.iro.editor;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;

/**
 * Hệ thống ngôn ngữ thiết kế giao diện Liquid Glass & Bảng màu Ấm Tối Giản (Warm Minimal Palette).
 * Chuyển đổi trực tiếp từ file {@code ios/IRO/Views/Studio.swift}.
 *
 * <p>Các thành phần chính được đồng bộ từ iOS:
 * <ul>
 *     <li><b>Bảng màu Liquid Glass:</b> Nền sứ trắng kem ({@link #PORCELAIN} {@code #F9F9FB}),
 *         mặt kính ({@link #SURFACE} {@code #F6F7F9}), chữ mực đậm ({@link #INK} {@code #16181C}),
 *         chữ phụ ({@link #MUTED} {@code #8A8E98}) và điểm nhấn Cam Hổ Phách ({@link #WARM_AMBER} {@code #E07A3B}).</li>
 *     <li><b>Nền {@link AntigravityBackground}:</b> Tái tạo hai vùng sáng chuyển sắc tròn (RadialGradient)
 *         ở góc trên phải (Warm Amber Dawn Glow) và góc dưới trái (Cool Mint Sky Glow) giống hệt {@code AntigravityBackground} trên iOS.</li>
 *     <li><b>Hiệu ứng thẻ kính mờ (Liquid Glass Card / Pill / Circle):</b> Có viền phản chiếu ánh sáng chéo
 *         từ trắng sáng ở góc trên trái xuống mờ nhẹ ở góc dưới phải.</li>
 *     <li><b>Bộ icon vẽ bằng Vector ({@link Icon}):</b> Vẽ trực tiếp bằng {@link Canvas} cho toàn bộ nút điều khiển
 *         (Back, Undo, Redo, Export, Menu, Wand Auto-Balance, 11 thông số chỉnh màu, Search, Heart, Folder...).</li>
 * </ul>
 */
public final class StudioStyle {

    // =========================================================================
    // HỆ THỐNG MÀU MATERIAL DESIGN 3 EXPRESSIVE (DYNAMIC THEME ENGINE)
    // =========================================================================

    private static boolean darkTheme = true;

    public static boolean isDarkTheme() {
        return darkTheme;
    }

    public static void setDarkTheme(boolean dark) {
        applyTheme(dark);
    }

    public static void toggleDarkTheme() {
        applyTheme(!darkTheme);
    }

    /** Nền chính ứng dụng (thích ứng theo Sáng/Tối). */
    public static int BG = 0xff0d0f14;
    public static int PORCELAIN = 0xff0d0f14;

    /** Bề mặt Surface Container chính (Thẻ Hero, Bảng điều khiển). */
    public static int SURFACE = 0xff161922;

    /** Bề mặt Surface Container cấp 2 (Thẻ Bento con, hộp tìm kiếm, chip). */
    public static int SURFACE_CONTAINER = 0xff1e222e;
    public static int SURFACE_CONTAINER_HIGH = 0xff272c3b;

    /** Đường viền mảnh siêu tinh tế chuẩn M3 Outline. */
    public static int LINE = 0x26ffffff;

    /** Màu chữ & biểu tượng chính (Sáng rõ trên nền tối / Đậm mực trên nền sáng). */
    public static int INK = 0xfff0f2f7;

    /** Màu chữ & biểu tượng phụ (Muted Slate / Charcoal Muted). */
    public static int MUTED = 0xff9ba2af;

    /** Điểm nhấn Cam Hổ Phách ấm chuyên nghiệp cho nhiếp ảnh (#F59E0B Warm Amber). */
    public static int WARM_AMBER = 0xfff59e0b;
    public static int ACCENT = 0xfff59e0b;

    /** Nền trạng thái đang chọn / Tonal Container nhạt. */
    public static int WARM_AMBER_LIGHT = 0xff2e220a;

    /** Màu nền viên thuốc tối. */
    public static int DARK_PILL = 0xff252a36;

    // Màu kênh chức năng (RGB curves, histogram, color balance)
    public static final int GOOGLE_RED = 0xffd9483b;
    public static final int RED_TONAL = 0xfffbeae8;
    public static final int GOOGLE_GREEN = 0xff2e9b52;
    public static final int GREEN_TONAL = 0xffe8f5ec;
    public static final int GOOGLE_BLUE = 0xff2563eb;
    public static final int BLUE_TONAL = 0xffedf2fd;
    public static final int GOOGLE_YELLOW = 0xffd9822b;
    public static final int YELLOW_TONAL = 0xfffbf2e6;

    // --- Hệ thống Token M3 Tonal Surface ---
    public static final int M3_PRIMARY = 0xfff59e0b;
    public static final int M3_ON_PRIMARY = 0xffffffff;
    public static final int M3_PRIMARY_CONTAINER = 0xffffdbca;
    public static final int M3_ON_PRIMARY_CONTAINER = 0xff341100;
    public static final int M3_OUTLINE = 0xff8a8e98;
    public static final int M3_OUTLINE_VARIANT = 0xffdcdfe6;
    public static final int M3_SURFACE_CONTAINER_LOW = 0xff15181e;

    public static final int M3_DARK_BG = 0xff0d0f14;
    public static final int M3_DARK_SURFACE = 0xff161922;
    public static final int M3_DARK_SURFACE_CONTAINER_LOW = 0xff191d27;
    public static final int M3_DARK_SURFACE_CONTAINER = 0xff1e222e;
    public static final int M3_DARK_SURFACE_CONTAINER_HIGH = 0xff272c3b;
    public static final int M3_DARK_PRIMARY = 0xfff59e0b;

    /**
     * Cập nhật toàn bộ bảng màu giao diện tức thì theo chế độ Tối (Darkroom Studio)
     * hoặc Sáng (Warm Porcelain Minimal).
     */
    public static void applyTheme(boolean dark) {
        darkTheme = dark;
        if (dark) {
            BG = 0xff0d0f14;
            PORCELAIN = 0xff0d0f14;
            SURFACE = 0xff161922;
            SURFACE_CONTAINER = 0xff1e222e;
            SURFACE_CONTAINER_HIGH = 0xff272c3b;
            LINE = 0x26ffffff;
            INK = 0xfff0f2f7;
            MUTED = 0xff9ba2af;
            WARM_AMBER = 0xfff59e0b;
            ACCENT = 0xfff59e0b;
            WARM_AMBER_LIGHT = 0xff2e220a;
            DARK_PILL = 0xff252a36;
        } else {
            BG = 0xfff6f7f9;
            PORCELAIN = 0xfff6f7f9;
            SURFACE = 0xffffffff;
            SURFACE_CONTAINER = 0xffeff1f5;
            SURFACE_CONTAINER_HIGH = 0xffe6e9ef;
            LINE = 0xffe1e4ea;
            INK = 0xff181a1f;
            MUTED = 0xff6e737d;
            WARM_AMBER = 0xffe07a3b;
            ACCENT = 0xffe07a3b;
            WARM_AMBER_LIGHT = 0xffffdbca;
            DARK_PILL = 0xff181a1f;
        }
    }

    public static int getBackgroundColor() {
        return BG;
    }

    public static int getSurfaceColor() {
        return SURFACE;
    }

    public static int getSurfaceContainerColor() {
        return SURFACE_CONTAINER;
    }

    public static int getSurfaceContainerLowColor() {
        return SURFACE_CONTAINER;
    }

    public static int getSurfaceContainerHighColor() {
        return SURFACE_CONTAINER_HIGH;
    }

    public static int getPrimaryColor() {
        return WARM_AMBER;
    }

    public static int getTextColor() {
        return INK;
    }

    public static int getMutedTextColor() {
        return MUTED;
    }

    // --- Bán kính bo góc chuẩn Material Design 3 (dp) ---
    public static final float CORNER_XS_DP = 4f;
    public static final float CORNER_SM_DP = 8f;
    public static final float CORNER_MD_DP = 12f;
    public static final float CORNER_LG_DP = 16f;
    public static final float CORNER_XL_DP = 28f;
    public static final float CORNER_FULL_DP = 999f;

    public static int dpToPx(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Tạo nền thẻ Material Design 3 kèm viền mảnh và bo góc chuẩn M3.
     */
    public static GradientDrawable createM3SurfaceDrawable(
            Context context,
            int fillColor,
            int strokeColor,
            float cornerDp,
            float strokeWidthDp
    ) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fillColor);
        gd.setCornerRadius(dpToPx(context, cornerDp));
        if (strokeWidthDp > 0f) {
            gd.setStroke(Math.max(1, dpToPx(context, strokeWidthDp)), strokeColor);
        }
        return gd;
    }

    /**
     * Tạo nút bấm hoặc Chip có hiệu ứng sóng nước (M3 Ripple State Layer).
     */
    public static Drawable createM3RippleSurface(
            Context context,
            int fillColor,
            int rippleColor,
            int strokeColor,
            float cornerDp
    ) {
        GradientDrawable content = createM3SurfaceDrawable(context, fillColor, strokeColor, cornerDp, 1f);
        GradientDrawable mask = createM3SurfaceDrawable(context, Color.WHITE, Color.TRANSPARENT, cornerDp, 0f);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    private StudioStyle() {
    }

    /**
     * Trả về màu nền Tonal trung tính nhẹ nhàng.
     */
    static int tonalForChannel(int channelColor) {
        return WARM_AMBER_LIGHT;
    }

    /**
     * Tạo hình chữ nhật bo góc cơ bản với màu nền và đường viền mảnh 1px.
     */
    static GradientDrawable shape(int fill, int stroke, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (stroke != 0) {
            d.setStroke(2, stroke);
        }
        return d;
    }

    /**
     * Tạo bề mặt phẳng có hiệu ứng chạm nhẹ (RippleDrawable) trung tính thích ứng Sáng/Tối.
     */
    static Drawable surface(int fill, int stroke, float radius) {
        return new RippleDrawable(
                ColorStateList.valueOf(darkTheme ? 0x24ffffff : 0x18181a1f),
                shape(fill, stroke, radius),
                shape(0xffffffff, 0, radius));
    }

    /**
     * Tạo thẻ Material Design 3 Expressive (nền Surface thích ứng Sáng/Tối + viền mảnh).
     */
    static Drawable liquidGlassCard(float radius) {
        return shape(SURFACE, LINE, radius);
    }

    /**
     * Tạo nút tròn tối giản thích ứng Sáng/Tối.
     */
    static Drawable liquidGlassCircle() {
        GradientDrawable circle = new GradientDrawable();
        circle.setColor(SURFACE);
        circle.setShape(GradientDrawable.OVAL);
        circle.setStroke(2, LINE);
        return new RippleDrawable(
                ColorStateList.valueOf(darkTheme ? 0x28ffffff : 0x18181a1f),
                circle,
                circle);
    }

    /**
     * Tạo nút viên thuốc chính màu Cam Hổ Phách (#F59E0B) chuẩn Material Design 3.
     */
    static Drawable warmAmberPill(float radius) {
        return new RippleDrawable(
                ColorStateList.valueOf(0x38ffffff),
                shape(WARM_AMBER, 0, radius),
                shape(0xffffffff, 0, radius));
    }

    /**
     * Nút viên thuốc trạng thái đang bật.
     */
    static Drawable rgbSpectrumPill(float radius) {
        return warmAmberPill(radius);
    }

    /**
     * Viên thuốc viền mảnh tối giản.
     */
    static Drawable rgbBorderPill(int innerFill, float radius) {
        return surface(innerFill, LINE, radius);
    }

    /**
     * Tạo viên thuốc nền Surface viền mảnh thích ứng.
     */
    static Drawable liquidGlassPill(float radius) {
        return surface(SURFACE, LINE, radius);
    }

    static ColorStateList inkStates() {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_selected}, new int[]{}},
                new int[]{INK, MUTED});
    }

    /**
     * Lớp nền Material Design 3 Expressive Studio:
     * Nền thích ứng theo Darkroom/Porcelain kèm 2 quầng sáng chuyển sắc dịu mắt
     * (Warm Amber Dawn ở góc trên phải & Cool Mint Sky ở góc dưới trái).
     */
    static final class AntigravityBackground extends View {
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        AntigravityBackground(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            canvas.drawColor(BG);
            if (w <= 0 || h <= 0) return;

            // Quầng sáng Cam Hổ Phách bình minh ấm áp ở góc trên phải
            int amberGlow = darkTheme ? 0x1f59e0b : 0x12f59e0b;
            RadialGradient amberGrad = new RadialGradient(
                    w * 0.88f, h * 0.10f, Math.max(w, h) * 0.52f,
                    amberGlow, Color.TRANSPARENT, Shader.TileMode.CLAMP);
            glowPaint.setShader(amberGrad);
            canvas.drawRect(0, 0, w, h, glowPaint);

            // Quầng sáng Bầu trời bạc hà mát mẻ ở góc dưới trái
            int mintGlow = darkTheme ? 0x1638bdf8 : 0x0c38bdf8;
            RadialGradient mintGrad = new RadialGradient(
                    w * 0.12f, h * 0.90f, Math.max(w, h) * 0.48f,
                    mintGlow, Color.TRANSPARENT, Shader.TileMode.CLAMP);
            glowPaint.setShader(mintGrad);
            canvas.drawRect(0, 0, w, h, glowPaint);
        }
    }

    /**
     * Bộ vẽ Icon Vector bằng {@link Canvas} trên lưới chuẩn 24×24.
     * Hỗ trợ đầy đủ các biểu tượng của bản iOS (SF Symbols tương ứng).
     */
    static final class Icon extends Drawable {
        final String kind;
        final int tint;
        final int size;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Icon(String kind, int tint, int size) {
            this.kind = kind;
            this.tint = tint;
            this.size = size;
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void draw(Canvas c) {
            c.save();
            c.translate(getBounds().left, getBounds().top);
            c.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            p.setColor(tint);
            p.setStrokeWidth(1.8f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStyle(Paint.Style.STROKE);

            switch (kind) {
                case "back": {
                    // chevron.left
                    Path path = new Path();
                    path.moveTo(15f, 5f);
                    path.lineTo(8f, 12f);
                    path.lineTo(15f, 19f);
                    c.drawPath(path, p);
                    break;
                }
                case "undo": {
                    // arrow.uturn.backward
                    Path path = new Path();
                    path.moveTo(9f, 10f);
                    path.lineTo(5f, 6f);
                    path.lineTo(9f, 2f);
                    c.drawPath(path, p);
                    c.drawLine(5f, 6f, 14f, 6f, p);
                    c.drawArc(new RectF(9f, 6f, 20f, 18f), -90f, 180f, false, p);
                    c.drawLine(9f, 18f, 14f, 18f, p);
                    break;
                }
                case "redo": {
                    // arrow.uturn.forward
                    Path path = new Path();
                    path.moveTo(15f, 10f);
                    path.lineTo(19f, 6f);
                    path.lineTo(15f, 2f);
                    c.drawPath(path, p);
                    c.drawLine(19f, 6f, 10f, 6f, p);
                    c.drawArc(new RectF(4f, 6f, 15f, 18f), -90f, -180f, false, p);
                    c.drawLine(10f, 18f, 15f, 18f, p);
                    break;
                }
                case "reset": {
                    // arrow.counterclockwise
                    c.drawArc(new RectF(5f, 5f, 19f, 19f), -45f, 270f, false, p);
                    Path arrow = new Path();
                    arrow.moveTo(12f, 2.5f);
                    arrow.lineTo(16.5f, 6f);
                    arrow.lineTo(11.5f, 8.5f);
                    c.drawPath(arrow, p);
                    break;
                }
                case "rotate_left": {
                    // Xoay 90 độ ngược chiều kim đồng hồ
                    c.drawArc(new RectF(4f, 4f, 20f, 20f), -45f, -270f, false, p);
                    Path arrow = new Path();
                    arrow.moveTo(12f, 2.5f);
                    arrow.lineTo(7.5f, 6f);
                    arrow.lineTo(12.5f, 8.5f);
                    c.drawPath(arrow, p);
                    break;
                }
                case "rotate_right": {
                    // Xoay 90 độ theo chiều kim đồng hồ
                    c.drawArc(new RectF(4f, 4f, 20f, 20f), -135f, 270f, false, p);
                    Path arrow = new Path();
                    arrow.moveTo(12f, 2.5f);
                    arrow.lineTo(16.5f, 6f);
                    arrow.lineTo(11.5f, 8.5f);
                    c.drawPath(arrow, p);
                    break;
                }
                case "wand": {
                    // wand.and.stars (Tự động cân bằng ảnh)
                    c.drawLine(4f, 20f, 16f, 8f, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(18f, 6f, 1.6f, p);
                    c.drawCircle(13f, 4f, 1.1f, p);
                    c.drawCircle(20f, 11f, 1.1f, p);
                    c.drawCircle(7f, 9f, 1.0f, p);
                    p.setStyle(Paint.Style.STROKE);
                    break;
                }
                case "search": {
                    // magnifyingglass
                    c.drawCircle(10.5f, 10.5f, 6f, p);
                    c.drawLine(15f, 15f, 20f, 20f, p);
                    break;
                }
                case "heart": {
                    // heart.fill
                    p.setStyle(Paint.Style.FILL);
                    Path path = new Path();
                    path.moveTo(12f, 20f);
                    path.cubicTo(4f, 14.5f, 3f, 9.5f, 6f, 6.5f);
                    path.cubicTo(8.5f, 4.2f, 11f, 6f, 12f, 7.8f);
                    path.cubicTo(13f, 6f, 15.5f, 4.2f, 18f, 6.5f);
                    path.cubicTo(21f, 9.5f, 20f, 14.5f, 12f, 20f);
                    path.close();
                    c.drawPath(path, p);
                    p.setStyle(Paint.Style.STROKE);
                    break;
                }
                case "folder": {
                    // folder
                    Path path = new Path();
                    path.moveTo(3f, 7f);
                    path.lineTo(9f, 7f);
                    path.lineTo(11f, 9f);
                    path.lineTo(21f, 9f);
                    path.lineTo(21f, 19f);
                    path.lineTo(3f, 19f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case "photo": {
                    c.drawRoundRect(3f, 4f, 21f, 20f, 3f, 3f, p);
                    c.drawCircle(8.5f, 9f, 1.6f, p);
                    Path path = new Path();
                    path.moveTo(4f, 17f);
                    path.lineTo(10f, 12f);
                    path.lineTo(14f, 16f);
                    path.lineTo(17f, 13f);
                    path.lineTo(21f, 17f);
                    c.drawPath(path, p);
                    break;
                }
                case "sliders": {
                    for (int y = 6; y <= 18; y += 6) {
                        c.drawLine(3f, y, 21f, y, p);
                        p.setStyle(Paint.Style.FILL);
                        c.drawCircle(y == 12 ? 15f : 9f, y, 2.4f, p);
                        p.setStyle(Paint.Style.STROKE);
                    }
                    break;
                }
                case "film":
                case "sparkles": {
                    // sparkles / film
                    Path star = new Path();
                    star.moveTo(12f, 3f);
                    star.lineTo(14.2f, 9.8f);
                    star.lineTo(21f, 12f);
                    star.lineTo(14.2f, 14.2f);
                    star.lineTo(12f, 21f);
                    star.lineTo(9.8f, 14.2f);
                    star.lineTo(3f, 12f);
                    star.lineTo(9.8f, 9.8f);
                    star.close();
                    c.drawPath(star, p);
                    break;
                }
                case "moon":
                case "theme_dark": {
                    // Trăng khuyết Material M3
                    Path moon = new Path();
                    moon.moveTo(14f, 4f);
                    moon.cubicTo(8.5f, 5.5f, 6.5f, 11f, 8.5f, 16.5f);
                    moon.cubicTo(10.5f, 20f, 15f, 21f, 18.5f, 19f);
                    moon.cubicTo(14f, 18.5f, 11.5f, 15f, 12f, 11.5f);
                    moon.cubicTo(12.5f, 8f, 15f, 5.5f, 17f, 4.5f);
                    moon.close();
                    p.setStyle(Paint.Style.FILL);
                    c.drawPath(moon, p);
                    p.setStyle(Paint.Style.STROKE);
                    break;
                }
                case "theme_light": {
                    // Mặt trời Material M3
                    c.drawCircle(12f, 12f, 4.2f, p);
                    c.drawLine(12f, 2f, 12f, 5f, p);
                    c.drawLine(12f, 19f, 12f, 22f, p);
                    c.drawLine(2f, 12f, 5f, 12f, p);
                    c.drawLine(19f, 12f, 22f, 12f, p);
                    c.drawLine(4.9f, 4.9f, 7.1f, 7.1f, p);
                    c.drawLine(16.9f, 16.9f, 19.1f, 19.1f, p);
                    c.drawLine(4.9f, 19.1f, 7.1f, 16.9f, p);
                    c.drawLine(16.9f, 7.1f, 19.1f, 4.9f, p);
                    break;
                }
                case "frame": {
                    // photo.artframe
                    c.drawRoundRect(3f, 4f, 21f, 20f, 2.5f, 2.5f, p);
                    c.drawRect(6.5f, 7.5f, 17.5f, 15.5f, p);
                    break;
                }
                case "export": {
                    c.drawLine(12f, 15f, 12f, 3f, p);
                    c.drawLine(7.5f, 7.5f, 12f, 3f, p);
                    c.drawLine(12f, 3f, 16.5f, 7.5f, p);
                    Path path = new Path();
                    path.moveTo(4f, 14f);
                    path.lineTo(4f, 20f);
                    path.lineTo(20f, 20f);
                    path.lineTo(20f, 14f);
                    c.drawPath(path, p);
                    break;
                }
                case "compare": {
                    c.drawCircle(12f, 12f, 8.5f, p);
                    c.drawLine(12f, 3.5f, 12f, 20.5f, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawArc(3.5f, 3.5f, 20.5f, 20.5f, 90f, 180f, true, p);
                    p.setStyle(Paint.Style.STROKE);
                    break;
                }
                case "exposure": {
                    // sun.max
                    c.drawCircle(12f, 12f, 4.2f, p);
                    for (int a = 0; a < 360; a += 45) {
                        double rad = Math.toRadians(a);
                        c.drawLine(
                                12f + (float) Math.cos(rad) * 6.5f,
                                12f + (float) Math.sin(rad) * 6.5f,
                                12f + (float) Math.cos(rad) * 9.2f,
                                12f + (float) Math.sin(rad) * 9.2f,
                                p);
                    }
                    break;
                }
                case "highlight": {
                    // sun.haze
                    c.drawArc(new RectF(7f, 6f, 17f, 16f), 180f, 180f, false, p);
                    c.drawLine(4f, 14f, 20f, 14f, p);
                    c.drawLine(6f, 18f, 18f, 18f, p);
                    break;
                }
                case "shadow": {
                    // moon
                    Path moon = new Path();
                    moon.addArc(new RectF(5f, 4f, 19f, 18f), 40f, 260f);
                    c.drawCircle(12f, 12f, 7.5f, p);
                    c.drawLine(7f, 16f, 17f, 8f, p);
                    break;
                }
                case "color": {
                    // paintpalette
                    c.drawCircle(12f, 12f, 8.5f, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(9f, 9.5f, 1.3f, p);
                    c.drawCircle(14.5f, 9f, 1.3f, p);
                    c.drawCircle(15.5f, 14f, 1.3f, p);
                    c.drawCircle(9.5f, 15f, 1.3f, p);
                    p.setStyle(Paint.Style.STROKE);
                    break;
                }
                case "temp": {
                    // thermometer.medium
                    c.drawRoundRect(10f, 4f, 14f, 16f, 2f, 2f, p);
                    c.drawCircle(12f, 17.5f, 3f, p);
                    c.drawLine(12f, 9f, 12f, 17.5f, p);
                    break;
                }
                case "red": {
                    c.drawCircle(12f, 12f, 8.5f, p);
                    c.drawLine(9.5f, 16f, 9.5f, 8f, p);
                    c.drawRoundRect(9.5f, 8f, 14.5f, 12f, 2f, 2f, p);
                    c.drawLine(12f, 12f, 14.8f, 16f, p);
                    break;
                }
                case "blue": {
                    c.drawCircle(12f, 12f, 8.5f, p);
                    c.drawLine(9.5f, 16f, 9.5f, 8f, p);
                    c.drawRoundRect(9.5f, 8f, 14f, 12f, 2f, 2f, p);
                    c.drawRoundRect(9.5f, 12f, 14.5f, 16f, 2f, 2f, p);
                    break;
                }
                case "grain": {
                    // circle.dotted
                    p.setStyle(Paint.Style.FILL);
                    for (int a = 0; a < 360; a += 36) {
                        double rad = Math.toRadians(a);
                        c.drawCircle(
                                12f + (float) Math.cos(rad) * 7.5f,
                                12f + (float) Math.sin(rad) * 7.5f,
                                1.2f, p);
                    }
                    c.drawCircle(12f, 12f, 1.4f, p);
                    p.setStyle(Paint.Style.STROKE);
                    break;
                }
                case "sharpness": {
                    // triangle
                    Path tri = new Path();
                    tri.moveTo(12f, 4.5f);
                    tri.lineTo(19.5f, 18.5f);
                    tri.lineTo(4.5f, 18.5f);
                    tri.close();
                    c.drawPath(tri, p);
                    break;
                }
                case "clarity": {
                    // diamond / crystal
                    Path dia = new Path();
                    dia.moveTo(12f, 3.5f);
                    dia.lineTo(20.5f, 12f);
                    dia.lineTo(12f, 20.5f);
                    dia.lineTo(3.5f, 12f);
                    dia.close();
                    c.drawPath(dia, p);
                    c.drawCircle(12f, 12f, 2.2f, p);
                    break;
                }
                case "nr": {
                    // shield
                    Path shield = new Path();
                    shield.moveTo(12f, 3.5f);
                    shield.lineTo(19f, 6.5f);
                    shield.cubicTo(19f, 13f, 16f, 18f, 12f, 20.5f);
                    shield.cubicTo(8f, 18f, 5f, 13f, 5f, 6.5f);
                    shield.close();
                    c.drawPath(shield, p);
                    break;
                }
                case "lightcraft": {
                    // Biểu tượng Đường cong S-Curve & Điểm điều khiển của LightCraft Darkroom
                    c.drawRoundRect(3.5f, 3.5f, 20.5f, 20.5f, 4f, 4f, p);
                    Path curve = new Path();
                    curve.moveTo(5.5f, 18.5f);
                    curve.cubicTo(10.5f, 18.5f, 13.5f, 5.5f, 18.5f, 5.5f);
                    c.drawPath(curve, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(9.2f, 15.2f, 1.8f, p);
                    c.drawCircle(14.8f, 8.8f, 1.8f, p);
                    p.setStyle(Paint.Style.STROKE);
                    break;
                }
                case "mist": {
                    // Kính lọc Black Pro-Mist: vòng tròn tỏa tia khuếch tán
                    c.drawCircle(12f, 12f, 4.5f, p);
                    for (int a = 0; a < 360; a += 45) {
                        double rad = Math.toRadians(a);
                        float x1 = 12f + (float) Math.cos(rad) * 6.5f;
                        float y1 = 12f + (float) Math.sin(rad) * 6.5f;
                        float x2 = 12f + (float) Math.cos(rad) * 9.2f;
                        float y2 = 12f + (float) Math.sin(rad) * 9.2f;
                        c.drawLine(x1, y1, x2, y2, p);
                    }
                    break;
                }
                case "flame": {
                    // Halation CineStill: ngọn lửa quầng đỏ
                    Path flame = new Path();
                    flame.moveTo(12f, 3.5f);
                    flame.cubicTo(16f, 7.5f, 19f, 12f, 17f, 16.5f);
                    flame.cubicTo(15.5f, 20f, 8.5f, 20f, 7f, 16.5f);
                    flame.cubicTo(5f, 12f, 8f, 7.5f, 12f, 3.5f);
                    flame.close();
                    c.drawPath(flame, p);
                    c.drawCircle(12f, 14f, 2f, p);
                    break;
                }
                case "silver": {
                    // Bleach Bypass: tinh thể bạc phòng tối
                    Path diamond = new Path();
                    diamond.moveTo(12f, 3.5f);
                    diamond.lineTo(19f, 12f);
                    diamond.lineTo(12f, 20.5f);
                    diamond.lineTo(5f, 12f);
                    diamond.close();
                    c.drawPath(diamond, p);
                    c.drawLine(12f, 3.5f, 12f, 20.5f, p);
                    c.drawLine(5f, 12f, 19f, 12f, p);
                    break;
                }
                case "fog": {
                    // Dehaze / Khử mù sương: các dải sóng ngang
                    c.drawLine(4f, 7.5f, 20f, 7.5f, p);
                    c.drawLine(6f, 12f, 18f, 12f, p);
                    c.drawLine(5f, 16.5f, 19f, 16.5f, p);
                    c.drawCircle(12f, 7.5f, 1.5f, p);
                    c.drawCircle(14f, 16.5f, 1.5f, p);
                    break;
                }
                case "texture": {
                    // Texture: lưới chi tiết vi mô
                    p.setStyle(Paint.Style.FILL);
                    for (int r = 6; r <= 18; r += 4) {
                        for (int col = 6; col <= 18; col += 4) {
                            c.drawCircle(col, r, 1.1f, p);
                        }
                    }
                    p.setStyle(Paint.Style.STROKE);
                    c.drawRoundRect(4f, 4f, 20f, 20f, 3f, 3f, p);
                    break;
                }
                case "person": {
                    // Skin Protect: chân dung người mẫu
                    c.drawCircle(12f, 8f, 3.5f, p);
                    Path body = new Path();
                    body.moveTo(5f, 20f);
                    body.cubicTo(5f, 14.5f, 9f, 13.5f, 12f, 13.5f);
                    body.cubicTo(15f, 13.5f, 19f, 14.5f, 19f, 20f);
                    c.drawPath(body, p);
                    break;
                }
                case "lens": {
                    // Swirly Bokeh Helios: xoáy ốc quang học
                    c.drawCircle(12f, 12f, 8.5f, p);
                    Path spiral = new Path();
                    spiral.moveTo(12f, 12f);
                    for (int t = 0; t <= 540; t += 30) {
                        double rad = Math.toRadians(t);
                        float r = (t / 540f) * 6.5f;
                        float sx = 12f + (float) Math.cos(rad) * r;
                        float sy = 12f + (float) Math.sin(rad) * r;
                        if (t == 0) spiral.moveTo(sx, sy);
                        else spiral.lineTo(sx, sy);
                    }
                    c.drawPath(spiral, p);
                    break;
                }
                case "cloud": {
                    // Sương mù 3D: đám mây chiều sâu
                    Path cloud = new Path();
                    cloud.moveTo(6f, 17f);
                    cloud.lineTo(18f, 17f);
                    cloud.cubicTo(20f, 17f, 21f, 15f, 20f, 13.5f);
                    cloud.cubicTo(20f, 11.5f, 18f, 11f, 17f, 11f);
                    cloud.cubicTo(16.5f, 8.5f, 14f, 7f, 11.5f, 7.5f);
                    cloud.cubicTo(9.5f, 8f, 8f, 9.5f, 8f, 12f);
                    cloud.cubicTo(6.5f, 12f, 5f, 13.5f, 5f, 15f);
                    cloud.cubicTo(5f, 16.5f, 5.5f, 17f, 6f, 17f);
                    cloud.close();
                    c.drawPath(cloud, p);
                    break;
                }
                case "sun": {
                    // Light leak: vệt lọt sáng phim góc
                    Path leak = new Path();
                    leak.moveTo(3.5f, 3.5f);
                    leak.lineTo(13f, 3.5f);
                    leak.lineTo(3.5f, 13f);
                    leak.close();
                    c.drawPath(leak, p);
                    c.drawLine(5f, 15f, 15f, 5f, p);
                    c.drawLine(8f, 18f, 18f, 8f, p);
                    c.drawCircle(16f, 16f, 3f, p);
                    break;
                }
                default: {
                    c.drawCircle(12f, 12f, 7f, p);
                    break;
                }
            }
            c.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            p.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            p.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
