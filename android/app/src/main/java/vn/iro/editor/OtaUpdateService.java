package vn.iro.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dịch vụ kiểm tra và cập nhật ứng dụng tự động qua mạng (Over-The-Air Update).
 * Tự động tải bản APK mới nhất từ máy chủ IRO Cloud và mở trình cài đặt Android 1-Click.
 */
public final class OtaUpdateService {

    private static final String TAG = "IRO_OTA";
    public static final String CHECK_ENDPOINT = "https://npccc.io.vn/api/mobile/ota/check";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private OtaUpdateService() {
    }

    public static int getCurrentVersionCode(Activity activity) {
        try {
            PackageInfo p = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) p.getLongVersionCode();
            } else {
                return p.versionCode;
            }
        } catch (Exception e) {
            return 2;
        }
    }

    public static String getCurrentVersionName(Activity activity) {
        try {
            PackageInfo p = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return p.versionName != null ? p.versionName : "0.2.0";
        } catch (Exception e) {
            return "0.2.0";
        }
    }

    /**
     * Kiểm tra bản cập nhật OTA.
     * @param activity Màn hình gọi kiểm tra
     * @param userInitiated true nếu người dùng chủ động bấm kiểm tra từ Menu
     */
    public static void checkForUpdates(Activity activity, boolean userInitiated) {
        if (activity == null || activity.isFinishing()) return;

        final int curCode = getCurrentVersionCode(activity);
        final String curName = getCurrentVersionName(activity);

        EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(CHECK_ENDPOINT + "?platform=android&versionCode=" + curCode);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "IRO-Android/" + curName + " (Build " + curCode + ")");

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    String resStr = readStream(conn.getInputStream());
                    JSONObject j = new JSONObject(resStr);

                    boolean hasUpdate = j.optBoolean("hasUpdate", false);
                    final int latestCode = j.optInt("latestVersionCode", curCode);
                    final String latestName = j.optString("latestVersionName", curName);
                    final String apkUrl = j.optString("apkUrl", "");
                    final String notes = j.optString("releaseNotes", "Cải tiến hiệu năng và tính năng mới.");

                    MAIN_HANDLER.post(() -> {
                        if (activity.isFinishing()) return;
                        if (hasUpdate && latestCode > curCode && !apkUrl.isEmpty()) {
                            showUpdatePrompt(activity, latestName, latestCode, notes, apkUrl);
                        } else if (userInitiated) {
                            new AlertDialog.Builder(activity)
                                    .setTitle("Phiên bản hiện tại: v" + curName)
                                    .setMessage("Ứng dụng IRO Studio của bạn đã là phiên bản mới nhất!\n\n(Build " + curCode + " · Kết nối IRO Cloud ổn định)")
                                    .setPositiveButton("Đóng", null)
                                    .show();
                        }
                    });
                } else if (userInitiated) {
                    MAIN_HANDLER.post(() -> {
                        if (!activity.isFinishing()) {
                            new AlertDialog.Builder(activity)
                                    .setTitle("Thông báo máy chủ")
                                    .setMessage("Không thể kết nối máy chủ OTA (Mã lỗi " + responseCode + ").")
                                    .setPositiveButton("Đóng", null)
                                    .show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "Lỗi kiểm tra OTA: " + e.getMessage());
                if (userInitiated) {
                    MAIN_HANDLER.post(() -> {
                        if (!activity.isFinishing()) {
                            new AlertDialog.Builder(activity)
                                    .setTitle("Lỗi kết nối")
                                    .setMessage("Không thể kết nối tới máy chủ IRO Cloud để kiểm tra cập nhật. Vui lòng kiểm tra kết nối mạng.")
                                    .setPositiveButton("Đóng", null)
                                    .show();
                        }
                    });
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static void showUpdatePrompt(Activity activity, String newVersionName, int newVersionCode, String notes, String apkUrl) {
        new AlertDialog.Builder(activity)
                .setTitle("🚀 Có bản cập nhật mới v" + newVersionName)
                .setMessage("Đã có bản cập nhật mới (Build " + newVersionCode + "):\n\n" + notes + "\n\nBạn có muốn tải và cài đặt ngay không?")
                .setPositiveButton("CẬP NHẬT NGAY", (dialog, which) -> downloadAndInstall(activity, apkUrl, newVersionName))
                .setNegativeButton("ĐỂ SAU", null)
                .setCancelable(false)
                .show();
    }

    private static void downloadAndInstall(Activity activity, String apkUrl, String versionName) {
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle("Đang tải bản cập nhật OTA");
        progress.setMessage("Đang tải IRO Studio v" + versionName + " từ máy chủ...");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setCancelable(false);
        progress.show();

        EXECUTOR.execute(() -> {
            File updatesDir = new File(activity.getCacheDir(), "updates");
            if (!updatesDir.isDirectory()) updatesDir.mkdirs();

            File apkFile = new File(updatesDir, "iro-studio-latest.apk");
            HttpURLConnection conn = null;
            try {
                URL url = new URL(apkUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "IRO-Android-OTA-Downloader");

                int fileLength = conn.getContentLength();
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(apkFile)) {

                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        total += count;
                        out.write(buffer, 0, count);
                        if (fileLength > 0) {
                            final int percent = (int) (total * 100 / fileLength);
                            MAIN_HANDLER.post(() -> progress.setProgress(percent));
                        }
                    }
                }

                MAIN_HANDLER.post(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    if (!activity.isFinishing()) {
                        promptInstallApk(activity, apkFile);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Lỗi tải APK OTA: " + e.getMessage(), e);
                MAIN_HANDLER.post(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    if (!activity.isFinishing()) {
                        new AlertDialog.Builder(activity)
                                .setTitle("Tải thất bại")
                                .setMessage("Không tải được tệp cập nhật: " + e.getMessage())
                                .setPositiveButton("Đóng", null)
                                .show();
                    }
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public static void promptInstallApk(Activity activity, File apkFile) {
        if (apkFile == null || !apkFile.exists() || apkFile.length() == 0) return;

        // Check unknown sources permission on Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                        .setTitle("Quyền cài đặt bản cập nhật")
                        .setMessage("Để cài đặt bản cập nhật trực tiếp qua OTA, vui lòng cho phép quyền 'Cài đặt ứng dụng không rõ nguồn' cho IRO Studio trong Cài đặt hệ thống.")
                        .setPositiveButton("MỞ CÀI ĐẶT", (d, w) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(intent);
                        })
                        .setNegativeButton("HỦY", null)
                        .show();
                return;
            }
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = Uri.parse("content://" + IroFileProvider.AUTHORITY + "/" + apkFile.getName());
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi mở bộ cài đặt: " + e.getMessage(), e);
        }
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
