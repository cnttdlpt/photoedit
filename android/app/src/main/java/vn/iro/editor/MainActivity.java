package vn.iro.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Màn hình điều khiển chính của ứng dụng chỉnh màu ảnh IRO Studio trên Android.
 * Chuyển đổi toàn diện kiến trúc và giao diện Liquid Glass từ:
 * <ul>
 *     <li>{@code ios/IRO/App/EditorModel.swift} (Quản lý trạng thái, luồng nền, Undo/Redo, Auto-Balance, LUT, Xuất ảnh).</li>
 *     <li>{@code ios/IRO/Views/EditorView.swift} (Header Undo/Redo, Mode Switcher Pill 3 tab, Stage chạm giữ xem gốc, Dock Liquid Glass).</li>
 *     <li>{@code ios/IRO/Views/Studio.swift} (Màn hình chào {@code EmptyPhoto} với thanh tìm kiếm, thẻ Album 2×2, Hero CTA).</li>
 * </ul>
 */
public class MainActivity extends Activity {

    // Mã yêu cầu Intent (Activity Result Request Codes)
    private static final int OPEN_PHOTO = 10;
    private static final int OPEN_LUT = 11;
    private static final int OPEN_RECIPE = 12;
    private static final int SAVE_IMAGE = 13;
    private static final int SAVE_RECIPE = 14;

    /**
     * Ba phân hệ công cụ chính trên thanh chuyển chế độ (Mode Switcher Pill).
     * Tương đương {@code enum EditorToolTab} trong {@code Studio.swift}.
     */
    public enum EditorToolTab {
        ADJUST("Tinh chỉnh", "sliders"),
        FILTERS("Màu phim", "sparkles"),
        FRAME("Khung ảnh", "frame"),
        LIGHTCRAFT("LightCraft", "lightcraft");

        public final String title;
        public final String icon;

        EditorToolTab(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    /**
     * Danh sách các thông số tinh chỉnh hiển thị dưới dạng dải nút tròn ngang (Circular Tool Strip).
     * Tương đương {@code enum AdjustParam} trong {@code EditorView.swift}.
     */
    public enum AdjustParam {
        EXPOSURE("Phơi sáng", "exposure", -3.0, 3.0, 0.1, 0.0),
        HIGHLIGHT("Vùng sáng", "highlight", -2.0, 4.0, 0.5, 0.0),
        SHADOW("Vùng tối", "shadow", -2.0, 4.0, 0.5, 0.0),
        COLOR("Độ đậm", "color", -4.0, 4.0, 1.0, 0.0),
        TEMP("Nhiệt độ", "temp", -100.0, 100.0, 1.0, 0.0),
        RED("Shift Đỏ", "red", -9.0, 9.0, 1.0, 0.0),
        BLUE("Shift Xanh", "blue", -9.0, 9.0, 1.0, 0.0),
        GRAIN("Hạt Grain", "grain", 0.0, 30.0, 1.0, 0.0),
        SHARPNESS("Độ nét", "sharpness", -4.0, 4.0, 1.0, 0.0),
        CLARITY("Độ trong", "clarity", -5.0, 5.0, 1.0, 0.0),
        NR("Khử nhiễu", "nr", -4.0, 4.0, 1.0, -4.0),
        LUT_AMOUNT("Cường độ LUT", "film", 0.0, 100.0, 1.0, 100.0),
        COLOR_CHROME("Color Chrome", "color", 0.0, 2.0, 1.0, 0.0),
        CHROME_BLUE("Chrome Blue", "blue", 0.0, 2.0, 1.0, 0.0);

        public final String label;
        public final String icon;
        public final double min;
        public final double max;
        public final double step;
        public final double defaultValue;

        AdjustParam(String label, String icon, double min, double max, double step, double defaultValue) {
            this.label = label;
            this.icon = icon;
            this.min = min;
            this.max = max;
            this.step = step;
            this.defaultValue = defaultValue;
        }

        public double read(Settings s) {
            switch (this) {
                case EXPOSURE: return s.exposure;
                case HIGHLIGHT: return s.highlight;
                case SHADOW: return s.shadow;
                case COLOR: return s.color;
                case TEMP: return s.temperature;
                case RED: return s.red;
                case BLUE: return s.blue;
                case GRAIN: return s.grain;
                case SHARPNESS: return s.sharpness;
                case CLARITY: return s.clarity;
                case NR: return s.nr;
                case LUT_AMOUNT: return s.lutAmount;
                case COLOR_CHROME: return s.colorChrome;
                case CHROME_BLUE: return s.chromeBlue;
                default: return 0.0;
            }
        }

        public void write(Settings s, double v) {
            double clamped = Math.max(min, Math.min(max, v));
            switch (this) {
                case EXPOSURE: s.exposure = clamped; break;
                case HIGHLIGHT: s.highlight = clamped; break;
                case SHADOW: s.shadow = clamped; break;
                case COLOR: s.color = clamped; break;
                case TEMP: s.temperature = clamped; break;
                case RED: s.red = clamped; break;
                case BLUE: s.blue = clamped; break;
                case GRAIN: s.grain = clamped; break;
                case SHARPNESS: s.sharpness = clamped; break;
                case CLARITY: s.clarity = clamped; break;
                case NR: s.nr = clamped; break;
                case LUT_AMOUNT: s.lutAmount = clamped; break;
                case COLOR_CHROME: s.colorChrome = clamped; break;
                case CHROME_BLUE: s.chromeBlue = clamped; break;
            }
        }

        public boolean isModified(Settings s) {
            return Math.abs(read(s) - defaultValue) >= (step * 0.4);
        }

        /**
         * Trả về màu chủ đạo theo kênh RGB chuẩn Google cho từng thông số tinh chỉnh:
         * <ul>
         *     <li>Kênh Đỏ ({@link StudioStyle#GOOGLE_RED}): Shift Đỏ, Độ đậm màu, Color Chrome, Cường độ LUT.</li>
         *     <li>Kênh Lục ({@link StudioStyle#GOOGLE_GREEN}): Độ nét, Độ trong (Clarity), Hạt Grain, Khử nhiễu NR.</li>
         *     <li>Kênh Lam ({@link StudioStyle#GOOGLE_BLUE}): Shift Xanh, Chrome Blue, Vùng tối (Shadow).</li>
         *     <li>Kênh Vàng Sáng ({@link StudioStyle#GOOGLE_YELLOW}): Phơi sáng, Vùng sáng (Highlight), Nhiệt độ màu.</li>
         * </ul>
         */
        public int channelColor() {
            switch (this) {
                case RED:
                case COLOR:
                case COLOR_CHROME:
                case LUT_AMOUNT:
                    return StudioStyle.GOOGLE_RED;
                case SHARPNESS:
                case CLARITY:
                case GRAIN:
                case NR:
                    return StudioStyle.GOOGLE_GREEN;
                case BLUE:
                case CHROME_BLUE:
                case SHADOW:
                    return StudioStyle.GOOGLE_BLUE;
                case EXPOSURE:
                case HIGHLIGHT:
                case TEMP:
                default:
                    return StudioStyle.GOOGLE_YELLOW;
            }
        }

        public String formatValue(Settings s) {
            double v = read(s);
            if (this == EXPOSURE || this == HIGHLIGHT || this == SHADOW) {
                return String.format(Locale.ROOT, "%+.1f", v);
            }
            if (this == GRAIN || this == LUT_AMOUNT || this == COLOR_CHROME || this == CHROME_BLUE) {
                return String.format(Locale.ROOT, "%.0f", v);
            }
            return String.format(Locale.ROOT, "%+.0f", v);
        }
    }

    // Luồng xử lý nền (Background Worker) & Handler luồng giao diện chính (Main Thread)
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Dữ liệu thư viện & lịch sử Undo/Redo
    private final List<Recipe> recipes = new ArrayList<>();
    private final List<String> recipeNotes = new ArrayList<>();
    private final Set<ThumbnailSession> thumbnailSessions = new HashSet<>();
    private final UndoState.History history = new UndoState.History();
    private ThumbnailSession inlineFilterSession;

    // Trạng thái chỉnh sửa hiện tại (tương đương các biến @Published trong EditorModel.swift)
    private Settings settings = new Settings();
    private Settings exportSettings;
    private Recipe currentRecipe;
    private CubeLut activeLut;
    private CubeLut exportLut;
    private Uri imageUri;
    private Uri exportUri;
    private Bitmap preview;
    private Bitmap rendered;
    private PhotoInfo photoInfo = new PhotoInfo();
    private PhotoInfo exportInfo;
    private int frameStyle = 0;
    private int exportFrameStyle = 0;
    private Bitmap frameSource;
    private Bitmap framePreview;
    private int frameVersion = 0;
    private int cachedFrameVersion = -1;
    private int cachedFrameStyle = -1;

    // Cờ trạng thái điều khiển luồng và UI
    private EditorToolTab selectedTab = EditorToolTab.ADJUST;
    private AdjustParam selectedAdjust = AdjustParam.EXPOSURE;
    private boolean isAutoBalanced = false;
    private boolean sliderUndoRecorded = false;
    private boolean isComparing = false;
    private boolean dead = false;
    private boolean exportBusy = false;
    private Future<?> renderTask;
    private int revision = 0;
    private int loadRevision = 0;
    private int lutRevision = 0;

    // Cấu hình xuất ảnh
    private String exportType = "image/jpeg";
    private String exportRecipeJson;
    private int exportScale = 2;
    private int exportQuality = 100;
    private boolean exportFullSize = true;

    // Các thành phần giao diện (Views)
    private FrameLayout rootStack;
    private View emptyLandingView;
    private View editorWorkspaceView;
    private ImageView imageStageView;
    private TextView compareOriginBadge;
    private TextView statusLabel;
    private TextView canvasRecipeChip;
    private View undoButton;
    private View redoButton;
    private View railAutoBalanceCircle;
    private final Map<EditorToolTab, TextView> tabPillButtons = new LinkedHashMap<>();
    private final Map<EditorToolTab, FrameLayout> railPillIndicators = new LinkedHashMap<>();
    private final Map<EditorToolTab, ImageView> railPillIcons = new LinkedHashMap<>();

    // Các phần trong Dock điều khiển (Bottom Dock trên điện thoại dọc / Right Inspector Sheet trên Tablet & Khổ ngang)
    private FrameLayout bottomDockContent;
    private View adjustSectionView;
    private View filtersSectionView;
    private View framesSectionView;
    private View lightcraftSectionView;

    // Thành phần bên trong tab Tinh chỉnh (Adjust Section)
    private RgbHistogramView rgbHistogramView;
    private ImageView activeParamIconView;
    private TextView activeParamTitleView;
    private TextView activeParamValueBadge;
    private View activeParamResetBtn;
    private TextView drBadgeButton;
    private TickRulerView tickRulerView;
    private LinearLayout adjustStripContainer;

    // Thành phần bên trong tab Màu phim (Filters Section)
    private TextView filterActiveRecipeTitle;
    private TextView filterLutStatusBadge;
    private LinearLayout filterCarouselContainer;

    // Thành phần bên trong tab Khung ảnh (Frames Section)
    private TextView frameActiveStyleTitle;
    private LinearLayout frameCarouselContainer;

    // Thành phần bên trong tab LightCraft Darkroom (tích hợp từ dự án cnttdlpt/lightcraft):
    // Tone Curve (Monotone Cubic Hermite Spline + 4-Zone Parametric) + 3-Way OkLab/OkLCh Color Grading
    // + 8-Band HSL Color Mixer + Local Masking & 12 Built-in Presets
    private final LightCraftEngine.State lightCraftState = new LightCraftEngine.State();
    private LightCraftView.StageMaskOverlayView stageMaskOverlayView;
    private LightCraftView.ToneCurveGraphView toneCurveGraphView;
    private LightCraftView.GradingWheelsView gradingWheelsView;
    private LinearLayout lightCraftSubTabRow;
    private LinearLayout lightCraftDetailContainer;
    private int lightCraftSubTab = 0;
    private int selectedMixerBand = 0;

    private boolean wideLayoutActive = false;
    private boolean wasShowingPhoto = false;
    private SharedPreferences prefs;
    private OnlineLutBrowser onlineLuts;

    // =========================================================================
    // VÒNG ĐỜI ACTIVITY & KHỞI TẠO (Lifecycle)
    // =========================================================================

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("iro", MODE_PRIVATE);

        // Khôi phục thông số và recipe từ phiên làm việc trước
        try {
            settings = SettingsCodec.decode(new JSONObject(prefs.getString("settings", "{}")));
            String rec = prefs.getString("recipe", null);
            if (rec != null) {
                currentRecipe = new Recipe(new JSONObject(rec));
            }
        } catch (Exception ignored) {
            settings = new Settings();
        }

        frameStyle = Math.max(0, Math.min(PhotoFrame.STYLES.length - 1, prefs.getInt("frame.style", 0)));
        onlineLuts = new OnlineLutBrowser(this, prefs);
        onlineLuts.checkOnOpen();

        buildUi();
        loadLibrary();
        loadBoundLut();

        // Khôi phục ảnh gần nhất nếu bản sao trong vùng riêng của app còn tồn tại
        String saved = prefs.getString("photo", null);
        if (saved != null) {
            loadPhoto(Uri.parse(saved), false);
        } else {
            updateWorkspaceVisibility();
        }
    }

    /**
     * Kiểm tra thiết bị đang ở chế độ Khổ ngang (Landscape) hoặc Máy tính bảng / Màn hình lớn (sw >= 600dp).
     * Khi {@code true}, giao diện tự động chuyển sang kiến trúc Google Material 3 Adaptive:
     * <ul>
     *     <li>Màn hình chào: Bố cục 2 cột (Dual-Pane Hero + Bento Grid).</li>
     *     <li>Phòng tối chỉnh sửa: Bố cục 3 vùng (Navigation Rail trái + Khung ảnh trung tâm + Bảng Inspector phải).</li>
     * </ul>
     */
    private boolean isWideScreen() {
        Configuration cfg = getResources().getConfiguration();
        return cfg.orientation == Configuration.ORIENTATION_LANDSCAPE || cfg.screenWidthDp >= 600;
    }

    private int dp(float v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    private TextView text(String value, float sizeSp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    // =========================================================================
    // XÂY DỰNG GIAO DIỆN ADAPTIVE MATERIAL 3 EXPRESSIVE & LIQUID GLASS
    // =========================================================================

    private void buildUi() {
        wideLayoutActive = isWideScreen();
        tabPillButtons.clear();
        railPillIndicators.clear();
        railPillIcons.clear();

        rootStack = new FrameLayout(this);
        setContentView(rootStack);

        // 1. Nền AntigravityBackground (Sứ trắng kem + quầng sáng bình minh & xanh bạc hà)
        StudioStyle.AntigravityBackground bgView = new StudioStyle.AntigravityBackground(this);
        rootStack.addView(bgView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Khung bao nội dung chính tự động căn lề theo WindowInsets
        FrameLayout contentWrapper = new FrameLayout(this);
        int hPad = wideLayoutActive ? dp(18) : dp(14);
        int vPad = wideLayoutActive ? dp(12) : dp(8);
        contentWrapper.setPadding(hPad, vPad, hPad, vPad);
        contentWrapper.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(
                    hPad + insets.getSystemWindowInsetLeft(),
                    vPad + insets.getSystemWindowInsetTop(),
                    hPad + insets.getSystemWindowInsetRight(),
                    vPad + insets.getSystemWindowInsetBottom());
            return insets;
        });
        rootStack.addView(contentWrapper, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // 2. Màn hình chào khi chưa mở ảnh (Adaptive Dual-Pane trên Tablet/Khổ ngang)
        emptyLandingView = buildEmptyLandingView();
        contentWrapper.addView(emptyLandingView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // 3. Không gian chỉnh sửa khi đã mở ảnh (Adaptive 3-Zone Studio trên Tablet/Khổ ngang)
        editorWorkspaceView = wideLayoutActive
                ? buildWideEditorWorkspaceView()
                : buildPortraitEditorWorkspaceView();
        contentWrapper.addView(editorWorkspaceView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        wasShowingPhoto = (preview != null);
        emptyLandingView.setVisibility(wasShowingPhoto ? View.GONE : View.VISIBLE);
        editorWorkspaceView.setVisibility(wasShowingPhoto ? View.VISIBLE : View.GONE);

        syncAllUiControls();
    }

    /**
     * Xây dựng màn hình chào {@code EmptyPhoto}:
     * Tự động thích ứng giữa bố cục 2 cột (Dual-Pane) trên Máy tính bảng / Khổ ngang
     * và bố cục 1 cột cuộn dọc trên Điện thoại dọc.
     */
    private View buildEmptyLandingView() {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);

        LinearLayout outer = column();
        outer.setPadding(dp(4), dp(4), dp(4), dp(16));
        scroll.addView(outer);

        // 1. Thanh App Bar & Tìm kiếm kiểu Google Material 3 Search Bar
        LinearLayout topBar = row();
        topBar.setPadding(0, 0, 0, dp(14));

        if (wideLayoutActive) {
            // Huy hiệu thương hiệu viền dải quang phổ Google RGB bên trái trên máy tính bảng / khổ ngang
            LinearLayout brandPill = row();
            brandPill.setPadding(dp(12), dp(8), dp(14), dp(8));
            brandPill.setBackground(StudioStyle.rgbBorderPill(Color.WHITE, dp(20)));
            ImageView spark = new ImageView(this);
            spark.setImageDrawable(new StudioStyle.Icon("sparkles", StudioStyle.GOOGLE_BLUE, dp(16)));
            brandPill.addView(spark, new LinearLayout.LayoutParams(dp(16), dp(16)));
            TextView brandTitle = text("IRO RGB Studio", 13.5f, StudioStyle.INK);
            brandTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            brandTitle.setPadding(dp(8), 0, 0, 0);
            brandPill.addView(brandTitle);
            LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            brandLp.rightMargin = dp(12);
            topBar.addView(brandPill, brandLp);
        }

        LinearLayout searchBox = row();
        searchBox.setPadding(dp(16), dp(11), dp(16), dp(11));
        searchBox.setBackground(StudioStyle.rgbBorderPill(Color.WHITE, dp(24)));
        ImageView searchIcon = new ImageView(this);
        searchIcon.setImageDrawable(new StudioStyle.Icon("search", StudioStyle.GOOGLE_BLUE, dp(17)));
        searchBox.addView(searchIcon, new LinearLayout.LayoutParams(dp(17), dp(17)));
        TextView searchHint = text("Tìm kiếm 392 công thức màu phim, quang phổ RGB, 3D LUT...", 13.5f, StudioStyle.MUTED);
        searchHint.setPadding(dp(10), 0, 0, 0);
        searchHint.setSingleLine(true);
        searchHint.setEllipsize(TextUtils.TruncateAt.END);
        searchBox.addView(searchHint, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView shortcutBadge = text("392 màu", 11, StudioStyle.GOOGLE_BLUE);
        shortcutBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        shortcutBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        shortcutBadge.setBackground(StudioStyle.shape(StudioStyle.BLUE_TONAL, 0, dp(12)));
        searchBox.addView(shortcutBadge);
        searchBox.setOnClickListener(v -> showLibrary());
        MotionStudio.attachExpressiveTouch(searchBox, 0.98f);
        topBar.addView(searchBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        View lutCloudBtn = createCircleIconButton("film", StudioStyle.GOOGLE_BLUE, dp(42), this::showOnlineLuts);
        lutCloudBtn.setContentDescription("Kho LUT trực tuyến");
        LinearLayout.LayoutParams lutParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        lutParams.leftMargin = dp(10);
        topBar.addView(lutCloudBtn, lutParams);

        View menuBtn = createCircleTextButton("⋯", StudioStyle.INK, dp(42), this::showMore);
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        menuParams.leftMargin = dp(8);
        topBar.addView(menuBtn, menuParams);

        outer.addView(topBar);

        // 2. Tạo khối Hero Studio Card (Mở ảnh Thư viện / Tệp tin / Ảnh mẫu Studio 1 chạm)
        LinearLayout heroCard = buildExpressiveHeroCard();

        // 3. Tạo khối Bento 2×2 & Dải Film Simulation tương tác
        LinearLayout bentoAndFilmsCol = buildBentoAndFilmShowcaseColumn();

        if (wideLayoutActive) {
            // BỐ CỤC 2 CỘT CHUẨN MÁY TÍNH BẢNG & KHỔ NGANG (Google Material 3 Canonical Dual-Pane)
            LinearLayout dualPane = row();
            dualPane.setGravity(Gravity.TOP);

            LinearLayout leftCol = column();
            leftCol.addView(heroCard);
            dualPane.addView(leftCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.46f));

            View gap = new View(this);
            dualPane.addView(gap, new LinearLayout.LayoutParams(dp(18), 1));

            dualPane.addView(bentoAndFilmsCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.54f));
            outer.addView(dualPane, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            MotionStudio.animateCascade(leftCol, 30, 55);
            MotionStudio.animateCascade(bentoAndFilmsCol, 70, 45);
        } else {
            // BỐ CỤC 1 CỘT TRÊN ĐIỆN THOẠI DỌC
            outer.addView(heroCard);
            LinearLayout.LayoutParams bentoLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            bentoLp.topMargin = dp(14);
            outer.addView(bentoAndFilmsCol, bentoLp);
            MotionStudio.animateCascade(outer, 20, 45);
        }

        return scroll;
    }

    /**
     * Thẻ Hero chính kiểu Google Material 3 Expressive RGB:
     * Có dải băng 4 chấm màu Google RGB (Red, Yellow, Green, Blue), nút mở Thư viện ảnh, Tệp tin và Ảnh mẫu 1 chạm.
     */
    private LinearLayout buildExpressiveHeroCard() {
        LinearLayout heroCard = column();
        heroCard.setPadding(dp(20), dp(20), dp(20), dp(20));
        heroCard.setBackground(StudioStyle.liquidGlassCard(dp(28)));

        // Dải băng 4 hạt màu Google RGB (Red - Yellow - Green - Blue) + Huy hiệu quang phổ RGB
        LinearLayout bannerRow = row();
        int[] swatchColors = {
                StudioStyle.GOOGLE_RED,
                StudioStyle.GOOGLE_YELLOW,
                StudioStyle.GOOGLE_GREEN,
                StudioStyle.GOOGLE_BLUE
        };
        for (int i = 0; i < swatchColors.length; i++) {
            View dot = new View(this);
            dot.setBackground(StudioStyle.shape(swatchColors[i], 0x44ffffff, dp(10)));
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(20), dp(20));
            if (i > 0) dotLp.leftMargin = dp(6);
            bannerRow.addView(dot, dotLp);
        }
        View bannerSpacer = new View(this);
        bannerRow.addView(bannerSpacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView engineTag = text("RGB Spectrum · Linear 3D LUT", 11, StudioStyle.GOOGLE_BLUE);
        engineTag.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        engineTag.setPadding(dp(10), dp(4), dp(10), dp(4));
        engineTag.setBackground(StudioStyle.rgbBorderPill(Color.WHITE, dp(12)));
        bannerRow.addView(engineTag);
        heroCard.addView(bannerRow);

        // Tiêu đề lớn & mô tả
        TextView heroTitle = text("Phòng tối Quang Phổ RGB & 3D LUT", wideLayoutActive ? 20f : 17f, StudioStyle.INK);
        heroTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        heroTitle.setPadding(0, dp(14), 0, dp(4));
        heroCard.addView(heroTitle);

        TextView heroSub = text(
                "Biểu đồ sóng RGB thời gian thực, 16 thông số phân tách theo kênh Đỏ · Lục · Lam · Sáng, cân bằng AI và 30 khung EXIF.",
                12.5f, StudioStyle.MUTED);
        heroSub.setLineSpacing(dp(2), 1.0f);
        heroCard.addView(heroSub);

        // Hàng nút CTA chính: Thư viện ảnh & Tệp tin
        LinearLayout primaryRow = row();
        primaryRow.setPadding(0, dp(18), 0, 0);

        LinearLayout galleryBtn = row();
        galleryBtn.setGravity(Gravity.CENTER);
        galleryBtn.setPadding(dp(18), dp(13), dp(18), dp(13));
        galleryBtn.setBackground(StudioStyle.warmAmberPill(dp(24)));
        ImageView galIcon = new ImageView(this);
        galIcon.setImageDrawable(new StudioStyle.Icon("photo", Color.WHITE, dp(17)));
        galleryBtn.addView(galIcon, new LinearLayout.LayoutParams(dp(17), dp(17)));
        TextView galText = text("Thư viện ảnh", 13.5f, Color.WHITE);
        galText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        galText.setPadding(dp(8), 0, 0, 0);
        galleryBtn.addView(galText);
        galleryBtn.setOnClickListener(v -> choose(OPEN_PHOTO, "image/*"));
        MotionStudio.attachExpressiveTouch(galleryBtn, 0.95f);
        primaryRow.addView(galleryBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout fileBtn = row();
        fileBtn.setGravity(Gravity.CENTER);
        fileBtn.setPadding(dp(18), dp(13), dp(18), dp(13));
        fileBtn.setBackground(StudioStyle.liquidGlassPill(dp(24)));
        ImageView folderIcon = new ImageView(this);
        folderIcon.setImageDrawable(new StudioStyle.Icon("folder", StudioStyle.INK, dp(16)));
        fileBtn.addView(folderIcon, new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView fileText = text("Tệp tin", 13.5f, StudioStyle.INK);
        fileText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        fileText.setPadding(dp(8), 0, 0, 0);
        fileBtn.addView(fileText);
        fileBtn.setOnClickListener(v -> chooseDocumentOnly(OPEN_PHOTO, "image/*"));
        MotionStudio.attachExpressiveTouch(fileBtn, 0.95f);
        LinearLayout.LayoutParams fileLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        fileLp.leftMargin = dp(10);
        primaryRow.addView(fileBtn, fileLp);

        heroCard.addView(primaryRow);

        // Nút tạo & mở Ảnh mẫu Studio 1 chạm với viền cầu vồng Google RGB
        LinearLayout sampleBtn = row();
        sampleBtn.setGravity(Gravity.CENTER);
        sampleBtn.setPadding(dp(14), dp(11), dp(14), dp(11));
        sampleBtn.setBackground(StudioStyle.rgbBorderPill(Color.WHITE, dp(20)));
        ImageView wandIcon = new ImageView(this);
        wandIcon.setImageDrawable(new StudioStyle.Icon("wand", StudioStyle.GOOGLE_BLUE, dp(16)));
        sampleBtn.addView(wandIcon, new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView sampleText = text("Mở ảnh mẫu Studio Fujifilm X-T5 (Thử ngay RGB 1 chạm)", 12.5f, StudioStyle.GOOGLE_BLUE);
        sampleText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        sampleText.setPadding(dp(8), 0, 0, 0);
        sampleBtn.addView(sampleText);
        sampleBtn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            loadStudioSamplePhoto(null);
        });
        MotionStudio.attachExpressiveTouch(sampleBtn, 0.96f);
        LinearLayout.LayoutParams sampleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sampleLp.topMargin = dp(10);
        heroCard.addView(sampleBtn, sampleLp);

        return heroCard;
    }

    /**
     * Cột bên phải trên Máy tính bảng / Khổ ngang: Lưới Bento 2×2 mang 4 sắc thái Google RGB (Đỏ, Vàng, Lục, Lam).
     */
    private LinearLayout buildBentoAndFilmShowcaseColumn() {
        LinearLayout col = column();

        // Tiêu đề phần Bento
        LinearLayout sectionHeader = row();
        sectionHeader.setPadding(dp(4), 0, dp(4), dp(8));
        TextView albumTitle = text("Bộ công cụ Quang phổ RGB & Kho màu phim", 14.5f, StudioStyle.INK);
        albumTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        sectionHeader.addView(albumTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView openQuickBtn = text("Xem tất cả 392 ›", 12.5f, StudioStyle.GOOGLE_BLUE);
        openQuickBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        openQuickBtn.setOnClickListener(v -> showLibrary());
        sectionHeader.addView(openQuickBtn);
        col.addView(sectionHeader);

        // Lưới 2×2 Bento Cards với 4 màu chủ đạo Google (Red, Yellow, Green, Blue)
        LinearLayout gridRow1 = row();
        gridRow1.addView(buildAlbumCard("Fuji Chrome", "392 công thức X-Trans", "film", StudioStyle.GOOGLE_RED, this::showLibrary),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        View spacer1 = new View(this);
        gridRow1.addView(spacer1, new LinearLayout.LayoutParams(dp(12), 1));
        gridRow1.addView(buildAlbumCard("Kodak Portra", "Thử ngay màu nắng ấm", "sparkles", StudioStyle.GOOGLE_YELLOW, () -> loadStudioSamplePhoto("Classic Chrome")),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(gridRow1);

        LinearLayout gridRow2 = row();
        gridRow2.setPadding(0, dp(12), 0, 0);
        gridRow2.addView(buildAlbumCard("Fujicolor & B&W", "Velvia, Eterna & Acros", "compare", StudioStyle.GOOGLE_GREEN, () -> loadStudioSamplePhoto("Velvia")),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        View spacer2 = new View(this);
        gridRow2.addView(spacer2, new LinearLayout.LayoutParams(dp(12), 1));
        gridRow2.addView(buildAlbumCard("Khung & EXIF", "30 mẫu viền nghệ thuật", "frame", StudioStyle.GOOGLE_BLUE, () -> {
            selectedTab = EditorToolTab.FRAME;
            frameStyle = 1;
            loadStudioSamplePhoto(null);
        }), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(gridRow2);

        // Thẻ chọn nhanh Film Simulation (Chạm vào chip để mở ngay ảnh mẫu với giả lập màu đó)
        LinearLayout filmShowcase = column();
        filmShowcase.setPadding(dp(14), dp(12), dp(14), dp(12));
        filmShowcase.setBackground(StudioStyle.surface(Color.WHITE, StudioStyle.LINE, dp(22)));
        LinearLayout.LayoutParams showcaseLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        showcaseLp.topMargin = dp(12);

        TextView chipHeader = text("Chạm thử nhanh Giả lập Màu Phim (Film Simulation)", 12, StudioStyle.MUTED);
        chipHeader.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        chipHeader.setPadding(0, 0, 0, dp(8));
        filmShowcase.addView(chipHeader);

        HorizontalScrollView filmScroll = new HorizontalScrollView(this);
        filmScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filmRow = row();
        String[] sampleFilms = {
                "Classic Chrome", "Velvia", "Classic Negative", "Astia",
                "Nostalgic Neg.", "Acros+G", "Eterna", "Reala Ace"
        };
        int[] dotPalette = {
                StudioStyle.GOOGLE_RED, StudioStyle.GOOGLE_YELLOW,
                StudioStyle.GOOGLE_GREEN, StudioStyle.GOOGLE_BLUE
        };
        for (int i = 0; i < sampleFilms.length; i++) {
            final String filmName = sampleFilms[i];
            LinearLayout pill = row();
            pill.setPadding(dp(12), dp(7), dp(12), dp(7));
            pill.setBackground(StudioStyle.surface(StudioStyle.SURFACE, StudioStyle.LINE, dp(18)));

            View colorDot = new View(this);
            colorDot.setBackground(StudioStyle.shape(dotPalette[i % dotPalette.length], 0, dp(4)));
            pill.addView(colorDot, new LinearLayout.LayoutParams(dp(6), dp(6)));

            TextView label = text(filmName, 11.5f, StudioStyle.INK);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            label.setPadding(dp(6), 0, 0, 0);
            pill.addView(label);

            pill.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                loadStudioSamplePhoto(filmName);
            });
            MotionStudio.attachExpressiveTouch(pill, 0.94f);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                lp.leftMargin = dp(8);
            }
            filmRow.addView(pill, lp);
        }
        filmScroll.addView(filmRow);
        filmShowcase.addView(filmScroll);
        col.addView(filmShowcase, showcaseLp);

        return col;
    }

    private View buildAlbumCard(String title, String subtitle, String iconName, int accentColor, Runnable action) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setMinimumHeight(dp(90));
        card.setBackground(StudioStyle.surface(Color.WHITE, StudioStyle.LINE, dp(22)));

        FrameLayout circle = new FrameLayout(this);
        int bgAlphaColor = (accentColor & 0x00ffffff) | 0x20000000;
        circle.setBackground(StudioStyle.shape(bgAlphaColor, 0, dp(16)));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(new StudioStyle.Icon(iconName, accentColor, dp(16)));
        circle.addView(icon, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        card.addView(circle, new LinearLayout.LayoutParams(dp(34), dp(34)));

        View spacer = new View(this);
        card.addView(spacer, new LinearLayout.LayoutParams(1, dp(10)));

        TextView titleView = text(title, 13.5f, StudioStyle.INK);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleView.setSingleLine(true);
        card.addView(titleView);

        TextView subView = text(subtitle, 11.5f, StudioStyle.MUTED);
        subView.setSingleLine(true);
        card.addView(subView);

        card.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            action.run();
        });
        MotionStudio.attachExpressiveTouch(card, 0.96f);
        return card;
    }

    // =========================================================================
    // BỐ CỤC CHỈNH SỬA 3 VÙNG NATIVE CHO MÁY TÍNH BẢNG & KHỔ NGANG (Wide 3-Zone Studio)
    // =========================================================================

    /**
     * Xây dựng giao diện chỉnh sửa 3 vùng chuẩn Google Photos / Material 3 Large Screen cho Tablet & Khổ ngang:
     * <ol>
     *     <li><b>Trái ({@code NavigationRail}):</b> Thanh điều hướng dọc với hiệu ứng viên thuốc (Active Indicator Pill),
     *         nút Đóng ảnh ở đỉnh và nút Tự động cân bằng (Magic Wand) ở đáy.</li>
     *     <li><b>Giữa ({@code Center Canvas Stage}):</b> Thanh công cụ nổi (Recipe/LUT chip + Undo/Redo/Compare + Xuất ảnh)
     *         và vùng hiển thị ảnh lớn tối đa chiều cao màn hình.</li>
     *     <li><b>Phải ({@code Right Studio Inspector Sheet}):</b> Bảng công cụ chuyên sâu rộng {@code 368dp}
     *         với lưới 2 cột thông số tinh chỉnh, lưới 3 cột màu phim và lưới 3 cột cho toàn bộ 30 khung ảnh EXIF.</li>
     * </ol>
     */
    private View buildWideEditorWorkspaceView() {
        LinearLayout rootRow = row();
        rootRow.setGravity(Gravity.FILL_VERTICAL);

        // ---------------------------------------------------------------------
        // VÙNG 1: THANH ĐIỀU HƯỚNG DỌC BÊN TRÁI (Material 3 Navigation Rail)
        // ---------------------------------------------------------------------
        LinearLayout navRail = column();
        navRail.setGravity(Gravity.CENTER_HORIZONTAL);
        navRail.setPadding(dp(8), dp(12), dp(8), dp(12));
        navRail.setBackground(StudioStyle.liquidGlassCard(dp(26)));

        View backBtn = createCircleIconButton("back", StudioStyle.INK, dp(42), this::confirmClosePhoto);
        backBtn.setContentDescription("Đóng ảnh hiện tại");
        navRail.addView(backBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));

        View topRailSpacer = new View(this);
        navRail.addView(topRailSpacer, new LinearLayout.LayoutParams(1, 0, 1f));

        // 3 Tab chính trên Navigation Rail (Tinh chỉnh, Màu phim, Khung ảnh)
        for (EditorToolTab tab : EditorToolTab.values()) {
            LinearLayout itemCol = column();
            itemCol.setGravity(Gravity.CENTER_HORIZONTAL);
            itemCol.setPadding(dp(4), dp(8), dp(4), dp(8));

            FrameLayout pillIndicator = new FrameLayout(this);
            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(new StudioStyle.Icon(tab.icon, StudioStyle.INK, dp(19)));
            pillIndicator.addView(iconView, new FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER));
            itemCol.addView(pillIndicator, new LinearLayout.LayoutParams(dp(56), dp(32)));

            TextView caption = text(tab.title, 11f, StudioStyle.INK);
            caption.setGravity(Gravity.CENTER);
            caption.setPadding(0, dp(4), 0, 0);
            caption.setSingleLine(true);
            itemCol.addView(caption);

            railPillIndicators.put(tab, pillIndicator);
            railPillIcons.put(tab, iconView);
            tabPillButtons.put(tab, caption);

            itemCol.setOnClickListener(v -> switchToolTab(tab));
            MotionStudio.attachExpressiveTouch(itemCol, 0.93f);
            navRail.addView(itemCol, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        // Nút mở nhanh Kho 392 công thức ngay trên Navigation Rail
        LinearLayout libRailItem = column();
        libRailItem.setGravity(Gravity.CENTER_HORIZONTAL);
        libRailItem.setPadding(dp(4), dp(8), dp(4), dp(8));
        FrameLayout libCircle = new FrameLayout(this);
        libCircle.setBackground(StudioStyle.shape(StudioStyle.WARM_AMBER_LIGHT, 0x33e07a3b, dp(16)));
        ImageView libIcon = new ImageView(this);
        libIcon.setImageDrawable(new StudioStyle.Icon("film", StudioStyle.WARM_AMBER, dp(17)));
        libCircle.addView(libIcon, new FrameLayout.LayoutParams(dp(17), dp(17), Gravity.CENTER));
        libRailItem.addView(libCircle, new LinearLayout.LayoutParams(dp(48), dp(30)));
        TextView libCap = text("Kho 392", 10.5f, StudioStyle.WARM_AMBER);
        libCap.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        libCap.setPadding(0, dp(3), 0, 0);
        libRailItem.addView(libCap);
        libRailItem.setOnClickListener(v -> showLibrary());
        MotionStudio.attachExpressiveTouch(libRailItem, 0.93f);
        navRail.addView(libRailItem);

        View bottomRailSpacer = new View(this);
        navRail.addView(bottomRailSpacer, new LinearLayout.LayoutParams(1, 0, 1f));

        // Nút Đũa thần Tự động cân bằng (Auto-Balance) ở đáy Navigation Rail
        LinearLayout autoCol = column();
        autoCol.setGravity(Gravity.CENTER_HORIZONTAL);
        railAutoBalanceCircle = createCircleIconButton("wand", StudioStyle.WARM_AMBER, dp(42), this::performAutoBalance);
        autoCol.addView(railAutoBalanceCircle, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView autoCap = text("Tự động", 10f, StudioStyle.MUTED);
        autoCap.setPadding(0, dp(3), 0, 0);
        autoCol.addView(autoCap);
        navRail.addView(autoCol);

        rootRow.addView(navRail, new LinearLayout.LayoutParams(dp(86), LinearLayout.LayoutParams.MATCH_PARENT));

        // ---------------------------------------------------------------------
        // VÙNG 2: SÂN KHẤU ẢNH TRUNG TÂM RỘNG RÃI (Center Spacious Canvas Stage)
        // ---------------------------------------------------------------------
        LinearLayout centerCol = column();
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        centerLp.leftMargin = dp(14);
        centerLp.rightMargin = dp(14);

        // Thanh điều khiển nổi phía trên bức ảnh (Top Action Bar)
        LinearLayout topBar = row();
        topBar.setPadding(dp(4), 0, dp(4), dp(8));

        // Huy hiệu hiển thị tên màu phim & LUT đang áp dụng (bấm để mở tùy chọn LUT)
        canvasRecipeChip = text("✦ Nguyên bản", 12f, StudioStyle.INK);
        canvasRecipeChip.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        canvasRecipeChip.setPadding(dp(14), dp(8), dp(14), dp(8));
        canvasRecipeChip.setBackground(StudioStyle.liquidGlassPill(dp(20)));
        canvasRecipeChip.setSingleLine(true);
        canvasRecipeChip.setEllipsize(TextUtils.TruncateAt.END);
        canvasRecipeChip.setOnClickListener(v -> showLutOptions());
        MotionStudio.attachExpressiveTouch(canvasRecipeChip, 0.96f);
        topBar.addView(canvasRecipeChip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        View topSpacerLeft = new View(this);
        topBar.addView(topSpacerLeft, new LinearLayout.LayoutParams(0, 1, 1f));

        // Cụm nút Hoàn tác (Undo) / Làm lại (Redo) / Giữ xem Gốc ở giữa
        LinearLayout historyPill = row();
        historyPill.setPadding(dp(4), dp(4), dp(4), dp(4));
        historyPill.setBackground(StudioStyle.liquidGlassCard(dp(22)));

        undoButton = createCircleIconButton("undo", StudioStyle.INK, dp(36), this::performUndo);
        undoButton.setContentDescription("Hoàn tác (Undo)");
        historyPill.addView(undoButton, new LinearLayout.LayoutParams(dp(36), dp(36)));

        redoButton = createCircleIconButton("redo", StudioStyle.INK, dp(36), this::performRedo);
        redoButton.setContentDescription("Làm lại (Redo)");
        LinearLayout.LayoutParams redoLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        redoLp.leftMargin = dp(6);
        historyPill.addView(redoButton, redoLp);

        TextView holdComparePill = text("Giữ xem Gốc", 11.5f, StudioStyle.INK);
        holdComparePill.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        holdComparePill.setPadding(dp(12), dp(7), dp(12), dp(7));
        holdComparePill.setBackground(StudioStyle.shape(StudioStyle.SURFACE, StudioStyle.LINE, dp(18)));
        holdComparePill.setOnTouchListener((v, event) -> handleCompareTouch(v, event));
        LinearLayout.LayoutParams holdLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        holdLp.leftMargin = dp(8);
        historyPill.addView(holdComparePill, holdLp);

        topBar.addView(historyPill);

        View topSpacerRight = new View(this);
        topBar.addView(topSpacerRight, new LinearLayout.LayoutParams(0, 1, 1f));

        // Cụm nút Xuất ảnh & Menu (⋯) bên phải
        LinearLayout rightActions = row();
        TextView exportBtn = text("Xuất ảnh", 13f, Color.WHITE);
        exportBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        exportBtn.setGravity(Gravity.CENTER);
        exportBtn.setPadding(dp(18), dp(9), dp(18), dp(9));
        exportBtn.setBackground(StudioStyle.warmAmberPill(dp(22)));
        exportBtn.setOnClickListener(v -> chooseExport());
        MotionStudio.attachExpressiveTouch(exportBtn, 0.94f);
        rightActions.addView(exportBtn);

        View moreBtn = createCircleTextButton("⋯", StudioStyle.INK, dp(38), this::showMore);
        LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        moreLp.leftMargin = dp(8);
        rightActions.addView(moreBtn, moreLp);
        topBar.addView(rightActions);

        centerCol.addView(topBar);

        // Khung chứa bức ảnh trung tâm (Stage Card)
        FrameLayout stageCard = buildPhotoStageContainer();
        centerCol.addView(stageCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Dòng trạng thái phía dưới bức ảnh
        statusLabel = text("Ảnh được xử lý trực tiếp trên thiết bị", 11.5f, StudioStyle.MUTED);
        statusLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        statusLabel.setPadding(0, dp(6), 0, dp(2));
        statusLabel.setSingleLine(true);
        statusLabel.setEllipsize(TextUtils.TruncateAt.END);
        centerCol.addView(statusLabel);

        rootRow.addView(centerCol, centerLp);

        // ---------------------------------------------------------------------
        // VÙNG 3: BẢNG INSPECTOR BÊN PHẢI (Right Studio Inspector Side Sheet)
        // ---------------------------------------------------------------------
        bottomDockContent = new FrameLayout(this);
        bottomDockContent.setPadding(dp(16), dp(14), dp(16), dp(14));
        bottomDockContent.setBackground(StudioStyle.liquidGlassCard(dp(28)));

        adjustSectionView = buildAdjustSection();
        filtersSectionView = buildFiltersSection();
        framesSectionView = buildFramesSection();
        lightcraftSectionView = buildLightCraftSection();

        bottomDockContent.addView(adjustSectionView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        bottomDockContent.addView(filtersSectionView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        bottomDockContent.addView(framesSectionView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        bottomDockContent.addView(lightcraftSectionView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        int inspectorWidth = Math.min(dp(370), (int) (getResources().getDisplayMetrics().widthPixels * 0.36f));
        inspectorWidth = Math.max(dp(300), inspectorWidth);
        rootRow.addView(bottomDockContent, new LinearLayout.LayoutParams(
                inspectorWidth,
                LinearLayout.LayoutParams.MATCH_PARENT));

        return rootRow;
    }

    // =========================================================================
    // BỐ CỤC CHỈNH SỬA DỌC CHO ĐIỆN THOẠI (Portrait Editor Workspace)
    // =========================================================================

    private View buildPortraitEditorWorkspaceView() {
        LinearLayout workspace = column();

        // 1. HEADER: Nút Đóng ảnh (<) | Undo (↶) & Redo (↷) | Xuất & Menu (⋯)
        LinearLayout header = row();
        header.setPadding(dp(2), 0, dp(2), dp(6));

        View backBtn = createCircleIconButton("back", StudioStyle.INK, dp(38), this::confirmClosePhoto);
        backBtn.setContentDescription("Đóng ảnh hiện tại");
        header.addView(backBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));

        View leftSpacer = new View(this);
        header.addView(leftSpacer, new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout undoRedoGroup = row();
        undoButton = createCircleIconButton("undo", StudioStyle.INK, dp(36), this::performUndo);
        undoButton.setContentDescription("Hoàn tác (Undo)");
        undoRedoGroup.addView(undoButton, new LinearLayout.LayoutParams(dp(36), dp(36)));

        redoButton = createCircleIconButton("redo", StudioStyle.INK, dp(36), this::performRedo);
        redoButton.setContentDescription("Làm lại (Redo)");
        LinearLayout.LayoutParams redoParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        redoParams.leftMargin = dp(8);
        undoRedoGroup.addView(redoButton, redoParams);
        header.addView(undoRedoGroup);

        View rightSpacer = new View(this);
        header.addView(rightSpacer, new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout rightGroup = row();
        TextView exportBtn = text("Xuất", 12.5f, Color.WHITE);
        exportBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        exportBtn.setGravity(Gravity.CENTER);
        exportBtn.setPadding(dp(14), dp(7), dp(14), dp(7));
        exportBtn.setBackground(StudioStyle.warmAmberPill(dp(20)));
        exportBtn.setOnClickListener(v -> chooseExport());
        MotionStudio.attachExpressiveTouch(exportBtn, 0.94f);
        rightGroup.addView(exportBtn);

        View moreBtn = createCircleTextButton("⋯", StudioStyle.INK, dp(36), this::showMore);
        moreBtn.setContentDescription("Thêm tùy chọn");
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        moreParams.leftMargin = dp(8);
        rightGroup.addView(moreBtn, moreParams);
        header.addView(rightGroup);

        workspace.addView(header);

        // 2. SUB-HEADER FLOATING MODE SWITCHER PILL (Tinh chỉnh | Màu phim | Khung ảnh | LightCraft)
        FrameLayout pillWrapper = new FrameLayout(this);
        LinearLayout modePill = row();
        modePill.setPadding(dp(3), dp(3), dp(3), dp(3));
        modePill.setBackground(StudioStyle.shape(0x0c000000, StudioStyle.LINE, dp(20)));

        for (EditorToolTab tab : EditorToolTab.values()) {
            TextView tabBtn = text(tab.title, 11.5f, StudioStyle.INK);
            tabBtn.setGravity(Gravity.CENTER);
            tabBtn.setPadding(dp(11), dp(6), dp(11), dp(6));
            tabBtn.setOnClickListener(v -> switchToolTab(tab));
            MotionStudio.attachExpressiveTouch(tabBtn, 0.94f);
            tabPillButtons.put(tab, tabBtn);
            modePill.addView(tabBtn, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        pillWrapper.addView(modePill, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        workspace.addView(pillWrapper, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // 3. SPACIOUS PHOTO STAGE
        FrameLayout stage = buildPhotoStageContainer();
        workspace.addView(stage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        statusLabel = text("Ảnh được xử lý trên thiết bị", 11, StudioStyle.MUTED);
        statusLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        statusLabel.setPadding(0, dp(2), 0, dp(4));
        statusLabel.setSingleLine(true);
        statusLabel.setEllipsize(TextUtils.TruncateAt.END);
        workspace.addView(statusLabel);

        // 4. LIQUID GLASS BOTTOM CONTROLS DOCK
        bottomDockContent = new FrameLayout(this);
        bottomDockContent.setPadding(dp(14), dp(12), dp(14), dp(12));
        bottomDockContent.setBackground(StudioStyle.liquidGlassCard(dp(28)));

        adjustSectionView = buildAdjustSection();
        filtersSectionView = buildFiltersSection();
        framesSectionView = buildFramesSection();
        lightcraftSectionView = buildLightCraftSection();

        bottomDockContent.addView(adjustSectionView);
        bottomDockContent.addView(filtersSectionView);
        bottomDockContent.addView(framesSectionView);
        bottomDockContent.addView(lightcraftSectionView);

        workspace.addView(bottomDockContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        return workspace;
    }

    /**
     * Khung hiển thị bức ảnh chính (dùng chung cho cả chế độ Khổ ngang/Tablet và Điện thoại dọc),
     * tích hợp lớp phủ kéo ghim mặt nạ cục bộ {@link LightCraftView.StageMaskOverlayView} cùng cử chỉ nhấn giữ xem ảnh GỐC.
     */
    private FrameLayout buildPhotoStageContainer() {
        FrameLayout stage = new FrameLayout(this);
        stage.setPadding(dp(8), dp(8), dp(8), dp(8));
        if (wideLayoutActive) {
            stage.setBackground(StudioStyle.shape(0x88ffffff, StudioStyle.LINE, dp(24)));
        }

        imageStageView = new ImageView(this);
        imageStageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageStageView.setContentDescription("Ảnh đang chỉnh sửa (Chạm và giữ để xem ảnh gốc)");
        imageStageView.setOnTouchListener(this::handleCompareTouch);
        stage.addView(imageStageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Lớp phủ điều khiển ghim mặt nạ cục bộ (Radial / Linear Gradient Mask) chuẩn LightCraft Darkroom
        // (Tái sử dụng instance khi xoay ngang/dọc hoặc gập mở màn hình để bảo toàn vị trí ghim)
        if (stageMaskOverlayView == null) {
            stageMaskOverlayView = new LightCraftView.StageMaskOverlayView(this, lightCraftState, this::showImage);
        } else if (stageMaskOverlayView.getParent() instanceof ViewGroup) {
            ((ViewGroup) stageMaskOverlayView.getParent()).removeView(stageMaskOverlayView);
        }
        stage.addView(stageMaskOverlayView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Huy hiệu "ĐANG XEM ẢNH GỐC" nổi lên với hiệu ứng đàn hồi khi chạm giữ
        compareOriginBadge = text("ĐANG XEM ẢNH GỐC", 11.5f, Color.WHITE);
        compareOriginBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        compareOriginBadge.setPadding(dp(14), dp(6), dp(14), dp(6));
        compareOriginBadge.setBackground(StudioStyle.shape(0xd814161a, 0x66ffffff, dp(18)));
        compareOriginBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        badgeParams.setMargins(dp(10), dp(14), dp(10), dp(10));
        stage.addView(compareOriginBadge, badgeParams);

        return stage;
    }

    private boolean handleCompareTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isComparing) {
                    isComparing = true;
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    MotionStudio.animateCompareMode(imageStageView, compareOriginBadge, true);
                    showImage();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isComparing) {
                    isComparing = false;
                    MotionStudio.animateCompareMode(imageStageView, compareOriginBadge, false);
                    showImage();
                }
                return true;
        }
        return false;
    }

    private void switchToolTab(EditorToolTab newTab) {
        if (newTab == selectedTab) {
            return;
        }
        int direction = newTab.ordinal() - selectedTab.ordinal();
        selectedTab = newTab;
        if (stageMaskOverlayView != null) {
            stageMaskOverlayView.setInteractiveEnabled(selectedTab == EditorToolTab.LIGHTCRAFT && lightCraftSubTab == 3);
        }
        if (rootStack != null) {
            rootStack.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        syncModeSwitcherTabs();

        View targetPanel = (selectedTab == EditorToolTab.ADJUST) ? adjustSectionView
                : (selectedTab == EditorToolTab.FILTERS ? filtersSectionView
                : (selectedTab == EditorToolTab.FRAME ? framesSectionView : lightcraftSectionView));
        MotionStudio.animateSharedAxisTab(targetPanel, wideLayoutActive, direction);
    }

    /**
     * Xây dựng phân hệ Tab 1: {@code adjustSection} theo ngôn ngữ Google RGB Studio.
     * <ul>
     *     <li>Đầu bảng tích hợp biểu đồ sóng {@link RgbHistogramView} thời gian thực (Red / Green / Blue)
     *         và thước chia vạch quang phổ {@link TickRulerView} tự động đổi màu theo kênh đang chọn.</li>
     *     <li>Trên Tablet / Khổ ngang: Bên dưới là lưới 2 cột hiển thị toàn bộ 14 thông số được mã hóa màu theo kênh RGB.</li>
     *     <li>Trên Điện thoại dọc: Giữ dải nút tròn cuộn ngang gọn gàng.</li>
     * </ul>
     */
    private View buildAdjustSection() {
        LinearLayout section = column();

        // 1. Biểu đồ sóng quang phổ RGB thời gian thực (chạm để lọc riêng Kênh R / G / B)
        rgbHistogramView = new RgbHistogramView(this);
        rgbHistogramView.setChannelClickListener(mode -> {
            if (mode == RgbHistogramView.MODE_RED) {
                selectedAdjust = AdjustParam.RED;
            } else if (mode == RgbHistogramView.MODE_GREEN) {
                selectedAdjust = AdjustParam.CLARITY;
            } else if (mode == RgbHistogramView.MODE_BLUE) {
                selectedAdjust = AdjustParam.BLUE;
            }
            syncAdjustSection();
        });
        LinearLayout.LayoutParams histLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                wideLayoutActive ? dp(78) : dp(62));
        histLp.bottomMargin = dp(8);
        section.addView(rgbHistogramView, histLp);

        // 2. Hàng thông tin thông số: Icon + Tên thông số đang chọn | Nút DR | Huy hiệu giá trị | Nút Đặt lại lẻ
        LinearLayout topRow = row();
        topRow.setPadding(dp(2), 0, dp(2), dp(6));

        activeParamIconView = new ImageView(this);
        topRow.addView(activeParamIconView, new LinearLayout.LayoutParams(dp(16), dp(16)));

        activeParamTitleView = text("", 13.5f, StudioStyle.INK);
        activeParamTitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        activeParamTitleView.setPadding(dp(6), 0, 0, 0);
        topRow.addView(activeParamTitleView);

        View spacer = new View(this);
        topRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        // Nút chuyển nhanh Dynamic Range (DR100 / DR200 / DR400)
        drBadgeButton = text("DR100", 11, StudioStyle.MUTED);
        drBadgeButton.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        drBadgeButton.setPadding(dp(9), dp(4), dp(9), dp(4));
        drBadgeButton.setBackground(StudioStyle.shape(0xccffffff, StudioStyle.LINE, dp(12)));
        drBadgeButton.setOnClickListener(v -> {
            recordUndo();
            settings.dr = (settings.dr == 100) ? 200 : (settings.dr == 200 ? 400 : 100);
            isAutoBalanced = false;
            syncAdjustSection();
            MotionStudio.popBadge(drBadgeButton);
            persist();
            requestRender();
        });
        LinearLayout.LayoutParams drParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        drParams.rightMargin = dp(6);
        topRow.addView(drBadgeButton, drParams);

        // Huy hiệu hiển thị giá trị số hiện tại của thông số đang chọn
        activeParamValueBadge = text("+0.0", 12, StudioStyle.MUTED);
        activeParamValueBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        activeParamValueBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        topRow.addView(activeParamValueBadge);

        // Nút đặt lại riêng thông số đang chọn về mặc định
        activeParamResetBtn = createCircleIconButton("reset", StudioStyle.MUTED, dp(28), () -> {
            recordUndo();
            selectedAdjust.write(settings, selectedAdjust.defaultValue);
            isAutoBalanced = false;
            syncAdjustSection();
            MotionStudio.popBadge(activeParamValueBadge);
            persist();
            requestRender();
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        resetParams.leftMargin = dp(6);
        topRow.addView(activeParamResetBtn, resetParams);

        section.addView(topRow);

        // 3. Hàng giữa: Thước chia vạch chính xác cao có dải quang phổ RGB (TickRulerView)
        tickRulerView = new TickRulerView(this);
        tickRulerView.setListener(new TickRulerView.Listener() {
            @Override
            public void onValueChanged(double newValue) {
                if (!sliderUndoRecorded) {
                    recordUndo();
                    sliderUndoRecorded = true;
                }
                selectedAdjust.write(settings, newValue);
                isAutoBalanced = false;
                syncAdjustHeaderOnly();
                requestRender();
            }

            @Override
            public void onEditingChanged(boolean isDragging) {
                if (!isDragging) {
                    sliderUndoRecorded = false;
                    syncAdjustSection();
                    persist();
                }
            }
        });
        section.addView(tickRulerView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        if (wideLayoutActive) {
            // TRÊN TABLET & KHỔ NGANG: Khung cuộn dọc chứa lưới 2 cột thông số tinh chỉnh
            ScrollView gridScroll = new ScrollView(this);
            gridScroll.setVerticalScrollBarEnabled(false);
            gridScroll.setPadding(0, dp(8), 0, 0);
            adjustStripContainer = column();
            gridScroll.addView(adjustStripContainer);
            section.addView(gridScroll, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            // TRÊN ĐIỆN THOẠI DỌC: Dải nút tròn cuộn ngang
            HorizontalScrollView stripScroll = new HorizontalScrollView(this);
            stripScroll.setHorizontalScrollBarEnabled(false);
            stripScroll.setPadding(0, dp(8), 0, 0);
            adjustStripContainer = row();
            stripScroll.addView(adjustStripContainer);
            section.addView(stripScroll);
        }

        return section;
    }

    /**
     * Xây dựng phân hệ Tab 2: {@code filtersSection}.
     * Trên Tablet / Khổ ngang hiển thị lưới 3 cột ảnh thu nhỏ màu phim; trên Điện thoại dọc hiển thị dải ngang.
     */
    private View buildFiltersSection() {
        LinearLayout section = column();

        LinearLayout topRow = row();
        topRow.setPadding(dp(2), 0, dp(2), dp(8));

        ImageView sparkIcon = new ImageView(this);
        sparkIcon.setImageDrawable(new StudioStyle.Icon("sparkles", StudioStyle.WARM_AMBER, dp(15)));
        topRow.addView(sparkIcon, new LinearLayout.LayoutParams(dp(15), dp(15)));

        filterActiveRecipeTitle = text("Nguyên bản", 13f, StudioStyle.INK);
        filterActiveRecipeTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        filterActiveRecipeTitle.setPadding(dp(6), 0, dp(6), 0);
        filterActiveRecipeTitle.setSingleLine(true);
        filterActiveRecipeTitle.setEllipsize(TextUtils.TruncateAt.END);
        topRow.addView(filterActiveRecipeTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        filterLutStatusBadge = text("＋ LUT", 11, StudioStyle.INK);
        filterLutStatusBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        filterLutStatusBadge.setPadding(dp(9), dp(4), dp(9), dp(4));
        filterLutStatusBadge.setBackground(StudioStyle.shape(Color.WHITE, StudioStyle.LINE, dp(14)));
        filterLutStatusBadge.setOnClickListener(v -> showLutOptions());
        LinearLayout.LayoutParams lutBadgeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lutBadgeParams.rightMargin = dp(6);
        topRow.addView(filterLutStatusBadge, lutBadgeParams);

        TextView openLibraryPill = text("+ Kho 392 màu", 11.5f, StudioStyle.WARM_AMBER);
        openLibraryPill.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        openLibraryPill.setPadding(dp(10), dp(4), dp(10), dp(4));
        openLibraryPill.setBackground(StudioStyle.shape(StudioStyle.WARM_AMBER_LIGHT, 0x44e07a3b, dp(14)));
        openLibraryPill.setOnClickListener(v -> showLibrary());
        topRow.addView(openLibraryPill);

        section.addView(topRow);

        if (wideLayoutActive) {
            ScrollView vScroll = new ScrollView(this);
            vScroll.setVerticalScrollBarEnabled(false);
            filterCarouselContainer = column();
            vScroll.addView(filterCarouselContainer);
            section.addView(vScroll, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            HorizontalScrollView hScroll = new HorizontalScrollView(this);
            hScroll.setHorizontalScrollBarEnabled(false);
            filterCarouselContainer = row();
            hScroll.addView(filterCarouselContainer);
            section.addView(hScroll);
        }

        return section;
    }

    /**
     * Xây dựng phân hệ Tab 3: {@code framesSection}.
     * Trên Tablet / Khổ ngang hiển thị đầy đủ 30 mẫu khung trong lưới 3 cột cuộn dọc trực tiếp.
     */
    private View buildFramesSection() {
        LinearLayout section = column();

        LinearLayout topRow = row();
        topRow.setPadding(dp(2), 0, dp(2), dp(8));

        ImageView frameIcon = new ImageView(this);
        frameIcon.setImageDrawable(new StudioStyle.Icon("frame", StudioStyle.WARM_AMBER, dp(15)));
        topRow.addView(frameIcon, new LinearLayout.LayoutParams(dp(15), dp(15)));

        frameActiveStyleTitle = text("Khung: Không viền", 13f, StudioStyle.INK);
        frameActiveStyleTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        frameActiveStyleTitle.setPadding(dp(6), 0, dp(6), 0);
        frameActiveStyleTitle.setSingleLine(true);
        frameActiveStyleTitle.setEllipsize(TextUtils.TruncateAt.END);
        topRow.addView(frameActiveStyleTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView editExifPill = text("✎ Sửa EXIF", 11.5f, StudioStyle.WARM_AMBER);
        editExifPill.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        editExifPill.setPadding(dp(10), dp(4), dp(10), dp(4));
        editExifPill.setBackground(StudioStyle.shape(StudioStyle.WARM_AMBER_LIGHT, 0x44e07a3b, dp(14)));
        editExifPill.setOnClickListener(v -> editPhotoFrame());
        topRow.addView(editExifPill);

        section.addView(topRow);

        if (wideLayoutActive) {
            ScrollView vScroll = new ScrollView(this);
            vScroll.setVerticalScrollBarEnabled(false);
            frameCarouselContainer = column();
            vScroll.addView(frameCarouselContainer);
            section.addView(vScroll, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            HorizontalScrollView hScroll = new HorizontalScrollView(this);
            hScroll.setHorizontalScrollBarEnabled(false);
            frameCarouselContainer = row();
            hScroll.addView(frameCarouselContainer);
            section.addView(hScroll);
        }

        return section;
    }

    /**
     * Xây dựng phân hệ Tab 4: {@code lightcraftSection} — tích hợp bộ công cụ phòng tối chuyên sâu từ dự án <b>LightCraft</b>
     * ({@code https://github.com/cnttdlpt/lightcraft.git}):
     * <ul>
     *     <li><b>{@code Tone Curve} ({@link LightCraftView.ToneCurveGraphView}):</b> Đường cong Monotone Cubic Hermite Spline
     *         (Fritsch–Carlson) cho 4 kênh {@code Master / Red / Green / Blue} + Đường cong tham số 4 vùng
     *         ({@code Shadows / Darks / Lights / Highlights}).</li>
     *     <li><b>{@code 3-Way Color Grading} ({@link LightCraftView.GradingWheelsView}):</b> 3 bánh xe màu cảm nhận thị giác
     *         OkLab / OkLCh cho {@code Vùng tối · Trung gian · Vùng sáng} kèm {@code Blending} và {@code Balance}.</li>
     *     <li><b>{@code 8-Band HSL Color Mixer}:</b> Bộ trộn 8 dải màu ({@code Đỏ, Cam, Vàng, Lục, Ngọc, Lam, Tím, Hồng})
     *         theo hàm trọng số Raised-Cosine Partition-of-Unity.</li>
     *     <li><b>{@code Local Masking & 12 Darkroom Presets}:</b> Mặt nạ cục bộ ({@code Radial, Linear, Sky, Subject})
     *         và 12 preset màu phòng tối chuẩn LightCraft.</li>
     * </ul>
     */
    private View buildLightCraftSection() {
        LinearLayout section = column();

        // 1. Tiêu đề phân hệ LightCraft Darkroom
        LinearLayout topRow = row();
        topRow.setPadding(dp(2), 0, dp(2), dp(6));

        ImageView lcIcon = new ImageView(this);
        lcIcon.setImageDrawable(new StudioStyle.Icon("lightcraft", StudioStyle.GOOGLE_BLUE, dp(16)));
        topRow.addView(lcIcon, new LinearLayout.LayoutParams(dp(16), dp(16)));

        TextView lcTitle = text("LightCraft · Tone Curve & OkLab Grading", 13f, StudioStyle.INK);
        lcTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        lcTitle.setPadding(dp(6), 0, dp(6), 0);
        lcTitle.setSingleLine(true);
        lcTitle.setEllipsize(TextUtils.TruncateAt.END);
        topRow.addView(lcTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView resetLcBtn = text("Đặt lại LightCraft", 11f, StudioStyle.GOOGLE_RED);
        resetLcBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        resetLcBtn.setPadding(dp(9), dp(4), dp(9), dp(4));
        resetLcBtn.setBackground(StudioStyle.shape(StudioStyle.RED_TONAL, 0x44ea4335, dp(12)));
        resetLcBtn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            lightCraftState.resetAll();
            syncLightCraftSection();
            showImage();
            message("Đã đặt lại toàn bộ thông số LightCraft về mặc định");
        });
        topRow.addView(resetLcBtn);
        section.addView(topRow);

        // 2. Dải 4 tab con của LightCraft (Đường cong | 3-Way Grading | 8-Kênh HSL | Mask & Preset)
        HorizontalScrollView subTabScroll = new HorizontalScrollView(this);
        subTabScroll.setHorizontalScrollBarEnabled(false);
        subTabScroll.setPadding(0, 0, 0, dp(6));
        lightCraftSubTabRow = row();
        subTabScroll.addView(lightCraftSubTabRow);
        section.addView(subTabScroll);

        // 3. Đồ thị Đường cong Tone Curve tương tác (hiển thị khi chọn tab con 0: Đường cong)
        toneCurveGraphView = new LightCraftView.ToneCurveGraphView(this, lightCraftState, this::showImage);
        LinearLayout.LayoutParams curveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                wideLayoutActive ? dp(178) : dp(152));
        curveLp.bottomMargin = dp(6);
        section.addView(toneCurveGraphView, curveLp);

        // 4. Cụm 3 Bánh xe màu OkLab/OkLCh 3-Way Color Grading (hiển thị khi chọn tab con 1: 3-Way Grading)
        gradingWheelsView = new LightCraftView.GradingWheelsView(this, lightCraftState, this::showImage);
        LinearLayout.LayoutParams wheelsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                wideLayoutActive ? dp(156) : dp(138));
        wheelsLp.bottomMargin = dp(6);
        section.addView(gradingWheelsView, wheelsLp);

        // 5. Khung cuộn chứa các thanh trượt chi tiết theo từng phân hệ con của LightCraft
        ScrollView detailScroll = new ScrollView(this);
        detailScroll.setVerticalScrollBarEnabled(false);
        lightCraftDetailContainer = column();
        detailScroll.addView(lightCraftDetailContainer);
        section.addView(detailScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                wideLayoutActive ? 0 : dp(148),
                wideLayoutActive ? 1f : 0f));

        return section;
    }

    private View createCircleIconButton(String iconType, int tintColor, int sizePx, Runnable action) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(StudioStyle.liquidGlassCircle());
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(new StudioStyle.Icon(iconType, tintColor, (int) (sizePx * 0.46f)));
        frame.addView(icon, new FrameLayout.LayoutParams(
                (int) (sizePx * 0.46f),
                (int) (sizePx * 0.46f),
                Gravity.CENTER));
        frame.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            action.run();
        });
        MotionStudio.attachExpressiveTouch(frame, 0.91f);
        return frame;
    }

    private View createCircleTextButton(String label, int textColor, int sizePx, Runnable action) {
        TextView btn = text(label, 15, textColor);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(StudioStyle.liquidGlassCircle());
        btn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            action.run();
        });
        MotionStudio.attachExpressiveTouch(btn, 0.91f);
        return btn;
    }

    // =========================================================================
    // ĐỒNG BỘ TRẠNG THÁI GIAO DIỆN & CHUYỂN ĐỘNG MATERIAL 3 EXPRESSIVE
    // =========================================================================

    private void updateWorkspaceVisibility() {
        boolean hasPhoto = (preview != null);
        if (emptyLandingView == null || editorWorkspaceView == null) {
            return;
        }
        if (hasPhoto != wasShowingPhoto) {
            wasShowingPhoto = hasPhoto;
            if (hasPhoto) {
                MotionStudio.transitionScreens(emptyLandingView, editorWorkspaceView, true);
            } else {
                MotionStudio.transitionScreens(editorWorkspaceView, emptyLandingView, false);
            }
        } else {
            emptyLandingView.setVisibility(hasPhoto ? View.GONE : View.VISIBLE);
            editorWorkspaceView.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        }
    }

    private void syncAllUiControls() {
        updateWorkspaceVisibility();
        syncUndoRedoButtons();
        syncModeSwitcherTabs();
        syncAdjustSection();
        syncFiltersSection();
        syncFramesSection();
        syncLightCraftSection();
    }

    private void syncUndoRedoButtons() {
        if (undoButton != null) {
            undoButton.setEnabled(history.canUndo());
            undoButton.setAlpha(history.canUndo() ? 1.0f : 0.35f);
        }
        if (redoButton != null) {
            redoButton.setEnabled(history.canRedo());
            redoButton.setAlpha(history.canRedo() ? 1.0f : 0.35f);
        }
    }

    private void syncModeSwitcherTabs() {
        for (Map.Entry<EditorToolTab, TextView> entry : tabPillButtons.entrySet()) {
            EditorToolTab tab = entry.getKey();
            boolean active = (tab == selectedTab);
            TextView label = entry.getValue();
            label.setTypeface(Typeface.create("sans-serif-medium", active ? Typeface.BOLD : Typeface.NORMAL));

            if (wideLayoutActive) {
                // Cập nhật trạng thái Navigation Rail chuẩn Material 3 trên Tablet / Khổ ngang
                label.setTextColor(active ? StudioStyle.WARM_AMBER : StudioStyle.MUTED);
                FrameLayout pill = railPillIndicators.get(tab);
                ImageView icon = railPillIcons.get(tab);
                if (pill != null) {
                    pill.setBackground(active
                            ? (tab == EditorToolTab.LIGHTCRAFT ? StudioStyle.rgbSpectrumPill(dp(16)) : StudioStyle.shape(StudioStyle.WARM_AMBER, 0, dp(16)))
                            : StudioStyle.shape(0x00000000, 0, dp(16)));
                    if (active) {
                        MotionStudio.popBadge(pill);
                    }
                }
                if (icon != null) {
                    icon.setImageDrawable(new StudioStyle.Icon(
                            tab.icon,
                            active ? Color.WHITE : StudioStyle.INK,
                            dp(19)));
                }
            } else {
                // Cập nhật thanh Mode Pill trên Điện thoại dọc
                label.setTextColor(active ? StudioStyle.INK : StudioStyle.MUTED);
                label.setBackground(active ? StudioStyle.shape(Color.WHITE, StudioStyle.LINE, dp(18)) : null);
            }
        }
        if (adjustSectionView != null) {
            adjustSectionView.setVisibility(selectedTab == EditorToolTab.ADJUST ? View.VISIBLE : View.GONE);
        }
        if (filtersSectionView != null) {
            filtersSectionView.setVisibility(selectedTab == EditorToolTab.FILTERS ? View.VISIBLE : View.GONE);
        }
        if (framesSectionView != null) {
            framesSectionView.setVisibility(selectedTab == EditorToolTab.FRAME ? View.VISIBLE : View.GONE);
        }
        if (lightcraftSectionView != null) {
            lightcraftSectionView.setVisibility(selectedTab == EditorToolTab.LIGHTCRAFT ? View.VISIBLE : View.GONE);
        }
    }

    private void syncAdjustHeaderOnly() {
        if (activeParamIconView == null) return;
        int chColor = selectedAdjust.channelColor();
        int chTonal = StudioStyle.tonalForChannel(chColor);
        activeParamIconView.setImageDrawable(new StudioStyle.Icon(selectedAdjust.icon, chColor, dp(16)));
        activeParamTitleView.setText(selectedAdjust.label);

        boolean modified = selectedAdjust.isModified(settings);
        activeParamValueBadge.setText(selectedAdjust.formatValue(settings));
        activeParamValueBadge.setTextColor(modified ? chColor : StudioStyle.MUTED);
        activeParamValueBadge.setBackground(StudioStyle.shape(
                modified ? chTonal : 0xccffffff,
                modified ? ((chColor & 0x00ffffff) | 0x55000000) : StudioStyle.LINE,
                dp(12)));

        activeParamResetBtn.setVisibility(modified ? View.VISIBLE : View.GONE);

        drBadgeButton.setText("DR" + settings.dr);
        drBadgeButton.setTextColor(settings.dr != 100 ? StudioStyle.GOOGLE_YELLOW : StudioStyle.MUTED);
        drBadgeButton.setBackground(StudioStyle.shape(
                settings.dr != 100 ? StudioStyle.YELLOW_TONAL : 0xccffffff,
                settings.dr != 100 ? 0x55f29900 : StudioStyle.LINE,
                dp(12)));
    }

    private void syncAdjustSection() {
        if (tickRulerView == null || adjustStripContainer == null) {
            return;
        }
        syncAdjustHeaderOnly();
        tickRulerView.setAccentColor(selectedAdjust.channelColor());
        tickRulerView.configure(
                selectedAdjust.read(settings),
                selectedAdjust.min,
                selectedAdjust.max,
                selectedAdjust.step);

        if (railAutoBalanceCircle != null) {
            railAutoBalanceCircle.setBackground(isAutoBalanced
                    ? StudioStyle.rgbSpectrumPill(dp(21))
                    : StudioStyle.liquidGlassCircle());
        }

        adjustStripContainer.removeAllViews();

        if (wideLayoutActive) {
            // TRÊN TABLET & KHỔ NGANG: Hiển thị nút Tự động cân bằng quang phổ RGB + Lưới 2 cột thông số trực quan
            LinearLayout autoBanner = row();
            autoBanner.setPadding(dp(12), dp(10), dp(12), dp(10));
            autoBanner.setBackground(isAutoBalanced
                    ? StudioStyle.rgbSpectrumPill(dp(18))
                    : StudioStyle.rgbBorderPill(Color.WHITE, dp(18)));
            ImageView wand = new ImageView(this);
            wand.setImageDrawable(new StudioStyle.Icon("wand", isAutoBalanced ? Color.WHITE : StudioStyle.GOOGLE_BLUE, dp(16)));
            autoBanner.addView(wand, new LinearLayout.LayoutParams(dp(16), dp(16)));
            TextView autoTitle = text(
                    isAutoBalanced ? "Đã bật Tự động cân bằng AI (RGB)" : "Tự động cân bằng quang phổ RGB",
                    12.5f,
                    isAutoBalanced ? Color.WHITE : StudioStyle.INK);
            autoTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            autoTitle.setPadding(dp(8), 0, 0, 0);
            autoBanner.addView(autoTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            autoBanner.setOnClickListener(v -> performAutoBalance());
            MotionStudio.attachExpressiveTouch(autoBanner, 0.96f);
            LinearLayout.LayoutParams autoBannerLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            autoBannerLp.bottomMargin = dp(8);
            adjustStripContainer.addView(autoBanner, autoBannerLp);

            AdjustParam[] allParams = AdjustParam.values();
            for (int i = 0; i < allParams.length; i += 2) {
                LinearLayout pairRow = row();
                pairRow.setPadding(0, dp(4), 0, dp(4));

                View leftCard = buildWideAdjustParamCard(allParams[i]);
                pairRow.addView(leftCard, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                View gap = new View(this);
                pairRow.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));

                if (i + 1 < allParams.length) {
                    View rightCard = buildWideAdjustParamCard(allParams[i + 1]);
                    pairRow.addView(rightCard, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                } else {
                    View emptySlot = new View(this);
                    pairRow.addView(emptySlot, new LinearLayout.LayoutParams(0, 1, 1f));
                }
                adjustStripContainer.addView(pairRow);
            }
        } else {
            // TRÊN ĐIỆN THOẠI DỌC: Dải nút tròn cuộn ngang
            adjustStripContainer.addView(buildCircularToolItem(
                    "Tự động",
                    "wand",
                    null,
                    isAutoBalanced,
                    false,
                    StudioStyle.GOOGLE_BLUE,
                    this::performAutoBalance));

            for (AdjustParam param : AdjustParam.values()) {
                boolean isSelected = (selectedAdjust == param);
                boolean modified = param.isModified(settings);
                String valueInsideCircle = (isSelected && modified) ? param.formatValue(settings) : null;

                View item = buildCircularToolItem(
                        param.label,
                        param.icon,
                        valueInsideCircle,
                        isSelected,
                        modified,
                        param.channelColor(),
                        () -> {
                            selectedAdjust = param;
                            syncAdjustSection();
                        });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = dp(10);
                adjustStripContainer.addView(item, lp);
            }
        }
    }

    /**
     * Thẻ thông số dạng viên thuốc ngang (Material 3 RGB Tonal Chip Card) dùng cho lưới 2 cột trên Tablet & Khổ ngang.
     * Tự động đổi màu theo kênh RGB của từng thông số ({@link AdjustParam#channelColor()}).
     */
    private View buildWideAdjustParamCard(final AdjustParam param) {
        boolean isSelected = (selectedAdjust == param);
        boolean modified = param.isModified(settings);
        int chColor = param.channelColor();
        int chTonal = StudioStyle.tonalForChannel(chColor);

        LinearLayout card = row();
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        int bgColor = isSelected ? chColor : (modified ? chTonal : Color.WHITE);
        int borderColor = isSelected ? 0
                : (modified ? ((chColor & 0x00ffffff) | 0x55000000) : StudioStyle.LINE);
        card.setBackground(StudioStyle.surface(bgColor, borderColor, dp(16)));

        int iconColor = isSelected ? Color.WHITE : chColor;
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(new StudioStyle.Icon(param.icon, iconColor, dp(15)));
        card.addView(icon, new LinearLayout.LayoutParams(dp(15), dp(15)));

        TextView label = text(param.label, 11.5f, isSelected ? Color.WHITE : StudioStyle.INK);
        label.setTypeface(Typeface.create("sans-serif-medium", (isSelected || modified) ? Typeface.BOLD : Typeface.NORMAL));
        label.setPadding(dp(6), 0, dp(4), 0);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView val = text(param.formatValue(settings), 11f,
                isSelected ? Color.WHITE : (modified ? chColor : StudioStyle.MUTED));
        val.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        card.addView(val);

        card.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            selectedAdjust = param;
            syncAdjustSection();
            MotionStudio.popBadge(activeParamValueBadge);
        });
        MotionStudio.attachExpressiveTouch(card, 0.95f);
        return card;
    }

    private View buildCircularToolItem(String label,
                                       String iconName,
                                       String overrideCenterText,
                                       boolean isSelected,
                                       boolean isModified,
                                       Runnable onClick) {
        return buildCircularToolItem(label, iconName, overrideCenterText, isSelected, isModified, StudioStyle.GOOGLE_BLUE, onClick);
    }

    private View buildCircularToolItem(String label,
                                       String iconName,
                                       String overrideCenterText,
                                       boolean isSelected,
                                       boolean isModified,
                                       int accentColor,
                                       Runnable onClick) {
        LinearLayout wrapper = column();
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout circle = new FrameLayout(this);
        circle.setBackground(StudioStyle.shape(
                isSelected ? accentColor : 0xd9ffffff,
                isSelected ? 0 : StudioStyle.LINE,
                dp(24)));

        if (overrideCenterText != null) {
            TextView valText = text(overrideCenterText, 11.5f, Color.WHITE);
            valText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            valText.setGravity(Gravity.CENTER);
            circle.addView(valText, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            int iconColor = isSelected ? Color.WHITE : accentColor;
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(new StudioStyle.Icon(iconName, iconColor, dp(18)));
            circle.addView(icon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        }

        if (!isSelected && isModified) {
            View dot = new View(this);
            dot.setBackground(StudioStyle.shape(accentColor, 0, dp(3)));
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(5), dp(5), Gravity.TOP | Gravity.END);
            dotParams.setMargins(0, dp(6), dp(6), 0);
            circle.addView(dot, dotParams);
        }

        wrapper.addView(circle, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView caption = text(label, 10.5f, isSelected ? accentColor : StudioStyle.MUTED);
        caption.setTypeface(Typeface.create("sans-serif-medium", isSelected ? Typeface.BOLD : Typeface.NORMAL));
        caption.setPadding(0, dp(4), 0, 0);
        caption.setSingleLine(true);
        wrapper.addView(caption);

        wrapper.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            onClick.run();
        });
        MotionStudio.attachExpressiveTouch(wrapper, 0.92f);
        return wrapper;
    }

    private void syncFiltersSection() {
        if (filterActiveRecipeTitle == null || filterCarouselContainer == null) {
            return;
        }
        String activeName = (currentRecipe == null) ? "Nguyên bản" : currentRecipe.name;
        filterActiveRecipeTitle.setText(activeName);

        String lutBadgeText;
        if (activeLut != null) {
            lutBadgeText = "LUT ✓";
            filterLutStatusBadge.setTextColor(StudioStyle.WARM_AMBER);
        } else if (!"Original".equals(settings.film)) {
            lutBadgeText = "Thiếu LUT ›";
            filterLutStatusBadge.setTextColor(StudioStyle.WARM_AMBER);
        } else {
            lutBadgeText = "＋ LUT ›";
            filterLutStatusBadge.setTextColor(StudioStyle.MUTED);
        }
        filterLutStatusBadge.setText(lutBadgeText);

        if (canvasRecipeChip != null) {
            String filmInfo = "Original".equals(settings.film) ? "" : (" · " + settings.film);
            canvasRecipeChip.setText("✦ " + activeName + filmInfo + (activeLut != null ? " · LUT ✓" : ""));
        }

        filterCarouselContainer.removeAllViews();

        if (wideLayoutActive) {
            // TRÊN TABLET & KHỔ NGANG: Lưới 3 cột ảnh thu nhỏ Recipe trực quan
            int maxRecipes = Math.min(29, recipes.size());
            int totalCells = 1 + maxRecipes; // Ô 0 là "Gốc", tiếp theo là 29 Recipes
            int cols = 3;
            for (int base = 0; base < totalCells; base += cols) {
                LinearLayout gridRow = row();
                gridRow.setPadding(0, dp(4), 0, dp(4));
                for (int c = 0; c < cols; c++) {
                    int cellIdx = base + c;
                    View cellView;
                    if (cellIdx == 0) {
                        cellView = buildFilterGridCard(null);
                    } else if (cellIdx - 1 < maxRecipes) {
                        cellView = buildFilterGridCard(recipes.get(cellIdx - 1));
                    } else {
                        cellView = new View(this);
                    }
                    LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    if (c > 0) cellLp.leftMargin = dp(8);
                    gridRow.addView(cellView, cellLp);
                }
                filterCarouselContainer.addView(gridRow);
            }
        } else {
            // TRÊN ĐIỆN THOẠI DỌC: Carousel ngang
            View originalItem = buildCircularToolItem(
                    "Gốc",
                    "compare",
                    null,
                    currentRecipe == null,
                    false,
                    () -> applyOriginalLook(false));
            filterCarouselContainer.addView(originalItem);

            int limit = Math.min(25, recipes.size());
            for (int i = 0; i < limit; i++) {
                final Recipe r = recipes.get(i);
                View card = buildFilterGridCard(r);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        dp(68),
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = dp(10);
                filterCarouselContainer.addView(card, lp);
            }
        }
    }

    private View buildFilterGridCard(final Recipe r) {
        boolean isOriginal = (r == null);
        boolean selected = isOriginal
                ? (currentRecipe == null)
                : (currentRecipe != null && currentRecipe.id.equals(r.id));

        LinearLayout card = column();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(6), dp(6), dp(6), dp(6));
        card.setBackground(StudioStyle.surface(
                selected ? StudioStyle.WARM_AMBER_LIGHT : Color.WHITE,
                selected ? StudioStyle.WARM_AMBER : StudioStyle.LINE,
                dp(16)));

        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(StudioStyle.shape(StudioStyle.SURFACE_CONTAINER, 0, dp(11)));
        thumb.setClipToOutline(true);
        thumb.setImageDrawable(new StudioStyle.Icon(isOriginal ? "compare" : "photo", StudioStyle.MUTED, dp(18)));
        card.addView(thumb, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        if (inlineFilterSession != null) {
            if (isOriginal) {
                if (inlineFilterSession.original != null) {
                    thumb.setImageBitmap(inlineFilterSession.original);
                }
            } else {
                Settings s = r.adjustments(new ArrayList<>());
                String lutFile = prefs.getString("lut." + s.film, null);
                inlineFilterSession.bind(
                        thumb,
                        null,
                        "inline-" + r.id,
                        s,
                        lutFile == null ? null : new File(getFilesDir(), lutFile),
                        null);
            }
        }

        TextView name = text(isOriginal ? "Gốc" : r.name, 10.5f, selected ? StudioStyle.WARM_AMBER : StudioStyle.INK);
        name.setTypeface(Typeface.create("sans-serif-medium", selected ? Typeface.BOLD : Typeface.NORMAL));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(4), 0, 0);
        card.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        card.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (isOriginal) {
                applyOriginalLook(false);
            } else {
                applyRecipe(r);
            }
        });
        if (!isOriginal) {
            card.setOnLongClickListener(v -> {
                showRecipe(r);
                return true;
            });
        }
        MotionStudio.attachExpressiveTouch(card, 0.94f);
        return card;
    }

    private void syncFramesSection() {
        if (frameActiveStyleTitle == null || frameCarouselContainer == null) {
            return;
        }
        String currentName = (frameStyle == 0)
                ? "Khung: Không viền"
                : PhotoFrame.STYLES[frameStyle].split(" · ")[0];
        frameActiveStyleTitle.setText(currentName);

        frameCarouselContainer.removeAllViews();

        if (wideLayoutActive) {
            // TRÊN TABLET & KHỔ NGANG: Hiển thị toàn bộ 30 mẫu khung trong lưới 3 cột
            int total = PhotoFrame.STYLES.length;
            int cols = 3;
            for (int base = 0; base < total; base += cols) {
                LinearLayout gridRow = row();
                gridRow.setPadding(0, dp(4), 0, dp(4));
                for (int c = 0; c < cols; c++) {
                    int idx = base + c;
                    View itemView = (idx < total) ? buildFrameCardItem(idx) : new View(this);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    if (c > 0) lp.leftMargin = dp(8);
                    gridRow.addView(itemView, lp);
                }
                frameCarouselContainer.addView(gridRow);
            }
        } else {
            // TRÊN ĐIỆN THOẠI DỌC: Dải cuộn ngang
            for (int idx = 0; idx < PhotoFrame.STYLES.length; idx++) {
                View item = buildFrameCardItem(idx);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        dp(64),
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                if (idx > 0) lp.leftMargin = dp(8);
                frameCarouselContainer.addView(item, lp);
            }
        }
    }

    private View buildFrameCardItem(final int styleIdx) {
        boolean selected = (frameStyle == styleIdx);
        String[] parts = PhotoFrame.STYLES[styleIdx].split(" · ");
        String shortLabel = (styleIdx == 0) ? "Tắt" : parts[0];
        String subLabel = (parts.length > 1) ? parts[1] : "Chuẩn";

        LinearLayout item = column();
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(dp(6), dp(8), dp(6), dp(8));
        item.setBackground(StudioStyle.surface(
                selected ? StudioStyle.WARM_AMBER : Color.WHITE,
                selected ? 0 : StudioStyle.LINE,
                dp(16)));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(new StudioStyle.Icon(
                styleIdx == 0 ? "reset" : "frame",
                selected ? Color.WHITE : StudioStyle.WARM_AMBER,
                dp(18)));
        item.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView label = text(shortLabel, 11f, selected ? Color.WHITE : StudioStyle.INK);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(4), 0, 0);
        item.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        if (wideLayoutActive) {
            TextView sub = text(subLabel, 9.5f, selected ? 0xeeffffff : StudioStyle.MUTED);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setGravity(Gravity.CENTER);
            item.addView(sub, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        item.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (frameStyle != styleIdx) {
                recordUndo();
                frameStyle = styleIdx;
                frameVersion++;
                isComparing = false;
                savePhotoFrame();
                syncFramesSection();
                showImage();
            }
        });
        MotionStudio.attachExpressiveTouch(item, 0.94f);
        return item;
    }

    /**
     * Đồng bộ giao diện tab LightCraft Darkroom (tích hợp từ dự án {@code cnttdlpt/lightcraft}):
     * <ul>
     *     <li><b>Tab con 0 (Đường cong Curve):</b> Chọn kênh ({@code Tham số 4 vùng}, {@code Tổng RGB}, {@code Kênh Đỏ}, {@code Kênh Lục}, {@code Kênh Lam}),
     *         đồ thị {@link LightCraftView.ToneCurveGraphView} và 4 thanh trượt tham số {@code Shadows / Darks / Lights / Highlights}.</li>
     *     <li><b>Tab con 1 (3-Way Grading):</b> 3 bánh xe màu cảm nhận thị giác OkLab/OkLCh {@link LightCraftView.GradingWheelsView}
     *         ({@code Vùng tối · Trung gian · Vùng sáng}) kèm độ sáng từng vùng, {@code Blending} và {@code Balance}.</li>
     *     <li><b>Tab con 2 (8-Kênh HSL):</b> Bộ trộn màu 8 dải chuẩn LightCraft ({@code Đỏ, Cam, Vàng, Lục, Ngọc, Lam, Tím, Hồng})
     *         với 3 thanh trượt {@code Đổi sắc (Hue)}, {@code Độ bão hòa (Sat)} và {@code Độ sáng (Lum)}.</li>
     *     <li><b>Tab con 3 (Mask & Preset):</b> 12 Preset phòng tối LightCraft + Mặt nạ cục bộ ({@code Radial}, {@code Linear}, {@code Sky}, {@code Subject}).</li>
     * </ul>
     */
    private void syncLightCraftSection() {
        if (lightCraftSubTabRow == null || lightCraftDetailContainer == null) {
            return;
        }
        if (stageMaskOverlayView != null) {
            stageMaskOverlayView.setInteractiveEnabled(selectedTab == EditorToolTab.LIGHTCRAFT && lightCraftSubTab == 3);
            stageMaskOverlayView.invalidate();
        }

        // 1. Xây dựng hàng 4 viên thuốc chuyển phân hệ con của LightCraft
        lightCraftSubTabRow.removeAllViews();
        final String[] subNames = {"📈 Đường cong", "🎡 3-Way Grading", "🌈 8-Kênh HSL", "🎯 Mask & Preset"};
        final int[] subColors = {
                StudioStyle.GOOGLE_BLUE,
                StudioStyle.GOOGLE_RED,
                StudioStyle.GOOGLE_GREEN,
                StudioStyle.WARM_AMBER
        };
        for (int i = 0; i < subNames.length; i++) {
            final int tabIdx = i;
            boolean active = (lightCraftSubTab == i);
            TextView pill = text(subNames[i], 11.5f, active ? Color.WHITE : StudioStyle.INK);
            pill.setTypeface(Typeface.create("sans-serif-medium", active ? Typeface.BOLD : Typeface.NORMAL));
            pill.setPadding(dp(12), dp(6), dp(12), dp(6));
            pill.setBackground(StudioStyle.surface(
                    active ? subColors[i] : StudioStyle.SURFACE,
                    active ? 0 : StudioStyle.LINE,
                    dp(999)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            pill.setLayoutParams(lp);
            pill.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                lightCraftSubTab = tabIdx;
                syncLightCraftSection();
            });
            MotionStudio.attachExpressiveTouch(pill, 0.94f);
            lightCraftSubTabRow.addView(pill);
        }

        // 2. Ẩn/hiện đồ thị Đường cong Tone Curve và Bánh xe 3-Way Grading theo tab con đang chọn
        if (toneCurveGraphView != null) {
            toneCurveGraphView.setVisibility(lightCraftSubTab == 0 ? View.VISIBLE : View.GONE);
            toneCurveGraphView.invalidate();
        }
        if (gradingWheelsView != null) {
            gradingWheelsView.setVisibility(lightCraftSubTab == 1 ? View.VISIBLE : View.GONE);
            gradingWheelsView.invalidate();
        }

        // 3. Đổ nội dung chi tiết tương ứng vào khung cuộn
        lightCraftDetailContainer.removeAllViews();
        if (lightCraftSubTab == 0) {
            buildToneCurveSubPanel(lightCraftDetailContainer);
        } else if (lightCraftSubTab == 1) {
            buildGradingSubPanel(lightCraftDetailContainer);
        } else if (lightCraftSubTab == 2) {
            buildColorMixerSubPanel(lightCraftDetailContainer);
        } else {
            buildMaskAndPresetsSubPanel(lightCraftDetailContainer);
        }
    }

    /**
     * Phân hệ con 0 của LightCraft: Chọn kênh Đường cong ({@code Tham số 4 vùng / RGB / R / G / B})
     * và 4 thanh trượt vùng sáng tối ({@code Shadows, Darks, Lights, Highlights}).
     */
    private void buildToneCurveSubPanel(LinearLayout container) {
        HorizontalScrollView chScroll = new HorizontalScrollView(this);
        chScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chRow = row();
        chRow.setGravity(Gravity.CENTER_VERTICAL);
        chRow.setPadding(0, 0, 0, dp(6));

        LightCraftView.ToneCurveGraphView.Channel currentCh = toneCurveGraphView != null
                ? toneCurveGraphView.getChannel()
                : LightCraftView.ToneCurveGraphView.Channel.MASTER;

        for (final LightCraftView.ToneCurveGraphView.Channel ch : LightCraftView.ToneCurveGraphView.Channel.values()) {
            boolean active = (currentCh == ch);
            int badgeBg = active
                    ? (ch == LightCraftView.ToneCurveGraphView.Channel.MASTER
                            || ch == LightCraftView.ToneCurveGraphView.Channel.PARAMETRIC
                            ? StudioStyle.INK : ch.color)
                    : StudioStyle.SURFACE;
            TextView chip = text(ch.label, 11f, active ? Color.WHITE : StudioStyle.INK);
            chip.setTypeface(Typeface.create("sans-serif-medium", active ? Typeface.BOLD : Typeface.NORMAL));
            chip.setPadding(dp(11), dp(5), dp(11), dp(5));
            chip.setBackground(StudioStyle.surface(badgeBg, active ? 0 : StudioStyle.LINE, dp(999)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                if (toneCurveGraphView != null) {
                    toneCurveGraphView.setChannel(ch);
                }
                syncLightCraftSection();
            });
            chRow.addView(chip);
        }

        TextView resetChBtn = text("Đặt lại kênh", 10.5f, StudioStyle.MUTED);
        resetChBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        resetChBtn.setPadding(dp(10), dp(5), dp(10), dp(5));
        resetChBtn.setBackground(StudioStyle.surface(StudioStyle.SURFACE, StudioStyle.LINE, dp(999)));
        resetChBtn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (currentCh == LightCraftView.ToneCurveGraphView.Channel.PARAMETRIC) {
                lightCraftState.paramShadows = 0f;
                lightCraftState.paramDarks = 0f;
                lightCraftState.paramLights = 0f;
                lightCraftState.paramHighlights = 0f;
            } else if (currentCh == LightCraftView.ToneCurveGraphView.Channel.RED) {
                lightCraftState.redCurve.clear();
                lightCraftState.redCurve.addAll(LightCraftEngine.State.defaultCurvePoints());
            } else if (currentCh == LightCraftView.ToneCurveGraphView.Channel.GREEN) {
                lightCraftState.greenCurve.clear();
                lightCraftState.greenCurve.addAll(LightCraftEngine.State.defaultCurvePoints());
            } else if (currentCh == LightCraftView.ToneCurveGraphView.Channel.BLUE) {
                lightCraftState.blueCurve.clear();
                lightCraftState.blueCurve.addAll(LightCraftEngine.State.defaultCurvePoints());
            } else {
                lightCraftState.masterCurve.clear();
                lightCraftState.masterCurve.addAll(LightCraftEngine.State.defaultCurvePoints());
            }
            syncLightCraftSection();
            showImage();
        });
        chRow.addView(resetChBtn);
        chScroll.addView(chRow);
        container.addView(chScroll);

        // 4 thanh trượt Parametric Curve (Shadows / Darks / Lights / Highlights) từ finish.rs
        container.addView(buildLightCraftSliderRow(
                "Vùng tối sâu (Shadows)", lightCraftState.paramShadows, -100f, 100f, StudioStyle.GOOGLE_BLUE,
                val -> {
                    lightCraftState.paramShadows = val;
                    if (toneCurveGraphView != null) toneCurveGraphView.invalidate();
                    showImage();
                }));
        container.addView(buildLightCraftSliderRow(
                "Vùng tối (Darks)", lightCraftState.paramDarks, -100f, 100f, StudioStyle.GOOGLE_BLUE,
                val -> {
                    lightCraftState.paramDarks = val;
                    if (toneCurveGraphView != null) toneCurveGraphView.invalidate();
                    showImage();
                }));
        container.addView(buildLightCraftSliderRow(
                "Vùng sáng (Lights)", lightCraftState.paramLights, -100f, 100f, StudioStyle.GOOGLE_YELLOW,
                val -> {
                    lightCraftState.paramLights = val;
                    if (toneCurveGraphView != null) toneCurveGraphView.invalidate();
                    showImage();
                }));
        container.addView(buildLightCraftSliderRow(
                "Đỉnh sáng (Highlights)", lightCraftState.paramHighlights, -100f, 100f, StudioStyle.GOOGLE_RED,
                val -> {
                    lightCraftState.paramHighlights = val;
                    if (toneCurveGraphView != null) toneCurveGraphView.invalidate();
                    showImage();
                }));
    }

    /**
     * Phân hệ con 1 của LightCraft: Thanh trượt độ sáng từng bánh xe OkLab/OkLCh + Blending & Balance.
     */
    private void buildGradingSubPanel(LinearLayout container) {
        container.addView(buildLightCraftSliderRow(
                "Độ sáng Vùng tối (Shadow Lum)", lightCraftState.shadowsWheel.lum, -100f, 100f, StudioStyle.GOOGLE_BLUE,
                val -> {
                    lightCraftState.shadowsWheel.lum = val;
                    showImage();
                }));
        container.addView(buildLightCraftSliderRow(
                "Độ sáng Trung gian (Mid Lum)", lightCraftState.midtonesWheel.lum, -100f, 100f, StudioStyle.GOOGLE_GREEN,
                val -> {
                    lightCraftState.midtonesWheel.lum = val;
                    showImage();
                }));
        container.addView(buildLightCraftSliderRow(
                "Độ sáng Vùng sáng (High Lum)", lightCraftState.highlightsWheel.lum, -100f, 100f, StudioStyle.GOOGLE_YELLOW,
                val -> {
                    lightCraftState.highlightsWheel.lum = val;
                    showImage();
                }));
        container.addView(buildLightCraftSliderRow(
                "Hòa trộn vùng (Blending)", lightCraftState.gradingBlending, 0f, 100f, StudioStyle.WARM_AMBER,
                val -> {
                    lightCraftState.gradingBlending = val;
                    showImage();
                }));
        container.addView(buildLightCraftSliderRow(
                "Cân bằng Sáng/Tối (Balance)", lightCraftState.gradingBalance, -100f, 100f, StudioStyle.GOOGLE_RED,
                val -> {
                    lightCraftState.gradingBalance = val;
                    showImage();
                }));
    }

    /**
     * Phân hệ con 2 của LightCraft: Bộ trộn 8 dải màu HSL (Red, Orange, Yellow, Green, Aqua, Blue, Purple, Magenta).
     */
    private void buildColorMixerSubPanel(LinearLayout container) {
        HorizontalScrollView bandScroll = new HorizontalScrollView(this);
        bandScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout bandRow = row();
        bandRow.setGravity(Gravity.CENTER_VERTICAL);
        bandRow.setPadding(0, 0, 0, dp(8));

        for (int b = 0; b < 8; b++) {
            final int bandIdx = b;
            boolean active = (selectedMixerBand == b);
            int bandColor = LightCraftEngine.MIXER_BAND_COLORS[b];
            boolean modified = Math.abs(lightCraftState.mixerHue[b]) > 0.5f
                    || Math.abs(lightCraftState.mixerSat[b]) > 0.5f
                    || Math.abs(lightCraftState.mixerLum[b]) > 0.5f;

            TextView chip = text(
                    LightCraftEngine.MIXER_BAND_NAMES[b] + (modified ? " •" : ""),
                    11.5f,
                    active ? Color.WHITE : StudioStyle.INK);
            chip.setTypeface(Typeface.create("sans-serif-medium", active ? Typeface.BOLD : Typeface.NORMAL));
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            chip.setBackground(StudioStyle.surface(
                    active ? bandColor : StudioStyle.SURFACE,
                    active ? 0 : bandColor,
                    dp(999)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                selectedMixerBand = bandIdx;
                syncLightCraftSection();
            });
            bandRow.addView(chip);
        }
        bandScroll.addView(bandRow);
        container.addView(bandScroll);

        final int b = selectedMixerBand;
        int accent = LightCraftEngine.MIXER_BAND_COLORS[b];
        String bandTitle = LightCraftEngine.MIXER_BAND_NAMES[b];

        container.addView(buildLightCraftSliderRow(
                "Đổi sắc " + bandTitle + " (Hue)", lightCraftState.mixerHue[b], -100f, 100f, accent,
                val -> {
                    lightCraftState.mixerHue[b] = val;
                    showImage();
                }));
        container.addView(buildLightCraftSliderRow(
                "Độ bão hòa " + bandTitle + " (Sat)", lightCraftState.mixerSat[b], -100f, 100f, accent,
                val -> {
                    lightCraftState.mixerSat[b] = val;
                    showImage();
                }));
        container.addView(buildLightCraftSliderRow(
                "Độ sáng " + bandTitle + " (Lum)", lightCraftState.mixerLum[b], -100f, 100f, accent,
                val -> {
                    lightCraftState.mixerLum[b] = val;
                    showImage();
                }));
    }

    /**
     * Phân hệ con 3 của LightCraft: 12 Preset phòng tối chuẩn LightCraft + Mặt nạ cục bộ (Radial, Linear, Sky, Subject).
     */
    private void buildMaskAndPresetsSubPanel(LinearLayout container) {
        // 1. Dải 12 Preset màu phòng tối tích hợp sẵn từ crates/engine/src/presets.rs
        TextView presetHdr = text("12 PRESET PHÒNG TỐI LIGHTCRAFT (CHẠM ĐỂ ÁP DỤNG)", 10.5f, StudioStyle.MUTED);
        presetHdr.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        presetHdr.setLetterSpacing(0.05f);
        presetHdr.setPadding(0, 0, 0, dp(4));
        container.addView(presetHdr);

        HorizontalScrollView presetScroll = new HorizontalScrollView(this);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetRow = row();
        presetRow.setPadding(0, 0, 0, dp(8));

        for (final LightCraftEngine.BuiltinPreset preset : LightCraftEngine.PRESETS) {
            boolean activePreset = preset.id.equals(lightCraftState.activePresetId);
            LinearLayout card = column();
            card.setPadding(dp(11), dp(6), dp(11), dp(6));
            card.setBackground(StudioStyle.surface(
                    activePreset ? StudioStyle.WARM_AMBER_LIGHT : Color.WHITE,
                    activePreset ? preset.badgeColor : StudioStyle.LINE,
                    dp(14)));

            TextView pName = text(preset.name, 11.5f, StudioStyle.INK);
            pName.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            card.addView(pName);

            TextView pCat = text(preset.group + " · " + preset.subtitle, 10f, preset.badgeColor);
            card.addView(pCat);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            card.setLayoutParams(lp);
            card.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                preset.applyTo(lightCraftState, settings);
                syncAdjustSection();
                syncLightCraftSection();
                requestRenderImmediate();
                message("Đã áp dụng LightCraft Preset: " + preset.name);
            });
            MotionStudio.attachExpressiveTouch(card, 0.95f);
            presetRow.addView(card);
        }
        presetScroll.addView(presetRow);
        container.addView(presetScroll);

        // 2. Chọn chế độ Mặt nạ cục bộ (Local Masking từ crates/pipeline/src/masks.rs)
        TextView maskHdr = text("MẶT NẠ CỤC BỘ (LOCAL MASKING · KÉO GHIM TRÊN ẢNH)", 10.5f, StudioStyle.MUTED);
        maskHdr.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        maskHdr.setLetterSpacing(0.05f);
        maskHdr.setPadding(0, dp(2), 0, dp(4));
        container.addView(maskHdr);

        HorizontalScrollView maskScroll = new HorizontalScrollView(this);
        maskScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout maskRow = row();
        maskRow.setPadding(0, 0, 0, dp(6));

        for (final LightCraftEngine.MaskMode mt : LightCraftEngine.MaskMode.values()) {
            boolean active = (lightCraftState.maskMode == mt);
            TextView chip = text(mt.label, 11f, active ? Color.WHITE : StudioStyle.INK);
            chip.setTypeface(Typeface.create("sans-serif-medium", active ? Typeface.BOLD : Typeface.NORMAL));
            chip.setPadding(dp(11), dp(5), dp(11), dp(5));
            chip.setBackground(StudioStyle.surface(
                    active ? StudioStyle.GOOGLE_BLUE : StudioStyle.SURFACE,
                    active ? 0 : StudioStyle.LINE,
                    dp(999)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                lightCraftState.maskMode = mt;
                syncLightCraftSection();
                showImage();
            });
            maskRow.addView(chip);
        }

        // Nút Đảo ngược vùng Mask (Invert) & Hiện lớp phủ Đỏ (Overlay)
        if (lightCraftState.maskMode != LightCraftEngine.MaskMode.NONE) {
            TextView invChip = text("⇄ Đảo vùng", 11f, lightCraftState.maskInvert ? Color.WHITE : StudioStyle.INK);
            invChip.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            invChip.setPadding(dp(10), dp(5), dp(10), dp(5));
            invChip.setBackground(StudioStyle.surface(
                    lightCraftState.maskInvert ? StudioStyle.WARM_AMBER : StudioStyle.SURFACE,
                    lightCraftState.maskInvert ? 0 : StudioStyle.LINE,
                    dp(999)));
            LinearLayout.LayoutParams invLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            invLp.rightMargin = dp(6);
            invChip.setLayoutParams(invLp);
            invChip.setOnClickListener(v -> {
                lightCraftState.maskInvert = !lightCraftState.maskInvert;
                syncLightCraftSection();
                showImage();
            });
            maskRow.addView(invChip);

            TextView ovlChip = text("🔴 Vùng đỏ", 11f, lightCraftState.showMaskOverlay ? Color.WHITE : StudioStyle.GOOGLE_RED);
            ovlChip.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            ovlChip.setPadding(dp(10), dp(5), dp(10), dp(5));
            ovlChip.setBackground(StudioStyle.surface(
                    lightCraftState.showMaskOverlay ? StudioStyle.GOOGLE_RED : StudioStyle.RED_TONAL,
                    0,
                    dp(999)));
            ovlChip.setOnClickListener(v -> {
                lightCraftState.showMaskOverlay = !lightCraftState.showMaskOverlay;
                syncLightCraftSection();
                showImage();
            });
            maskRow.addView(ovlChip);
        }

        maskScroll.addView(maskRow);
        container.addView(maskScroll);

        if (lightCraftState.maskMode != LightCraftEngine.MaskMode.NONE) {
            container.addView(buildLightCraftSliderRow(
                    "Phơi sáng vùng Mask (EV)", lightCraftState.maskExposure * 50f, -100f, 100f, StudioStyle.GOOGLE_YELLOW,
                    val -> {
                        lightCraftState.maskExposure = val / 50f;
                        showImage();
                    }));
            container.addView(buildLightCraftSliderRow(
                    "Nhiệt độ vùng Mask", lightCraftState.maskTemp, -100f, 100f, StudioStyle.WARM_AMBER,
                    val -> {
                        lightCraftState.maskTemp = val;
                        showImage();
                    }));
            container.addView(buildLightCraftSliderRow(
                    "Độ trong vùng Mask (Clarity)", lightCraftState.maskClarity, -100f, 100f, StudioStyle.GOOGLE_GREEN,
                    val -> {
                        lightCraftState.maskClarity = val;
                        showImage();
                    }));
            container.addView(buildLightCraftSliderRow(
                    "Bão hòa vùng Mask (Sat)", lightCraftState.maskSaturation, -100f, 100f, StudioStyle.GOOGLE_RED,
                    val -> {
                        lightCraftState.maskSaturation = val;
                        showImage();
                    }));
        }
    }

    private interface FloatConsumer {
        void accept(float value);
    }

    private View buildLightCraftSliderRow(
            final String title,
            float currentVal,
            final float minVal,
            final float maxVal,
            int tintColor,
            final FloatConsumer onChanged) {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(3));

        final TextView lbl = text(
                String.format(Locale.US, "%s: %+.0f", title, currentVal),
                11f,
                StudioStyle.INK);
        lbl.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        lbl.setSingleLine(true);
        lbl.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(lbl, new LinearLayout.LayoutParams(dp(154), LinearLayout.LayoutParams.WRAP_CONTENT));

        SeekBar seek = new SeekBar(this);
        final int steps = 200;
        seek.setMax(steps);
        int progress = Math.round(((currentVal - minVal) / (maxVal - minVal)) * steps);
        seek.setProgress(Math.max(0, Math.min(steps, progress)));
        seek.setProgressTintList(ColorStateList.valueOf(tintColor));
        seek.setThumbTintList(ColorStateList.valueOf(tintColor));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                if (!fromUser) return;
                float v = minVal + (p / (float) steps) * (maxVal - minVal);
                if (Math.abs(v) < (maxVal - minVal) * 0.015f) {
                    v = 0f;
                }
                lbl.setText(String.format(Locale.US, "%s: %+.0f", title, v));
                onChanged.accept(v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        row.addView(seek, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /**
     * Khởi tạo bức ảnh mẫu Studio độ phân giải cao ngay trong bộ nhớ kèm siêu dữ liệu EXIF Fujifilm X-T5,
     * giúp người dùng thử nghiệm tức thì trên Máy tính bảng / Máy ảo chỉ với 1 chạm.
     */
    private void loadStudioSamplePhoto(final String optionalFilmKeyword) {
        if (statusLabel != null) {
            statusLabel.setText("Đang dựng ảnh mẫu Studio…");
        }
        executor.execute(() -> {
            try {
                int w = 1280;
                int h = 860;
                Bitmap sample = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                android.graphics.Canvas c = new android.graphics.Canvas(sample);
                android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

                // 1. Bầu trời hoàng hôn chuyển sắc (Golden Hour Sky Gradient)
                p.setShader(new android.graphics.LinearGradient(
                        0, 0, w, h * 0.72f,
                        new int[]{0xff1e3c64, 0xff4a6fa5, 0xffe07a3b, 0xfff7c582},
                        new float[]{0f, 0.38f, 0.76f, 1f},
                        android.graphics.Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);

                // 2. Mặt trời rực sáng (Radial Sun Glow)
                p.setShader(new android.graphics.RadialGradient(
                        w * 0.72f, h * 0.30f, w * 0.22f,
                        new int[]{0xfffff7e0, 0xddffbe6b, 0x00ffbe6b},
                        new float[]{0f, 0.45f, 1f},
                        android.graphics.Shader.TileMode.CLAMP));
                c.drawCircle(w * 0.72f, h * 0.30f, w * 0.22f, p);
                p.setShader(null);

                // 3. Dãy núi xa và tiền cảnh có chiều sâu vùng tối (Shadow & Midtone Test Zones)
                android.graphics.Path farMtn = new android.graphics.Path();
                farMtn.moveTo(0, h);
                farMtn.lineTo(0, h * 0.58f);
                farMtn.lineTo(w * 0.26f, h * 0.42f);
                farMtn.lineTo(w * 0.52f, h * 0.60f);
                farMtn.lineTo(w * 0.80f, h * 0.39f);
                farMtn.lineTo(w, h * 0.52f);
                farMtn.lineTo(w, h);
                farMtn.close();
                p.setColor(0xff2c3a4f);
                c.drawPath(farMtn, p);

                android.graphics.Path nearHills = new android.graphics.Path();
                nearHills.moveTo(0, h);
                nearHills.lineTo(0, h * 0.70f);
                nearHills.lineTo(w * 0.35f, h * 0.62f);
                nearHills.lineTo(w * 0.68f, h * 0.74f);
                nearHills.lineTo(w, h * 0.64f);
                nearHills.lineTo(w, h);
                nearHills.close();
                p.setColor(0xff1b2533);
                c.drawPath(nearHills, p);

                // 4. Bảng 6 ô màu chuẩn ở góc dưới trái (Skin Tone, Foliage, Sky Blue, Crimson, Gold, Neutral Grey)
                int[] chartColors = {0xffe2a887, 0xff4c8557, 0xff3b82b6, 0xffc84b31, 0xffe8b84b, 0xff808080};
                float boxW = 46f;
                float boxH = 34f;
                float startX = 42f;
                float startY = h - 72f;
                for (int i = 0; i < chartColors.length; i++) {
                    float left = startX + i * (boxW + 10f);
                    p.setColor(chartColors[i]);
                    c.drawRoundRect(left, startY, left + boxW, startY + boxH, 8f, 8f, p);
                }

                // Lưu vào vùng riêng của app để tính năng Xuất ảnh 1x/2x đọc được từ Uri
                File folder = new File(getFilesDir(), "imported-photos");
                if (!folder.isDirectory()) {
                    folder.mkdirs();
                }
                File sampleFile = new File(folder, "photo-studio-sample.img");
                try (FileOutputStream out = new FileOutputStream(sampleFile)) {
                    sample.compress(Bitmap.CompressFormat.JPEG, 98, out);
                }
                final Uri sampleUri = Uri.fromFile(sampleFile);

                final PhotoInfo sampleExif = new PhotoInfo();
                sampleExif.camera = "FUJIFILM X-T5";
                sampleExif.focal = "35 mm (eq.)";
                sampleExif.aperture = "f/1.4";
                sampleExif.shutter = "1/500 s";
                sampleExif.iso = "ISO 160";
                sampleExif.date = "2026.10.12 15:00";
                sampleExif.author = "IRO Studio";

                runOnUiThread(() -> {
                    if (dead) {
                        sample.recycle();
                        return;
                    }
                    imageUri = sampleUri;
                    preview = sample;
                    rendered = null;
                    isComparing = false;
                    isAutoBalanced = false;
                    photoInfo = sampleExif;
                    frameVersion++;
                    frameSource = null;
                    framePreview = null;
                    history.clear();

                    if (inlineFilterSession != null) {
                        closeThumbnails(inlineFilterSession);
                    }
                    inlineFilterSession = thumbnailSession();

                    // Nếu người dùng bấm vào một chip Film Simulation cụ thể thì áp dụng công thức đó luôn
                    if (optionalFilmKeyword != null && !recipes.isEmpty()) {
                        for (Recipe r : recipes) {
                            if ((r.film + " " + r.name).toLowerCase(Locale.ROOT)
                                    .contains(optionalFilmKeyword.toLowerCase(Locale.ROOT))) {
                                recipeNotes.clear();
                                settings = r.adjustments(recipeNotes);
                                currentRecipe = r;
                                selectedTab = EditorToolTab.FILTERS;
                                break;
                            }
                        }
                    }

                    prefs.edit().putString("photo", sampleUri.toString()).apply();
                    savePhotoFrame();
                    syncAllUiControls();
                    loadBoundLut();
                    showImage();
                    requestRenderImmediate();
                });
            } catch (Exception e) {
                error(e);
            }
        });
    }

    // =========================================================================
    // CÁC HÀNH ĐỘNG CHỈNH SỬA: UNDO / REDO / AUTO-BALANCE / RESET / ĐÓNG ẢNH
    // =========================================================================

    private UndoState currentUndoSnapshot() {
        return new UndoState(settings, currentRecipe, frameStyle, photoInfo, recipeNotes, isAutoBalanced);
    }

    private void recordUndo() {
        history.record(currentUndoSnapshot());
        syncUndoRedoButtons();
    }

    private void restoreUndoState(UndoState state) {
        if (state == null) {
            return;
        }
        settings = state.settings.copy();
        currentRecipe = state.recipe;
        frameStyle = state.frameStyle;
        photoInfo = state.photoInfo.copy();
        recipeNotes.clear();
        recipeNotes.addAll(state.notes);
        isAutoBalanced = state.isAutoBalanced;
        frameVersion++;
        isComparing = false;

        syncAllUiControls();
        loadBoundLut();
        savePhotoFrame();
        persist();
    }

    private void performUndo() {
        if (!history.canUndo()) {
            return;
        }
        UndoState prev = history.popUndo(currentUndoSnapshot());
        syncUndoRedoButtons();
        restoreUndoState(prev);
    }

    private void performRedo() {
        if (!history.canRedo()) {
            return;
        }
        UndoState next = history.popRedo(currentUndoSnapshot());
        syncUndoRedoButtons();
        restoreUndoState(next);
    }

    /**
     * Tự động cân bằng ảnh bằng {@link AutoBalancer} (tương đương {@code EditorModel.autoBalance()} trên iOS).
     */
    private void performAutoBalance() {
        if (preview == null) {
            message("Mở ảnh trước khi tự động cân bằng");
            return;
        }
        recordUndo();
        settings = AutoBalancer.analyze(preview, settings);
        isAutoBalanced = true;
        isComparing = false;
        syncAllUiControls();
        MotionStudio.popBadge(activeParamValueBadge);
        persist();
        requestRenderImmediate();
        message("Đã tự động cân bằng ánh sáng và màu sắc");
    }

    private void applyOriginalLook(boolean resetFrameAlso) {
        recordUndo();
        settings = new Settings();
        currentRecipe = null;
        recipeNotes.clear();
        isAutoBalanced = false;
        isComparing = false;
        if (resetFrameAlso) {
            frameStyle = 0;
            frameSource = null;
            framePreview = null;
            savePhotoFrame();
        }
        syncAllUiControls();
        loadBoundLut();
        showImage();
        persist();
    }

    private void confirmClosePhoto() {
        if (preview == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Đóng ảnh hiện tại?")
                .setMessage("Ảnh đang chỉnh sửa sẽ được đóng và quay về màn hình ban đầu.")
                .setPositiveButton("Đóng ảnh", (d, w) -> clearPhoto())
                .setNegativeButton("Hủy", null)
                .show();
    }

    /**
     * Đóng ảnh hiện tại và quay về màn hình chào {@code EmptyPhoto} (tương đương {@code EditorModel.clearPhoto()}).
     */
    private void clearPhoto() {
        revision++;
        loadRevision++;
        lutRevision++;
        if (renderTask != null) {
            renderTask.cancel(true);
        }
        if (inlineFilterSession != null) {
            closeThumbnails(inlineFilterSession);
            inlineFilterSession = null;
        }
        history.clear();
        isAutoBalanced = false;
        isComparing = false;
        imageUri = null;
        preview = null;
        rendered = null;
        frameSource = null;
        framePreview = null;
        photoInfo = new PhotoInfo();
        frameStyle = 0;
        settings = new Settings();
        currentRecipe = null;
        recipeNotes.clear();
        prefs.edit().remove("photo").apply();
        syncAllUiControls();
    }


    // =========================================================================
    // MENU TÙY CHỌN (⋯) & HỘP THOẠI QUẢN LÝ LUT / RECIPE / KHUNG / XUẤT ẢNH
    // =========================================================================

    private void showMore() {
        String[] actions = {
                "✦ Tự động cân bằng ảnh",
                "Mở ảnh mới từ Thư viện",
                "Mở ảnh từ Tệp tin",
                "Đặt lại ảnh gốc",
                "Đóng ảnh hiện tại",
                "Quản lý LUT của tôi",
                "Kho LUT trực tuyến (GitHub)",
                "Thông số Recipe đang chọn",
                "Sửa bản riêng Recipe",
                "Lưu công thức JSON…",
                "Nhập công thức JSON…",
                "Khung thông tin ảnh (30 mẫu)",
                "Hướng dẫn & giới hạn"
        };
        new AlertDialog.Builder(this)
                .setTitle("Tùy chọn IRO Studio")
                .setItems(actions, (d, which) -> {
                    switch (which) {
                        case 0: performAutoBalance(); break;
                        case 1: choose(OPEN_PHOTO, "image/*"); break;
                        case 2: chooseDocumentOnly(OPEN_PHOTO, "image/*"); break;
                        case 3: applyOriginalLook(true); break;
                        case 4: confirmClosePhoto(); break;
                        case 5: showLutOptions(); break;
                        case 6: showOnlineLuts(); break;
                        case 7:
                            if (currentRecipe == null) message("Chưa chọn recipe");
                            else showRecipe(currentRecipe);
                            break;
                        case 8: editRecipe(); break;
                        case 9: saveRecipe(); break;
                        case 10: choose(OPEN_RECIPE, "*/*"); break;
                        case 11: editPhotoFrame(); break;
                        case 12: showHelpDialog(); break;
                    }
                })
                .show();
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Chỉnh màu cùng IRO")
                .setMessage("1. Mở ảnh từ Thư viện hoặc Tệp.\n"
                        + "2. Chọn màu từ thư viện 392 công thức hoặc bấm Tự động cân bằng (✦).\n"
                        + "3. Gắn LUT 3D (.cube) sRGB cho film nếu cần màu nền.\n"
                        + "4. Kéo thước chia vạch để tinh chỉnh, chọn Khung EXIF rồi Xuất ảnh.\n\n"
                        + "Chạm và giữ vào bức ảnh để so sánh nhanh với ảnh GỐC. Dùng hai nút ↶ / ↷ trên thanh đầu trang để Hoàn tác / Làm lại tới 40 bước.\n\n"
                        + "Xem trước tối đa 1280 px; xuất tiêu chuẩn tối đa 12 MP, hoặc kích thước gốc / phóng 2× khi đủ bộ nhớ. Ảnh được xử lý hoàn toàn trên thiết bị.")
                .setPositiveButton("Đóng", null)
                .show();
    }

    private ImageView thumbnail() {
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setBackground(StudioStyle.shape(StudioStyle.SURFACE_CONTAINER, StudioStyle.LINE, dp(12)));
        view.setClipToOutline(true);
        view.setImageDrawable(new StudioStyle.Icon("photo", StudioStyle.MUTED, dp(24)));
        return view;
    }

    private ThumbnailSession thumbnailSession() {
        ThumbnailSession session = new ThumbnailSession(preview);
        thumbnailSessions.add(session);
        return session;
    }

    private void closeThumbnails(ThumbnailSession session) {
        if (session == null) return;
        session.close();
        thumbnailSessions.remove(session);
    }

    private void showOnlineLuts() {
        final String film = settings.film;
        onlineLuts.show(preview, file -> {
            prefs.edit().putString("lut." + film, file.getName()).apply();
            if (settings.film.equals(film)) {
                loadBoundLut();
            }
            message("Đã gắn LUT cho " + film);
        });
    }

    private void showLutOptions() {
        ThumbnailSession session = thumbnailSession();
        LinearLayout content = column();
        content.setPadding(dp(20), dp(8), dp(20), dp(12));
        content.addView(text(
                "Film: " + settings.film + "\n"
                        + (activeLut == null ? "Chưa có LUT màu nền." : "Đang dùng: " + activeLut.title),
                13, StudioStyle.INK));

        LinearLayout pair = row();
        pair.setPadding(0, dp(8), 0, dp(8));
        LinearLayout before = column();
        LinearLayout after = column();
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        second.leftMargin = dp(10);
        pair.addView(before, first);
        pair.addView(after, second);

        ImageView originalImage = thumbnail();
        ImageView coloredImage = thumbnail();
        originalImage.setContentDescription("Ảnh gốc");
        coloredImage.setContentDescription("Ảnh xem trước với LUT và chỉnh tay");
        before.addView(originalImage, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150)));
        after.addView(coloredImage, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150)));
        before.addView(text("Ảnh gốc", 11, StudioStyle.MUTED));
        TextView label = text("", 11, StudioStyle.WARM_AMBER);
        after.addView(label);
        content.addView(pair);

        if (session.original != null) {
            originalImage.setImageBitmap(session.original);
        }
        session.bind(coloredImage, label, "active", settings, null, activeLut);
        content.addView(text(
                "Xem trước trên ảnh của bạn. Nhập LUT .cube nhận ảnh sRGB (2–65 điểm mỗi chiều); LUT được lưu riêng cho film đang chọn.",
                11, StudioStyle.MUTED));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("LUT của tôi")
                .setView(scroll)
                .setPositiveButton("Nhập LUT .cube", (d, w) -> choose(OPEN_LUT, "*/*"))
                .setNeutralButton("Bỏ LUT", (d, w) -> {
                    prefs.edit().remove(lutKey()).apply();
                    activeLut = null;
                    lutRevision++;
                    syncFiltersSection();
                    requestRender();
                })
                .setNegativeButton("Đóng", null)
                .create();
        dialog.setOnDismissListener(d -> closeThumbnails(session));
        dialog.show();
    }

    // =========================================================================
    // ĐỌC / LƯU TỆP TIN, GIẢI MÃ ẢNH VÀ XỬ LÝ MÀU NỀN (Photo I/O & Rendering)
    // =========================================================================

    private void message(String m) {
        if (!dead) {
            Toast.makeText(this, m, Toast.LENGTH_LONG).show();
        }
    }

    private void error(Exception e) {
        DebugLogger.e(DebugLogger.TAG_EDITOR, "Lỗi trong MainActivity", e);
        runOnUiThread(() -> {
            if (dead) return;
            String errText = "Lỗi: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            if (statusLabel != null) {
                statusLabel.setText(errText);
            }
            message(errText);
        });
    }

    private void choose(int request, String mime) {
        if (request == OPEN_PHOTO) {
            Intent photos = new Intent(Intent.ACTION_PICK)
                    .setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
            photos.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(photos, OPEN_PHOTO);
                return;
            } catch (ActivityNotFoundException unavailable) {
                // Nếu thiết bị không có ứng dụng Gallery thì chuyển sang Document Picker bên dưới.
            }
        }
        chooseDocumentOnly(request, mime);
    }

    private void chooseDocumentOnly(int request, String mime) {
        Intent document = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType(mime)
                .addCategory(Intent.CATEGORY_OPENABLE);
        document.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(document, request);
        } catch (ActivityNotFoundException unavailable) {
            message("Không có ứng dụng chọn ảnh/tệp trên thiết bị.");
        }
    }

    private void createDocument(int request, String mime, String name) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .setType(mime)
                .addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, request);
    }

    private static byte[] readLimited(InputStream in, int max) throws IOException {
        if (in == null) {
            throw new IOException("Không đọc được file");
        }
        try (InputStream stream = in;
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = stream.read(buffer)) != -1) {
                if (out.size() + n > max) {
                    throw new IOException("File vượt giới hạn " + (max / 1024 / 1024) + " MB");
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) {
            if (request == SAVE_IMAGE) {
                exportBusy = false;
                exportUri = null;
                exportLut = null;
            }
            return;
        }
        Uri uri = data.getData();
        if (request == OPEN_PHOTO) {
            loadPhoto(uri, true);
        } else if (request == OPEN_LUT) {
            importLut(uri);
        } else if (request == OPEN_RECIPE) {
            importRecipe(uri);
        } else if (request == SAVE_IMAGE) {
            exportImage(uri);
        } else if (request == SAVE_RECIPE) {
            final String payload = exportRecipeJson;
            executor.execute(() -> {
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IOException("Không mở được file đích");
                    out.write(payload.getBytes(StandardCharsets.UTF_8));
                    runOnUiThread(() -> message("Đã lưu recipe và chỉnh sửa JSON"));
                } catch (Exception e) {
                    error(e);
                }
            });
        }
    }

    /**
     * Giải mã ảnh về không gian màu sRGB phần mềm (ALLOCATOR_SOFTWARE) để đọc/ghi mảng pixel trực tiếp.
     */
    private Bitmap decode(Uri uri, int edge, long maxPixels) throws IOException {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri), (decoder, info, src) -> {
            int w = info.getSize().getWidth();
            int h = info.getSize().getHeight();
            double scale = Math.min(1.0, Math.sqrt((double) maxPixels / ((double) w * h)));
            if (edge > 0) {
                scale = Math.min(scale, (double) edge / Math.max(w, h));
            }
            decoder.setTargetSize(
                    Math.max(1, (int) Math.floor(w * scale)),
                    Math.max(1, (int) Math.floor(h * scale)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
        });
    }

    private void loadPhoto(Uri uri, boolean importFromGallery) {
        int token = ++loadRevision;
        if (statusLabel != null) {
            statusLabel.setText("Đang mở ảnh…");
        }
        executor.execute(() -> {
            File imported = null;
            try {
                Uri source = uri;
                // Sao chép ảnh vào vùng riêng của app (tối đa 100 MB) để giữ quyền đọc lâu dài
                if (importFromGallery) {
                    File folder = new File(getFilesDir(), "imported-photos");
                    if (!folder.isDirectory() && !folder.mkdirs()) {
                        throw new IOException("Không tạo được nơi lưu ảnh");
                    }
                    imported = File.createTempFile("photo-", ".img", folder);
                    try (InputStream in = getContentResolver().openInputStream(uri);
                         FileOutputStream out = new FileOutputStream(imported)) {
                        if (in == null) {
                            throw new IOException("Không đọc được ảnh đã chọn");
                        }
                        byte[] buffer = new byte[32768];
                        long total = 0;
                        int n;
                        while ((n = in.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted()) {
                                throw new InterruptedIOException("Đã hủy mở ảnh");
                            }
                            total += n;
                            if (total > 100L * 1024 * 1024) {
                                throw new IOException("Ảnh vượt giới hạn 100 MB");
                            }
                            out.write(buffer, 0, n);
                        }
                    }
                    source = Uri.fromFile(imported);
                }

                Bitmap b = decode(source, 1280, 1_638_400);
                PhotoInfo readInfo = PhotoInfo.read(getContentResolver(), source);
                if (source.toString().equals(prefs.getString("frame.photo", ""))) {
                    try {
                        readInfo = PhotoInfo.from(new JSONObject(prefs.getString("frame.info", "{}")));
                    } catch (Exception ignored) {
                    }
                }

                final PhotoInfo loadedInfo = readInfo;
                final Uri selected = source;
                final File copy = imported;

                runOnUiThread(() -> {
                    if (dead || token != loadRevision) {
                        b.recycle();
                        if (copy != null) copy.delete();
                        return;
                    }
                    imageUri = selected;
                    preview = b;
                    rendered = null;
                    isComparing = false;
                    isAutoBalanced = false;
                    photoInfo = loadedInfo;
                    frameVersion++;
                    frameSource = null;
                    framePreview = null;
                    history.clear();

                    if (inlineFilterSession != null) {
                        closeThumbnails(inlineFilterSession);
                    }
                    inlineFilterSession = thumbnailSession();

                    prefs.edit().putString("photo", selected.toString()).apply();

                    // Dọn các bản sao ảnh cũ không còn sử dụng
                    File[] old = new File(getFilesDir(), "imported-photos").listFiles();
                    if (old != null) {
                        for (File f : old) {
                            if (f.getName().startsWith("photo-")
                                    && f.getName().endsWith(".img")
                                    && !Uri.fromFile(f).equals(imageUri)
                                    && !Uri.fromFile(f).equals(exportUri)) {
                                f.delete();
                            }
                        }
                    }

                    syncAllUiControls();
                    showImage();
                    requestRenderImmediate();
                });
            } catch (Exception e) {
                if (imported != null) imported.delete();
                error(e);
            } catch (OutOfMemoryError e) {
                if (imported != null) imported.delete();
                runOnUiThread(() -> {
                    if (!dead) message("Không đủ bộ nhớ để mở ảnh này");
                });
            }
        });
    }

    private void showImage() {
        if (preview == null) {
            updateWorkspaceVisibility();
            return;
        }
        updateWorkspaceVisibility();
        compareOriginBadge.setVisibility(isComparing ? View.VISIBLE : View.GONE);

        Bitmap base = (isComparing || rendered == null) ? preview : rendered;
        if (!isComparing && (!lightCraftState.isIdentity()
                || (lightCraftState.showMaskOverlay && lightCraftState.maskMode != LightCraftEngine.MaskMode.NONE))) {
            base = LightCraftEngine.processBitmap(base, lightCraftState);
        }
        if (rgbHistogramView != null) {
            rgbHistogramView.updateFromBitmap(base);
        }
        Bitmap stageBitmap = base;
        if (!isComparing && frameStyle > 0) {
            try {
                if (frameSource != base || cachedFrameStyle != frameStyle || cachedFrameVersion != frameVersion) {
                    framePreview = PhotoFrame.render(base, photoInfo, frameStyle);
                    frameSource = base;
                    cachedFrameStyle = frameStyle;
                    cachedFrameVersion = frameVersion;
                }
                stageBitmap = framePreview;
            } catch (OutOfMemoryError e) {
                stageBitmap = base;
                message("Không đủ bộ nhớ xem khung ảnh");
            }
        }
        imageStageView.setImageBitmap(stageBitmap);
        if (stageMaskOverlayView != null && stageBitmap != null) {
            stageMaskOverlayView.setImageAspect(stageBitmap.getWidth(), stageBitmap.getHeight());
            stageMaskOverlayView.setInteractiveEnabled(selectedTab == EditorToolTab.LIGHTCRAFT && lightCraftSubTab == 3);
            stageMaskOverlayView.invalidate();
        }

        if (isComparing) {
            statusLabel.setText("Đang xem ảnh gốc (nhả tay để quay lại bản chỉnh)");
        }
    }

    private final Runnable delayedRender = this::startRender;

    private void requestRender() {
        revision++;
        if (renderTask != null) {
            renderTask.cancel(true);
        }
        handler.removeCallbacks(delayedRender);
        handler.postDelayed(delayedRender, 120);
    }

    private void requestRenderImmediate() {
        revision++;
        if (renderTask != null) {
            renderTask.cancel(true);
        }
        handler.removeCallbacks(delayedRender);
        startRender();
    }

    private void startRender() {
        if (preview == null || dead) {
            return;
        }
        final int token = revision;
        final Bitmap src = preview;
        final Settings s = settings.copy();
        final CubeLut lut = activeLut;

        statusLabel.setText("Đang xử lý màu…");
        renderTask = executor.submit(() -> {
            try {
                int w = src.getWidth();
                int h = src.getHeight();
                int[] p = new int[w * h];
                src.getPixels(p, 0, w, 0, 0, w, h);

                int[] out = ColorEngine.process(p, w, h, s, lut);
                Bitmap b = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888);

                runOnUiThread(() -> {
                    if (dead || token != revision) {
                        return;
                    }
                    rendered = b;
                    showImage();
                    if (!isComparing) {
                        String frameDesc = (frameStyle == 0) ? "Không khung" : PhotoFrame.STYLES[frameStyle].split(" · ")[0];
                        String lutDesc = (lut == null) ? "chưa gắn LUT" : ("LUT: " + lut.title);
                        statusLabel.setText(w + " × " + h + " xem trước · " + frameDesc + " · " + lutDesc);
                    }
                });
            } catch (CancellationException ignored) {
            } catch (Exception e) {
                error(e);
            } catch (OutOfMemoryError e) {
                runOnUiThread(() -> message("Không đủ bộ nhớ xử lý ảnh"));
            }
        });
    }

    private String lutKey() {
        return "lut." + settings.film;
    }

    private void loadBoundLut() {
        final int token = ++lutRevision;
        String file = prefs.getString(lutKey(), null);
        activeLut = null;
        syncFiltersSection();
        requestRender();
        if (file == null) {
            return;
        }
        executor.execute(() -> {
            try (Reader r = new InputStreamReader(new FileInputStream(new File(getFilesDir(), file)), StandardCharsets.UTF_8)) {
                CubeLut lut = CubeLut.parse(r);
                runOnUiThread(() -> {
                    if (dead || token != lutRevision) return;
                    activeLut = lut;
                    syncFiltersSection();
                    requestRenderImmediate();
                });
            } catch (Exception e) {
                error(e);
            }
        });
    }

    private void importLut(Uri uri) {
        final String key = lutKey();
        final int token = ++lutRevision;
        statusLabel.setText("Đang kiểm tra LUT…");
        executor.execute(() -> {
            try {
                byte[] bytes = readLimited(getContentResolver().openInputStream(uri), 35 * 1024 * 1024);
                CubeLut lut = CubeLut.parse(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
                String file = "lut-" + UUID.randomUUID() + ".cube";
                try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), file))) {
                    out.write(bytes);
                }
                prefs.edit().putString(key, file).apply();
                runOnUiThread(() -> {
                    if (dead) return;
                    if (token == lutRevision && key.equals(lutKey())) {
                        activeLut = lut;
                        syncFiltersSection();
                        requestRenderImmediate();
                    }
                    message("Đã lưu LUT cho " + key.substring(4));
                });
            } catch (Exception e) {
                error(e);
            }
        });
    }

    // =========================================================================
    // THƯ VIỆN 392 CÔNG THỨC MÀU (Recipe Library)
    // =========================================================================

    private void loadLibrary() {
        executor.execute(() -> {
            try {
                JSONObject j = new JSONObject(new String(
                        readLimited(getAssets().open("recipes.json"), 8 * 1024 * 1024),
                        StandardCharsets.UTF_8));
                JSONArray list = j.getJSONArray("recipes");
                List<Recipe> parsed = new ArrayList<>();
                parsed.add(new Recipe(new JSONObject(new String(
                        readLimited(getAssets().open("sample-recipe.json"), 100_000),
                        StandardCharsets.UTF_8))));
                for (int i = 0; i < list.length(); i++) {
                    parsed.add(new Recipe(list.getJSONObject(i)));
                }
                runOnUiThread(() -> {
                    if (dead) return;
                    recipes.clear();
                    recipes.addAll(parsed);
                    syncFiltersSection();
                });
            } catch (Exception e) {
                error(e);
            }
        });
    }

    private void showLibrary() {
        if (recipes.isEmpty()) {
            message("Thư viện đang tải, vui lòng thử lại");
            return;
        }
        ThumbnailSession thumbnails = thumbnailSession();
        LinearLayout layout = column();
        layout.setPadding(dp(16), dp(8), dp(16), 0);

        EditText search = new EditText(this);
        search.setSingleLine();
        search.setHint("Tìm tên recipe hoặc film simulation");
        layout.addView(search);

        TreeSet<String> sensors = new TreeSet<>();
        for (Recipe r : recipes) {
            if (!r.sensors.isEmpty()) {
                sensors.addAll(Arrays.asList(r.sensors.split(" / ")));
            }
        }
        List<String> options = new ArrayList<>();
        options.add("Tất cả cảm biến");
        options.addAll(sensors);

        Spinner sensor = new Spinner(this);
        sensor.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
        layout.addView(sensor);

        TextView count = text("", 11, StudioStyle.MUTED);
        count.setPadding(dp(4), dp(4), dp(4), dp(4));
        layout.addView(count);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(dp(8));
        layout.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(380)));

        List<Recipe> filtered = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, new ArrayList<>()) {
            @Override
            public View getView(int position, View reusable, ViewGroup parent) {
                Recipe recipe = filtered.get(position);
                boolean selected = (currentRecipe != null && currentRecipe.id.equals(recipe.id));

                LinearLayout card = row();
                card.setPadding(dp(8), dp(8), dp(8), dp(8));
                card.setBackground(StudioStyle.surface(
                        selected ? StudioStyle.WARM_AMBER_LIGHT : StudioStyle.SURFACE,
                        selected ? StudioStyle.WARM_AMBER : StudioStyle.LINE,
                        dp(16)));

                ImageView symbol = thumbnail();
                symbol.setContentDescription("Minh họa màu " + recipe.name);
                card.addView(symbol, new LinearLayout.LayoutParams(dp(72), dp(76)));

                LinearLayout description = column();
                description.setPadding(dp(10), 0, dp(4), 0);
                TextView name = text(recipe.name, 14.5f, StudioStyle.INK);
                name.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                name.setMaxLines(2);
                name.setEllipsize(TextUtils.TruncateAt.END);
                description.addView(name);

                TextView detail = text(recipe.film + " · " + recipe.sensors, 11, StudioStyle.MUTED);
                detail.setPadding(0, dp(3), 0, 0);
                detail.setMaxLines(2);
                description.addView(detail);

                TextView previewState = text("", 10.5f, StudioStyle.WARM_AMBER);
                previewState.setPadding(0, dp(4), 0, 0);
                description.addView(previewState);

                Settings recipeSettings = recipe.adjustments(new ArrayList<>());
                String lutFile = prefs.getString("lut." + recipeSettings.film, null);
                thumbnails.bind(
                        symbol,
                        previewState,
                        recipe.id,
                        recipeSettings,
                        lutFile == null ? null : new File(getFilesDir(), lutFile),
                        null);

                card.addView(description, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                if (selected) {
                    TextView check = text("✓", 18, StudioStyle.WARM_AMBER);
                    check.setPadding(dp(6), 0, dp(6), 0);
                    card.addView(check);
                }
                return card;
            }
        };
        list.setAdapter(adapter);

        Runnable filter = () -> {
            String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            String ss = (String) sensor.getSelectedItem();
            filtered.clear();
            adapter.clear();
            for (Recipe r : recipes) {
                if ((r.name + " " + r.film).toLowerCase(Locale.ROOT).contains(q)
                        && (sensor.getSelectedItemPosition() == 0 || Arrays.asList(r.sensors.split(" / ")).contains(ss))) {
                    filtered.add(r);
                    adapter.add(r.name);
                }
            }
            count.setText(filtered.size() + " / " + recipes.size() + " recipe · chạm để dùng, giữ để xem thông số");
        };

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int f) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { filter.run(); }
            @Override public void afterTextChanged(Editable e) {}
        });
        sensor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> a) {}
            @Override public void onItemSelected(AdapterView<?> a, View v, int p, long i) { filter.run(); }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Chọn màu phim (" + recipes.size() + " công thức)")
                .setView(layout)
                .setNeutralButton("Nguyên bản", (d, w) -> applyOriginalLook(false))
                .setNegativeButton("Đóng", null)
                .create();

        list.setOnItemClickListener((p, v, pos, id) -> {
            Recipe r = filtered.get(pos);
            dialog.dismiss();
            applyRecipe(r);
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            showRecipe(filtered.get(pos));
            return true;
        });
        dialog.setOnDismissListener(d -> closeThumbnails(thumbnails));
        filter.run();
        dialog.show();
    }

    private String applicationNotes(Recipe r) {
        List<String> notes = new ArrayList<>();
        r.adjustments(notes);
        return notes.isEmpty() ? "" : "\nCách áp dụng trong IRO:\n" + String.join("\n", notes) + "\n";
    }

    private void showRecipe(Recipe r) {
        ScrollView scroll = new ScrollView(this);
        TextView text = text(
                r.description()
                        + applicationNotes(r)
                        + "\nNguồn: " + r.sourceUrl
                        + "\n\nThông số này dành cho máy Fuji. App áp dụng gần đúng các chỉnh sửa hậu kỳ; màu nền cần LUT riêng. Các giá trị thiếu trong nguồn không được tự điền.",
                13, StudioStyle.INK);
        text.setTextIsSelectable(true);
        text.setPadding(dp(20), dp(12), dp(20), dp(12));
        scroll.addView(text);

        new AlertDialog.Builder(this)
                .setTitle(r.name)
                .setView(scroll)
                .setPositiveButton("Dùng recipe", (d, w) -> applyRecipe(r))
                .setNeutralButton("Mở bài nguồn", (d, w) -> {
                    Uri uri = Uri.parse(r.sourceUrl);
                    if ("https".equals(uri.getScheme())) {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } else {
                        message("Không có đường dẫn nguồn hợp lệ");
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void applyRecipe(Recipe r) {
        recordUndo();
        recipeNotes.clear();
        settings = r.adjustments(recipeNotes);
        currentRecipe = r;
        isAutoBalanced = false;
        isComparing = false;
        syncAllUiControls();
        loadBoundLut();
        persist();
    }

    private void editRecipe() {
        if (currentRecipe == null) {
            message("Chọn recipe trước khi sửa các thông số nguồn");
            return;
        }
        try {
            Recipe edited = new Recipe(currentRecipe.json());
            LinearLayout form = column();
            form.setPadding(dp(18), dp(8), dp(18), dp(8));
            Map<String, EditText> fields = new LinkedHashMap<>();

            EditText name = new EditText(this);
            name.setText(edited.name + " · bản riêng");
            form.addView(text("Tên recipe", 12, StudioStyle.MUTED));
            form.addView(name);

            EditText film = new EditText(this);
            film.setText(edited.film);
            form.addView(text("Film Simulation (khớp với LUT đã gắn)", 12, StudioStyle.MUTED));
            form.addView(film);

            for (Map.Entry<String, String> e : edited.raw.entrySet()) {
                form.addView(text(e.getKey(), 12, StudioStyle.MUTED));
                EditText value = new EditText(this);
                value.setText(e.getValue());
                form.addView(value);
                fields.put(e.getKey(), value);
            }

            ScrollView scroll = new ScrollView(this);
            scroll.addView(form);
            new AlertDialog.Builder(this)
                    .setTitle("Sửa bản riêng của recipe")
                    .setView(scroll)
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Áp dụng", (d, w) -> {
                        edited.id = "custom-" + UUID.randomUUID();
                        edited.name = name.getText().toString();
                        edited.film = film.getText().toString();
                        for (Map.Entry<String, EditText> e : fields.entrySet()) {
                            edited.raw.put(e.getKey(), e.getValue().getText().toString());
                        }
                        edited.raw.remove("Film Simulation");
                        recipes.add(0, edited);
                        applyRecipe(edited);
                        message("Dùng Lưu công thức JSON để xuất file bản chỉnh sửa.");
                    })
                    .show();
        } catch (Exception e) {
            error(e);
        }
    }

    private void importRecipe(Uri uri) {
        executor.execute(() -> {
            try {
                JSONObject j = new JSONObject(new String(
                        readLimited(getContentResolver().openInputStream(uri), 8 * 1024 * 1024),
                        StandardCharsets.UTF_8));
                String appTag = j.optString("app");
                if ("IRO_ANDROID".equals(appTag) || "IRO_IOS".equals(appTag)) {
                    Settings s = SettingsCodec.decode(j.getJSONObject("settings"));
                    Recipe r = j.has("recipe") ? new Recipe(j.getJSONObject("recipe")) : null;
                    runOnUiThread(() -> {
                        if (dead) return;
                        recordUndo();
                        settings = s;
                        currentRecipe = r;
                        isAutoBalanced = false;
                        syncAllUiControls();
                        loadBoundLut();
                        persist();
                        message("Đã nhập chỉnh sửa. LUT cần được nhập riêng.");
                    });
                } else {
                    JSONArray a = j.optJSONArray("recipes");
                    if (a == null) {
                        a = new JSONArray().put(j);
                    }
                    if (a.length() > 1000) {
                        throw new JSONException("Tối đa 1000 recipe mỗi file");
                    }
                    List<Recipe> imported = new ArrayList<>();
                    for (int i = 0; i < a.length(); i++) {
                        imported.add(new Recipe(a.getJSONObject(i)));
                    }
                    runOnUiThread(() -> {
                        if (dead) return;
                        recipes.addAll(0, imported);
                        syncFiltersSection();
                        if (imported.size() == 1) {
                            applyRecipe(imported.get(0));
                        }
                        message("Đã nhập " + imported.size() + " recipe vào phiên làm việc");
                    });
                }
            } catch (Exception e) {
                error(e);
            }
        });
    }

    private void saveRecipe() {
        try {
            JSONObject j = new JSONObject();
            j.put("app", "IRO_ANDROID");
            j.put("version", 1);
            j.put("settings", SettingsCodec.encode(settings));
            if (currentRecipe != null) {
                j.put("recipe", currentRecipe.json());
            }
            j.put("lutNote", "LUT must be imported separately for this film simulation");
            exportRecipeJson = j.toString(2);
            createDocument(SAVE_RECIPE, "application/json", "iro-recipe.json");
        } catch (Exception e) {
            error(e);
        }
    }

    private void persist() {
        try {
            SharedPreferences.Editor e = prefs.edit()
                    .putString("settings", SettingsCodec.encode(settings).toString());
            if (currentRecipe == null) {
                e.remove("recipe");
            } else {
                e.putString("recipe", currentRecipe.json().toString());
            }
            e.apply();
        } catch (Exception e) {
            error(e);
        }
    }

    // =========================================================================
    // HỘP THOẠI KHUNG THÔNG TIN ẢNH EXIF & XUẤT ẢNH ĐỘ PHÂN GIẢI CAO
    // =========================================================================

    private void editPhotoFrame() {
        if (preview == null) {
            message("Mở ảnh trước khi chèn thông tin");
            return;
        }
        final PhotoInfo draft = photoInfo.copy();
        final int[] style = {frameStyle == 0 ? 1 : frameStyle};

        LinearLayout form = column();
        form.setPadding(dp(16), dp(8), dp(16), dp(12));

        TextView pickerBtn = text("Chọn mẫu · " + PhotoFrame.STYLES[style[0]], 13, StudioStyle.WARM_AMBER);
        pickerBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        pickerBtn.setGravity(Gravity.CENTER);
        pickerBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
        pickerBtn.setBackground(StudioStyle.surface(StudioStyle.WARM_AMBER_LIGHT, StudioStyle.WARM_AMBER, dp(14)));
        form.addView(pickerBtn);

        Bitmap source = (rendered == null) ? preview : rendered;
        float ratio = Math.min(1f, 640f / Math.max(source.getWidth(), source.getHeight()));
        final Bitmap small = Bitmap.createScaledBitmap(
                source,
                Math.max(1, Math.round(source.getWidth() * ratio)),
                Math.max(1, Math.round(source.getHeight() * ratio)),
                true);

        ImageView example = new ImageView(this);
        example.setScaleType(ImageView.ScaleType.FIT_CENTER);
        example.setContentDescription("Xem trước khung thông tin ảnh");
        example.setPadding(0, dp(8), 0, dp(8));
        form.addView(example, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200)));

        form.addView(text(
                "Thông tin đọc từ ảnh gốc; mục trống là ảnh không có dữ liệu. Bạn có thể sửa hoặc để trống. Glass, Date và Signature chèn lên ảnh; các mẫu còn lại thêm viền ngoài.",
                12, StudioStyle.MUTED));

        String[] labels = {
                "Tên máy / điện thoại",
                "Tiêu cự",
                "Khẩu độ",
                "Tốc độ chụp",
                "ISO",
                "Ngày giờ chụp",
                "Tên tác giả / chữ ký"
        };
        String[] values = {
                draft.camera, draft.focal, draft.aperture,
                draft.shutter, draft.iso, draft.date, draft.author
        };
        EditText[] fields = new EditText[values.length];
        final boolean[] open = {true};

        Runnable redraw = () -> {
            if (!open[0]) return;
            draft.camera = PhotoInfo.clean(fields[0].getText().toString());
            draft.focal = PhotoInfo.clean(fields[1].getText().toString());
            draft.aperture = PhotoInfo.clean(fields[2].getText().toString());
            draft.shutter = PhotoInfo.clean(fields[3].getText().toString());
            draft.iso = PhotoInfo.clean(fields[4].getText().toString());
            draft.date = PhotoInfo.clean(fields[5].getText().toString());
            draft.author = PhotoInfo.clean(fields[6].getText().toString());
            example.setImageBitmap(PhotoFrame.render(small, draft, style[0]));
        };

        for (int i = 0; i < values.length; i++) {
            TextView lbl = text(labels[i], 12, StudioStyle.MUTED);
            lbl.setPadding(0, dp(6), 0, 0);
            form.addView(lbl);

            EditText input = new EditText(this);
            input.setSingleLine();
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
            input.setText(values[i]);
            fields[i] = input;
            form.addView(input);

            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int c, int f) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                    handler.removeCallbacks(redraw);
                    handler.postDelayed(redraw, 120);
                }
                @Override public void afterTextChanged(Editable e) {}
            });
        }

        pickerBtn.setOnClickListener(v -> {
            redraw.run();
            FrameTemplatePicker.show(this, small, draft, style[0], n -> {
                style[0] = n;
                pickerBtn.setText("Chọn mẫu · " + PhotoFrame.STYLES[n]);
                redraw.run();
            });
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Khung thông tin ảnh")
                .setView(scroll)
                .setPositiveButton("Áp dụng", (d, n) -> {
                    redraw.run();
                    recordUndo();
                    photoInfo = draft.copy();
                    frameStyle = style[0];
                    frameVersion++;
                    isComparing = false;
                    savePhotoFrame();
                    syncFramesSection();
                    showImage();
                })
                .setNeutralButton("Bỏ khung", (d, n) -> {
                    recordUndo();
                    frameStyle = 0;
                    frameSource = null;
                    framePreview = null;
                    savePhotoFrame();
                    syncFramesSection();
                    showImage();
                })
                .setNegativeButton("Hủy", null)
                .create();

        dialog.setOnDismissListener(d -> {
            open[0] = false;
            handler.removeCallbacks(redraw);
        });
        dialog.show();
        redraw.run();
    }

    private void savePhotoFrame() {
        if (imageUri == null) return;
        try {
            prefs.edit()
                    .putInt("frame.style", frameStyle)
                    .putString("frame.photo", imageUri.toString())
                    .putString("frame.info", photoInfo.json().toString())
                    .apply();
        } catch (Exception e) {
            error(e);
        }
    }

    private void chooseExport() {
        if (imageUri == null) {
            message("Mở ảnh trước khi xuất");
            return;
        }
        if (exportBusy) {
            message("Đang xuất ảnh, vui lòng đợi");
            return;
        }
        LinearLayout form = column();
        form.setPadding(dp(20), dp(8), dp(20), dp(12));

        form.addView(text("Độ phân giải", 13, StudioStyle.INK));
        Spinner size = new Spinner(this);
        size.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{
                "Tiêu chuẩn · tối đa 12 MP",
                "Giữ kích thước gốc",
                "Phóng lớn 2× · gấp 4 số điểm ảnh"
        }));
        size.setSelection(2);
        form.addView(size);

        TextView fmtLabel = text("Định dạng", 13, StudioStyle.INK);
        fmtLabel.setPadding(0, dp(8), 0, 0);
        form.addView(fmtLabel);

        Spinner format = new Spinner(this);
        format.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{
                "JPEG · chất lượng 100%",
                "JPEG · chất lượng 95%",
                "PNG · nén không mất dữ liệu"
        }));
        form.addView(format);

        TextView note1 = text(
                "2× tăng cả chiều rộng và chiều cao từ ảnh gốc bằng nội suy, chưa phải AI phục hồi chi tiết. Nếu thiếu bộ nhớ, chọn kích thước gốc hoặc tiêu chuẩn.",
                12, StudioStyle.MUTED);
        note1.setPadding(0, dp(8), 0, dp(4));
        form.addView(note1);

        form.addView(text(
                frameStyle == 0
                        ? "Không chèn khung thông tin ảnh"
                        : "Kèm khung: " + PhotoFrame.STYLES[frameStyle] + ". Kích thước file có thêm phần khung.",
                12, StudioStyle.WARM_AMBER));

        new AlertDialog.Builder(this)
                .setTitle("Xuất ảnh đã chỉnh")
                .setView(form)
                .setPositiveButton("Tiếp tục", (d, n) -> {
                    int selected = size.getSelectedItemPosition();
                    int type = format.getSelectedItemPosition();
                    exportScale = (selected == 2) ? 2 : 1;
                    exportFullSize = (selected > 0);
                    exportQuality = (type == 1) ? 95 : 100;
                    exportType = (type == 2) ? "image/png" : "image/jpeg";
                    exportSettings = settings.copy();
                    exportLut = activeLut;
                    exportUri = imageUri;
                    exportInfo = photoInfo.copy();
                    exportFrameStyle = frameStyle;
                    exportBusy = true;
                    try {
                        createDocument(
                                SAVE_IMAGE,
                                exportType,
                                "iro-" + System.currentTimeMillis()
                                        + (exportScale == 2 ? "-2x" : "")
                                        + (type == 2 ? ".png" : ".jpg"));
                    } catch (Exception e) {
                        exportBusy = false;
                        exportUri = null;
                        exportLut = null;
                        error(e);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private Bitmap prepareExport(Uri src,
                                 Settings s,
                                 CubeLut lut,
                                 int scale,
                                 boolean fullSize,
                                 PhotoInfo frameInfo,
                                 int style) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long available = Math.max(0L, runtime.maxMemory() - used - 24L * 1024 * 1024);
        long pixelBudget = available / (24L + (style > 0 ? 14L : 4L) * scale * scale);
        if (pixelBudget < 1) {
            throw new IOException("Không đủ bộ nhớ xuất ảnh. Hãy đóng ứng dụng khác rồi thử lại.");
        }

        Bitmap decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), src), (decoder, info, source) -> {
            int[] size = ExportSizing.dimensions(
                    info.getSize().getWidth(),
                    info.getSize().getHeight(),
                    scale,
                    fullSize,
                    pixelBudget);
            decoder.setTargetSize(size[0], size[1]);
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
        });

        Bitmap result = null;
        try {
            int w = decoded.getWidth();
            int h = decoded.getHeight();
            int[] input = new int[w * h];
            decoded.getPixels(input, 0, w, 0, 0, w, h);
            decoded.recycle();
            decoded = null;

            int[] out = ColorEngine.process(input, w, h, s, lut);
            result = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888);

            if (!lightCraftState.isIdentity()) {
                boolean prevOvl = lightCraftState.showMaskOverlay;
                lightCraftState.showMaskOverlay = false;
                try {
                    Bitmap lcProcessed = LightCraftEngine.processBitmap(result, lightCraftState);
                    if (lcProcessed != result) {
                        result.recycle();
                        result = lcProcessed;
                    }
                } finally {
                    lightCraftState.showMaskOverlay = prevOvl;
                }
            }

            if (scale > 1) {
                Bitmap enlarged = Bitmap.createScaledBitmap(result, w * scale, h * scale, true);
                result.recycle();
                result = enlarged;
            }
            if (style > 0) {
                Bitmap framed = PhotoFrame.render(result, frameInfo, style);
                result.recycle();
                result = framed;
            }
            Bitmap ready = result;
            result = null;
            return ready;
        } finally {
            if (decoded != null) decoded.recycle();
            if (result != null) result.recycle();
        }
    }

    private void exportImage(Uri destination) {
        if (exportUri == null || exportSettings == null) {
            exportBusy = false;
            message("Phiên xuất ảnh đã hết. Vui lòng xuất lại.");
            return;
        }
        final Uri src = exportUri;
        final Settings s = exportSettings;
        final CubeLut lut = exportLut;
        final String mime = exportType;
        final int scale = exportScale;
        final int quality = exportQuality;
        final boolean fullSize = exportFullSize;
        final PhotoInfo info = exportInfo;
        final int style = exportFrameStyle;

        statusLabel.setText(scale == 2 ? "Đang chỉnh màu và phóng lớn 2×…" : "Đang xuất ảnh…");
        executor.execute(() -> {
            Bitmap result = null;
            try {
                result = prepareExport(src, s, lut, scale, fullSize, info, style);
                int w = result.getWidth();
                int h = result.getHeight();
                try (OutputStream stream = getContentResolver().openOutputStream(destination, "wt")) {
                    if (stream == null) throw new IOException("Không mở được nơi lưu");
                    Bitmap.CompressFormat fmt = "image/png".equals(mime)
                            ? Bitmap.CompressFormat.PNG
                            : Bitmap.CompressFormat.JPEG;
                    if (!result.compress(fmt, quality, stream)) {
                        throw new IOException("Không nén được ảnh");
                    }
                }
                runOnUiThread(() -> {
                    if (dead) return;
                    statusLabel.setText("Đã lưu ảnh " + w + " × " + h + " px");
                    message(statusLabel.getText().toString());
                });
            } catch (Exception e) {
                error(e);
            } catch (OutOfMemoryError e) {
                runOnUiThread(() -> {
                    if (!dead) {
                        message("Không đủ bộ nhớ. Chọn kích thước gốc hoặc tiêu chuẩn và xuất lại.");
                    }
                });
            } finally {
                if (result != null) result.recycle();
                runOnUiThread(() -> {
                    exportBusy = false;
                    exportUri = null;
                    exportLut = null;
                });
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        // Khi xoay ngang/dọc hoặc thay đổi kích thước cửa sổ trên Máy tính bảng,
        // tự động dựng lại giao diện Adaptive (Dual-Pane / 3-Zone Studio <-> Portrait) mà vẫn giữ nguyên ảnh & Undo
        buildUi();
        showImage();
    }

    @Override
    protected void onPause() {
        persist();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        dead = true;
        if (onlineLuts != null) {
            onlineLuts.close();
        }
        for (ThumbnailSession session : new ArrayList<>(thumbnailSessions)) {
            session.close();
        }
        thumbnailSessions.clear();
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }
}
