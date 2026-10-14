package vn.iro.editor;

import org.json.JSONException;
import org.json.JSONObject;
import java.lang.reflect.Field;

/**
 * Bộ mã hóa / giải mã JSON cho {@link Settings}.
 * Tương đương với phần triển khai {@code Codable} và {@code validated()} trong {@code ios/IRO/Core/Settings.swift}.
 *
 * <p>Đảm bảo mọi tệp công thức JSON được nhập vào hoặc đọc từ {@code SharedPreferences} đều nằm trong
 * giới hạn an toàn trước khi đưa vào {@link ColorEngine}.
 */
public final class SettingsCodec {

    private SettingsCodec() {
    }

    /**
     * Chuyển đổi đối tượng {@link Settings} thành {@link JSONObject} để lưu phiên làm việc hoặc xuất file JSON.
     */
    public static JSONObject encode(Settings settings) throws Exception {
        JSONObject json = new JSONObject();
        for (Field field : Settings.class.getFields()) {
            json.put(field.getName(), field.get(settings));
        }
        return json;
    }

    /**
     * Đọc và kiểm tra tính hợp lệ của {@link Settings} từ {@link JSONObject}.
     * Nếu có bất kỳ thông số nào nằm ngoài dải cho phép (ví dụ exposure > 3 hoặc dr != 100/200/400),
     * phương thức sẽ ném {@link JSONException} để chặn dữ liệu hỏng.
     */
    public static Settings decode(JSONObject json) throws Exception {
        Settings settings = new Settings();
        for (Field field : Settings.class.getFields()) {
            String key = field.getName();
            if (!json.has(key)) {
                continue;
            }

            // Kiểm tra các trường chuỗi (film, wb) không vượt quá 300 ký tự UTF-16
            if (field.getType() == String.class) {
                String value = json.getString(key);
                if (value.length() > 300) {
                    throw new JSONException("Giá trị quá dài: " + key);
                }
                field.set(settings, value);
                continue;
            }

            // Kiểm tra biên cho các trường số thực và số nguyên
            double value = json.getDouble(key);
            double low = -4.0;
            double high = 4.0;

            switch (key) {
                case "exposure":
                    low = -3.0;
                    high = 3.0;
                    break;
                case "red":
                case "blue":
                    low = -9.0;
                    high = 9.0;
                    break;
                case "temperature":
                    low = -100.0;
                    high = 100.0;
                    break;
                case "highlight":
                case "shadow":
                    low = -2.0;
                    high = 4.0;
                    break;
                case "clarity":
                    low = -5.0;
                    high = 5.0;
                    break;
                case "lutAmount":
                    low = 0.0;
                    high = 100.0;
                    break;
                case "grain":
                    low = 0.0;
                    high = 30.0;
                    break;
                case "grainSize":
                    low = 1.0;
                    high = 2.0;
                    break;
                case "colorChrome":
                case "chromeBlue":
                    low = 0.0;
                    high = 2.0;
                    break;
                case "proMist":
                case "halation":
                case "bleachBypass":
                case "lightLeak":
                case "skinProtect":
                case "swirlyBokeh":
                case "depthFog":
                    low = 0.0;
                    high = 100.0;
                    break;
                case "dehaze":
                case "texture":
                    low = -100.0;
                    high = 100.0;
                    break;
                case "dr":
                    if (value != 100 && value != 200 && value != 400) {
                        throw new JSONException("DR không hợp lệ: " + value);
                    }
                    low = 100.0;
                    high = 400.0;
                    break;
                default:
                    // color, sharpness, nr đều có biên [-4, 4]
                    break;
            }

            if (!Double.isFinite(value) || value < low || value > high) {
                throw new JSONException("Ngoài phạm vi: " + key + " (" + value + ")");
            }

            if (field.getType() == int.class) {
                field.setInt(settings, (int) value);
            } else {
                field.setDouble(settings, value);
            }
        }
        return settings;
    }
}
