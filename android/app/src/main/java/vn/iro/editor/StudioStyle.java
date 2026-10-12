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
final class StudioStyle {

    // =========================================================================
    // BẢNG MÀU GOOGLE MATERIAL 3 RGB SPECTRUM (Google Blue, Red, Green, Yellow)
    // =========================================================================

    /** Nền sáng chuẩn Google Material 3 (#F8FAFD). */
    static final int BG = 0xfff8fafd;
    static final int PORCELAIN = 0xfff8fafd;

    /** Nền bề mặt thẻ (#F3F6FC). */
    static final int SURFACE = 0xfff3f6fc;

    /** Nền bề mặt cấp 2 (#EAEFF8). */
    static final int SURFACE_CONTAINER = 0xffeaeff8;

    /** Đường viền mảnh (#DEE3EC). */
    static final int LINE = 0xffdee3ec;

    /** Màu chữ chính mực đậm (#1B1C1E). */
    static final int INK = 0xff1b1c1e;

    /** Màu chữ phụ xám trung tính (#6E7480). */
    static final int MUTED = 0xff6e7480;

    // 4 màu thương hiệu Google RGB + Y (Red, Green, Blue, Yellow)
    /** Kênh Đỏ Google (Google Red #EA4335). */
    static final int GOOGLE_RED = 0xffea4335;
    static final int RED_TONAL = 0xfffce8e6;

    /** Kênh Lục Google (Google Green #34A853). */
    static final int GOOGLE_GREEN = 0xff34a853;
    static final int GREEN_TONAL = 0xffe6f4ea;

    /** Kênh Lam Google (Google Blue #1A73E8 - Màu nhấn chính chuẩn Material 3). */
    static final int GOOGLE_BLUE = 0xff1a73e8;
    static final int BLUE_TONAL = 0xffe8f0fe;

    /** Kênh Vàng/Ánh sáng Google (Google Yellow/Amber #F29900). */
    static final int GOOGLE_YELLOW = 0xfff29900;
    static final int YELLOW_TONAL = 0xfffef7e0;

    /** Điểm nhấn mặc định dùng Google Blue (#1A73E8) để đồng bộ chuẩn Google Photos. */
    static final int WARM_AMBER = GOOGLE_BLUE;
    static final int ACCENT = GOOGLE_BLUE;

    /** Nền xanh nhạt Tonal cho các huy hiệu đang kích hoạt (#E8F0FE). */
    static final int WARM_AMBER_LIGHT = BLUE_TONAL;

    /** Màu viên thuốc tối (#14161A). */
    static final int DARK_PILL = 0xff14161a;

    private StudioStyle() {
    }

    /**
     * Trả về màu nền Tonal dịu tương ứng với từng kênh màu RGB của Google.
     */
    static int tonalForChannel(int channelColor) {
        if (channelColor == GOOGLE_RED) return RED_TONAL;
        if (channelColor == GOOGLE_GREEN) return GREEN_TONAL;
        if (channelColor == GOOGLE_YELLOW) return YELLOW_TONAL;
        return BLUE_TONAL;
    }

    /**
     * Tạo hình chữ nhật bo góc cơ bản với màu nền và đường viền.
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
     * Tạo bề mặt có hiệu ứng gợn sóng khi chạm (RippleDrawable) với điểm nhấn Google RGB Blue.
     */
    static Drawable surface(int fill, int stroke, float radius) {
        return new RippleDrawable(
                ColorStateList.valueOf(0x221a73e8),
                shape(fill, stroke, radius),
                shape(0xffffffff, 0, radius));
    }

    /**
     * Tạo thẻ kính mờ Liquid Glass pha sắc trắng xanh chuẩn Google Material 3.
     */
    static Drawable liquidGlassCard(float radius) {
        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xf6ffffff, 0xe8f3f7fd});
        base.setCornerRadius(radius);
        base.setStroke(2, 0xffdde3ee);
        return base;
    }

    /**
     * Tạo nút tròn kính mờ Liquid Glass.
     */
    static Drawable liquidGlassCircle() {
        GradientDrawable circle = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xfaffffff, 0xebf1f5fc});
        circle.setShape(GradientDrawable.OVAL);
        circle.setStroke(2, 0xffdde3ee);
        return new RippleDrawable(
                ColorStateList.valueOf(0x221a73e8),
                circle,
                circle);
    }

    /**
     * Tạo nút viên thuốc (Capsule Pill) màu Google Blue nổi bật.
     */
    static Drawable warmAmberPill(float radius) {
        GradientDrawable pill = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xff4285f4, GOOGLE_BLUE});
        pill.setCornerRadius(radius);
        return new RippleDrawable(
                ColorStateList.valueOf(0x33ffffff),
                pill,
                shape(0xffffffff, 0, radius));
    }

    /**
     * Tạo nút viên thuốc dải quang phổ Google RGB (Red -> Yellow -> Green -> Blue)
     * đặc trưng của Google AI / Google Photos Magic Editor.
     */
    static Drawable rgbSpectrumPill(float radius) {
        GradientDrawable spectrum = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{GOOGLE_RED, 0xfff9ab00, GOOGLE_GREEN, GOOGLE_BLUE});
        spectrum.setCornerRadius(radius);
        return new RippleDrawable(
                ColorStateList.valueOf(0x33ffffff),
                spectrum,
                shape(0xffffffff, 0, radius));
    }

    /**
     * Tạo thẻ/viên thuốc có viền chuyển sắc 4 màu Google RGB (Red, Yellow, Green, Blue)
     * và phần ruột màu sáng bên trong.
     */
    static Drawable rgbBorderPill(int innerFill, float radius) {
        GradientDrawable outerSpectrum = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{GOOGLE_RED, 0xfffbbc04, GOOGLE_GREEN, GOOGLE_BLUE});
        outerSpectrum.setCornerRadius(radius);

        GradientDrawable inner = new GradientDrawable();
        inner.setColor(innerFill);
        inner.setCornerRadius(Math.max(1f, radius - 4f));

        LayerDrawable layered = new LayerDrawable(new Drawable[]{outerSpectrum, inner});
        layered.setLayerInset(1, 4, 4, 4, 4);
        return new RippleDrawable(
                ColorStateList.valueOf(0x224285f4),
                layered,
                shape(0xffffffff, 0, radius));
    }

    /**
     * Tạo viên thuốc kính mờ trắng.
     */
    static Drawable liquidGlassPill(float radius) {
        return surface(0xf5ffffff, LINE, radius);
    }

    static ColorStateList inkStates() {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_selected}, new int[]{}},
                new int[]{GOOGLE_BLUE, INK});
    }

    /**
     * Lớp nền {@code AntigravityBackground} nâng cấp lên chuẩn Google RGB Ambient Mesh:
     * Vẽ nền sáng {@code #F8FAFD} kèm 3 quầng sáng quang phổ R - G - B dịu ở 3 góc màn hình
     * tạo chiều sâu kính mờ đặc trưng của Google Photos & Gemini Studio.
     */
    static final class AntigravityBackground extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        AntigravityBackground(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            // 1. Nền sáng cơ bản (#F8FAFD)
            canvas.drawColor(PORCELAIN);

            // 2. Góc trên trái: Quầng sáng Kênh Lam (Google Blue #4285F4)
            float blueRadius = Math.max(1f, w * 0.58f);
            float blueCx = w * 0.14f;
            float blueCy = h * 0.12f;
            paint.setShader(new RadialGradient(
                    blueCx, blueCy, blueRadius,
                    new int[]{0x42cfe2ff, 0x18e8f0fe, 0x00ffffff},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(blueCx, blueCy, blueRadius, paint);

            // 3. Góc trên phải: Quầng sáng Kênh Đỏ - Vàng (Google Red #EA4335 & Yellow #FBBC04)
            float redRadius = Math.max(1f, w * 0.55f);
            float redCx = w * 0.86f;
            float redCy = h * 0.14f;
            paint.setShader(new RadialGradient(
                    redCx, redCy, redRadius,
                    new int[]{0x38ffd8d4, 0x1effefd0, 0x00ffffff},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(redCx, redCy, redRadius, paint);

            // 4. Góc dưới trái/giữa: Quầng sáng Kênh Lục (Google Green #34A853)
            float greenRadius = Math.max(1f, w * 0.58f);
            float greenCx = w * 0.24f;
            float greenCy = h * 0.88f;
            paint.setShader(new RadialGradient(
                    greenCx, greenCy, greenRadius,
                    new int[]{0x3ad2f4dc, 0x00ffffff},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(greenCx, greenCy, greenRadius, paint);
            paint.setShader(null);
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
