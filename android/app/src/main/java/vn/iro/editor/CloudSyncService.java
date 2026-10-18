package vn.iro.editor;

import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dịch vụ đồng bộ và lưu trữ ảnh chỉnh sửa lên máy chủ IRO Cloud (npccc.io.vn).
 * Tự động ghi nhận phiên chỉnh sửa, công thức màu (recipe) và lưu trữ ảnh an toàn
 * trên nền luồng độc lập, đảm bảo không làm gián đoạn trải nghiệm người dùng.
 */
public final class CloudSyncService {

    private static final String TAG = "IRO_CloudSync";
    public static final String DEFAULT_SERVER_URL = "https://npccc.io.vn/api/mobile/sync-edit";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static final String PREF_CLOUD_SYNC = "iro_cloud_sync_prefs";
    private static final String KEY_SYNC_ENABLED = "cloud_sync_enabled";

    public static boolean isCloudSyncEnabled(android.content.Context context) {
        if (context == null) return true;
        android.content.SharedPreferences sp = context.getSharedPreferences(PREF_CLOUD_SYNC, android.content.Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_SYNC_ENABLED, true);
    }

    public static void setCloudSyncEnabled(android.content.Context context, boolean enabled) {
        if (context == null) return;
        android.content.SharedPreferences sp = context.getSharedPreferences(PREF_CLOUD_SYNC, android.content.Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply();
    }

    public interface SyncCallback {
        void onSuccess(String sessionId, String remoteUrl);
        void onError(String message);
    }

    private CloudSyncService() {
    }

    /**
     * Đồng bộ ảnh đã xuất lên máy chủ IRO Cloud.
     * Nén ảnh ngay trên luồng gọi (background export thread) trước khi đưa vào hàng đợi mạng
     * để tránh rủi ro bộ nhớ khi Bitmap bị recycle.
     */
    public static void syncExportedPhoto(
            Bitmap bitmap,
            String filename,
            Settings settings,
            String recipeName,
            int width,
            int height
    ) {
        syncExportedPhoto(bitmap, filename, settings, recipeName, width, height, null);
    }

    public static void syncExportedPhoto(
            Bitmap bitmap,
            String filename,
            Settings settings,
            String recipeName,
            int width,
            int height,
            SyncCallback callback
    ) {
        if (bitmap == null || bitmap.isRecycled()) return;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos);
            byte[] jpegBytes = baos.toByteArray();
            EXECUTOR.execute(() -> uploadBytes(jpegBytes, filename, settings, recipeName, width, height, callback));
        } catch (Throwable t) {
            Log.w(TAG, "Lỗi nén ảnh gửi lên cloud: " + t.getMessage());
            if (callback != null) callback.onError(t.getMessage());
        }
    }

    private static void uploadBytes(
            byte[] jpegBytes,
            String filename,
            Settings settings,
            String recipeName,
            int width,
            int height,
            SyncCallback callback
    ) {
        HttpURLConnection conn = null;
        try {
            String boundary = "===IRO_ANDROID_" + System.currentTimeMillis() + "===";
            URL url = new URL(DEFAULT_SERVER_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            
            String deviceModel = (Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")").trim();
            conn.setRequestProperty("User-Agent", "IRO-Android/" + deviceModel);

            JSONObject settingsObj = (settings != null) ? SettingsCodec.encode(settings) : new JSONObject();

            DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

            addFormField(dos, boundary, "platform", "Android");
            addFormField(dos, boundary, "deviceId", deviceModel);
            addFormField(dos, boundary, "filename", (filename != null && !filename.isEmpty()) ? filename : "iro_photo.jpg");
            addFormField(dos, boundary, "recipeName", (recipeName != null && !recipeName.isEmpty()) ? recipeName : "Classic Chrome");
            addFormField(dos, boundary, "width", String.valueOf(width));
            addFormField(dos, boundary, "height", String.valueOf(height));
            addFormField(dos, boundary, "settings", settingsObj.toString());

            // Add photo binary part
            dos.writeBytes("--" + boundary + "\r\n");
            dos.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"exported.jpg\"\r\n");
            dos.writeBytes("Content-Type: image/jpeg\r\n\r\n");
            dos.write(jpegBytes);
            dos.writeBytes("\r\n");

            dos.writeBytes("--" + boundary + "--\r\n");
            dos.flush();
            dos.close();

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String resStr = readStream(is);

            if (code >= 200 && code < 300) {
                JSONObject resJson = new JSONObject(resStr);
                String sessionId = resJson.optString("sessionId");
                String remoteUrl = resJson.optString("url");
                Log.i(TAG, "Đồng bộ ảnh lên IRO Cloud thành công: " + sessionId + " -> " + remoteUrl);
                if (callback != null) callback.onSuccess(sessionId, remoteUrl);
            } else {
                Log.w(TAG, "Đồng bộ ảnh thất bại HTTP " + code + ": " + resStr);
                if (callback != null) callback.onError("HTTP " + code + ": " + resStr);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Lỗi kết nối khi đồng bộ ảnh: " + t.getMessage());
            if (callback != null) callback.onError(t.getMessage());
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    private static void addFormField(DataOutputStream dos, String boundary, String name, String value) throws Exception {
        dos.writeBytes("--" + boundary + "\r\n");
        dos.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
        dos.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
        dos.write(value.getBytes(StandardCharsets.UTF_8));
        dos.writeBytes("\r\n");
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return baos.toString("UTF-8");
        }
    }
}
