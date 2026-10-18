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
 */
public class IroFileProvider extends ContentProvider {

    public static final String AUTHORITY = "vn.iro.editor.fileprovider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File updatesDir = new File(getContext().getCacheDir(), "updates");
        File file = new File(updatesDir, uri.getLastPathSegment());
        if (!file.exists()) {
            throw new FileNotFoundException("Không tìm thấy tệp APK: " + uri.getPath());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File updatesDir = new File(getContext().getCacheDir(), "updates");
        File file = new File(updatesDir, uri.getLastPathSegment());
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{file.getName(), file.length()});
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
