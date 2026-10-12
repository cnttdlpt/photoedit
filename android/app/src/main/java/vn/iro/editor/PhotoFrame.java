package vn.iro.editor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

/**
 * Bộ dựng 30 mẫu khung ảnh và thông tin EXIF dùng chung cho cả Thumbnail, Preview và Xuất ảnh.
 * Đồng bộ hoàn toàn ID, tỷ lệ bố cục và tên hiển thị với {@code ios/IRO/Imaging/PhotoFrame.swift}.
 *
 * <p>Phân loại 30 mẫu khung (ID từ 1 đến 30; 0 = Không viền):
 * <ul>
 *     <li><b>Mẫu cơ bản (1..3):</b> Classic (dải trắng dưới), White (cột thông tin phải), Glass (dải mờ đè lên ảnh).</li>
 *     <li><b>Mẫu mở rộng (4..18):</b> Dark, Centered, Gallery, Noir, Instant, Vintage, Film 35 (viền lỗ phim),
 *         Cinema, Blur (nền ảnh làm mờ), Palette (5 ô màu trích từ giữa ảnh), Postcard, Editorial, Double,
 *         Date (dấu ngày cam), Signature (chữ ký tối giản).</li>
 *     <li><b>Mẫu trang trí & nghệ thuật (19..30):</b> Float (đổ bóng), Soft (bo góc giấy), Torn (giấy xé răng cưa),
 *         Tape (băng dính dán góc), Wood (vân gỗ), Gold (viền kim loại vàng), Neon (viền phát sáng),
 *         Notebook (giấy kẻ ô), Ticket (vé đục lỗ), Blueprint (bản vẽ kỹ thuật xanh), Arch (vòm triển lãm),
 *         Corners (4 góc kẹp ảnh).</li>
 * </ul>
 */
final class PhotoFrame {

    /** Danh sách tên 31 trạng thái khung (chỉ số 0 = tắt khung, 1..30 = 30 mẫu khung). */
    static final String[] STYLES = {
            "Không chèn thông tin",
            "Classic · dải trắng",
            "White · cột thông tin",
            "Glass · chữ trên ảnh",
            "Dark · dải đen",
            "Centered · thông số giữa",
            "Gallery · viền trắng rộng",
            "Noir · viền đen rộng",
            "Instant · ảnh lấy liền",
            "Vintage · giấy kem",
            "Film 35 · viền lỗ phim",
            "Cinema · thanh đen",
            "Blur · nền ảnh mờ",
            "Palette · bảng màu ảnh",
            "Postcard · bưu thiếp",
            "Editorial · trang tạp chí",
            "Double · viền kép",
            "Date · dấu ngày màu cam",
            "Signature · chữ ký tối giản",
            "Float · ảnh nổi đổ bóng",
            "Soft · viền bo tròn",
            "Torn · giấy xé",
            "Tape · băng dính scrapbook",
            "Wood · khung gỗ",
            "Gold · viền kim loại",
            "Neon · ánh sáng viền",
            "Notebook · giấy kẻ ô",
            "Ticket · vé kỷ niệm",
            "Blueprint · bản vẽ xanh",
            "Arch · vòm triển lãm",
            "Corners · góc giữ ảnh"
    };

    /** Các nhóm lọc khung trong hộp thoại chọn mẫu. */
    static final String[] GROUPS = {
            "Tất cả",
            "Thông số máy",
            "Tối giản",
            "Film / hoài cổ",
            "Sáng tạo",
            "Mẫu mới"
    };

    private PhotoFrame() {
    }

    /**
     * Trả về mã nhóm (1..4) của một mẫu khung để lọc trên giao diện.
     */
    static int group(int style) {
        if (style == 19 || style == 20 || style == 30) return 2;
        if (style == 21 || style == 22 || style == 23 || style == 27) return 3;
        if (style == 1 || style == 2 || style == 4 || style == 5) return 1;
        if (style == 3 || style == 6 || style == 7 || style == 16 || style == 18) return 2;
        if (style == 8 || style == 9 || style == 10 || style == 11 || style == 17) return 3;
        return 4;
    }

    /**
     * Kết xuất bức ảnh kèm khung và thông tin EXIF theo mẫu {@code style}.
     *
     * @param photo Ảnh nguồn đã qua xử lý màu (không bị sửa đổi hay recycle bên trong hàm này).
     * @param info  Thông tin máy ảnh, ống kính, ngày chụp và tác giả.
     * @param style Chỉ số mẫu khung (0..30). Nếu bằng 0 thì trả về nguyên {@code photo}.
     * @return {@link Bitmap} mới đã vẽ khung.
     */
    static Bitmap render(Bitmap photo, PhotoInfo info, int style) {
        if (style < 1 || style >= STYLES.length) {
            return photo;
        }
        PhotoInfo safeInfo = info != null ? info.sanitized() : new PhotoInfo();
        DebugLogger.d(DebugLogger.TAG_FRAME, "Đang vẽ khung style=#" + style + " (" + STYLES[style] + ")");

        if (style >= 19) {
            return decorative(photo, safeInfo, style);
        }
        if (style > 3) {
            return extended(photo, safeInfo, style);
        }

        // =========================================================================
        // NHÓM MẪU CƠ BẢN (1: Classic, 2: White, 3: Glass)
        // =========================================================================
        int w = photo.getWidth();
        int h = photo.getHeight();
        int pad = Math.max(1, Math.round(w * 0.025f));
        int footer = Math.max(1, Math.round(w * 0.125f));
        int side = Math.max(1, Math.round(w * 0.30f));

        int ow = (style == 2) ? w + side + pad * 2 : w;
        int oh = (style == 1) ? h + footer : ((style == 2) ? h + pad * 2 : h);

        Bitmap out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(photo, style == 2 ? pad : 0, style == 2 ? pad : 0, p);

        String title = safeInfo.camera.isEmpty() ? "PHOTO" : safeInfo.camera;

        if (style == 1) {
            // Mẫu 1 (Classic): Dải trắng phía dưới ảnh, tên máy bên trái, thông số bên phải
            line(c, p, title, pad, h + footer * 0.37f, w * 0.43f, w * 0.028f, 0xff202520, true);
            line(c, p, safeInfo.author, pad, h + footer * 0.70f, w * 0.43f, w * 0.018f, 0xff687068, false);
            line(c, p, safeInfo.settings(), w * 0.49f, h + footer * 0.37f, w * 0.485f, w * 0.020f, 0xff353b35, false);
            line(c, p, safeInfo.date, w * 0.49f, h + footer * 0.70f, w * 0.485f, w * 0.018f, 0xff687068, false);
        } else if (style == 2) {
            // Mẫu 2 (White): Viền trắng bao quanh và cột thông tin dọc bên phải
            float x = w + pad * 2;
            float width = side - pad;
            float center = oh * 0.5f;
            float unit = Math.min(w, h * 1.7f);

            line(c, p, title, x, center - unit * 0.07f, width, unit * 0.032f, 0xff202520, true);
            String[] rows = {
                    safeInfo.focal,
                    join(safeInfo.aperture, safeInfo.shutter),
                    safeInfo.iso,
                    safeInfo.date,
                    safeInfo.author
            };
            float y = center - unit * 0.025f;
            for (String row : rows) {
                if (!row.isEmpty()) {
                    line(c, p, row, x, y, width, unit * 0.019f, 0xff687068, false);
                    y += unit * 0.032f;
                }
            }
        } else {
            // Mẫu 3 (Glass): Dải chuyển sắc đen mờ đè lên phần chân ảnh, giữ nguyên kích thước gốc
            float bar = Math.min(h * 0.35f, w * 0.15f);
            float top = h - bar;
            float unit = bar / 0.15f;

            p.setShader(new LinearGradient(0, top, 0, h, 0x08000000, 0xc9000000, Shader.TileMode.CLAMP));
            c.drawRect(0, top, w, h, p);
            p.setShader(null);

            line(c, p, title, pad, top + bar * 0.39f, w - pad * 2, unit * 0.031f, Color.WHITE, true);
            line(c, p, safeInfo.settings(), pad, top + bar * 0.65f, w - pad * 2, unit * 0.020f, 0xffeeeeee, false);
            line(c, p, join(safeInfo.date, safeInfo.author), pad, top + bar * 0.87f, w - pad * 2, unit * 0.018f, 0xffdddddd, false);
        }
        return out;
    }

    /**
     * Kết xuất nhóm mẫu mở rộng (4..18): Dark, Centered, Gallery, Noir, Instant, Vintage,
     * Film 35, Cinema, Blur, Palette, Postcard, Editorial, Double, Date, Signature.
     */
    private static Bitmap extended(Bitmap photo, PhotoInfo info, int style) {
        int w = photo.getWidth();
        int h = photo.getHeight();
        float unit = Math.min(w, h * 1.7f);
        int pad = Math.max(1, Math.round(w * 0.05f));
        int foot = Math.max(1, Math.round(w * 0.15f));
        boolean overlay = (style == 17 || style == 18);

        if (style == 4 || style == 5) {
            pad = 0;
            foot = Math.max(1, Math.round(w * 0.14f));
        }
        if (style == 6 || style == 7) {
            pad = Math.max(1, Math.round(w * 0.09f));
        }
        if (style == 8 || style == 9) {
            foot = Math.max(1, Math.round(w * 0.23f));
        }
        if (style == 10) {
            pad = Math.max(1, Math.round(w * 0.055f));
        }
        if (style == 11) {
            pad = 0;
            foot = Math.max(1, Math.round(w * 0.12f));
        }
        if (style == 15) {
            pad = Math.max(1, Math.round(w * 0.04f));
            foot = Math.max(1, Math.round(w * 0.20f));
        }
        if (overlay) {
            pad = 0;
            foot = 0;
        }

        int top = (style == 11) ? foot : pad;
        int ow = w + pad * 2;
        int oh = h + top + pad + foot;

        boolean dark = (style == 4 || style == 7 || style == 10 || style == 11 || style == 12);
        int bg = dark ? 0xff151716 : (style == 9 || style == 14 ? 0xfff1eadb : Color.WHITE);
        int ink = dark ? 0xfff2f0e7 : 0xff252b28;
        int muted = dark ? 0xffb6bcb4 : 0xff68716a;

        Bitmap out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // Mẫu 12 (Blur): Thu nhỏ ảnh về chiều ngang 18px rồi phóng có lọc tuyến tính làm nền mờ
        if (style == 12) {
            Bitmap tiny = Bitmap.createScaledBitmap(photo, 18, Math.max(1, Math.round(18f * h / w)), true);
            p.setFilterBitmap(true);
            c.drawBitmap(tiny, null, new Rect(0, 0, ow, oh), p);
            if (tiny != photo) {
                tiny.recycle();
            }
            p.setColor(0x60000000);
            c.drawRect(0, 0, ow, oh, p);
            p.setColor(0x55000000);
            c.drawRect(pad - w * 0.008f, top - w * 0.008f, pad + w + w * 0.008f, top + h + w * 0.018f, p);
        }

        p.setAlpha(255);
        c.drawBitmap(photo, pad, top, p);

        String title = info.camera.isEmpty() ? "PHOTO" : info.camera;
        String details = info.settings();
        String bottom = join(info.date, info.author);

        // Mẫu 17 (Date) và 18 (Signature): Vẽ đè trực tiếp lên góc dưới ảnh
        if (overlay) {
            float margin = w * 0.035f;
            float base = h - unit * 0.035f;
            String text = (style == 17) ? info.date : (info.author.isEmpty() ? title : info.author);
            p.setShadowLayer(unit * 0.006f, 0, unit * 0.002f, 0xaa000000);
            line(c, p, text, margin, base, w - margin * 2,
                    unit * (style == 17 ? 0.036f : 0.028f),
                    style == 17 ? 0xffffaa54 : Color.WHITE,
                    style == 17);
            p.clearShadowLayer();
            return out;
        }

        float x = Math.max(pad, w * 0.025f);
        float area = ow - x * 2;
        float y = top + h + foot * 0.35f;
        if (style == 13) {
            area -= w * 0.25f;
        }

        if (style == 8 || style == 9) {
            // Instant / Vintage
            line(c, p, info.author.isEmpty() ? title : info.author, x, top + h + foot * 0.40f, area, w * 0.038f, ink, true);
            line(c, p, details, x, top + h + foot * 0.65f, area, w * 0.020f, muted, false);
            line(c, p, info.date, x, top + h + foot * 0.82f, area, w * 0.018f, muted, false);
        } else if (style == 14) {
            // Postcard: Có vạch ngăn dọc giữa tên máy và thông số
            p.setColor(0xffb9ad98);
            p.setStrokeWidth(Math.max(1, w * 0.001f));
            c.drawLine(ow * 0.62f, top + h + foot * 0.15f, ow * 0.62f, oh - pad * 0.3f, p);
            line(c, p, title, x, y, ow * 0.55f, w * 0.030f, ink, true);
            line(c, p, info.author, x, y + foot * 0.27f, ow * 0.55f, w * 0.019f, muted, false);
            line(c, p, details, ow * 0.65f, y, ow * 0.31f, w * 0.017f, muted, false);
            line(c, p, info.date, ow * 0.65f, y + foot * 0.27f, ow * 0.31f, w * 0.017f, muted, false);
        } else if (style == 15) {
            // Editorial: Bố cục trang tạp chí kèm đường kẻ ngang đậm
            line(c, p, "PHOTOGRAPH / " + title, x, top + h + foot * 0.30f, area, w * 0.032f, ink, true);
            p.setColor(ink);
            c.drawRect(x, top + h + foot * 0.43f, ow - x, top + h + foot * 0.436f, p);
            line(c, p, details, x, top + h + foot * 0.64f, area, w * 0.020f, muted, false);
            line(c, p, bottom, x, top + h + foot * 0.84f, area, w * 0.018f, muted, false);
        } else {
            boolean centered = (style == 5 || style == 6 || style == 7 || style == 11 || style == 12 || style == 16);
            if (centered) {
                center(c, p, title, ow * 0.5f, y, area, w * 0.027f, ink, true);
                center(c, p, details, ow * 0.5f, y + foot * 0.25f, area, w * 0.019f, muted, false);
                center(c, p, bottom, ow * 0.5f, y + foot * 0.47f, area, w * 0.017f, muted, false);
            } else {
                line(c, p, title, x, y, area, w * 0.028f, ink, true);
                line(c, p, details, x, y + foot * 0.25f, area, w * 0.020f, muted, false);
                line(c, p, bottom, x, y + foot * 0.47f, area, w * 0.017f, muted, false);
            }
        }

        // Mẫu 10 (Film 35): Vẽ hai hàng lỗ đục cuộn phim 35mm dọc hai bên viền
        if (style == 10) {
            p.setColor(0xffdad4c5);
            float holeW = pad * 0.45f;
            float holeH = w * 0.025f;
            float step = Math.max(3, holeH * 1.8f);
            for (float yy = top; yy < top + h - holeH; yy += step) {
                c.drawRoundRect(pad * 0.25f, yy, pad * 0.25f + holeW, yy + holeH, holeW * 0.2f, holeW * 0.2f, p);
                c.drawRoundRect(ow - pad * 0.70f, yy, ow - pad * 0.25f, yy + holeH, holeW * 0.2f, holeW * 0.2f, p);
            }
        }

        // Mẫu 13 (Palette): Lấy 5 mẫu màu dọc theo đường ngang giữa bức ảnh
        if (style == 13) {
            float swatch = w * 0.032f;
            float gap = w * 0.007f;
            float start = ow - pad - 5 * swatch - 4 * gap;
            for (int i = 0; i < 5; i++) {
                p.setColor(photo.getPixel(Math.min(w - 1, (i * 2 + 1) * w / 10), h / 2));
                float left = start + i * (swatch + gap);
                float topY = top + h + foot * 0.2f;
                c.drawRect(left, topY, left + swatch, topY + swatch, p);
            }
        }

        // Mẫu 16 (Double): Vẽ đường viền mảnh bao quanh mép ngoài
        if (style == 16) {
            p.setColor(0xffbebeb5);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, w * 0.001f));
            float inset = pad * 0.4f;
            c.drawRect(inset, inset, ow - inset, oh - inset, p);
            p.setStyle(Paint.Style.FILL);
        }

        return out;
    }

    /**
     * Kết xuất nhóm mẫu trang trí mới (19..30): Float, Soft, Torn, Tape, Wood, Gold,
     * Neon, Notebook, Ticket, Blueprint, Arch, Corners.
     */
    private static Bitmap decorative(Bitmap photo, PhotoInfo info, int style) {
        int w = photo.getWidth();
        int h = photo.getHeight();
        float pad = Math.max(1, w * 0.07f);
        float foot = Math.max(1, w * 0.17f);
        float top = (style == 29) ? pad * 2.2f : pad;

        int ow = Math.round(w + pad * 2);
        int oh = Math.round(h + top + pad + foot);
        boolean dark = (style == 25 || style == 28);

        int bg = (style == 23) ? 0xff9d7750
                : (style == 24) ? 0xfff2eee5
                : (style == 25) ? 0xff101122
                : (style == 28) ? 0xff153553
                : (style == 26) ? 0xfff7f3e7
                : (style == 21 || style == 22 || style == 27) ? 0xffded6c7
                : 0xffe9edf0;
        int ink = dark ? 0xffeef5ff : 0xff2e3433;
        int muted = dark ? 0xffb8cde2 : 0xff707572;

        Bitmap out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // Mẫu 23 (Wood): Vẽ các đường vân gỗ cong Bezier
        if (style == 23) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, w * 0.0015f));
            for (int i = 0; i < 24; i++) {
                float x = (i + 0.5f) * ow / 24f;
                p.setColor(i % 2 == 0 ? 0x30804524 : 0x25ffe6aa);
                Path grain = new Path();
                grain.moveTo(x, 0);
                grain.cubicTo(x + pad * 0.4f, oh * 0.33f, x - pad * 0.3f, oh * 0.66f, x, oh);
                c.drawPath(grain, p);
            }
            p.setStyle(Paint.Style.FILL);
        }

        // Mẫu 26 (Notebook) & 28 (Blueprint): Kẻ lưới ô vuông kỹ thuật
        if (style == 26 || style == 28) {
            p.setColor(style == 28 ? 0x3048c8ff : 0x284c8190);
            p.setStrokeWidth(Math.max(1, w * 0.001f));
            float step = Math.max(4, w * 0.035f);
            for (float x = 0; x < ow; x += step) {
                c.drawLine(x, 0, x, oh, p);
            }
            for (float y = 0; y < oh; y += step) {
                c.drawLine(0, y, ow, y, p);
            }
        }

        RectF paper = new RectF(pad * 0.5f, top - pad * 0.5f, w + pad * 1.5f, oh - pad * 0.45f);

        // Mẫu 19 (Float): Đổ bóng nhiều lớp mịn dưới tấm thẻ giấy
        if (style == 19) {
            for (int i = 10; i > 0; i--) {
                p.setColor(Color.argb(4, 0, 0, 0));
                float d = pad * i * 0.035f;
                c.drawRoundRect(
                        paper.left - d,
                        paper.top + pad * 0.2f - d,
                        paper.right + d,
                        paper.bottom + pad * 0.2f + d,
                        pad * 0.15f,
                        pad * 0.15f,
                        p);
            }
        }

        p.setColor(dark ? 0xff112334 : Color.WHITE);
        if (style == 21) {
            // Mẫu 21 (Torn): Mép giấy xé lượn sóng hình sin
            Path torn = new Path();
            float step = Math.max(2, w * 0.022f);
            torn.moveTo(paper.left, paper.top);
            for (float x = paper.left; x < paper.right; x += step) {
                torn.lineTo(x, paper.top + pad * 0.09f * (float) Math.sin(x * 1.3));
            }
            torn.lineTo(paper.right, paper.bottom);
            for (float x = paper.right; x > paper.left; x -= step) {
                torn.lineTo(x, paper.bottom + pad * 0.09f * (float) Math.sin(x * 0.7));
            }
            torn.close();
            c.drawPath(torn, p);
        } else if (style == 20) {
            // Mẫu 20 (Soft): Tấm giấy bo góc tròn lớn
            c.drawRoundRect(paper, pad * 0.42f, pad * 0.42f, p);
        } else if (style == 29) {
            // Mẫu 29 (Arch): Đỉnh giấy uốn vòm triển lãm
            Path arch = new Path();
            arch.moveTo(paper.left, paper.bottom);
            arch.lineTo(paper.left, top);
            arch.cubicTo(
                    paper.left, paper.top - pad * 0.8f,
                    paper.right, paper.top - pad * 0.8f,
                    paper.right, top);
            arch.lineTo(paper.right, paper.bottom);
            arch.close();
            c.drawPath(arch, p);
        } else {
            c.drawRect(paper, p);
        }

        // Mẫu 25 (Neon): Hào quang phát sáng quanh khung ảnh
        if (style == 25) {
            p.setStyle(Paint.Style.STROKE);
            for (int i = 8; i > 0; i--) {
                p.setStrokeWidth(Math.max(1, w * 0.0015f * i));
                p.setColor(Color.argb(14, 105, 220, 255));
                c.drawRect(pad - pad * 0.12f, top - pad * 0.12f, pad + w + pad * 0.12f, top + h + pad * 0.12f, p);
            }
            p.setStrokeWidth(Math.max(1, w * 0.002f));
            p.setColor(0xffb7f0ff);
            c.drawRect(pad - pad * 0.12f, top - pad * 0.12f, pad + w + pad * 0.12f, top + h + pad * 0.12f, p);
            p.setStyle(Paint.Style.FILL);
        }

        p.setAlpha(255);
        c.drawBitmap(photo, pad, top, p);

        // Mẫu 24 (Gold): Viền kim loại ánh vàng chuyển sắc
        if (style == 24) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, w * 0.008f));
            p.setShader(new LinearGradient(
                    0, 0, ow, oh,
                    new int[]{0xff9c7637, 0xffefd99b, 0xffae8742},
                    null,
                    Shader.TileMode.CLAMP));
            c.drawRect(paper, p);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
        }

        // Mẫu 22 (Tape): Hai miếng băng dính dán chéo ở hai góc trên
        if (style == 22) {
            p.setColor(0xbde1c791);
            for (int side = 0; side < 2; side++) {
                float cx = (side == 0) ? pad * 1.15f : pad + w - pad * 0.15f;
                c.save();
                c.rotate(side == 0 ? -24 : 23, cx, top);
                c.drawRect(cx - pad * 0.58f, top - pad * 0.22f, cx + pad * 0.58f, top + pad * 0.22f, p);
                c.restore();
            }
        }

        // Mẫu 30 (Corners): Bốn góc tam giác giữ ảnh kiểu album cổ điển
        if (style == 30) {
            p.setColor(0xff3d4944);
            float len = pad * 0.55f;
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    float cx = pad + x * w;
                    float cy = top + y * h;
                    float dx = (x == 0) ? 1 : -1;
                    float dy = (y == 0) ? 1 : -1;
                    Path corner = new Path();
                    corner.moveTo(cx, cy);
                    corner.lineTo(cx + dx * len, cy);
                    corner.lineTo(cx, cy + dy * len);
                    corner.close();
                    c.drawPath(corner, p);
                }
            }
        }

        // Mẫu 27 (Ticket): Đường răng cưa xé vé và hai полу-hình tròn khuyết hai bên
        if (style == 27) {
            float y = top + h + foot * 0.12f;
            p.setColor(0xffa69c8a);
            p.setStrokeWidth(Math.max(1, w * 0.002f));
            for (float x = pad; x < pad + w; x += Math.max(3, w * 0.024f)) {
                c.drawLine(x, y, Math.min(x + w * 0.012f, pad + w), y, p);
            }
            p.setColor(bg);
            c.drawCircle(paper.left, y, pad * 0.18f, p);
            c.drawCircle(paper.right, y, pad * 0.18f, p);
        }

        // Vẽ 3 dòng chữ EXIF căn giữa phía dưới cho toàn bộ nhóm 19..30
        String title = info.camera.isEmpty() ? "PHOTO" : info.camera;
        float baseline = top + h + foot * 0.39f;
        float area = w;
        center(c, p, title, pad + w * 0.5f, baseline, area, w * 0.028f, ink, true);
        center(c, p, info.settings(), pad + w * 0.5f, baseline + foot * 0.24f, area, w * 0.019f, muted, false);
        center(c, p, join(info.date, info.author), pad + w * 0.5f, baseline + foot * 0.47f, area, w * 0.017f, muted, false);
        return out;
    }

    /** Vẽ một dòng chữ căn giữa theo tọa độ ngang {@code cx}. */
    private static void center(Canvas c, Paint p, String text, float cx, float baseline, float width, float size, int color, boolean bold) {
        p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        p.setTextSize(Math.max(1f, size));
        float measured = Math.min(width, p.measureText(text));
        line(c, p, text, cx - measured / 2f, baseline, width, size, color, bold);
    }

    private static String join(String a, String b) {
        if (a == null || a.isEmpty()) return b == null ? "" : b;
        if (b == null || b.isEmpty()) return a;
        return a + "  ·  " + b;
    }

    /** Vẽ một dòng chữ có tự động thu nhỏ cỡ chữ và cắt dấu ba chấm nếu quá dài. */
    private static void line(Canvas c, Paint p, String text, float x, float baseline, float width, float size, int color, boolean bold) {
        if (text == null || text.isEmpty() || width <= 0) {
            return;
        }
        p.setColor(color);
        p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        p.setTextSize(Math.max(1f, size));
        float measured = p.measureText(text);
        if (measured > width) {
            p.setTextSize(Math.max(size * 0.65f, size * width / measured));
        }
        TextPaint tp = new TextPaint(p);
        String fitted = TextUtils.ellipsize(text, tp, width, TextUtils.TruncateAt.END).toString();
        c.drawText(fitted, x, baseline, p);
    }
}
