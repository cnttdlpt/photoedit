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
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;

/**
 * Hệ thống ngôn ngữ thiết kế giao diện IRO PLAYFUL CLASSIC CLAYMORPHISM.
 * Đồng bộ hoàn chỉnh trên toàn bộ các nền tảng (Web, Android, Desktop, iOS).
 *
 * <p>Đặc trưng cốt lõi của Playful Classic Claymorphism:
 * <ul>
 *     <li><b>Bảng màu kẹo mềm / Candy Pastels:</b> Coral (#FF6B8B), Sky Blue (#48DBFB),
 *         Mint (#1DD1A1), Butter (#FECA57), Lilac (#9B59B6) trên nền mây xốp (#EBF1FA).</li>
 *     <li><b>Hình khối phồng nổi 3D (Extrusion & Dual Inset Bevels):</b> Đổ bóng rơi mềm mại đa tầng
 *         kết hợp phản chiếu ánh sáng trắng trên-trái và bóng chìm chiều sâu dưới-phải.</li>
 *     <li><b>Cảm giác bấm xúc giác đàn hồi (Tactile Squishy Depth):</b> Bo góc phồng lớn (20px - 32px, Pill 999dp),
 *         phản hồi ấn chạm chìm lõm (sunken bevel) và độ nảy cao.</li>
 * </ul>
 */
public final class StudioStyle {

    // =========================================================================
    // HỆ THỐNG MÀU PLAYFUL CLASSIC CLAYMORPHISM (DYNAMIC THEME ENGINE)
    // =========================================================================

    private static boolean darkTheme = false;

    public static boolean isDarkTheme() {
        return darkTheme;
    }

    public static void setDarkTheme(boolean dark) {
        applyTheme(dark);
    }

    public static void toggleDarkTheme() {
        applyTheme(!darkTheme);
    }

    /** Nền chính ứng dụng (thích ứng theo Sáng/Tối) - Clay Marshmallow Ground. */
    public static int BG = 0xffebf1fa;
    public static int PORCELAIN = 0xffebf1fa;

    /** Bề mặt Surface Container chính (Thẻ Hero, Bảng điều khiển, Khối Clay nổi). */
    public static int SURFACE = 0xffffffff;

    /** Bề mặt Surface Container cấp 2 (Thẻ Bento con, hộp tìm kiếm, chip). */
    public static int SURFACE_CONTAINER = 0xfff5f8fd;
    public static int SURFACE_CONTAINER_HIGH = 0xffe4ebf5;

    /** Nền sân khấu ảnh (Stage Ground / Sunken Clay Base). */
    public static int STAGE = 0xffe4ebf5;

    /** Đường viền mềm mại chuẩn Playful Clay. */
    public static int LINE = 0xffd2dbe8;

    /** Màu chữ & biểu tượng chính (Slate Ink trên nền sáng / Sáng rõ trên nền tối). */
    public static int INK = 0xff2c3e50;

    /** Màu chữ & biểu tượng phụ (Soft Slate Muted). */
    public static int MUTED = 0xff7f8c8d;

    /** Điểm nhấn Playful Clay: Candy Coral (#FF6B8B) / Coral Pastel. */
    public static int ACCENT = 0xffff6b8b;
    public static int ON_ACCENT = 0xffffffff;

    /** Điểm nhấn thứ 2: Candy Butter / Warm Amber (#FECA57). */
    public static int WARM_AMBER = 0xfffeca57;

    /** Nền trạng thái đang chọn / Tonal Container nhạt. */
    public static int WARM_AMBER_LIGHT = 0xfffff3d6;

    /** Màu nền viên thuốc tối. */
    public static int DARK_PILL = 0xff2c3e50;

    // Bộ màu điểm nhấn kẹo Playful Claymorphism
    public static final int CLAY_CORAL = 0xffff6b8b;
    public static final int CLAY_SKY = 0xff48dbfb;
    public static final int CLAY_MINT = 0xff1dd1a1;
    public static final int CLAY_BUTTER = 0xfffeca57;
    public static final int CLAY_LILAC = 0xff9b59b6;

    // Màu kênh chức năng (RGB curves, histogram, color balance)
    public static final int GOOGLE_RED = 0xffff6b8b;
    public static final int RED_TONAL = 0xffffeef2;
    public static final int GOOGLE_GREEN = 0xff1dd1a1;
    public static final int GREEN_TONAL = 0xffe8faf4;
    public static final int GOOGLE_BLUE = 0xff48dbfb;
    public static final int BLUE_TONAL = 0xffeefaff;
    public static final int GOOGLE_YELLOW = 0xfffeca57;
    public static final int YELLOW_TONAL = 0xfffffbf0;

    // --- Hệ thống Token M3 Tonal Surface ---
    public static int M3_PRIMARY = 0xffff6b8b;
    public static int M3_ON_PRIMARY = 0xffffffff;
    public static int M3_PRIMARY_CONTAINER = 0xffffeef2;
    public static int M3_ON_PRIMARY_CONTAINER = 0xffff5277;
    public static int M3_OUTLINE = 0xffd2dbe8;
    public static int M3_OUTLINE_VARIANT = 0xffe2e8f0;
    public static int M3_SURFACE_CONTAINER_LOW = 0xffebf1fa;

    public static final int M3_DARK_BG = 0xff1e1f29;
    public static final int M3_DARK_SURFACE = 0xff282a37;
    public static final int M3_DARK_SURFACE_CONTAINER_LOW = 0xff232532;
    public static final int M3_DARK_SURFACE_CONTAINER = 0xff323546;
    public static final int M3_DARK_SURFACE_CONTAINER_HIGH = 0xff3d4256;
    public static final int M3_DARK_PRIMARY = 0xffff7597;

    /**
     * Cập nhật toàn bộ bảng màu giao diện tức thì theo chế độ Playful Dark Slate
     * hoặc Playful Light Cloud Clay.
     */
    public static void applyTheme(boolean dark) {
        darkTheme = dark;
        if (dark) {
            BG = 0xff1e1f29;
            PORCELAIN = 0xff1e1f29;
            SURFACE = 0xff282a37;
            SURFACE_CONTAINER = 0xff323546;
            SURFACE_CONTAINER_HIGH = 0xff3d4256;
            STAGE = 0xff16171f;
            LINE = 0xff3d4256;
            INK = 0xfff8f9fd;
            MUTED = 0xffa0a6b8;
            WARM_AMBER = 0xfffed36e;
            ACCENT = 0xffff7597;
            ON_ACCENT = 0xff1e1f29;
            WARM_AMBER_LIGHT = 0xff3b3628;
            DARK_PILL = 0xff323546;
            M3_PRIMARY = 0xffff7597;
            M3_ON_PRIMARY = 0xff1e1f29;
            M3_PRIMARY_CONTAINER = 0xff3d2a33;
            M3_ON_PRIMARY_CONTAINER = 0xffff7597;
            M3_OUTLINE = 0xff3d4256;
        } else {
            BG = 0xffebf1fa;
            PORCELAIN = 0xffebf1fa;
            SURFACE = 0xffffffff;
            SURFACE_CONTAINER = 0xfff5f8fd;
            SURFACE_CONTAINER_HIGH = 0xffe4ebf5;
            STAGE = 0xffe4ebf5;
            LINE = 0xffd2dbe8;
            INK = 0xff2c3e50;
            MUTED = 0xff7f8c8d;
            WARM_AMBER = 0xfffeca57;
            ACCENT = 0xffff6b8b;
            ON_ACCENT = 0xffffffff;
            WARM_AMBER_LIGHT = 0xfffff3d6;
            DARK_PILL = 0xff2c3e50;
            M3_PRIMARY = 0xffff6b8b;
            M3_ON_PRIMARY = 0xffffffff;
            M3_PRIMARY_CONTAINER = 0xffffeef2;
            M3_ON_PRIMARY_CONTAINER = 0xffff5277;
            M3_OUTLINE = 0xffd2dbe8;
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

    public static int getStageColor() {
        return STAGE;
    }

    public static int getPrimaryColor() {
        return ACCENT;
    }

    public static int getOnAccentColor() {
        return ON_ACCENT;
    }

    public static int getTextColor() {
        return INK;
    }

    public static int getMutedTextColor() {
        return MUTED;
    }

    // --- Bán kính bo góc đầy đặn chuẩn Playful Classic Claymorphism (dp) ---
    public static final float CORNER_XS_DP = 8f;
    public static final float CORNER_SM_DP = 14f;
    public static final float CORNER_MD_DP = 20f;
    public static final float CORNER_LG_DP = 26f;
    public static final float CORNER_XL_DP = 32f;
    public static final float CORNER_FULL_DP = 999f;

    public static int dpToPx(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Bộ vẽ hình khối đất sét phồng 3D (Extruded 3D Clay Drawable) với phản chiếu ánh sáng 2 lớp.
     */
    public static class ClayDrawable extends Drawable {
        private int fillColor;
        private int strokeColor;
        private float cornerRadius;
        private boolean isSunken;
        private boolean isPressed;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint innerShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF boundsF = new RectF();

        public ClayDrawable(int fillColor, int strokeColor, float cornerRadius) {
            this(fillColor, strokeColor, cornerRadius, false);
        }

        public ClayDrawable(int fillColor, int strokeColor, float cornerRadius, boolean isSunken) {
            this.fillColor = fillColor;
            this.strokeColor = strokeColor;
            this.cornerRadius = cornerRadius;
            this.isSunken = isSunken;
            fillPaint.setStyle(Paint.Style.FILL);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(1.6f);
            highlightPaint.setStyle(Paint.Style.STROKE);
            highlightPaint.setStrokeWidth(2.5f);
            innerShadowPaint.setStyle(Paint.Style.STROKE);
            innerShadowPaint.setStrokeWidth(2.5f);
        }

        public void setColors(int fill, int stroke) {
            this.fillColor = fill;
            this.strokeColor = stroke;
            invalidateSelf();
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] states) {
            boolean pressed = false;
            if (states != null) {
                for (int s : states) {
                    if (s == android.R.attr.state_pressed) {
                        pressed = true;
                        break;
                    }
                }
            }
            if (this.isPressed != pressed) {
                this.isPressed = pressed;
                invalidateSelf();
                return true;
            }
            return super.onStateChange(states);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.width() <= 0 || bounds.height() <= 0) return;
            boundsF.set(bounds);
            float r = Math.min(cornerRadius, Math.min(boundsF.width(), boundsF.height()) / 2f);

            if (fillColor == 0 || (fillColor & 0xff000000) == 0) {
                if (strokeColor != 0) {
                    strokePaint.setColor(strokeColor);
                    canvas.drawRoundRect(boundsF, r, r, strokePaint);
                }
                return;
            }

            if (isSunken || isPressed) {
                // Trạng thái lõm sâu (Sunken Clay / Squishy Active): Đổ bóng chìm lòng trong
                fillPaint.setColor(fillColor);
                canvas.drawRoundRect(boundsF, r, r, fillPaint);

                // Bóng chìm tối góc trên - trái
                innerShadowPaint.setColor(darkTheme ? 0x55000000 : 0x242c3e50);
                RectF insetTL = new RectF(boundsF.left + 1.5f, boundsF.top + 1.5f, boundsF.right - 1.5f, boundsF.bottom - 1.5f);
                canvas.drawRoundRect(insetTL, Math.max(0, r - 1.5f), Math.max(0, r - 1.5f), innerShadowPaint);

                // Viền phản chiếu sáng góc dưới - phải
                highlightPaint.setColor(darkTheme ? 0x18ffffff : 0x55ffffff);
                RectF insetBR = new RectF(boundsF.left + 2f, boundsF.top + 2f, boundsF.right - 0.5f, boundsF.bottom - 0.5f);
                canvas.drawRoundRect(insetBR, Math.max(0, r - 1.5f), Math.max(0, r - 1.5f), highlightPaint);
            } else {
                // Hiệu ứng phồng nổi 3D kinh điển (Classic 3D Extrusion)
                // Lớp 1: Bóng rơi mềm mại phía dưới (Soft Drop Shadow)
                shadowPaint.setStyle(Paint.Style.FILL);
                shadowPaint.setColor(darkTheme ? 0x2e000000 : 0x1870809b);
                RectF shadowRect = new RectF(boundsF.left, boundsF.top + 2.5f, boundsF.right, boundsF.bottom + 3.5f);
                canvas.drawRoundRect(shadowRect, r, r, shadowPaint);

                // Lớp 2: Quầng sáng trắng nổi khối phía trên
                shadowPaint.setColor(darkTheme ? 0x0cffffff : 0x50ffffff);
                RectF glowRect = new RectF(boundsF.left, boundsF.top - 1.2f, boundsF.right, boundsF.bottom - 1.2f);
                canvas.drawRoundRect(glowRect, r, r, shadowPaint);

                // Lớp 3: Thân viên đất sét mềm
                fillPaint.setColor(fillColor);
                canvas.drawRoundRect(boundsF, r, r, fillPaint);

                // Lớp 4: Gờ sáng phản quang trên - trái (Top-Left Specular Bevel Highlight)
                highlightPaint.setColor(darkTheme ? 0x24ffffff : 0x75ffffff);
                RectF bevelTL = new RectF(boundsF.left + 1.2f, boundsF.top + 1.2f, boundsF.right - 1.5f, boundsF.bottom - 1.5f);
                canvas.drawRoundRect(bevelTL, Math.max(0, r - 1.2f), Math.max(0, r - 1.2f), highlightPaint);

                // Lớp 5: Gờ bóng chiều sâu dưới - phải (Bottom-Right Soft Depth Shadow)
                innerShadowPaint.setColor(darkTheme ? 0x3a000000 : 0x142c3e50);
                RectF bevelBR = new RectF(boundsF.left + 1.5f, boundsF.top + 1.5f, boundsF.right - 1.2f, boundsF.bottom - 1.2f);
                canvas.drawRoundRect(bevelBR, Math.max(0, r - 1.2f), Math.max(0, r - 1.2f), innerShadowPaint);
            }

            if (strokeColor != 0) {
                strokePaint.setColor(strokeColor);
                canvas.drawRoundRect(boundsF, r, r, strokePaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
            strokePaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * Tạo nền thẻ Claymorphism kèm viền mảnh và bo góc tròn đầy.
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
     * Tạo nút bấm hoặc Chip có hiệu ứng sóng nước nảy mềm (Clay Ripple Layer).
     */
    public static Drawable createM3RippleSurface(
            Context context,
            int fillColor,
            int rippleColor,
            int strokeColor,
            float cornerDp
    ) {
        ClayDrawable content = new ClayDrawable(fillColor, strokeColor, dpToPx(context, cornerDp));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dpToPx(context, cornerDp));
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
     * Tạo hình khối đất sét bo tròn với màu nền, đường viền và hiệu ứng phồng nổi 3D.
     */
    static Drawable shape(int fill, int stroke, float radius) {
        return new ClayDrawable(fill, stroke, radius);
    }

    /**
     * Tạo bề mặt Clay phồng có hiệu ứng chạm nảy mềm (Ripple) thích ứng Sáng/Tối.
     */
    static Drawable surface(int fill, int stroke, float radius) {
        ClayDrawable clay = new ClayDrawable(fill, stroke, radius);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(radius);
        mask.setColor(Color.WHITE);
        return new RippleDrawable(
                ColorStateList.valueOf(darkTheme ? 0x24ffffff : 0x18181a1f),
                clay,
                mask);
    }

    /**
     * Tạo thẻ Clay Card 3D phồng nổi (nền Surface trắng đất sét mềm + viền pastel).
     */
    public static Drawable clayCard(float radius) {
        return new ClayDrawable(SURFACE, LINE, radius);
    }

    /**
     * Tạo viên thuốc đất sét phồng màu tùy chọn.
     */
    public static Drawable clayPill(int fill, float radius) {
        return surface(fill, 0, radius);
    }

    /**
     * Tạo viên thuốc đất sét phồng có viền.
     */
    public static Drawable clayPill(int fill, int stroke, float radius) {
        return surface(fill, stroke, radius);
    }

    /**
     * Tạo khay lõm đất sét chìm (Sunken Clay Base cho Stage & Slider).
     */
    public static Drawable claySunken(float radius) {
        return new ClayDrawable(STAGE, LINE, radius, true);
    }

    /**
     * Tương thích ngược: Thẻ Liquid Glass Card chuyển đổi mượt mà sang Clay Card.
     */
    static Drawable liquidGlassCard(float radius) {
        return clayCard(radius);
    }

    /**
     * Tạo nút tròn đất sét phồng mềm thích ứng Sáng/Tối.
     */
    static Drawable liquidGlassCircle() {
        ClayDrawable circle = new ClayDrawable(SURFACE, LINE, 999f);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        return new RippleDrawable(
                ColorStateList.valueOf(darkTheme ? 0x28ffffff : 0x18181a1f),
                circle,
                mask);
    }

    /**
     * Tạo nút viên thuốc chính màu Kẹo Coral / Candy Butter phồng nảy 3D.
     */
    static Drawable warmAmberPill(float radius) {
        return surface(ACCENT, 0, radius);
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
     * Tạo viên thuốc nền Surface viền mềm thích ứng.
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
     * Lớp nền IRO Playful Claymorphism:
     * Nền xốp bồng bềnh Pastel Cloud với hai quầng cực quang kẹo ngọt (Coral & Sky Glow)
     * tạo không gian 3D mềm mại, vui mắt và giàu cảm xúc.
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

            // Vùng sáng Playful Candy Glows
            int coralGlow = darkTheme ? 0x16ff7597 : 0x18ff6b8b;
            RadialGradient coralGrad = new RadialGradient(
                    w * 0.90f, h * 0.08f, Math.max(w, h) * 0.55f,
                    coralGlow, Color.TRANSPARENT, Shader.TileMode.CLAMP);
            glowPaint.setShader(coralGrad);
            canvas.drawRect(0, 0, w, h, glowPaint);

            int skyGlow = darkTheme ? 0x1454e1ff : 0x1648dbfb;
            RadialGradient skyGrad = new RadialGradient(
                    w * 0.10f, h * 0.85f, Math.max(w, h) * 0.50f,
                    skyGlow, Color.TRANSPARENT, Shader.TileMode.CLAMP);
            glowPaint.setShader(skyGrad);
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
                case "camera": {
                    c.drawRoundRect(new RectF(3f, 7f, 21f, 19f), 3f, 3f, p);
                    c.drawCircle(12f, 13f, 3.5f, p);
                    c.drawRect(new RectF(8f, 4.5f, 12f, 7f), p);
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
