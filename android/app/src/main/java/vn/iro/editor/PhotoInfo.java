package vn.iro.editor;

import android.content.ContentResolver;
import android.media.ExifInterface;
import android.net.Uri;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;

/**
 * Cấu trúc thông tin siêu dữ liệu (EXIF Metadata) của bức ảnh để hiển thị trên 30 mẫu khung.
 * Chuyển đổi trực tiếp từ {@code ios/IRO/Imaging/PhotoInfo.swift} và {@code PhotoIO.readMetadata}.
 *
 * <p>Nguyên tắc bảo toàn tính xác thực:
 * <ul>
 *     <li>Chỉ đọc thông tin EXIF thực tế có trong file ảnh gốc hoặc nội dung người dùng tự sửa.</li>
 *     <li>Nếu ảnh không có EXIF (như ảnh chụp màn hình, ảnh tải qua mạng xã hội), các trường được để trống,
 *         tuyệt đối không tự điền tên điện thoại đang chạy app hay thông số giả từ Recipe.</li>
 *     <li>Ưu tiên tiêu cự quy đổi 35mm ({@code TAG_FOCAL_LENGTH_IN_35MM_FILM}) và ghi chú {@code (eq.)}.</li>
 * </ul>
 */
public final class PhotoInfo {

    /** Tên hãng và dòng máy ảnh / điện thoại (ví dụ: {@code FUJIFILM X-T5}). */
    public String camera = "";

    /** Tiêu cự ống kính (ví dụ: {@code 35 mm (eq.)}). */
    public String focal = "";

    /** Khẩu độ ống kính (ví dụ: {@code f/2.8}). */
    public String aperture = "";

    /** Tốc độ màn trập / thời gian phơi sáng (ví dụ: {@code 1/250 s}). */
    public String shutter = "";

    /** Độ nhạy sáng ISO lúc chụp (ví dụ: {@code ISO 400}). */
    public String iso = "";

    /** Ngày giờ chụp định dạng {@code YYYY.MM.DD HH:mm:ss}. */
    public String date = "";

    /** Tên tác giả / chữ ký cá nhân do người dùng nhập hoặc đọc từ thẻ Artist. */
    public String author = "";

    /**
     * Tạo bản sao độc lập của {@link PhotoInfo} để phục vụ chỉnh sửa nháp (draft) hoặc lưu vào UndoStack.
     */
    public PhotoInfo copy() {
        PhotoInfo i = new PhotoInfo();
        i.camera = this.camera;
        i.focal = this.focal;
        i.aperture = this.aperture;
        i.shutter = this.shutter;
        i.iso = this.iso;
        i.date = this.date;
        i.author = this.author;
        return i;
    }

    /**
     * Làm sạch toàn bộ các trường văn bản (loại bỏ ký tự điều khiển, cắt tối đa 100 ký tự).
     * Tương đương hàm {@code PhotoInfo.sanitized()} trên iOS.
     */
    public PhotoInfo sanitized() {
        PhotoInfo i = new PhotoInfo();
        i.camera = clean(this.camera);
        i.focal = clean(this.focal);
        i.aperture = clean(this.aperture);
        i.shutter = clean(this.shutter);
        i.iso = clean(this.iso);
        i.date = clean(this.date);
        i.author = clean(this.author);
        return i;
    }

    /**
     * Ghép chuỗi thông số phơi sáng {@code "Tiêu cự · Khẩu độ · Tốc độ · ISO"} để vẽ lên khung ảnh.
     */
    public String settings() {
        StringBuilder out = new StringBuilder();
        for (String value : new String[]{focal, aperture, shutter, iso}) {
            if (value != null && !value.trim().isEmpty()) {
                if (out.length() > 0) {
                    out.append("  ·  ");
                }
                out.append(value.trim());
            }
        }
        return out.toString();
    }

    /**
     * Chuyển đổi sang {@link JSONObject} để lưu vào phiên làm việc.
     */
    public JSONObject json() throws Exception {
        JSONObject j = new JSONObject();
        j.put("camera", camera);
        j.put("focal", focal);
        j.put("aperture", aperture);
        j.put("shutter", shutter);
        j.put("iso", iso);
        j.put("date", date);
        j.put("author", author);
        return j;
    }

    /**
     * Khôi phục {@link PhotoInfo} từ {@link JSONObject}.
     */
    public static PhotoInfo from(JSONObject j) {
        PhotoInfo i = new PhotoInfo();
        if (j == null) {
            return i;
        }
        i.camera = clean(j.optString("camera"));
        i.focal = clean(j.optString("focal"));
        i.aperture = clean(j.optString("aperture"));
        i.shutter = clean(j.optString("shutter"));
        i.iso = clean(j.optString("iso"));
        i.date = clean(j.optString("date"));
        i.author = clean(j.optString("author"));
        return i;
    }

    /**
     * Loại bỏ ký tự điều khiển / xuống dòng và giới hạn độ dài chuỗi tối đa 100 ký tự.
     */
    public static String clean(String value) {
        if (value == null) {
            return "";
        }
        value = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return value.substring(0, Math.min(100, value.length()));
    }

    private static String attr(ExifInterface exif, String tag) {
        String val = exif.getAttribute(tag);
        return val == null ? "" : clean(val);
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value).replaceFirst("\\.0$", "");
    }

    /**
     * Đọc siêu dữ liệu EXIF từ {@link Uri} ảnh nguồn.
     */
    public static PhotoInfo read(ContentResolver resolver, Uri source) {
        try (InputStream stream = resolver.openInputStream(source)) {
            return stream == null ? new PhotoInfo() : fromExif(new ExifInterface(stream));
        } catch (Exception ignored) {
            return new PhotoInfo();
        }
    }

    /**
     * Trích xuất thông tin máy ảnh, ống kính, thông số chụp và ngày chụp từ {@link ExifInterface}.
     * Giữ định dạng chuỗi đầu ra khớp hoàn toàn với {@code PhotoIO.readMetadata} trên iOS.
     */
    public static PhotoInfo fromExif(ExifInterface exif) {
        PhotoInfo info = new PhotoInfo();
        try {
            String make = attr(exif, ExifInterface.TAG_MAKE);
            String model = attr(exif, ExifInterface.TAG_MODEL);
            info.camera = model.toLowerCase(Locale.ROOT).startsWith(make.toLowerCase(Locale.ROOT))
                    ? model
                    : (make + " " + model).trim();

            double focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0.0);
            boolean equivalent = focal > 0.0;
            if (!equivalent) {
                focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0);
            }
            if (focal > 0.0 && Double.isFinite(focal)) {
                info.focal = number(focal) + " mm" + (equivalent ? " (eq.)" : "");
            }

            double aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0);
            if (aperture > 0.0 && Double.isFinite(aperture)) {
                info.aperture = "f/" + number(aperture);
            }

            double time = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0);
            if (time > 0.0 && Double.isFinite(time)) {
                info.shutter = time < 0.5
                        ? "1/" + Math.round(1.0 / time) + " s"
                        : number(time) + " s";
            }

            String iso = attr(exif, ExifInterface.TAG_ISO_SPEED_RATINGS);
            if (!iso.isEmpty() && !iso.equals("0")) {
                info.iso = "ISO " + iso;
            }

            info.date = attr(exif, ExifInterface.TAG_DATETIME_ORIGINAL);
            if (info.date.isEmpty()) {
                info.date = attr(exif, ExifInterface.TAG_DATETIME);
            }
            if (info.date.length() >= 10) {
                info.date = info.date.substring(0, 10).replace(':', '.') + info.date.substring(10);
            }

            String artist = attr(exif, ExifInterface.TAG_ARTIST);
            if (!artist.isEmpty()) {
                info.author = artist;
            }
        } catch (Exception ignored) {
            // Ảnh thiếu EXIF là bình thường với ảnh chụp màn hình hoặc ảnh chia sẻ qua mạng.
        }
        return info;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PhotoInfo)) return false;
        PhotoInfo that = (PhotoInfo) o;
        return Objects.equals(camera, that.camera)
                && Objects.equals(focal, that.focal)
                && Objects.equals(aperture, that.aperture)
                && Objects.equals(shutter, that.shutter)
                && Objects.equals(iso, that.iso)
                && Objects.equals(date, that.date)
                && Objects.equals(author, that.author);
    }

    @Override
    public int hashCode() {
        return Objects.hash(camera, focal, aperture, shutter, iso, date, author);
    }
}
