package vn.iro.editor;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * ContentProvider độc lập, thuần native (zero-dependency) cấp quyền đọc an toàn
 * cho trình cài đặt hệ thống (PackageInstaller) khi cài đặt cập nhật APK OTA.
 * Đã được gia cố bảo mật chống Path Traversal và kiểm soát truy cập nghiêm ngặt.
 */
public class IroFileProvider extends ContentProvider {

    public static final String AUTHORITY = "vn.iro.editor.fileprovider";

    @Override
    public boolean onCreate() {
        return true;
    }

    private File getVerifiedFile(Uri uri) throws FileNotFoundException {
        if (uri == null) {
            throw new FileNotFoundException("Uri rỗng");
        }
        String segment = uri.getLastPathSegment();
        if (segment == null || segment.trim().isEmpty() || segment.contains("..") || segment.contains("/") || segment.contains("\\")) {
            throw new SecurityException("Đường dẫn tệp không hợp lệ hoặc chứa ký tự nguy hiểm: " + segment);
        }
        if (getContext() == null) {
            throw new FileNotFoundException("Không lấy được context hệ thống");
        }
        File updatesDir = new File(getContext().getCacheDir(), "updates");
        File file = new File(updatesDir, segment);
        try {
            String canonicalDir = updatesDir.getCanonicalPath();
            String canonicalFile = file.getCanonicalPath();
            if (!canonicalFile.startsWith(canonicalDir + File.separator)) {
                throw new SecurityException("Truy cập tệp ngoài phạm vi thư mục updates: " + segment);
            }
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new FileNotFoundException("Lỗi kiểm tra bảo mật tệp: " + e.getMessage());
        }
        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("Không tìm thấy tệp APK hợp lệ: " + segment);
        }
        return file;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && !mode.equals("r")) {
            throw new SecurityException("Chế độ mở tệp bị từ chối: Chỉ hỗ trợ quyền đọc (r)");
        }
        File file = getVerifiedFile(uri);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File file = getVerifiedFile(uri);
            MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            cursor.addRow(new Object[]{file.getName(), file.length()});
            return cursor;
        } catch (Exception e) {
            return new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
