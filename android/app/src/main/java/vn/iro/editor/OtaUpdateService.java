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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dịch vụ kiểm tra và cập nhật ứng dụng tự động qua mạng (Over-The-Air Update).
 * Tự động tải bản APK mới nhất từ máy chủ IRO Cloud và mở trình cài đặt Android 1-Click.
 * Đã được gia cố bảo mật: Whitelist miền HTTPS, xác thực toàn vẹn mã băm SHA-256,
 * và xử lý ghi tệp tạm thời chống tấn công ghi đè độc hại.
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
     * Xác thực URL tải APK có an toàn và nằm trong whitelist máy chủ tin cậy hay không.
     */
    public static boolean isAllowedDownloadUrl(String urlStr) {
        if (urlStr == null || urlStr.trim().isEmpty()) return false;
        try {
            URL url = new URL(urlStr);
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                return false;
            }
            String host = url.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("npccc.io.vn") || host.endsWith(".npccc.io.vn")
                    || host.equals("github.com") || host.endsWith(".github.com")
                    || host.equals("githubusercontent.com") || host.endsWith(".githubusercontent.com");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Tính toán mã băm SHA-256 của tệp để kiểm tra tính toàn vẹn (anti-tamper).
     */
    public static String calculateSha256(File file) {
        if (file == null || !file.exists() || !file.isFile()) return "";
        try (InputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            Log.w(TAG, "Lỗi tính mã băm SHA-256: " + e.getMessage());
            return "";
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
                    final String sha256 = j.optString("sha256", j.optString("hash", "")).trim();
                    final String notes = j.optString("releaseNotes", "Cải tiến hiệu năng và tính năng mới.");

                    MAIN_HANDLER.post(() -> {
                        if (activity.isFinishing()) return;
                        if (hasUpdate && latestCode > curCode && !apkUrl.isEmpty()) {
                            showUpdatePrompt(activity, latestName, latestCode, notes, apkUrl, sha256);
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

    private static void showUpdatePrompt(Activity activity, String newVersionName, int newVersionCode, String notes, String apkUrl, String sha256) {
        new AlertDialog.Builder(activity)
                .setTitle("🚀 Có bản cập nhật mới v" + newVersionName)
                .setMessage("Đã có bản cập nhật mới (Build " + newVersionCode + "):\n\n" + notes + "\n\nBạn có muốn tải và cài đặt ngay không?")
                .setPositiveButton("CẬP NHẬT NGAY", (dialog, which) -> downloadAndInstall(activity, apkUrl, newVersionName, sha256))
                .setNegativeButton("ĐỂ SAU", null)
                .setCancelable(false)
                .show();
    }

    private static void downloadAndInstall(Activity activity, String apkUrl, String versionName, String expectedSha256) {
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

            File tempFile = new File(updatesDir, "iro-update-" + System.currentTimeMillis() + ".apk.part");
            File finalApkFile = new File(updatesDir, "iro-studio-latest.apk");
            HttpURLConnection conn = null;
            try {
                if (!isAllowedDownloadUrl(apkUrl)) {
                    throw new SecurityException("Địa chỉ tải APK không thuộc máy chủ an toàn được ủy quyền: " + apkUrl);
                }

                URL url = new URL(apkUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "IRO-Android-OTA-Downloader");

                int fileLength = conn.getContentLength();
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(tempFile)) {

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
                    out.flush();
                }

                // Kiểm tra mã băm SHA-256 nếu có khai báo trong manifest
                if (expectedSha256 != null && !expectedSha256.isEmpty()) {
                    String actualSha256 = calculateSha256(tempFile);
                    if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
                        tempFile.delete();
                        throw new SecurityException("Mã băm SHA-256 không khớp! Tệp APK có thể đã bị sửa đổi trái phép.\nKỳ vọng: "
                                + expectedSha256 + "\nThực tế: " + actualSha256);
                    }
                    Log.i(TAG, "Xác thực mã băm SHA-256 APK cập nhật thành công.");
                }

                // Di chuyển sang tệp chính thức
                if (finalApkFile.exists()) {
                    finalApkFile.delete();
                }
                if (!tempFile.renameTo(finalApkFile)) {
                    // Nếu renameTo thất bại (khác filesystem), fallback sao chép
                    try (InputStream fis = new FileInputStream(tempFile);
                         FileOutputStream fos = new FileOutputStream(finalApkFile)) {
                        byte[] b = new byte[8192];
                        int n;
                        while ((n = fis.read(b)) != -1) fos.write(b, 0, n);
                        fos.flush();
                    }
                    tempFile.delete();
                }

                MAIN_HANDLER.post(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    if (!activity.isFinishing()) {
                        promptInstallApk(activity, finalApkFile);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Lỗi tải APK OTA: " + e.getMessage(), e);
                if (tempFile.exists()) tempFile.delete();
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
