package vn.iro.editor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioAttributes;
import android.media.Image;
import android.media.ImageReader;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Range;
import android.util.Size;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Phân hệ máy ảnh quang học Leica M Rangefinder & Film Alchemy trực tiếp (Live Optical Camera).
 *
 * <p>Được thiết kế và nâng cấp từ phân tích kỹ thuật REA trên Xiaomi Leica Camera Flagship (APK 9.0.2):
 * <ul>
 *     <li><b>Âm thanh màn trập cơ học:</b> Tích hợp SoundPool phát âm thanh màn trập Leica cổ điển (vintage shutter click)
 *         và âm báo lấy nét quang học (autofocus chirp) chuẩn cơ khí.</li>
 *     <li><b>Biểu trưng Leica chính thức:</b> Khung ảnh đóng dấu sử dụng tệp logo Leica Red Dot chính hãng độ phân giải cao.</li>
 *     <li><b>Đa dạng chế độ khung ảnh:</b>
 *         <ul>
 *             <li>Khung triển lãm trắng (Leica Exhibition White Frame).</li>
 *             <li>Khung phiên bản giới hạn đen Obsidian (Leica Limited Edition Black).</li>
 *             <li>Khung cuộn phim 35mm hoài cổ có lỗ răng cưa (35mm Sprocket Film Frame).</li>
 *             <li>Chế độ nguyên bản không đóng khung (Raw Unframed).</li>
 *         </ul>
 *     </li>
 *     <li><b>Khung ngắm Rangefinder Brightlines:</b> Bốn góc quang học, tâm ngắm cơ học và thước cân bằng điện tử gyro level.</li>
 *     <li><b>Dải thông số Telemetry HUD:</b> Cập nhật ISO, tốc độ màn trập, khẩu độ và bù sáng EV từ cảm biến thời gian thực.</li>
 *     <li><b>Bộ chuyển đổi tiêu cự Summilux:</b> 28mm (Wide) · 35mm (Street) · 50mm (Standard) · 75mm (Portrait).</li>
 *     <li><b>Bàn xoay phim cơ học (Film Simulation Reel):</b> 28 cấu hình Leica M & phim nhựa kinh điển.</li>
 * </ul>
 */
public class CameraActivity extends Activity {

    private static final int PERMISSION_REQUEST_CAMERA = 101;
    public static final String EXTRA_RECIPE_ID = "recipe_id";
    public static final String EXTRA_IMAGE_URI = "image_uri";

    // Views
    private TextureView textureView;
    private RangefinderOverlayView rangefinderOverlay;
    private FocusReticleView focusReticle;
    private View shutterFlashView;
    private TextView countdownView;
    private TextView hudTelemetryView;
    private ImageView recentThumbBtn;
    private View shutterInnerBtn;
    private LinearLayout filmReelContainer;
    private HorizontalScrollView filmReelScroll;
    private LinearLayout focalSelectorRow;
    private TextView watermarkTopBtn;
    private TextView modeTitleView;
    private TextView modeSubView;
    private View toastNotificationBanner;
    private ImageView toastThumbView;
    private TextView toastTitleView;

    // Camera2 API
    private CameraManager cameraManager;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private Size previewSize;
    private Size captureSize;
    private Rect sensorArraySize;
    private ImageReader imageReader;
    private int sensorOrientation = 0;
    private boolean isFrontFacing = false;

    // Threads
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private final ExecutorService processExecutor = Executors.newSingleThreadExecutor();

    // Sensors
    private SensorManager sensorManager;
    private Sensor accelerometer;

    // Sound effects (Trích xuất từ APK Xiaomi/Leica Pro Camera 9.0.2)
    private SoundPool soundPool;
    private int soundShutterVintageId = 0;
    private int soundFocusId = 0;

    // Watermark graphics (Trích xuất từ APK Xiaomi/Leica Pro Camera 9.0.2)
    private Bitmap leicaLogoBitmap = null;
    private Bitmap sprocketTopBitmap = null;
    private Bitmap sprocketBottomBitmap = null;

    // Chế độ Watermark: 0: Trắng Leica, 1: Đen Leica, 2: Khung phim 35mm, 3: Không khung
    private int watermarkMode = 0;
    private final String[] WATERMARK_NAMES = { "🔴 TRẮNG", "⚫ ĐEN", "🎞 PHIM", "○ GỐC" };
    private final String[] WATERMARK_SUB_NAMES = { "EXHIBITION", "LIMITED", "SPROCKET", "UNFRAMED" };

    // Data catalog
    private final List<Recipe> cameraRecipes = new ArrayList<>();
    private Recipe activeRecipe;
    private CubeLut activeLut;

    // Camera settings state
    private int flashMode = CaptureRequest.CONTROL_AE_MODE_ON;
    private int flashStateIndex = 0; // 0: Auto, 1: On, 2: Off
    private int timerSeconds = 0; // 0: Off, 3: 3s, 10: 10s
    private boolean isGridEnabled = true;
    private int currentEv = 0;
    private Range<Integer> evRange = new Range<>(0, 0);
    private float evStep = 0.333f;
    private Uri lastCapturedUri = null;
    private boolean isCapturing = false;

    // Lens focal crop settings (28mm, 35mm, 50mm, 75mm)
    private int currentFocalIndex = 1; // Mặc định 35mm (Classic Leica Street)
    private final float[] FOCAL_ZOOMS = { 1.0f, 1.25f, 1.8f, 2.7f };
    private final String[] FOCAL_NAMES = { "28mm", "35mm", "50mm", "75mm" };
    private final String[] FOCAL_LENSES = {
            "SUMMICRON-M 1:2.0/28 ASPH.",
            "SUMMILUX-M 1:1.4/35 ASPH.",
            "NOCTILUX-M 1:0.95/50 ASPH.",
            "APO-SUMMICRON-M 1:2.0/75 ASPH."
    };

    // Live exposure telemetry
    private int liveIso = 100;
    private String liveShutter = "1/250s";
    private float liveAperture = 1.4f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Chế độ toàn màn hình điện ảnh (Immersive Cinematic Mode)
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        initSounds();
        loadWatermarkBitmaps();
        loadCameraCatalog();
        buildUi();
    }

    private int dp(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // =========================================================================
    // NẠP TÀI NGUYÊN ÂM THANH & ĐỒ HỌA REA
    // =========================================================================

    private void initSounds() {
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(3)
                    .setAudioAttributes(attrs)
                    .build();

            AssetFileDescriptor afd1 = getAssets().openFd("sounds/shutter_vintage.wav");
            soundShutterVintageId = soundPool.load(afd1, 1);

            AssetFileDescriptor afd2 = getAssets().openFd("sounds/camera_focus.wav");
            soundFocusId = soundPool.load(afd2, 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playShutterSound() {
        if (soundPool != null && soundShutterVintageId != 0) {
            soundPool.play(soundShutterVintageId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    private void playFocusSound() {
        if (soundPool != null && soundFocusId != 0) {
            soundPool.play(soundFocusId, 0.7f, 0.7f, 1, 0, 1.0f);
        }
    }

    private void loadWatermarkBitmaps() {
        processExecutor.execute(() -> {
            try {
                try (InputStream in = getAssets().open("watermarks/leica_logo.webp")) {
                    leicaLogoBitmap = BitmapFactory.decodeStream(in);
                }
                try (InputStream in = getAssets().open("watermarks/sprocket_top.webp")) {
                    sprocketTopBitmap = BitmapFactory.decodeStream(in);
                }
                try (InputStream in = getAssets().open("watermarks/sprocket_bottom.webp")) {
                    sprocketBottomBitmap = BitmapFactory.decodeStream(in);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // =========================================================================
    // NẠP CATALOG MÀU PHIM & 3D LUT
    // =========================================================================

    private void loadCameraCatalog() {
        try {
            try (InputStream in = getAssets().open("cameralook-recipes.json")) {
                JSONObject obj = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
                JSONArray arr = obj.getJSONArray("recipes");
                for (int i = 0; i < arr.length(); i++) {
                    cameraRecipes.add(new Recipe(arr.getJSONObject(i)));
                }
            }

            String targetId = getIntent().getStringExtra(EXTRA_RECIPE_ID);
            if (targetId != null) {
                for (Recipe r : cameraRecipes) {
                    if (targetId.equals(r.id)) {
                        activeRecipe = r;
                        break;
                    }
                }
            }
            if (activeRecipe == null && !cameraRecipes.isEmpty()) {
                for (Recipe r : cameraRecipes) {
                    if ("leica_m9_classic_ccd".equals(r.id)) {
                        activeRecipe = r;
                        break;
                    }
                }
                if (activeRecipe == null) {
                    activeRecipe = cameraRecipes.get(0);
                }
            }

            loadActiveLut();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadActiveLut() {
        if (activeRecipe == null || activeRecipe.cubeAsset == null || activeRecipe.cubeAsset.isEmpty()) {
            activeLut = null;
            return;
        }
        processExecutor.execute(() -> {
            try (Reader r = new InputStreamReader(getAssets().open(activeRecipe.cubeAsset), StandardCharsets.UTF_8)) {
                CubeLut lut = CubeLut.parse(r);
                runOnUiThread(() -> {
                    activeLut = lut;
                    updateTelemetryDisplay();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    // =========================================================================
    // XÂY DỰNG GIAO DIỆN LEICA M LUX PRO VIEWFINDER
    // =========================================================================

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff090a0d); // Obsidian Black
        setContentView(root);

        // 1. Khung ngắm chính TextureView (4:3 chuẩn cảm biến quang học)
        FrameLayout viewfinderContainer = new FrameLayout(this);
        FrameLayout.LayoutParams vfLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        root.addView(viewfinderContainer, vfLp);

        textureView = new TextureView(this);
        textureView.setSurfaceTextureListener(textureListener);
        textureView.setOnTouchListener(viewfinderTouchListener);
        viewfinderContainer.addView(textureView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        // 2. Lớp lưới & vạch ngắm Rangefinder (Brightlines, Center Patch, Horizon Level)
        rangefinderOverlay = new RangefinderOverlayView(this);
        rangefinderOverlay.setGridEnabled(isGridEnabled);
        viewfinderContainer.addView(rangefinderOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 3. Vòng lấy nét chạm & thước trượt bù sáng EV trực quan
        focusReticle = new FocusReticleView(this);
        viewfinderContainer.addView(focusReticle, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 4. Thanh công cụ Top Pro Bar
        LinearLayout topBar = buildTopBar();
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        topLp.topMargin = dp(14);
        topLp.leftMargin = dp(16);
        topLp.rightMargin = dp(16);
        root.addView(topBar, topLp);

        // 5. Dải thông số quang học Telemetry HUD
        hudTelemetryView = buildTelemetryHud();
        FrameLayout.LayoutParams hudLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hudLp.topMargin = dp(68);
        root.addView(hudTelemetryView, hudLp);

        // 6. Bệ điều khiển máy ảnh đáy (Bottom Control Deck)
        LinearLayout bottomDeck = buildBottomDeck();
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        bottomLp.bottomMargin = dp(14);
        root.addView(bottomDeck, bottomLp);

        // 7. Banner thông báo nướng ảnh thành công
        toastNotificationBanner = buildToastBanner();
        FrameLayout.LayoutParams toastLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        toastLp.topMargin = dp(14);
        toastLp.leftMargin = dp(16);
        toastLp.rightMargin = dp(16);
        root.addView(toastNotificationBanner, toastLp);

        // 8. Đồng hồ đếm ngược hẹn giờ (Countdown)
        countdownView = new TextView(this);
        countdownView.setTextColor(0xfff59e0b);
        countdownView.setTextSize(92f);
        countdownView.setTypeface(Typeface.create("sans-serif-thin", Typeface.BOLD));
        countdownView.setGravity(Gravity.CENTER);
        countdownView.setVisibility(View.GONE);
        root.addView(countdownView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        // 9. Hiệu ứng chớp màn trập cơ khí
        shutterFlashView = new View(this);
        shutterFlashView.setBackgroundColor(Color.WHITE);
        shutterFlashView.setAlpha(0f);
        root.addView(shutterFlashView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        updateTelemetryDisplay();
    }

    // =========================================================================
    // CÁC THÀNH PHẦN GIAO DIỆN CON
    // =========================================================================

    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(4), dp(8), dp(4));
        bar.setBackground(createPillDrawable(0xe612141a, 0x33ffffff, dp(22)));

        // Nút Back
        ImageView backBtn = createIconBtn("back", v -> finish());
        bar.addView(backBtn);

        // Spacer
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        // Flash Button (Auto / On / Off)
        final TextView flashBtn = createTextBtn("⚡ A", v -> {
            flashStateIndex = (flashStateIndex + 1) % 3;
            if (flashStateIndex == 0) {
                flashMode = CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH;
                ((TextView) v).setText("⚡ A");
                ((TextView) v).setTextColor(0xfff59e0b);
            } else if (flashStateIndex == 1) {
                flashMode = CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH;
                ((TextView) v).setText("⚡ ON");
                ((TextView) v).setTextColor(0xfff59e0b);
            } else {
                flashMode = CaptureRequest.CONTROL_AE_MODE_ON;
                ((TextView) v).setText("⚡ ⃠");
                ((TextView) v).setTextColor(0xff8a8e9b);
            }
            updatePreviewSettings();
        });
        flashBtn.setTextColor(0xfff59e0b);
        bar.addView(flashBtn);

        // Lưới & Thước cân bằng Level
        final TextView gridBtn = createTextBtn("☩ GRID", v -> {
            isGridEnabled = !isGridEnabled;
            rangefinderOverlay.setGridEnabled(isGridEnabled);
            ((TextView) v).setTextColor(isGridEnabled ? 0xfff59e0b : 0xff8a8e9b);
        });
        gridBtn.setTextColor(isGridEnabled ? 0xfff59e0b : 0xff8a8e9b);
        bar.addView(gridBtn);

        // Hẹn giờ (Timer)
        final TextView timerBtn = createTextBtn("⏱ OFF", v -> {
            if (timerSeconds == 0) {
                timerSeconds = 3;
                ((TextView) v).setText("⏱ 3s");
                ((TextView) v).setTextColor(0xfff59e0b);
            } else if (timerSeconds == 3) {
                timerSeconds = 10;
                ((TextView) v).setText("⏱ 10s");
                ((TextView) v).setTextColor(0xfff59e0b);
            } else {
                timerSeconds = 0;
                ((TextView) v).setText("⏱ OFF");
                ((TextView) v).setTextColor(0xff8a8e9b);
            }
        });
        bar.addView(timerBtn);

        // Chuyển đổi khung ảnh (Watermark Mode Switcher: Trắng / Đen / Phim / Gốc)
        watermarkTopBtn = createTextBtn(WATERMARK_NAMES[watermarkMode], v -> cycleWatermarkMode());
        watermarkTopBtn.setTextColor(0xffffffff);
        bar.addView(watermarkTopBtn);

        // Đảo camera Trước/Sau
        ImageView flipBtn = createIconBtn("rotate_right", v -> switchCamera());
        bar.addView(flipBtn);

        return bar;
    }

    private void cycleWatermarkMode() {
        watermarkMode = (watermarkMode + 1) % WATERMARK_NAMES.length;
        if (watermarkTopBtn != null) {
            watermarkTopBtn.setText(WATERMARK_NAMES[watermarkMode]);
            watermarkTopBtn.setTextColor(watermarkMode != 3 ? 0xffffffff : 0xff8a8e9b);
        }
        if (modeTitleView != null && modeSubView != null) {
            modeTitleView.setText(WATERMARK_NAMES[watermarkMode]);
            modeSubView.setText(WATERMARK_SUB_NAMES[watermarkMode]);
        }
    }

    private TextView buildTelemetryHud() {
        TextView tv = new TextView(this);
        tv.setTextColor(0xfff0f2f8);
        tv.setTextSize(10.5f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setPadding(dp(12), dp(5), dp(12), dp(5));
        tv.setBackground(createPillDrawable(0xb30d0f14, 0x26ffffff, dp(14)));
        return tv;
    }

    private void updateTelemetryDisplay() {
        if (hudTelemetryView == null) return;
        String recipeTag = "LEICA M9 · CCD";
        if (activeRecipe != null) {
            recipeTag = activeRecipe.name.replace("[CameraLook] ", "").replace("[Leica M] ", "").toUpperCase(Locale.ROOT);
        }
        float displayEv = currentEv * evStep;
        String evStr = (displayEv >= 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f", displayEv) + " EV";
        String lens = FOCAL_NAMES[currentFocalIndex];

        hudTelemetryView.setText(String.format(Locale.ROOT,
                "🔴 %s  ·  %s  ·  ISO %d  ·  %s  ·  f/%.1f  ·  %s",
                recipeTag, lens, liveIso, liveShutter, liveAperture, evStr));
    }

    private LinearLayout buildBottomDeck() {
        LinearLayout deck = new LinearLayout(this);
        deck.setOrientation(LinearLayout.VERTICAL);
        deck.setGravity(Gravity.CENTER_HORIZONTAL);

        // 1. Hàng chọn tiêu cự ống kính cố định Leica (Lens Focal Crop Switcher)
        focalSelectorRow = new LinearLayout(this);
        focalSelectorRow.setOrientation(LinearLayout.HORIZONTAL);
        focalSelectorRow.setGravity(Gravity.CENTER);
        focalSelectorRow.setPadding(dp(16), 0, dp(16), dp(8));
        populateFocalSelector();
        deck.addView(focalSelectorRow);

        // 2. Bàn xoay phim cơ học (Film Simulation Reel)
        filmReelScroll = new HorizontalScrollView(this);
        filmReelScroll.setHorizontalScrollBarEnabled(false);
        filmReelScroll.setClipToPadding(false);
        filmReelScroll.setPadding(dp(16), 0, dp(16), dp(12));

        filmReelContainer = new LinearLayout(this);
        filmReelContainer.setOrientation(LinearLayout.HORIZONTAL);
        filmReelContainer.setGravity(Gravity.CENTER_VERTICAL);
        populateFilmReel();
        filmReelScroll.addView(filmReelContainer);
        deck.addView(filmReelScroll);

        // 3. Cụm phím bấm chụp cơ học Leica M
        LinearLayout shutterRow = new LinearLayout(this);
        shutterRow.setOrientation(LinearLayout.HORIZONTAL);
        shutterRow.setGravity(Gravity.CENTER_VERTICAL);
        shutterRow.setPadding(dp(28), dp(4), dp(28), dp(4));

        // Nút xem ảnh gần nhất
        FrameLayout recentFrame = new FrameLayout(this);
        recentFrame.setBackground(createPillDrawable(0xff181a22, 0x40ffffff, dp(12)));
        recentFrame.setPadding(dp(2), dp(2), dp(2), dp(2));

        recentThumbBtn = new ImageView(this);
        recentThumbBtn.setScaleType(ImageView.ScaleType.CENTER_CROP);
        recentFrame.addView(recentThumbBtn, new FrameLayout.LayoutParams(dp(46), dp(46)));

        recentFrame.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (lastCapturedUri != null) {
                Intent res = new Intent();
                res.setData(lastCapturedUri);
                setResult(RESULT_OK, res);
                finish();
            } else {
                Toast.makeText(this, "Chưa có ảnh mới vừa chụp", Toast.LENGTH_SHORT).show();
            }
        });
        shutterRow.addView(recentFrame);

        // Spacer
        shutterRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        // Nút chụp cơ khí Leica M (Milled Knurled Bezel & Cable Release Center Hole)
        FrameLayout shutterFrame = new FrameLayout(this);

        GradientDrawable knurledBezel = new GradientDrawable();
        knurledBezel.setShape(GradientDrawable.OVAL);
        knurledBezel.setColor(0xff22252e);
        knurledBezel.setStroke(dp(3f), 0xff3a3e4c);
        shutterFrame.setBackground(knurledBezel);

        shutterInnerBtn = new View(this);
        GradientDrawable innerButton = new GradientDrawable();
        innerButton.setShape(GradientDrawable.OVAL);
        innerButton.setColor(0xfff8f9fb);
        innerButton.setStroke(dp(1.5f), 0xffd1d5db);
        shutterInnerBtn.setBackground(innerButton);

        View cableReleaseHole = new View(this);
        GradientDrawable hole = new GradientDrawable();
        hole.setShape(GradientDrawable.OVAL);
        hole.setColor(0xff16181f);
        cableReleaseHole.setBackground(hole);

        FrameLayout.LayoutParams innerLp = new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER);
        shutterFrame.addView(shutterInnerBtn, innerLp);
        shutterFrame.addView(cableReleaseHole, new FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER));

        shutterFrame.setOnClickListener(v -> handleShutterClick());
        shutterRow.addView(shutterFrame, new LinearLayout.LayoutParams(dp(82), dp(82)));

        // Spacer
        shutterRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        // Nút bấm huy hiệu khung ảnh (Chạm để đổi khung nhanh: Trắng / Đen / Phim / Gốc)
        LinearLayout modeIndicator = new LinearLayout(this);
        modeIndicator.setOrientation(LinearLayout.VERTICAL);
        modeIndicator.setGravity(Gravity.CENTER);
        modeIndicator.setPadding(dp(12), dp(8), dp(12), dp(8));
        modeIndicator.setBackground(createPillDrawable(0xff181a22, 0x33ffffff, dp(14)));

        modeTitleView = new TextView(this);
        modeTitleView.setText(WATERMARK_NAMES[watermarkMode]);
        modeTitleView.setTextColor(0xfff59e0b);
        modeTitleView.setTextSize(10f);
        modeTitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        modeIndicator.addView(modeTitleView);

        modeSubView = new TextView(this);
        modeSubView.setText(WATERMARK_SUB_NAMES[watermarkMode]);
        modeSubView.setTextColor(0xff94a3b8);
        modeSubView.setTextSize(8.5f);
        modeIndicator.addView(modeSubView);

        modeIndicator.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            cycleWatermarkMode();
        });

        shutterRow.addView(modeIndicator);
        deck.addView(shutterRow);
        return deck;
    }

    private void populateFocalSelector() {
        focalSelectorRow.removeAllViews();
        for (int i = 0; i < FOCAL_NAMES.length; i++) {
            final int index = i;
            final boolean active = (index == currentFocalIndex);
            TextView chip = new TextView(this);
            chip.setText(FOCAL_NAMES[i]);
            chip.setTextSize(10.5f);
            chip.setTypeface(Typeface.create("sans-serif-medium", active ? Typeface.BOLD : Typeface.NORMAL));
            chip.setTextColor(active ? 0xff090a0d : 0xffc4c8d4);
            chip.setPadding(dp(14), dp(4), dp(14), dp(4));
            chip.setBackground(createPillDrawable(
                    active ? 0xfff59e0b : 0xcc181a22,
                    active ? 0 : 0x33ffffff,
                    dp(12)));

            chip.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                setFocalLength(index);
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(4), 0, dp(4), 0);
            focalSelectorRow.addView(chip, lp);
        }
    }

    private void setFocalLength(int index) {
        currentFocalIndex = index;
        populateFocalSelector();
        applyFocalCrop();
        updateTelemetryDisplay();
    }

    private void applyFocalCrop() {
        if (captureSession == null || previewRequestBuilder == null || sensorArraySize == null) return;
        try {
            float zoom = FOCAL_ZOOMS[currentFocalIndex];
            int cropW = Math.round(sensorArraySize.width() / zoom);
            int cropH = Math.round(sensorArraySize.height() / zoom);
            int cropX = (sensorArraySize.width() - cropW) / 2;
            int cropY = (sensorArraySize.height() - cropH) / 2;
            Rect cropRect = new Rect(cropX, cropY, cropX + cropW, cropY + cropH);
            previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, cropRect);
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), previewCaptureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void populateFilmReel() {
        filmReelContainer.removeAllViews();

        for (final Recipe r : cameraRecipes) {
            final boolean selected = (activeRecipe != null && activeRecipe.id.equals(r.id));

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(6), dp(12), dp(6));

            int accentColor = 0xfff59e0b;
            String subTag = "COLOR FILM";
            if (r.id.contains("leica")) {
                accentColor = 0xffd41428;
                subTag = r.id.contains("mono") ? "M3 MONOCHROM" : "CCD VINTAGE";
            } else if (r.id.contains("kodak") || r.id.contains("portra") || r.id.contains("kodachrome")) {
                accentColor = 0xffeab308;
                subTag = "COLOR NEGATIVE";
            } else if (r.id.contains("fuji") || r.id.contains("velvia") || r.id.contains("provia")) {
                accentColor = 0xff10b981;
                subTag = "CHROME REVERSAL";
            } else if (r.id.contains("cinestill")) {
                accentColor = 0xff06b6d4;
                subTag = "TUNGSTEN 800T";
            } else if (r.id.contains("mono") || r.id.contains("tri_x") || r.id.contains("hp5")) {
                accentColor = 0xffe2e8f0;
                subTag = "SILVER B&W";
            }

            card.setBackground(createPillDrawable(
                    selected ? 0xff20242e : 0xd913151b,
                    selected ? accentColor : 0x2bffffff,
                    dp(14)));

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            View dot = new View(this);
            GradientDrawable dotDraw = new GradientDrawable();
            dotDraw.setShape(GradientDrawable.OVAL);
            dotDraw.setColor(accentColor);
            dot.setBackground(dotDraw);
            header.addView(dot, new LinearLayout.LayoutParams(dp(6), dp(6)));

            TextView title = new TextView(this);
            String titleText = r.name.replace("[CameraLook] ", "").replace("[Leica M] ", "").toUpperCase(Locale.ROOT);
            title.setText(titleText);
            title.setTextSize(11f);
            title.setTypeface(Typeface.create("sans-serif-medium", selected ? Typeface.BOLD : Typeface.NORMAL));
            title.setTextColor(selected ? Color.WHITE : 0xffc7cad4);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            titleLp.leftMargin = dp(6);
            header.addView(title, titleLp);
            card.addView(header);

            TextView sub = new TextView(this);
            sub.setText(subTag);
            sub.setTextSize(8.5f);
            sub.setTextColor(selected ? accentColor : 0xff737987);
            sub.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
            card.addView(sub);

            card.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                activeRecipe = r;
                loadActiveLut();
                populateFilmReel();
                updateTelemetryDisplay();
                int targetX = card.getLeft() - (filmReelScroll.getWidth() - card.getWidth()) / 2;
                filmReelScroll.smoothScrollTo(Math.max(0, targetX), 0);
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            filmReelContainer.addView(card, lp);
        }
    }

    private View buildToastBanner() {
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(12), dp(8), dp(14), dp(8));
        banner.setBackground(createPillDrawable(0xf2141720, 0x40f59e0b, dp(18)));
        banner.setVisibility(View.GONE);
        banner.setAlpha(0f);

        toastThumbView = new ImageView(this);
        toastThumbView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        toastThumbView.setBackground(createPillDrawable(0xff000000, 0x33ffffff, dp(6)));
        banner.addView(toastThumbView, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.leftMargin = dp(10);

        toastTitleView = new TextView(this);
        toastTitleView.setTextColor(Color.WHITE);
        toastTitleView.setTextSize(11.5f);
        toastTitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textCol.addView(toastTitleView);

        TextView actionHint = new TextView(this);
        actionHint.setText("Chạm để mở phòng tối IRO Studio →");
        actionHint.setTextColor(0xfff59e0b);
        actionHint.setTextSize(9.5f);
        textCol.addView(actionHint);
        banner.addView(textCol, textLp);

        banner.setOnClickListener(v -> {
            if (lastCapturedUri != null) {
                Intent res = new Intent();
                res.setData(lastCapturedUri);
                setResult(RESULT_OK, res);
                finish();
            }
        });

        return banner;
    }

    private void showToastBanner(String filmName, Bitmap thumb) {
        if (toastNotificationBanner == null) return;
        toastThumbView.setImageBitmap(thumb);
        toastTitleView.setText("✓ Đã nướng màu [" + filmName + "]");
        toastNotificationBanner.setVisibility(View.VISIBLE);
        toastNotificationBanner.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(220)
                .start();

        toastNotificationBanner.removeCallbacks(hideToastRunnable);
        toastNotificationBanner.postDelayed(hideToastRunnable, 3200);
    }

    private final Runnable hideToastRunnable = () -> {
        if (toastNotificationBanner != null) {
            toastNotificationBanner.animate()
                    .alpha(0f)
                    .translationY(-dp(20))
                    .setDuration(250)
                    .withEndAction(() -> toastNotificationBanner.setVisibility(View.GONE))
                    .start();
        }
    };

    private ImageView createIconBtn(String iconName, View.OnClickListener l) {
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new StudioStyle.Icon(iconName, Color.WHITE, dp(18)));
        iv.setPadding(dp(10), dp(8), dp(10), dp(8));
        iv.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            l.onClick(v);
        });
        return iv;
    }

    private TextView createTextBtn(String text, View.OnClickListener l) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xffc4c8d4);
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setPadding(dp(10), dp(6), dp(10), dp(6));
        tv.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            l.onClick(v);
        });
        return tv;
    }

    private GradientDrawable createPillDrawable(int bgColor, int strokeColor, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(bgColor);
        gd.setCornerRadius(radius);
        if (strokeColor != 0) {
            gd.setStroke(dp(1f), strokeColor);
        }
        return gd;
    }

    // =========================================================================
    // QUY TRÌNH BẤM MÀN TRẬP & NƯỚNG MÀU PHIM
    // =========================================================================

    private void handleShutterClick() {
        if (isCapturing) return;

        // Phát âm thanh màn trập cơ học Leica M
        playShutterSound();

        // Phản hồi cơ khí haptic kép
        shutterInnerBtn.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        shutterInnerBtn.animate()
                .scaleX(0.86f).scaleY(0.86f)
                .setDuration(60)
                .withEndAction(() -> shutterInnerBtn.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                .start();

        if (timerSeconds > 0) {
            startCountdown(timerSeconds);
        } else {
            captureStillPicture();
        }
    }

    private void startCountdown(int sec) {
        isCapturing = true;
        countdownView.setVisibility(View.VISIBLE);
        runTimerTick(sec);
    }

    private void runTimerTick(final int count) {
        if (count <= 0) {
            countdownView.setVisibility(View.GONE);
            captureStillPicture();
            return;
        }
        countdownView.setText(String.valueOf(count));
        countdownView.setScaleX(1.4f);
        countdownView.setScaleY(1.4f);
        countdownView.animate().scaleX(1f).scaleY(1f).setDuration(350).start();
        countdownView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);

        countdownView.postDelayed(() -> runTimerTick(count - 1), 1000);
    }

    private void captureStillPicture() {
        if (cameraDevice == null || captureSession == null) {
            isCapturing = false;
            return;
        }

        try {
            isCapturing = true;

            // Hiệu ứng chớp màn trập cơ học
            shutterFlashView.setAlpha(0.85f);
            shutterFlashView.animate().alpha(0f).setDuration(110).start();
            shutterFlashView.performHapticFeedback(HapticFeedbackConstants.CONFIRM);

            CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode);
            captureBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentEv);

            float zoom = FOCAL_ZOOMS[currentFocalIndex];
            if (sensorArraySize != null && zoom > 1.0f) {
                int cropW = Math.round(sensorArraySize.width() / zoom);
                int cropH = Math.round(sensorArraySize.height() / zoom);
                int cropX = (sensorArraySize.width() - cropW) / 2;
                int cropY = (sensorArraySize.height() - cropH) / 2;
                captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, new Rect(cropX, cropY, cropX + cropW, cropY + cropH));
            }

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation(sensorOrientation, rotation, isFrontFacing));

            captureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    isCapturing = false;
                }
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
            isCapturing = false;
        }
    }

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) return;
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                Bitmap rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (rawBitmap != null) {
                    processExecutor.execute(() -> bakeAndSavePhoto(rawBitmap));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (image != null) image.close();
            }
        }
    };

    private void bakeAndSavePhoto(Bitmap rawBitmap) {
        try {
            Bitmap oriented = rawBitmap;
            if (isFrontFacing) {
                Matrix m = new Matrix();
                m.preScale(-1.0f, 1.0f);
                oriented = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.getWidth(), rawBitmap.getHeight(), m, true);
                if (oriented != rawBitmap) rawBitmap.recycle();
            }

            Recipe r = activeRecipe;
            CubeLut lut = activeLut;

            int w = oriented.getWidth();
            int h = oriented.getHeight();
            int[] pixels = new int[w * h];
            oriented.getPixels(pixels, 0, w, 0, 0, w, h);
            oriented.recycle();

            Settings settings = (r != null)
                    ? r.adjustments(new ArrayList<>())
                    : new Settings();

            int[] processedPixels = ColorEngine.process(pixels, w, h, settings, lut);
            Bitmap baked = Bitmap.createBitmap(processedPixels, w, h, Bitmap.Config.ARGB_8888);

            // Chèn khung đóng dấu Leica hoặc 35mm Film Watermark
            Bitmap finalPhoto = drawWatermarkPipeline(baked, r);
            if (finalPhoto != baked) {
                baked.recycle();
            }

            Uri savedUri = saveToGallery(finalPhoto);
            lastCapturedUri = savedUri;

            final Bitmap thumb = Bitmap.createScaledBitmap(finalPhoto, dp(46), dp(46), true);
            final String filmName = (r != null)
                    ? r.name.replace("[CameraLook] ", "").replace("[Leica M] ", "")
                    : "TIÊU CHUẨN";

            runOnUiThread(() -> {
                if (recentThumbBtn != null) {
                    recentThumbBtn.setImageBitmap(thumb);
                    recentThumbBtn.setScaleX(1.2f);
                    recentThumbBtn.setScaleY(1.2f);
                    recentThumbBtn.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
                }
                showToastBanner(filmName, thumb);
            });

        } catch (Exception | OutOfMemoryError e) {
            e.printStackTrace();
            runOnUiThread(() -> Toast.makeText(this, "Lỗi nướng màu ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    // =========================================================================
    // HỆ THỐNG ĐÓNG DẤU KHUNG ẢNH NÂNG CAO (WATERMARK PIPELINE REA)
    // =========================================================================

    private Bitmap drawWatermarkPipeline(Bitmap photo, Recipe recipe) {
        // Mode 3: Không khung
        if (watermarkMode == 3) {
            return photo;
        }

        int w = photo.getWidth();
        int h = photo.getHeight();

        // Mode 2: Khung phim 35mm có lỗ răng cưa (Vintage 35mm Sprocket Film Frame)
        if (watermarkMode == 2) {
            return drawSprocketFilmFrame(photo, recipe, w, h);
        }

        // Mode 0 & 1: Khung Leica Gallery Trắng hoặc Đen Obsidian
        boolean isWhiteMode = (watermarkMode == 0);
        int footH = Math.max(dp(70), Math.round(w * 0.088f));
        Bitmap framed = Bitmap.createBitmap(w, h + footH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(framed);

        int bgColor = isWhiteMode ? 0xffffffff : 0xff141414;
        int fgPrimary = isWhiteMode ? 0xff12141a : 0xfff0f2f8;
        int fgMuted = isWhiteMode ? 0xff525866 : 0xffa1a7b5;
        int fgExif = isWhiteMode ? 0xff6e7485 : 0xff888e9d;
        int dividerColor = isWhiteMode ? 0x22000000 : 0x33ffffff;

        c.drawColor(bgColor);
        c.drawBitmap(photo, 0, 0, null);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(bgColor);
        c.drawRect(0, h, w, h + footH, p);

        // Vạch phân cách mảnh trên đỉnh footer
        p.setColor(isWhiteMode ? 0x18000000 : 0x22ffffff);
        p.setStrokeWidth(Math.max(1f, w * 0.0006f));
        c.drawLine(w * 0.04f, h, w * 0.96f, h, p);

        // 1. Logo Leica Red Dot chính hãng
        float textStartX;
        if (leicaLogoBitmap != null) {
            float logoH = footH * 0.42f;
            float logoW = logoH * (leicaLogoBitmap.getWidth() / (float) leicaLogoBitmap.getHeight());
            float logoX = w * 0.05f;
            float logoY = h + (footH - logoH) / 2f;
            RectF dstRect = new RectF(logoX, logoY, logoX + logoW, logoY + logoH);
            c.drawBitmap(leicaLogoBitmap, null, dstRect, new Paint(Paint.FILTER_BITMAP_FLAG));
            textStartX = logoX + logoW + footH * 0.16f;
        } else {
            float dotRadius = footH * 0.17f;
            float dotX = w * 0.055f;
            float dotY = h + footH * 0.48f;
            p.setColor(0xffd41428);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(dotX, dotY, dotRadius, p);

            Paint whiteP = new Paint(Paint.ANTI_ALIAS_FLAG);
            whiteP.setColor(Color.WHITE);
            whiteP.setTextSize(dotRadius * 0.9f);
            whiteP.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
            whiteP.setTextAlign(Paint.Align.CENTER);
            c.drawText("Leica", dotX, dotY + dotRadius * 0.32f, whiteP);
            textStartX = dotX + dotRadius * 2.1f;
        }

        // 2. Tên máy & màu phim
        p.setColor(fgPrimary);
        p.setTextSize(footH * 0.25f);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        p.setTextAlign(Paint.Align.LEFT);

        String filmName = (recipe != null)
                ? recipe.film.toUpperCase(Locale.ROOT)
                : "CLASSIC CCD";
        String cameraHeader = "LEICA M9  ·  " + filmName;
        c.drawText(cameraHeader, textStartX, h + footH * 0.44f, p);

        // 3. Tên ống kính Summilux
        p.setColor(fgMuted);
        p.setTextSize(footH * 0.18f);
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        String lensName = FOCAL_LENSES[currentFocalIndex];
        c.drawText(lensName, textStartX, h + footH * 0.72f, p);

        // 4. Vạch phân cách đứng mảnh
        float dividerX = w * 0.52f;
        p.setColor(dividerColor);
        c.drawLine(dividerX, h + footH * 0.24f, dividerX, h + footH * 0.76f, p);

        // 5. Thông số phơi sáng EXIF & Ngày giờ
        p.setColor(fgPrimary);
        p.setTextSize(footH * 0.22f);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        p.setTextAlign(Paint.Align.RIGHT);

        String exifStr = String.format(Locale.ROOT, "%s  f/%.1f  %s  ISO %d",
                FOCAL_NAMES[currentFocalIndex], liveAperture, liveShutter, liveIso);
        c.drawText(exifStr, w * 0.95f, h + footH * 0.44f, p);

        p.setColor(fgExif);
        p.setTextSize(footH * 0.18f);
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd  HH:mm", Locale.ROOT);
        c.drawText(sdf.format(new Date()), w * 0.95f, h + footH * 0.72f, p);

        return framed;
    }

    private Bitmap drawSprocketFilmFrame(Bitmap photo, Recipe recipe, int w, int h) {
        int sprocketH = Math.max(dp(54), Math.round(w * 0.082f));
        Bitmap framed = Bitmap.createBitmap(w, h + sprocketH * 2, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(framed);

        c.drawColor(0xff0a0b0e);
        c.drawBitmap(photo, 0, sprocketH, null);

        Paint filterP = new Paint(Paint.FILTER_BITMAP_FLAG);
        if (sprocketTopBitmap != null) {
            c.drawBitmap(sprocketTopBitmap, null, new Rect(0, 0, w, sprocketH), filterP);
        }
        if (sprocketBottomBitmap != null) {
            c.drawBitmap(sprocketBottomBitmap, null, new Rect(0, h + sprocketH, w, h + sprocketH * 2), filterP);
        }

        // Chữ dập màu cam hổ phách phong cách viền phim nhựa Kodak Safety Film
        Paint edgeP = new Paint(Paint.ANTI_ALIAS_FLAG);
        edgeP.setColor(0xffe07a3b);
        edgeP.setTextSize(sprocketH * 0.32f);
        edgeP.setTypeface(Typeface.create("monospace", Typeface.BOLD));

        edgeP.setTextAlign(Paint.Align.LEFT);
        c.drawText("▶ 24A", w * 0.06f, sprocketH * 0.70f, edgeP);

        edgeP.setTextAlign(Paint.Align.CENTER);
        String filmTitle = (recipe != null) ? recipe.name.replace("[CameraLook] ", "").replace("[Leica M] ", "") : "KODAK SAFETY FILM";
        c.drawText(filmTitle.toUpperCase(Locale.ROOT) + " · 35MM", w * 0.50f, sprocketH * 0.70f, edgeP);

        edgeP.setTextAlign(Paint.Align.RIGHT);
        c.drawText("DX · ISO " + liveIso, w * 0.94f, sprocketH * 0.70f, edgeP);

        // Chữ dải dưới
        edgeP.setTextAlign(Paint.Align.LEFT);
        c.drawText("LEICA M OPTICAL SYSTEM", w * 0.06f, h + sprocketH + sprocketH * 0.70f, edgeP);

        edgeP.setTextAlign(Paint.Align.RIGHT);
        c.drawText(FOCAL_NAMES[currentFocalIndex] + "  f/" + liveAperture + "  " + liveShutter,
                w * 0.94f, h + sprocketH + sprocketH * 0.70f, edgeP);

        return framed;
    }

    private Uri saveToGallery(Bitmap bitmap) throws Exception {
        String filename = "IRO_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date()) + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/IRO Studio");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new Exception("Không thể tạo bản ghi MediaStore");

        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 98, out);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
        }

        return uri;
    }

    // =========================================================================
    // CHẠM LẤY NÉT & ĐIỀU CHỈNH BÙ SÁNG EV TRỰC QUAN
    // =========================================================================

    private final View.OnTouchListener viewfinderTouchListener = new View.OnTouchListener() {
        private float startY = 0;

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getY();
                    triggerTapToFocus(event.getX(), event.getY());
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dy = startY - event.getY();
                    if (Math.abs(dy) > dp(12)) {
                        adjustEv(dy > 0 ? 1 : -1);
                        startY = event.getY();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    return true;
            }
            return false;
        }
    };

    private void triggerTapToFocus(float x, float y) {
        if (cameraDevice == null || captureSession == null) return;
        try {
            playFocusSound();

            float displayEv = currentEv * evStep;
            focusReticle.show(x, y, displayEv);

            Rect viewRect = new Rect(0, 0, textureView.getWidth(), textureView.getHeight());
            int areaSize = dp(60);
            Rect focusRect = new Rect(
                    (int) Math.max(0, x - areaSize),
                    (int) Math.max(0, y - areaSize),
                    (int) Math.min(viewRect.width(), x + areaSize),
                    (int) Math.min(viewRect.height(), y + areaSize));

            MeteringRectangle metering = new MeteringRectangle(focusRect, MeteringRectangle.METERING_WEIGHT_MAX);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{metering});
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{metering});
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);

            captureSession.setRepeatingRequest(previewRequestBuilder.build(), previewCaptureCallback, backgroundHandler);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void adjustEv(int delta) {
        if (evRange == null) return;
        int next = Math.max(evRange.getLower(), Math.min(evRange.getUpper(), currentEv + delta));
        if (next != currentEv) {
            currentEv = next;
            float displayEv = currentEv * evStep;
            focusReticle.updateEv(displayEv);
            updatePreviewSettings();
            updateTelemetryDisplay();
        }
    }

    // =========================================================================
    // VÒNG ĐỜI CAMERA2 & SENSORS
    // =========================================================================

    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
            return;
        }

        try {
            int targetFacing = isFrontFacing
                    ? CameraCharacteristics.LENS_FACING_FRONT
                    : CameraCharacteristics.LENS_FACING_BACK;

            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == targetFacing) {
                    cameraId = id;
                    sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) != null
                            ? chars.get(CameraCharacteristics.SENSOR_ORIENTATION) : 90;

                    sensorArraySize = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                    evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
                    android.util.Rational step = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
                    if (step != null) evStep = step.floatValue();

                    StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map != null) {
                        previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), 1920, 1080);
                        captureSize = Collections.max(
                                Arrays.asList(map.getOutputSizes(android.graphics.ImageFormat.JPEG)),
                                new CompareSizesByArea());

                        imageReader = ImageReader.newInstance(
                                captureSize.getWidth(), captureSize.getHeight(),
                                android.graphics.ImageFormat.JPEG, 2);
                        imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
                    }
                    break;
                }
            }

            if (cameraId != null) {
                cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler);
            }
        } catch (SecurityException | CameraAccessException e) {
            e.printStackTrace();
            Toast.makeText(this, "Không thể truy cập máy ảnh quang học", Toast.LENGTH_SHORT).show();
        }
    }

    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    private void startPreview() {
        if (cameraDevice == null || !textureView.isAvailable() || previewSize == null) return;
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(
                    Arrays.asList(surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (cameraDevice == null) return;
                            captureSession = session;
                            applyFocalCrop();
                            updatePreviewSettings();
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Toast.makeText(CameraActivity.this, "Cấu hình khung ngắm thất bại", Toast.LENGTH_SHORT).show();
                        }
                    }, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreviewSettings() {
        if (captureSession == null || previewRequestBuilder == null) return;
        try {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentEv);
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), previewCaptureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraCaptureSession.CaptureCallback previewCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
            Long exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            Float ap = result.get(CaptureResult.LENS_APERTURE);

            if (iso != null) liveIso = iso;
            if (ap != null) liveAperture = ap;
            if (exp != null) {
                double s = exp / 1_000_000_000.0;
                if (s >= 1.0) {
                    liveShutter = String.format(Locale.ROOT, "%.1fs", s);
                } else if (s > 0) {
                    liveShutter = "1/" + Math.round(1.0 / s) + "s";
                }
            }
            runOnUiThread(CameraActivity.this::updateTelemetryDisplay);
        }
    };

    private void switchCamera() {
        isFrontFacing = !isFrontFacing;
        closeCamera();
        openCamera();
    }

    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                float ax = event.values[0];
                float ay = event.values[1];
                float roll = (float) Math.toDegrees(Math.atan2(-ax, ay));
                if (rangefinderOverlay != null) {
                    rangefinderOverlay.setHorizonRoll(roll);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(sensorListener);
        }
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Cần cấp quyền máy ảnh để sử dụng phân hệ Leica Camera", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    };

    private static int getJpegOrientation(int sensorOrientation, int deviceOrientation, boolean isFrontFacing) {
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return 0;
        int degrees = 0;
        switch (deviceOrientation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }
        if (isFrontFacing) {
            return (sensorOrientation + degrees) % 360;
        } else {
            return (sensorOrientation - degrees + 360) % 360;
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<>();
        for (Size option : choices) {
            if (option.getWidth() <= width && option.getHeight() <= height) {
                bigEnough.add(option);
            }
        }
        if (!bigEnough.isEmpty()) {
            return Collections.max(bigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }

    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    // =========================================================================
    // LỚP OVERLAY QUANG HỌC LEICA M (BRIGHTLINES & GYRO LEVEL)
    // =========================================================================

    private static class RangefinderOverlayView extends View {
        private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint horizonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint patchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private boolean gridEnabled = true;
        private float horizonRoll = 0f;
        private boolean wasLevel = false;

        public RangefinderOverlayView(Context context) {
            super(context);
            framePaint.setColor(0x73ffffff);
            framePaint.setStyle(Paint.Style.STROKE);
            framePaint.setStrokeWidth(2.5f);
            framePaint.setStrokeCap(Paint.Cap.ROUND);

            gridPaint.setColor(0x24ffffff);
            gridPaint.setStrokeWidth(1.2f);

            horizonPaint.setColor(0x80ffffff);
            horizonPaint.setStyle(Paint.Style.STROKE);
            horizonPaint.setStrokeWidth(2f);
            horizonPaint.setStrokeCap(Paint.Cap.ROUND);

            patchPaint.setColor(0x59ffffff);
            patchPaint.setStyle(Paint.Style.STROKE);
            patchPaint.setStrokeWidth(1.5f);
        }

        public void setGridEnabled(boolean enabled) {
            this.gridEnabled = enabled;
            invalidate();
        }

        public void setHorizonRoll(float roll) {
            this.horizonRoll = Math.max(-45f, Math.min(45f, roll));
            boolean isLevel = Math.abs(this.horizonRoll) < 1.0f;
            if (isLevel && !wasLevel) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            wasLevel = isLevel;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();

            if (gridEnabled) {
                canvas.drawLine(w / 3f, 0, w / 3f, h, gridPaint);
                canvas.drawLine(2f * w / 3f, 0, 2f * w / 3f, h, gridPaint);
                canvas.drawLine(0, h / 3f, w, h / 3f, gridPaint);
                canvas.drawLine(0, 2f * h / 3f, w, 2f * h / 3f, gridPaint);
            }

            float padX = w * 0.06f;
            float padY = h * 0.08f;
            float tickLen = w * 0.08f;

            canvas.drawLine(padX, padY, padX + tickLen, padY, framePaint);
            canvas.drawLine(padX, padY, padX, padY + tickLen, framePaint);

            canvas.drawLine(w - padX, padY, w - padX - tickLen, padY, framePaint);
            canvas.drawLine(w - padX, padY, w - padX, padY + tickLen, framePaint);

            canvas.drawLine(padX, h - padY, padX + tickLen, h - padY, framePaint);
            canvas.drawLine(padX, h - padY, padX, h - padY - tickLen, framePaint);

            canvas.drawLine(w - padX, h - padY, w - padX - tickLen, h - padY, framePaint);
            canvas.drawLine(w - padX, h - padY, w - padX, h - padY - tickLen, framePaint);

            float cx = w / 2f;
            float cy = h / 2f;
            float patchW = w * 0.16f;
            float patchH = w * 0.10f;
            canvas.drawRect(cx - patchW / 2f, cy - patchH / 2f, cx + patchW / 2f, cy + patchH / 2f, patchPaint);
            canvas.drawLine(cx - 10f, cy, cx + 10f, cy, patchPaint);
            canvas.drawLine(cx, cy - 10f, cx, cy + 10f, patchPaint);

            boolean isLevel = Math.abs(horizonRoll) < 1.0f;
            horizonPaint.setColor(isLevel ? 0xfff59e0b : 0x73ffffff);
            horizonPaint.setStrokeWidth(isLevel ? 3f : 1.8f);

            canvas.save();
            canvas.rotate(-horizonRoll, cx, cy);
            float lineLen = w * 0.22f;
            canvas.drawLine(cx - lineLen, cy, cx - 18f, cy, horizonPaint);
            canvas.drawLine(cx + 18f, cy, cx + lineLen, cy, horizonPaint);
            canvas.drawCircle(cx, cy, 3.5f, horizonPaint);
            canvas.restore();
        }
    }

    // =========================================================================
    // VÒNG CHẠM LẤY NÉT & THANH BÙ SÁNG SUNLIGHT EV
    // =========================================================================

    private static class FocusReticleView extends View {
        private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float fx = -1, fy = -1;
        private float currentEv = 0f;

        public FocusReticleView(Context context) {
            super(context);
            boxPaint.setColor(0xfff59e0b);
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(2f);

            sunPaint.setColor(0xfff59e0b);
            sunPaint.setStyle(Paint.Style.STROKE);
            sunPaint.setStrokeWidth(2f);

            textPaint.setColor(0xfff59e0b);
            textPaint.setTextSize(26f);
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        }

        public void show(float x, float y, float ev) {
            this.fx = x;
            this.fy = y;
            this.currentEv = ev;
            setAlpha(1f);
            setScaleX(1.25f);
            setScaleY(1.25f);
            invalidate();
            animate().scaleX(1f).scaleY(1f).setDuration(180).start();

            removeCallbacks(fadeOutRunnable);
            postDelayed(fadeOutRunnable, 2800);
        }

        public void updateEv(float ev) {
            this.currentEv = ev;
            setAlpha(1f);
            invalidate();
            removeCallbacks(fadeOutRunnable);
            postDelayed(fadeOutRunnable, 2500);
        }

        private final Runnable fadeOutRunnable = () -> animate().alpha(0f).setDuration(400).start();

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (fx >= 0 && fy >= 0) {
                float size = 42f;

                canvas.drawRect(fx - size, fy - size, fx + size, fy + size, boxPaint);
                canvas.drawCircle(fx, fy, 3.5f, boxPaint);

                canvas.drawLine(fx - size, fy, fx - size - 12f, fy, boxPaint);
                canvas.drawLine(fx + size, fy, fx + size + 12f, fy, boxPaint);
                canvas.drawLine(fx, fy - size, fx, fy - size - 12f, boxPaint);
                canvas.drawLine(fx, fy + size, fx, fy + size + 12f, boxPaint);

                float sliderX = fx + size + 28f;
                canvas.drawLine(sliderX, fy - 50f, sliderX, fy + 50f, sunPaint);

                float sunY = fy - Math.max(-50f, Math.min(50f, currentEv * 15f));
                canvas.drawCircle(sliderX, sunY, 6.5f, sunPaint);
                for (int i = 0; i < 8; i++) {
                    double rad = Math.toRadians(i * 45);
                    canvas.drawLine(
                            sliderX + (float) Math.cos(rad) * 9f,
                            sunY + (float) Math.sin(rad) * 9f,
                            sliderX + (float) Math.cos(rad) * 14f,
                            sunY + (float) Math.sin(rad) * 14f,
                            sunPaint);
                }

                String evText = (currentEv >= 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f", currentEv);
                canvas.drawText(evText, sliderX + 22f, sunY + 9f, textPaint);
            }
        }
    }
}
