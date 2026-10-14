package vn.iro.editor;

import java.util.Locale;
import java.util.Objects;

/**
 * Cấu trúc lưu trữ toàn bộ thông số chỉnh màu hậu kỳ trên ảnh sRGB.
 * Chuyển đổi trực tiếp từ file {@code ios/IRO/Core/Settings.swift}.
 *
 * <p>Lưu ý quan trọng khi debug:
 * <ul>
 *     <li>Tên {@link #film} chỉ đóng vai trò khóa liên kết (metadata) để tìm file LUT 3D {@code .cube} tương ứng.</li>
 *     <li>Nếu chưa gắn LUT cho {@link #film}, bộ xử lý sẽ giữ nguyên màu gốc của ảnh và chỉ áp dụng các thanh chỉnh hậu kỳ.</li>
 *     <li>Mọi thông số đều có biên hợp lệ rõ ràng được kiểm tra trong {@link #validate()}.</li>
 * </ul>
 */
public final class Settings {

    /** Bù phơi sáng (EV), phạm vi [-3.0, +3.0], tính toán trong không gian ánh sáng tuyến tính (linear light). */
    public double exposure = 0.0;

    /** Độ sáng vùng sáng (Highlight tone), phạm vi [-2.0, +4.0]. Giá trị âm nén vùng cháy sáng. */
    public double highlight = 0.0;

    /** Độ tối vùng tối (Shadow tone), phạm vi [-2.0, +4.0]. Giá trị âm nâng vùng tối, giá trị dương làm sâu vùng tối. */
    public double shadow = 0.0;

    /** Độ bão hòa / độ đậm màu (Color / Saturation), phạm vi [-4.0, +4.0]. */
    public double color = 0.0;

    /** Dịch chuyển cân bằng trắng kênh Đỏ (WB Shift Red), phạm vi [-9.0, +9.0]. */
    public double red = 0.0;

    /** Dịch chuyển cân bằng trắng kênh Xanh dương (WB Shift Blue), phạm vi [-9.0, +9.0]. */
    public double blue = 0.0;

    /** Nhiệt độ màu tương đối (Ấm / Lạnh), phạm vi [-100.0, +100.0]. Dương = ấm hơn, Âm = lạnh hơn. */
    public double temperature = 0.0;

    /** Độ sắc nét (Sharpness), phạm vi [-4.0, +4.0]. Thực hiện ở bước lọc không gian 4 lân cận. */
    public double sharpness = 0.0;

    /** Khử nhiễu (Noise Reduction - High ISO NR), phạm vi [-4.0, +4.0]. Giá trị -4.0 tương ứng tắt làm mịn. */
    public double nr = -4.0;

    /** Cường độ hạt phim (Grain Effect), phạm vi [0.0, 30.0]. Trong recipe: Weak ≈ 8, Strong ≈ 18. */
    public double grain = 0.0;

    /** Kích thước hạt phim (Grain Size), phạm vi [1.0, 2.0]. Small = 1, Large = 2. */
    public double grainSize = 1.0;

    /** Độ trong / tương phản cục bộ (Clarity), phạm vi [-5.0, +5.0], bán kính lấy mẫu 4 pixel. */
    public double clarity = 0.0;

    /** Hiệu ứng Color Chrome (làm sâu các màu có độ bão hòa cao), phạm vi [0.0, 2.0] (Off=0, Weak=1, Strong=2). */
    public double colorChrome = 0.0;

    /** Hiệu ứng Color Chrome FX Blue (làm sâu riêng vùng màu xanh dương/bầu trời), phạm vi [0.0, 2.0]. */
    public double chromeBlue = 0.0;

    /** Kính lọc khuếch tán quang học Black Pro-Mist, phạm vi [0.0, 100.0]. */
    public double proMist = 0.0;

    /** Quầng sáng đỏ quanh vùng cháy sáng (Halation CineStill 800T), phạm vi [0.0, 100.0]. */
    public double halation = 0.0;

    /** Hiệu ứng giữ bạc phòng tối (Bleach Bypass), phạm vi [0.0, 100.0]. */
    public double bleachBypass = 0.0;

    /** Vệt lọt sáng phim 35mm (Vintage Light Leak), phạm vi [0.0, 100.0]. */
    public double lightLeak = 0.0;

    /** Mặt nạ bảo vệ tông màu da người mẫu (Skin Protection Mask), phạm vi [0.0, 100.0]. */
    public double skinProtect = 0.0;

    /** Khử mù sương (Dehaze) theo thuật toán Dark Channel Prior, phạm vi [-100.0, +100.0]. */
    public double dehaze = 0.0;

    /** Tách tần số chi tiết vi mô / mịn da (Texture), phạm vi [-100.0, +100.0]. */
    public double texture = 0.0;

    /** Hiệu ứng Bokeh xoáy ống kính cổ Helios 44-2 58mm f/2 (Swirly Bokeh), phạm vi [0.0, 100.0]. */
    public double swirlyBokeh = 0.0;

    /** Sương mù theo chiều sâu hậu cảnh 3D (3D Depth Fog), phạm vi [0.0, 100.0]. */
    public double depthFog = 0.0;

    /** Cường độ pha trộn LUT 3D (%), phạm vi [0.0, 100.0]. Mặc định 100%. */
    public double lutAmount = 100.0;

    /** Dải tương phản động (Dynamic Range nén vùng sáng hậu kỳ): chỉ nhận giá trị 100, 200 hoặc 400. */
    public int dr = 100;

    /** Tên giả lập màu phim (Film Simulation), dùng để ánh xạ với LUT đã lưu của người dùng. */
    public String film = "Original";

    /** Chuỗi mô tả cân bằng trắng gốc trong công thức (ví dụ: Auto, Daylight, 5200K...). */
    public String wb = "As shot";

    /**
     * Khởi tạo bộ thông số mặc định (trạng thái nguyên bản, không biến đổi pixel).
     */
    public Settings() {
    }

    /**
     * Tạo một bản sao độc lập (deep copy) của đối tượng {@link Settings}.
     * Rất quan trọng khi gửi thông số sang luồng nền (worker thread) hoặc lưu vào ngăn xếp Undo/Redo.
     */
    public Settings copy() {
        Settings s = new Settings();
        s.exposure = this.exposure;
        s.highlight = this.highlight;
        s.shadow = this.shadow;
        s.color = this.color;
        s.red = this.red;
        s.blue = this.blue;
        s.temperature = this.temperature;
        s.sharpness = this.sharpness;
        s.nr = this.nr;
        s.grain = this.grain;
        s.grainSize = this.grainSize;
        s.clarity = this.clarity;
        s.colorChrome = this.colorChrome;
        s.chromeBlue = this.chromeBlue;
        s.proMist = this.proMist;
        s.halation = this.halation;
        s.bleachBypass = this.bleachBypass;
        s.lightLeak = this.lightLeak;
        s.skinProtect = this.skinProtect;
        s.dehaze = this.dehaze;
        s.texture = this.texture;
        s.swirlyBokeh = this.swirlyBokeh;
        s.depthFog = this.depthFog;
        s.lutAmount = this.lutAmount;
        s.dr = this.dr;
        s.film = this.film;
        s.wb = this.wb;
        return s;
    }

    /**
     * Kiểm tra tính hợp lệ của toàn bộ thông số (tương đương {@code Settings.validated()} bên iOS).
     *
     * @return Chính đối tượng này nếu hợp lệ.
     * @throws IllegalArgumentException Nếu có bất kỳ trường nào vượt biên hoặc không hữu hạn (NaN/Infinity).
     */
    public Settings validate() {
        checkRange("exposure", exposure, -3.0, 3.0);
        checkRange("highlight", highlight, -2.0, 4.0);
        checkRange("shadow", shadow, -2.0, 4.0);
        checkRange("color", color, -4.0, 4.0);
        checkRange("red", red, -9.0, 9.0);
        checkRange("blue", blue, -9.0, 9.0);
        checkRange("temperature", temperature, -100.0, 100.0);
        checkRange("sharpness", sharpness, -4.0, 4.0);
        checkRange("nr", nr, -4.0, 4.0);
        checkRange("grain", grain, 0.0, 30.0);
        checkRange("grainSize", grainSize, 1.0, 2.0);
        checkRange("clarity", clarity, -5.0, 5.0);
        checkRange("colorChrome", colorChrome, 0.0, 2.0);
        checkRange("chromeBlue", chromeBlue, 0.0, 2.0);
        checkRange("proMist", proMist, 0.0, 100.0);
        checkRange("halation", halation, 0.0, 100.0);
        checkRange("bleachBypass", bleachBypass, 0.0, 100.0);
        checkRange("lightLeak", lightLeak, 0.0, 100.0);
        checkRange("skinProtect", skinProtect, 0.0, 100.0);
        checkRange("dehaze", dehaze, -100.0, 100.0);
        checkRange("texture", texture, -100.0, 100.0);
        checkRange("swirlyBokeh", swirlyBokeh, 0.0, 100.0);
        checkRange("depthFog", depthFog, 0.0, 100.0);
        checkRange("lutAmount", lutAmount, 0.0, 100.0);

        if (dr != 100 && dr != 200 && dr != 400) {
            throw new IllegalArgumentException("DR không hợp lệ: " + dr);
        }
        if (film == null || wb == null || film.length() > 300 || wb.length() > 300) {
            throw new IllegalArgumentException("Giá trị Film hoặc WB quá dài hoặc rỗng");
        }
        return this;
    }

    private static void checkRange(String name, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Ngoài phạm vi: " + name + " = " + value);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Settings)) return false;
        Settings that = (Settings) o;
        return Double.compare(that.exposure, exposure) == 0
                && Double.compare(that.highlight, highlight) == 0
                && Double.compare(that.shadow, shadow) == 0
                && Double.compare(that.color, color) == 0
                && Double.compare(that.red, red) == 0
                && Double.compare(that.blue, blue) == 0
                && Double.compare(that.temperature, temperature) == 0
                && Double.compare(that.sharpness, sharpness) == 0
                && Double.compare(that.nr, nr) == 0
                && Double.compare(that.grain, grain) == 0
                && Double.compare(that.grainSize, grainSize) == 0
                && Double.compare(that.clarity, clarity) == 0
                && Double.compare(that.colorChrome, colorChrome) == 0
                && Double.compare(that.chromeBlue, chromeBlue) == 0
                && Double.compare(that.proMist, proMist) == 0
                && Double.compare(that.halation, halation) == 0
                && Double.compare(that.bleachBypass, bleachBypass) == 0
                && Double.compare(that.lightLeak, lightLeak) == 0
                && Double.compare(that.skinProtect, skinProtect) == 0
                && Double.compare(that.dehaze, dehaze) == 0
                && Double.compare(that.texture, texture) == 0
                && Double.compare(that.swirlyBokeh, swirlyBokeh) == 0
                && Double.compare(that.depthFog, depthFog) == 0
                && Double.compare(that.lutAmount, lutAmount) == 0
                && dr == that.dr
                && Objects.equals(film, that.film)
                && Objects.equals(wb, that.wb);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exposure, highlight, shadow, color, red, blue, temperature,
                sharpness, nr, grain, grainSize, clarity, colorChrome, chromeBlue,
                proMist, halation, bleachBypass, lightLeak, skinProtect, dehaze, texture,
                swirlyBokeh, depthFog, lutAmount, dr, film, wb);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "Settings[film=%s, ev=%.2f, hi=%.1f, sh=%.1f, col=%.1f, temp=%.0f, mist=%.0f, hal=%.0f, dehaze=%.0f, tex=%.0f, dr=%d]",
                film, exposure, highlight, shadow, color, temperature, proMist, halation, dehaze, texture, dr);
    }
}
