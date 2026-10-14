package vn.iro.editor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Dịch vụ phân tích ngữ cảnh bức ảnh và cân chỉnh màu sắc tự động qua <b>Gemini Vision AI</b>.
 * Đồng bộ tính năng hoàn chỉnh từ {@code ios/IRO/Core/GeminiAutoService.swift}.
 */
public final class GeminiAutoService {

    public static final String DEFAULT_MODEL = "gemini-3.1-flash-lite";
    private static final String PREF_NAME = "iro_gemini_prefs";
    private static final String KEY_API_KEY = "gemini_api_key";
    private static final String KEY_MODEL = "gemini_model";

    private static final String[] FALLBACK_MODELS = {
            "gemini-3.1-flash-lite",
            "gemini-3-flash-preview",
            "gemini-3.8-flash",
            "gemini-3.7-flash"
    };

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "IRO-GeminiAuto");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private GeminiAutoService() {
    }

    public static String getApiKey(Context context) {
        if (context == null) return "";
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String userKey = sp.getString(KEY_API_KEY, "");
        if (userKey != null && !userKey.trim().isEmpty()) {
            return userKey.trim();
        }
        return "";
    }

    public static void setApiKey(Context context, String key) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_API_KEY, key != null ? key.trim() : "").apply();
    }

    public static String getSelectedModel(Context context) {
        if (context == null) return DEFAULT_MODEL;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_MODEL, DEFAULT_MODEL);
    }

    public static void setSelectedModel(Context context, String model) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_MODEL, model).apply();
    }

    public interface Callback {
        void onSuccess(GeminiAutoResult result);
        void onError(String message);
    }

    public static void analyzeAsync(
            Context context,
            byte[] jpegData,
            int avgR,
            int avgG,
            int avgB,
            Settings baseline,
            Callback callback
    ) {
        String key = getApiKey(context);
        if (key.isEmpty()) {
            MAIN_HANDLER.post(() -> callback.onError("Chưa cấu hình Gemini API Key."));
            return;
        }

        WORKER.submit(() -> {
            try {
                GeminiAutoResult res = analyzeSync(key, getSelectedModel(context), jpegData, avgR, avgG, avgB, baseline);
                MAIN_HANDLER.post(() -> callback.onSuccess(res));
            } catch (Exception e) {
                MAIN_HANDLER.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Lỗi kết nối Gemini AI"));
            }
        });
    }

    public static GeminiAutoResult analyzeSync(
            String apiKey,
            String initialModel,
            byte[] jpegData,
            int avgR,
            int avgG,
            int avgB,
            Settings baseline
    ) throws Exception {
        String base64Image = Base64.encodeToString(jpegData, Base64.NO_WRAP);

        String prompt = "Bạn là chuyên gia cân chỉnh ánh sáng & màu sắc nhiếp ảnh (Studio Colorist) cho ứng dụng IRO.\n"
                + "Hãy quan sát bức ảnh đính kèm cùng dữ liệu đo sáng khách quan sau:\n"
                + String.format(Locale.ROOT, "- Trung bình kênh màu RGB (0..255): R=%d, G=%d, B=%d\n", avgR, avgG, avgB)
                + String.format(Locale.ROOT, "- Ước lượng tham khảo từ Histogram: Exposure=%.1f, Highlight=%.1f, Shadow=%.1f, Temp=%.0f, Color=%.1f.\n",
                baseline.exposure, baseline.highlight, baseline.shadow, baseline.temperature, baseline.color)
                + "\nNGUYÊN TẮC QUAN TRỌNG NHẤT:\n"
                + "- Ảnh đính kèm là ảnh gốc sRGB. Tất cả giá trị trả về là giá trị TUYỆT ĐỐI áp lên ảnh gốc.\n"
                + "- Giữ ý đồ ánh sáng của cảnh (đêm tối, tuyết trắng, hoàng hôn ấm). Không ép mọi ảnh về xám trung bình.\n"
                + "- Mục tiêu là CÂN BẰNG TỰ NHIÊN, TRONG TRẺO, TRUNG THỰC (Natural Studio Auto-Balance).\n"
                + "- Trong ColorEngine của IRO:\n"
                + "  * exposure: từ 0 đến +1.5.\n"
                + "  * dr: 100 hoặc 200.\n"
                + "  * highlight: -1.0 đến +0.5; mặc định 0.\n"
                + "  * shadow: -1.0 đến 0.0 (giá trị âm như -0.5, -1.0 giúp mở sáng vùng tối).\n"
                + "  * color: -0.5 đến +0.5; nếu có chân dung để 0.0.\n"
                + "  * temperature: -12 đến +12.\n"
                + "  * red: 0, blue: 0, sharpness: 0, clarity: 0, grain: 0, nr: -4.\n"
                + "- recommendedFilm: Gợi ý 1 tên giả lập màu Film Fujifilm hợp nhất (\"Classic Chrome\", \"Astia\", \"Velvia\", \"Classic Neg\", \"Nostalgic Neg\", \"Pro Neg Std\", \"Eterna\", \"Acros\").\n"
                + "- summary: 1 câu tiếng Việt ngắn gọn (dưới 25 từ) mô tả cách cân bằng ánh sáng & màu sắc vừa áp dụng.";

        JSONObject schema = buildResponseSchema();
        JSONObject body = new JSONObject();

        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();

        JSONObject textPart = new JSONObject();
        textPart.put("text", prompt);
        parts.put(textPart);

        JSONObject inlineData = new JSONObject();
        inlineData.put("mime_type", "image/jpeg");
        inlineData.put("data", base64Image);
        JSONObject imagePart = new JSONObject();
        imagePart.put("inline_data", inlineData);
        parts.put(imagePart);

        content.put("parts", parts);
        contents.put(content);
        body.put("contents", contents);

        JSONObject genConfig = new JSONObject();
        genConfig.put("temperature", 0.2);
        genConfig.put("responseMimeType", "application/json");
        genConfig.put("responseSchema", schema);
        body.put("generationConfig", genConfig);

        byte[] requestBytes = body.toString().getBytes(StandardCharsets.UTF_8);

        String[] modelsToTry = new String[FALLBACK_MODELS.length + 1];
        modelsToTry[0] = (initialModel != null && !initialModel.isEmpty()) ? initialModel : DEFAULT_MODEL;
        int mIdx = 1;
        for (String m : FALLBACK_MODELS) {
            if (!m.equals(modelsToTry[0])) {
                modelsToTry[mIdx++] = m;
            }
        }

        Exception lastException = null;
        for (int i = 0; i < mIdx; i++) {
            String model = modelsToTry[i];
            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(18000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("x-goog-api-key", apiKey);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBytes);
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code <= 299) {
                    try (InputStream is = conn.getInputStream()) {
                        String respText = readFully(is);
                        JSONObject root = new JSONObject(respText);
                        JSONArray candidates = root.optJSONArray("candidates");
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject cand = candidates.getJSONObject(0);
                            JSONObject respContent = cand.optJSONObject("content");
                            if (respContent != null) {
                                JSONArray respParts = respContent.optJSONArray("parts");
                                if (respParts != null && respParts.length() > 0) {
                                    String jsonStr = respParts.getJSONObject(0).optString("text", "");
                                    if (!jsonStr.isEmpty()) {
                                        return parseResult(new JSONObject(jsonStr));
                                    }
                                }
                            }
                        }
                    }
                }
                lastException = new Exception("Gemini HTTP " + code);
                if (code != 429 && code != 503 && code != 504 && code != 502) {
                    break;
                }
            } catch (Exception e) {
                lastException = e;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
            }
        }

        throw lastException != null ? lastException : new Exception("Không nhận được phản hồi từ Gemini AI");
    }

    private static String readFully(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toString("UTF-8");
    }

    private static JSONObject buildResponseSchema() throws Exception {
        JSONObject schema = new JSONObject();
        schema.put("type", "OBJECT");
        JSONObject props = new JSONObject();

        props.put("sceneType", new JSONObject().put("type", "STRING"));
        props.put("summary", new JSONObject().put("type", "STRING"));
        props.put("recommendedFilm", new JSONObject().put("type", "STRING"));
        props.put("exposure", new JSONObject().put("type", "NUMBER"));
        props.put("dr", new JSONObject().put("type", "INTEGER"));
        props.put("highlight", new JSONObject().put("type", "NUMBER"));
        props.put("shadow", new JSONObject().put("type", "NUMBER"));
        props.put("color", new JSONObject().put("type", "NUMBER"));
        props.put("temperature", new JSONObject().put("type", "NUMBER"));

        schema.put("properties", props);
        JSONArray required = new JSONArray();
        required.put("sceneType");
        required.put("summary");
        required.put("recommendedFilm");
        required.put("exposure");
        required.put("dr");
        required.put("highlight");
        required.put("shadow");
        required.put("color");
        required.put("temperature");
        schema.put("required", required);
        return schema;
    }

    private static GeminiAutoResult parseResult(JSONObject json) {
        GeminiAutoResult r = new GeminiAutoResult();
        r.sceneType = json.optString("sceneType", "Chung");
        r.summary = json.optString("summary", "Cân bằng ánh sáng tự nhiên.");
        r.recommendedFilm = json.optString("recommendedFilm", "Classic Chrome");
        r.exposure = json.optDouble("exposure", 0.0);
        r.dr = json.optInt("dr", 100);
        r.highlight = json.optDouble("highlight", 0.0);
        r.shadow = json.optDouble("shadow", 0.0);
        r.color = json.optDouble("color", 0.0);
        r.temperature = json.optDouble("temperature", 0.0);
        return r;
    }

    public static final class GeminiAutoResult {
        public String sceneType = "";
        public String summary = "";
        public String recommendedFilm = "";
        public double exposure = 0.0;
        public int dr = 100;
        public double highlight = 0.0;
        public double shadow = 0.0;
        public double color = 0.0;
        public double temperature = 0.0;

        public Settings appliedSettings(Settings base, Settings baseline) {
            Settings s = base != null ? base.copy() : new Settings();
            boolean isPortrait = sceneType.toLowerCase(Locale.ROOT).contains("chân dung")
                    || sceneType.toLowerCase(Locale.ROOT).contains("portrait")
                    || sceneType.toLowerCase(Locale.ROOT).contains("người");

            if (baseline != null) {
                double anchor = clamp(baseline.exposure, -1.5, 1.5, 0.1);
                s.exposure = clamp(exposure, Math.max(-1.5, anchor - 0.3), Math.min(1.5, anchor + 0.3), 0.1);
            } else {
                s.exposure = clamp(exposure, -1.5, 1.5, 0.1);
            }

            s.dr = dr >= 200 ? 200 : 100;
            s.highlight = clamp(highlight, -1.0, 0.5, 0.5);
            s.shadow = clamp(shadow, -1.0, 0.5, 0.5);
            s.temperature = clamp(temperature, -12.0, 12.0, 1.0);
            s.color = isPortrait ? 0.0 : clamp(color, -0.5, 0.5, 0.5);
            s.colorChrome = 0.0;
            s.chromeBlue = 0.0;
            s.red = 0.0;
            s.blue = 0.0;
            s.sharpness = 0.0;
            s.clarity = 0.0;
            s.grain = 0.0;
            s.nr = -4.0;
            return s;
        }

        private static double clamp(double v, double lo, double hi, double step) {
            if (!Double.isFinite(v)) return 0.0;
            double c = Math.max(lo, Math.min(hi, v));
            return Math.max(lo, Math.min(hi, Math.round(c / step) * step));
        }
    }
}
