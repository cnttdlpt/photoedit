package vn.iro.editor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mô hình dữ liệu Công thức màu phim (Recipe) và bộ ánh xạ sang thông số hậu kỳ {@link Settings}.
 * Chuyển đổi trực tiếp từ file {@code ios/IRO/Core/Recipe.swift}.
 *
 * <p>Nguyên tắc thiết kế (giống bản iOS):
 * <ul>
 *     <li>Giữ nguyên toàn bộ chuỗi thông số gốc trong {@link #raw} (bao gồm cả ISO, khoảng EV, WB Kelvin...).</li>
 *     <li>Khi áp dụng công thức qua {@link #adjustments(List)}, chỉ những thông số có thể mô phỏng hậu kỳ
 *         trên ảnh sRGB mới được điền vào {@link Settings}; các thông số mang tính tham khảo lúc chụp
 *         được ghi rõ vào danh sách {@code notes} để người dùng theo dõi và debug.</li>
 * </ul>
 */
public final class Recipe {

    public String id;
    public String name;
    public String film;
    public String sourceUrl;
    /** Đường dẫn ảnh mẫu đóng gói sẵn trong {@code assets/fujixweekly/} để hiển thị tức thì khi offline. */
    public String sampleAsset;
    /** Đường dẫn ảnh mẫu chính trực tiếp từ bài viết trên {@code fujixweekly.com}. */
    public String sampleImageUrl;
    /** Danh sách các ảnh mẫu chụp bằng công thức này trên {@code fujixweekly.com}. */
    public final List<String> sampleImages = new ArrayList<>();
    /** Chuỗi cảm biến ghép bằng {@code " / "} để hiển thị và lọc nhanh. */
    public String sensors;
    public boolean reviewRequired;
    public final List<String> missingFields = new ArrayList<>();
    /** Toàn bộ cặp khóa-giá trị thông số gốc từ bài viết Fuji X Weekly. */
    public final LinkedHashMap<String, String> raw = new LinkedHashMap<>();

    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[+−–-]?\\d+(?:\\.\\d+)?(?:/\\d+)?");
    private static final Pattern RED_SHIFT_PATTERN = Pattern.compile("([+−–-]?\\d+)\\s*Red", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLUE_SHIFT_PATTERN = Pattern.compile("([+−–-]?\\d+)\\s*Blue", Pattern.CASE_INSENSITIVE);

    /**
     * Khởi tạo {@link Recipe} từ đối tượng JSON trong {@code assets/recipes.json} hoặc file JSON người dùng nhập.
     */
    public Recipe(JSONObject json) throws JSONException {
        this.id = json.optString("id", "custom");
        this.name = json.optString("name", "Recipe tùy chỉnh");
        this.film = json.optString("film", "Original");
        this.sourceUrl = json.optString("sourceUrl", "");
        this.sampleAsset = json.optString("sampleAsset", "");
        this.sampleImageUrl = json.optString("sampleImageUrl", "");
        this.reviewRequired = json.optBoolean("reviewRequired", false);

        JSONArray sampleArray = json.optJSONArray("sampleImages");
        if (sampleArray != null) {
            for (int i = 0; i < sampleArray.length(); i++) {
                String u = sampleArray.optString(i, "");
                if (!u.isEmpty()) {
                    this.sampleImages.add(u);
                }
            }
        }
        if (this.sampleImages.isEmpty() && !this.sampleImageUrl.isEmpty()) {
            this.sampleImages.add(this.sampleImageUrl);
        }

        // Đọc danh sách cảm biến (sensors)
        JSONArray sensorArray = json.optJSONArray("sensors");
        StringBuilder sensorBuilder = new StringBuilder();
        if (sensorArray != null) {
            for (int i = 0; i < sensorArray.length(); i++) {
                if (i > 0) {
                    sensorBuilder.append(" / ");
                }
                sensorBuilder.append(sensorArray.optString(i));
            }
        }
        this.sensors = sensorBuilder.toString();

        // Đọc danh sách các trường bị thiếu trong bài gốc (nếu có)
        JSONArray missingArray = json.optJSONArray("missingFields");
        if (missingArray != null) {
            for (int i = 0; i < missingArray.length(); i++) {
                this.missingFields.add(missingArray.optString(i));
            }
        }

        // Đọc và kiểm tra tính hợp lệ của rawSettings (giới hạn tối đa 60 trường giống iOS)
        JSONObject rawObject = json.getJSONObject("rawSettings");
        if (rawObject.length() > 60) {
            throw new JSONException("Recipe có quá nhiều trường (tối đa 60)");
        }
        if (this.film.length() > 300) {
            throw new JSONException("Giá trị Film quá dài");
        }

        java.util.Iterator<String> keys = rawObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = rawObject.getString(key);
            if (key.length() > 80 || value.length() > 500) {
                throw new JSONException("Thông số quá dài: " + key);
            }
            this.raw.put(key, value);
        }

        // Kiểm tra thử khả năng ánh xạ sang Settings ngay lúc nạp (như Recipe.validate() trên iOS)
        try {
            adjustments(new ArrayList<>()).validate();
        } catch (IllegalArgumentException e) {
            throw new JSONException(e.getMessage());
        }
    }

    /**
     * Xuất {@link Recipe} ra {@link JSONObject} tương thích hoàn toàn giữa iOS và Android.
     */
    public JSONObject json() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("film", film);
        json.put("sourceUrl", sourceUrl);
        if (sampleAsset != null && !sampleAsset.isEmpty()) {
            json.put("sampleAsset", sampleAsset);
        }
        if (sampleImageUrl != null && !sampleImageUrl.isEmpty()) {
            json.put("sampleImageUrl", sampleImageUrl);
        }
        if (!sampleImages.isEmpty()) {
            json.put("sampleImages", new JSONArray(sampleImages));
        }
        json.put("sensors", new JSONArray(sensors.isEmpty() ? new String[0] : sensors.split(" / ")));
        json.put("rawSettings", new JSONObject(raw));
        json.put("reviewRequired", reviewRequired);
        if (!missingFields.isEmpty()) {
            json.put("missingFields", new JSONArray(missingFields));
        }
        return json;
    }

    /**
     * Tạo chuỗi mô tả thông số nguồn (tương đương {@code sourceDescription} trong {@code Recipe.swift}).
     */
    public String description() {
        StringBuilder builder = new StringBuilder();
        builder.append(name).append("\n")
                .append(sensors).append("\n\n")
                .append("Film Simulation: ").append(film).append("\n");
        for (String key : new TreeSet<>(raw.keySet())) {
            builder.append(key).append(": ").append(raw.get(key)).append('\n');
        }
        return builder.toString();
    }

    /**
     * Trích xuất giá trị số đầu tiên từ chuỗi thông số (hỗ trợ cả phân số như {@code +2/3}, {@code -1/3}
     * và các dấu trừ Unicode {@code −}, {@code –}).
     *
     * @param text     Chuỗi nguồn (ví dụ: {@code "+1.5"}, {@code "-2/3 EV"}, {@code "-1 to -2"}).
     * @param fallback Giá trị trả về nếu không tìm thấy số hợp lệ.
     */
    public static double number(String text, double fallback) {
        if (text == null) {
            return fallback;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(text.trim());
        if (!matcher.find()) {
            return fallback;
        }
        try {
            String matched = matcher.group()
                    .replace('−', '-')
                    .replace('–', '-');
            String[] parts = matched.split("/");
            double numerator = Double.parseDouble(parts[0]);
            if (parts.length == 2) {
                double denominator = Double.parseDouble(parts[1]);
                return denominator != 0 ? numerator / denominator : fallback;
            }
            return numerator;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double bound(double value, double low, double high) {
        return Double.isFinite(value) ? Math.max(low, Math.min(high, value)) : low;
    }

    /**
     * Ánh xạ các thông số của công thức sang đối tượng {@link Settings} để {@link ColorEngine} xử lý,
     * đồng thời ghi lại các chú thích giải thích vào {@code notes}.
     *
     * <p>Duyệt các khóa theo thứ tự bảng chữ cái ({@link TreeSet}) giống {@code rawSettings.keys.sorted()}
     * trên iOS để kết quả ghi chú luôn nhất quán khi debug và kiểm thử.
     */
    public Settings adjustments(List<String> notes) {
        Settings settings = new Settings();
        settings.film = this.film;

        for (String originalKey : new TreeSet<>(raw.keySet())) {
            String value = raw.get(originalKey);
            if (value == null) {
                continue;
            }
            String keyLower = originalKey.toLowerCase(Locale.ROOT);
            String valLower = value.toLowerCase(Locale.ROOT);
            double num = number(value, 0.0);

            // Cảnh báo nếu giá trị trong công thức gốc là một khoảng lựa chọn (ví dụ "+1/3 to +1")
            if (valLower.matches(".*(?:\\bto\\b|\\bor\\b|sometimes|depending|usually|occasionally).*")) {
                if (notes != null) {
                    notes.add(originalKey + ": có tùy chọn/range; xem giá trị nguồn.");
                }
            }

            switch (keyLower) {
                case "film simulation":
                    settings.film = value;
                    break;
                case "highlight":
                case "highlights":
                case "highlight tone":
                    settings.highlight = bound(num, -2.0, 4.0);
                    break;
                case "shadow":
                case "shadows":
                case "shadow tone":
                    settings.shadow = bound(num, -2.0, 4.0);
                    break;
                case "highlight & shadow":
                    settings.highlight = bound(num, -2.0, 4.0);
                    settings.shadow = bound(num, -2.0, 4.0);
                    break;
                case "color":
                case "saturation":
                    settings.color = bound(num, -4.0, 4.0);
                    break;
                case "sharpness":
                case "sharpeness":
                case "sharpening":
                    settings.sharpness = bound(num, -4.0, 4.0);
                    break;
                case "noise reduction":
                case "noise-reduction":
                case "high iso nr":
                    settings.nr = bound(num, -4.0, 4.0);
                    break;
                case "clarity":
                    settings.clarity = bound(num, -5.0, 5.0);
                    break;
                case "grain":
                case "grain effect":
                    settings.grain = valLower.contains("strong") ? 18.0 : (valLower.contains("weak") ? 8.0 : 0.0);
                    settings.grainSize = valLower.contains("large") ? 2.0 : 1.0;
                    break;
                case "color chrome":
                case "color chrome effect":
                    settings.colorChrome = valLower.contains("strong") ? 2.0 : (valLower.contains("weak") ? 1.0 : 0.0);
                    break;
                case "color chrome fx blue":
                case "color chrome effect blue":
                    settings.chromeBlue = valLower.contains("strong") ? 2.0 : (valLower.contains("weak") ? 1.0 : 0.0);
                    break;
                case "dynamic range":
                case "dr":
                    settings.dr = valLower.contains("400") ? 400 : (valLower.contains("200") ? 200 : 100);
                    if (valLower.contains("auto") && notes != null) {
                        notes.add("DR Auto: giữ DR100 trong editor, không giả lập quyết định đo sáng của máy.");
                    }
                    break;
                case "white balance":
                case "wb":
                    settings.wb = value.split(",")[0];
                    Matcher redMatcher = RED_SHIFT_PATTERN.matcher(value);
                    Matcher blueMatcher = BLUE_SHIFT_PATTERN.matcher(value);
                    if (redMatcher.find()) {
                        settings.red = bound(number(redMatcher.group(1), 0.0), -9.0, 9.0);
                    }
                    if (blueMatcher.find()) {
                        settings.blue = bound(number(blueMatcher.group(1), 0.0), -9.0, 9.0);
                    }
                    if (notes != null) {
                        notes.add("WB " + settings.wb + ": giữ WB gốc của ảnh; chỉ áp dụng Red/Blue. Chỉnh Nhiệt độ nếu cần.");
                    }
                    break;
                case "exposure compensation":
                case "exposure":
                    if (valLower.contains(" to ") || valLower.contains(" or ")) {
                        settings.exposure = 0.0;
                        if (notes != null) {
                            notes.add("Bù sáng là một khoảng: tự chọn EV, không tự lấy đầu khoảng.");
                        }
                    } else {
                        settings.exposure = bound(num, -3.0, 3.0);
                    }
                    break;
                case "iso":
                    if (notes != null) {
                        notes.add("ISO là thông số lúc chụp, không áp dụng lại lên JPEG.");
                    }
                    break;
                default:
                    if (notes != null) {
                        notes.add(originalKey + ": giữ để tham khảo, bộ xử lý chưa áp dụng.");
                    }
                    break;
            }
        }

        DebugLogger.d(DebugLogger.TAG_RECIPE, "Áp dụng Recipe '" + name + "' -> " + settings);
        return settings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Recipe)) return false;
        Recipe recipe = (Recipe) o;
        return reviewRequired == recipe.reviewRequired
                && Objects.equals(id, recipe.id)
                && Objects.equals(name, recipe.name)
                && Objects.equals(film, recipe.film)
                && Objects.equals(sourceUrl, recipe.sourceUrl)
                && Objects.equals(sensors, recipe.sensors)
                && Objects.equals(raw, recipe.raw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, film, sourceUrl, sensors, raw, reviewRequired);
    }
}
