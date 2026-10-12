package vn.iro.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.function.IntConsumer;

/**
 * Bộ chọn trực quan 30 mẫu khung ảnh theo từng nhóm chủ đề.
 * Tương đương với phần lưới chọn mẫu trong {@code ios/IRO/Views/FrameExportViews.swift}.
 *
 * <p>Mọi hình thu nhỏ (thumbnail) trong bộ chọn này đều được kết xuất trực tiếp từ {@link PhotoFrame#render}
 * trên bức ảnh người dùng đang mở để đảm bảo xem trước trung thực 100% với ảnh xuất.
 */
final class FrameTemplatePicker {

    private FrameTemplatePicker() {
    }

    /**
     * Hiển thị hộp thoại chọn mẫu khung ảnh.
     *
     * @param activity Activity hiện tại.
     * @param photo    Ảnh đang chỉnh sửa.
     * @param info     Thông tin EXIF hiện tại.
     * @param selected Mã mẫu khung đang chọn (0..30).
     * @param choose   Hàm callback nhận mã mẫu khung mới khi người dùng chạm chọn.
     */
    static void show(Activity activity, Bitmap photo, PhotoInfo info, int selected, IntConsumer choose) {
        float density = activity.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));

        // Bộ lọc nhóm khung (Tất cả, Thông số máy, Tối giản, Film / hoài cổ, Sáng tạo, Mẫu mới)
        Spinner groups = new Spinner(activity);
        groups.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, PhotoFrame.GROUPS));
        root.addView(groups);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);

        int maxDialogHeight = (int) (Math.min(440, activity.getResources().getConfiguration().screenHeightDp * 0.55f) * density);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxDialogHeight));

        // Thu nhỏ ảnh nguồn về tối đa 220px để vẽ lưới 30 mẫu khung mượt mà
        float scale = Math.min(1f, 220f / Math.max(photo.getWidth(), photo.getHeight()));
        Bitmap thumbnail = Bitmap.createScaledBitmap(
                photo,
                Math.max(1, Math.round(photo.getWidth() * scale)),
                Math.max(1, Math.round(photo.getHeight() * scale)),
                true);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle((PhotoFrame.STYLES.length - 1) + " mẫu khung ảnh")
                .setView(root)
                .setNegativeButton("Đóng", null)
                .setNeutralButton("Không dùng khung", (d, n) -> choose.accept(0))
                .create();

        groups.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int group, long id) {
                rows.removeAllViews();
                LinearLayout row = null;
                int count = 0;

                for (int style = 1; style < PhotoFrame.STYLES.length; style++) {
                    // Lọc theo nhóm: nhóm 5 là "Mẫu mới" (style >= 19), nhóm 0 là "Tất cả"
                    if (group == 5 ? style < 19 : (group != 0 && PhotoFrame.group(style) != group)) {
                        continue;
                    }
                    if (count++ % 2 == 0) {
                        row = new LinearLayout(activity);
                        rows.addView(row, new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT));
                    }

                    final int chosen = style;
                    boolean isSelected = (chosen == selected);

                    LinearLayout card = new LinearLayout(activity);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding((int) (8 * density), (int) (8 * density), (int) (8 * density), (int) (8 * density));
                    card.setBackground(StudioStyle.surface(
                            isSelected ? StudioStyle.WARM_AMBER_LIGHT : StudioStyle.SURFACE,
                            isSelected ? StudioStyle.WARM_AMBER : StudioStyle.LINE,
                            12 * density));

                    ImageView image = new ImageView(activity);
                    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    image.setImageBitmap(PhotoFrame.render(thumbnail, info, style));
                    card.addView(image, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            (int) (132 * density)));

                    TextView name = new TextView(activity);
                    name.setText((isSelected ? "✓ " : "") + PhotoFrame.STYLES[style]);
                    name.setTextSize(12);
                    name.setTextColor(isSelected ? StudioStyle.WARM_AMBER : StudioStyle.INK);
                    name.setMinHeight((int) (42 * density));
                    name.setGravity(Gravity.CENTER);
                    card.addView(name);

                    card.setContentDescription("Chọn khung " + PhotoFrame.STYLES[style]);
                    card.setOnClickListener(clicked -> {
                        dialog.dismiss();
                        choose.accept(chosen);
                    });

                    LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    cell.setMargins((int) (4 * density), (int) (4 * density), (int) (4 * density), (int) (4 * density));
                    row.addView(card, cell);
                }

                // Thêm ô trống nếu số lượng mẫu trong nhóm là số lẻ để giữ cột 50%
                if (count % 2 != 0 && row != null) {
                    row.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
                }
            }
        });

        dialog.show();
    }
}
