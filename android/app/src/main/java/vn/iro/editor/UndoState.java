package vn.iro.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Cấu trúc lưu trữ một bản chụp trạng thái chỉnh sửa (Snapshot) phục vụ hoàn tác (Undo) và làm lại (Redo).
 * Chuyển đổi trực tiếp từ {@code struct UndoState} và ngăn xếp {@code undoStack / redoStack}
 * trong {@code ios/IRO/App/EditorModel.swift} (dòng 60–67 và 298–346).
 *
 * <p>Mỗi khi người dùng bắt đầu kéo thước chỉnh ({@link TickRulerView}), bấm Tự động cân bằng ({@link AutoBalancer}),
 * chọn Công thức màu ({@link Recipe}), đổi Khung ảnh ({@link PhotoFrame}) hoặc Đặt lại,
 * trạng thái trước đó sẽ được đẩy vào ngăn xếp (giới hạn tối đa {@link #MAX_HISTORY} = 40 bước).
 */
public final class UndoState {

    /** Số bước hoàn tác tối đa được giữ trong bộ nhớ (giống giới hạn 40 trên iOS). */
    public static final int MAX_HISTORY = 40;

    public final Settings settings;
    public final Recipe recipe;
    public final int frameStyle;
    public final PhotoInfo photoInfo;
    public final List<String> notes;
    public final boolean isAutoBalanced;

    public UndoState(Settings settings,
                     Recipe recipe,
                     int frameStyle,
                     PhotoInfo photoInfo,
                     List<String> notes,
                     boolean isAutoBalanced) {
        this.settings = settings != null ? settings.copy() : new Settings();
        this.recipe = recipe;
        this.frameStyle = frameStyle;
        this.photoInfo = photoInfo != null ? photoInfo.copy() : new PhotoInfo();
        this.notes = notes != null ? Collections.unmodifiableList(new ArrayList<>(notes)) : Collections.emptyList();
        this.isAutoBalanced = isAutoBalanced;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UndoState)) return false;
        UndoState that = (UndoState) o;
        return frameStyle == that.frameStyle
                && isAutoBalanced == that.isAutoBalanced
                && Objects.equals(settings, that.settings)
                && Objects.equals(recipe, that.recipe)
                && Objects.equals(photoInfo, that.photoInfo)
                && Objects.equals(notes, that.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(settings, recipe, frameStyle, photoInfo, notes, isAutoBalanced);
    }

    /**
     * Bộ quản lý ngăn xếp Undo / Redo giúp tách biệt logic lịch sử khỏi giao diện để dễ theo dõi và debug.
     */
    public static final class History {
        private final List<UndoState> undoStack = new ArrayList<>();
        private final List<UndoState> redoStack = new ArrayList<>();

        public boolean canUndo() {
            return !undoStack.isEmpty();
        }

        public boolean canRedo() {
            return !redoStack.isEmpty();
        }

        public int undoCount() {
            return undoStack.size();
        }

        public int redoCount() {
            return redoStack.size();
        }

        /**
         * Ghi lại trạng thái hiện tại trước khi áp dụng thay đổi mới.
         * Nếu trạng thái trùng hệt với đỉnh ngăn xếp thì bỏ qua để không tạo bước hoàn tác thừa.
         */
        public void record(UndoState current) {
            if (current == null) {
                return;
            }
            if (undoStack.isEmpty() || !undoStack.get(undoStack.size() - 1).equals(current)) {
                undoStack.add(current);
                if (undoStack.size() > MAX_HISTORY) {
                    undoStack.remove(0);
                }
            }
            redoStack.clear();
            DebugLogger.d(DebugLogger.TAG_EDITOR,
                    "Ghi nhận Undo: undoStack=" + undoStack.size() + ", redoStack=0");
        }

        /**
         * Thực hiện Hoàn tác (Undo): đẩy trạng thái hiện tại sang {@code redoStack} và lấy trạng thái trước đó ra.
         */
        public UndoState popUndo(UndoState current) {
            if (undoStack.isEmpty()) {
                return null;
            }
            UndoState previous = undoStack.remove(undoStack.size() - 1);
            if (current != null) {
                redoStack.add(current);
            }
            DebugLogger.d(DebugLogger.TAG_EDITOR,
                    "Thực hiện Undo: còn lại undoStack=" + undoStack.size() + ", redoStack=" + redoStack.size());
            return previous;
        }

        /**
         * Thực hiện Làm lại (Redo): đẩy trạng thái hiện tại vào {@code undoStack} và lấy trạng thái kế tiếp ra.
         */
        public UndoState popRedo(UndoState current) {
            if (redoStack.isEmpty()) {
                return null;
            }
            UndoState next = redoStack.remove(redoStack.size() - 1);
            if (current != null) {
                undoStack.add(current);
            }
            DebugLogger.d(DebugLogger.TAG_EDITOR,
                    "Thực hiện Redo: undoStack=" + undoStack.size() + ", redoStack=" + redoStack.size());
            return next;
        }

        /**
         * Xóa sạch lịch sử Undo/Redo khi mở ảnh mới hoặc đóng ảnh.
         */
        public void clear() {
            undoStack.clear();
            redoStack.clear();
            DebugLogger.d(DebugLogger.TAG_EDITOR, "Đã xóa lịch sử Undo/Redo");
        }
    }
}
