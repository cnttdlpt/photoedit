package vn.iro.editor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
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

import java.io.File;
import java.io.FileOutputStream;
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
 * Máy ảnh quang học Leica & Film Simulation trực tiếp (Live Viewfinder Camera).
 *
 * <p>Tính năng vượt trội chuyển đổi từ Xiaomi / Leica Pro Camera (APK 9.0.2):
 * <ul>
 *     <li>Khung ngắm thời gian thực Camera2 hỗ trợ chạm lấy nét (Tap-to-Focus) và đo sáng vùng chọn.</li>
 *     <li>Thước ngắm bù trừ sáng quang học EV (-3.0 EV .. +3.0 EV).</li>
 *     <li>Hệ thống 28 dòng màu phim kinh điển & Leica (Leica M9 CCD, M3 Silver Gelatin, M10-R, Kodachrome, Portra, CineStill...).</li>
 *     <li>Tự động nướng màu độ phân giải đầy đủ (Full-Sensor Color Engine Baking) qua 3D LUT ngay khi bấm nút chụp.</li>
 *     <li>Tùy chọn đóng dấu khung ảnh Leica Watermark kinh điển (Leica Red Dot, thông số ống kính, khẩu độ, ISO, tốc độ).</li>
 *     <li>Nút chụp cơ học Leica với phản hồi rung xúc giác (Haptic Click) và hiệu ứng chớp sáng màn trập.</li>
 * </ul>
 */
public class CameraActivity extends Activity {

    private static final int PERMISSION_REQUEST_CAMERA = 101;
    public static final String EXTRA_RECIPE_ID = "recipe_id";
    public static final String EXTRA_IMAGE_URI = "image_uri";

    private TextureView textureView;
    private GridOverlayView gridOverlay;
    private FocusReticleView focusReticle;
    private View shutterFlashView;
    private TextView countdownView;
    private TextView activeLookBadge;
    private TextView evBadge;
    private ImageView recentThumbBtn;
    private View shutterInnerBtn;
    private LinearLayout ribbonContainer;

    private CameraManager cameraManager;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private Size previewSize;
    private Size captureSize;
    private ImageReader imageReader;
    private int sensorOrientation = 0;
    private boolean isFrontFacing = false;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private final ExecutorService processExecutor = Executors.newSingleThreadExecutor();

    private final List<Recipe> cameraRecipes = new ArrayList<>();
    private Recipe activeRecipe;
    private CubeLut activeLut;

    // Camera controls state
    private int flashMode = CaptureRequest.CONTROL_AE_MODE_ON;
    private int flashStateIndex = 0; // 0: Auto, 1: On, 2: Off
    private int timerSeconds = 0; // 0: Off, 3: 3s, 10: 10s
    private boolean isGridEnabled = false;
    private boolean isWatermarkEnabled = true;
    private int currentEv = 0;
    private Range<Integer> evRange = new Range<>(0, 0);
    private float evStep = 0.333f;
    private Uri lastCapturedUri = null;
    private boolean isCapturing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Chế độ toàn màn hình tối đa (Immersive Fullscreen)
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        loadCameraCatalog();

        buildUi();
    }

    private int dp(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void loadCameraCatalog() {
        try {
            // Nạp 28 công thức CameraLook & Leica M
            try (InputStream in = getAssets().open("cameralook-recipes.json")) {
                JSONObject obj = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
                JSONArray arr = obj.getJSONArray("recipes");
                for (int i = 0; i < arr.length(); i++) {
                    cameraRecipes.add(new Recipe(arr.getJSONObject(i)));
                }
            }

            // Nạp công thức mặc định (ưu tiên Leica M9 CCD hoặc recipe_id truyền vào)
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
                // Mặc định: Leica M9 Classic CCD (id: leica_m9_classic_ccd) hoặc tone 0
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
                    updateBadgeText();
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
    // XÂY DỰNG GIAO DIỆN KHUNG NGẮM MÁY ẢNH (VIEWFINDER UI)
    // =========================================================================

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        // 1. Khung ngắm TextureView
        textureView = new TextureView(this);
        textureView.setSurfaceTextureListener(textureListener);
        textureView.setOnTouchListener(viewfinderTouchListener);
        root.addView(textureView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        // 2. Lưới bố cục (Grid Overlay)
        gridOverlay = new GridOverlayView(this);
        gridOverlay.setVisibility(isGridEnabled ? View.VISIBLE : View.GONE);
        root.addView(gridOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 3. Điểm lấy nét chạm (Focus Reticle)
        focusReticle = new FocusReticleView(this);
        root.addView(focusReticle, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 4. Thanh điều khiển trên đỉnh (Top Toolbar)
        LinearLayout topBar = buildTopBar();
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        topLp.topMargin = dp(16);
        topLp.leftMargin = dp(16);
        topLp.rightMargin = dp(16);
        root.addView(topBar, topLp);

        // 5. Huy hiệu hiển thị màu phim đang kích hoạt (Active Optical Look Badge)
        activeLookBadge = new TextView(this);
        activeLookBadge.setTextColor(Color.WHITE);
        activeLookBadge.setTextSize(11.5f);
        activeLookBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        activeLookBadge.setPadding(dp(12), dp(6), dp(12), dp(6));
        activeLookBadge.setBackground(createPillDrawable(0x80000000, 0x33ffffff, dp(14)));
        updateBadgeText();

        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        badgeLp.topMargin = dp(76);
        root.addView(activeLookBadge, badgeLp);

        // 6. Thước bù sáng EV (Bên phải khung ngắm)
        evBadge = new TextView(this);
        evBadge.setTextColor(0xfff59e0b);
        evBadge.setTextSize(12f);
        evBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        evBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        evBadge.setBackground(createPillDrawable(0x70000000, 0x33ffffff, dp(12)));
        evBadge.setText("0.0 EV");
        evBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams evLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.END);
        evLp.rightMargin = dp(16);
        root.addView(evBadge, evLp);

        // 7. Bệ máy ảnh dưới đáy (Bottom Camera Deck)
        LinearLayout bottomDeck = buildBottomDeck();
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        bottomLp.bottomMargin = dp(20);
        root.addView(bottomDeck, bottomLp);

        // 8. Đồng hồ đếm ngược hẹn giờ (Countdown)
        countdownView = new TextView(this);
        countdownView.setTextColor(Color.WHITE);
        countdownView.setTextSize(88f);
        countdownView.setTypeface(Typeface.create("sans-serif-thin", Typeface.BOLD));
        countdownView.setGravity(Gravity.CENTER);
        countdownView.setVisibility(View.GONE);
        root.addView(countdownView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        // 9. Chớp màn trập khi chụp (Shutter Flash Blink)
        shutterFlashView = new View(this);
        shutterFlashView.setBackgroundColor(Color.WHITE);
        shutterFlashView.setAlpha(0f);
        root.addView(shutterFlashView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void updateBadgeText() {
        if (activeLookBadge == null) return;
        if (activeRecipe != null) {
            String name = activeRecipe.name.replace("[CameraLook] ", "").replace("[Leica M] ", "");
            activeLookBadge.setText("🔴 " + name.toUpperCase(Locale.ROOT));
        } else {
            activeLookBadge.setText("🔴 CHẾ ĐỘ TIÊU CHUẨN");
        }
    }

    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        bar.setBackground(createPillDrawable(0x70000000, 0x26ffffff, dp(22)));

        // Nút Back
        ImageView backBtn = createIconBtn("back", v -> finish());
        bar.addView(backBtn);

        // Spacer
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        // Nút Flash
        final TextView flashBtn = createTextBtn("⚡ Tự động", v -> {
            flashStateIndex = (flashStateIndex + 1) % 3;
            if (flashStateIndex == 0) {
                flashMode = CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH;
                ((TextView) v).setText("⚡ Tự động");
            } else if (flashStateIndex == 1) {
                flashMode = CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH;
                ((TextView) v).setText("⚡ Luôn bật");
            } else {
                flashMode = CaptureRequest.CONTROL_AE_MODE_ON;
                ((TextView) v).setText("⚡ Tắt");
            }
            updatePreviewSettings();
        });
        bar.addView(flashBtn);

        // Nút Grid
        final TextView gridBtn = createTextBtn("Lưới", v -> {
            isGridEnabled = !isGridEnabled;
            gridOverlay.setVisibility(isGridEnabled ? View.VISIBLE : View.GONE);
            ((TextView) v).setTextColor(isGridEnabled ? 0xfff59e0b : Color.WHITE);
        });
        bar.addView(gridBtn);

        // Nút Hẹn giờ (Timer)
        final TextView timerBtn = createTextBtn("⏱️ Tắt", v -> {
            if (timerSeconds == 0) {
                timerSeconds = 3;
                ((TextView) v).setText("⏱️ 3s");
                ((TextView) v).setTextColor(0xfff59e0b);
            } else if (timerSeconds == 3) {
                timerSeconds = 10;
                ((TextView) v).setText("⏱️ 10s");
                ((TextView) v).setTextColor(0xfff59e0b);
            } else {
                timerSeconds = 0;
                ((TextView) v).setText("⏱️ Tắt");
                ((TextView) v).setTextColor(Color.WHITE);
            }
        });
        bar.addView(timerBtn);

        // Nút Đảo Camera Trước/Sau
        ImageView flipBtn = createIconBtn("rotate_right", v -> switchCamera());
        bar.addView(flipBtn);

        return bar;
    }

    private LinearLayout buildBottomDeck() {
        LinearLayout deck = new LinearLayout(this);
        deck.setOrientation(LinearLayout.VERTICAL);
        deck.setGravity(Gravity.CENTER_HORIZONTAL);

        // 1. Dải chọn màu phim ngang (Film Ribbon)
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(16), 0, dp(16), dp(10));

        ribbonContainer = new LinearLayout(this);
        ribbonContainer.setOrientation(LinearLayout.HORIZONTAL);
        ribbonContainer.setGravity(Gravity.CENTER_VERTICAL);
        populateRibbon();
        scroll.addView(ribbonContainer);
        deck.addView(scroll);

        // 2. Hàng nút Chụp chính (Shutter row)
        LinearLayout shutterRow = new LinearLayout(this);
        shutterRow.setOrientation(LinearLayout.HORIZONTAL);
        shutterRow.setGravity(Gravity.CENTER);
        shutterRow.setPadding(dp(24), dp(8), dp(24), dp(8));

        // Nút xem ảnh gần nhất (Recent Thumb)
        recentThumbBtn = new ImageView(this);
        recentThumbBtn.setScaleType(ImageView.ScaleType.CENTER_CROP);
        recentThumbBtn.setBackground(createCircleDrawable(0x40ffffff, 0xffffffff, dp(1.5f)));
        recentThumbBtn.setPadding(dp(2), dp(2), dp(2), dp(2));
        recentThumbBtn.setOnClickListener(v -> {
            if (lastCapturedUri != null) {
                Intent res = new Intent();
                res.setData(lastCapturedUri);
                setResult(RESULT_OK, res);
                finish();
            } else {
                Toast.makeText(this, "Chưa có ảnh chụp mới", Toast.LENGTH_SHORT).show();
            }
        });
        shutterRow.addView(recentThumbBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));

        // Spacer
        shutterRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        // Nút Shutter cơ học Leica
        FrameLayout shutterFrame = new FrameLayout(this);
        GradientDrawable outerRing = new GradientDrawable();
        outerRing.setShape(GradientDrawable.OVAL);
        outerRing.setColor(Color.TRANSPARENT);
        outerRing.setStroke(dp(3.5f), Color.WHITE);
        shutterFrame.setBackground(outerRing);

        shutterInnerBtn = new View(this);
        GradientDrawable innerCircle = new GradientDrawable();
        innerCircle.setShape(GradientDrawable.OVAL);
        innerCircle.setColor(Color.WHITE);
        shutterInnerBtn.setBackground(innerCircle);

        FrameLayout.LayoutParams innerLp = new FrameLayout.LayoutParams(dp(62), dp(62), Gravity.CENTER);
        shutterFrame.addView(shutterInnerBtn, innerLp);

        shutterFrame.setOnClickListener(v -> handleShutterClick());
        shutterRow.addView(shutterFrame, new LinearLayout.LayoutParams(dp(76), dp(76)));

        // Spacer
        shutterRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        // Nút Bật/Tắt Watermark Leica
        final TextView watermarkBtn = createTextBtn(isWatermarkEnabled ? "🔴 Khung Leica" : "⚪ Không khung", v -> {
            isWatermarkEnabled = !isWatermarkEnabled;
            ((TextView) v).setText(isWatermarkEnabled ? "🔴 Khung Leica" : "⚪ Không khung");
            ((TextView) v).setTextColor(isWatermarkEnabled ? 0xfff59e0b : Color.WHITE);
        });
        watermarkBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        watermarkBtn.setBackground(createPillDrawable(0x60000000, 0x26ffffff, dp(16)));
        shutterRow.addView(watermarkBtn);

        deck.addView(shutterRow);
        return deck;
    }

    private void populateRibbon() {
        ribbonContainer.removeAllViews();

        for (final Recipe r : cameraRecipes) {
            final boolean selected = (activeRecipe != null && activeRecipe.id.equals(r.id));
            final TextView chip = new TextView(this);
            String title = r.name.replace("[CameraLook] ", "").replace("[Leica M] ", "");
            chip.setText(title);
            chip.setTextSize(11f);
            chip.setTypeface(Typeface.create("sans-serif-medium", selected ? Typeface.BOLD : Typeface.NORMAL));
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            chip.setTextColor(selected ? Color.BLACK : Color.WHITE);
            chip.setBackground(createPillDrawable(
                    selected ? 0xfff59e0b : 0x60000000,
                    selected ? 0 : 0x26ffffff,
                    dp(16)));

            chip.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                activeRecipe = r;
                loadActiveLut();
                populateRibbon();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            ribbonContainer.addView(chip, lp);
        }
    }

    private ImageView createIconBtn(String iconName, View.OnClickListener l) {
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new StudioStyle.Icon(iconName, Color.WHITE, dp(18)));
        iv.setPadding(dp(10), dp(10), dp(10), dp(10));
        iv.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            l.onClick(v);
        });
        return iv;
    }

    private TextView createTextBtn(String text, View.OnClickListener l) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(11.5f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
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

    private GradientDrawable createCircleDrawable(int bgColor, int strokeColor, float strokeWidth) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(bgColor);
        if (strokeColor != 0) {
            gd.setStroke(Math.round(strokeWidth), strokeColor);
        }
        return gd;
    }

    // =========================================================================
    // XỬ LÝ CHỤP ẢNH & ĐẾM NGƯỢC (SHUTTER PIPELINE)
    // =========================================================================

    private void handleShutterClick() {
        if (isCapturing) return;

        shutterInnerBtn.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        shutterInnerBtn.animate().scaleX(0.85f).scaleY(0.85f).setDuration(70).withEndAction(() -> {
            shutterInnerBtn.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        }).start();

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
        countdownView.setAlpha(1f);
        countdownView.setScaleX(1.4f);
        countdownView.setScaleY(1.4f);
        countdownView.animate().scaleX(1f).scaleY(1f).alpha(0.6f).setDuration(900).withEndAction(() -> {
            runTimerTick(count - 1);
        }).start();
    }

    private void captureStillPicture() {
        if (cameraDevice == null || captureSession == null || imageReader == null) {
            isCapturing = false;
            return;
        }

        try {
            isCapturing = true;
            // Hiệu ứng chớp màn trập
            shutterFlashView.setAlpha(0.7f);
            shutterFlashView.animate().alpha(0f).setDuration(160).start();

            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());

            // Tự động nét và phơi sáng
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode);
            captureBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, currentEv);

            // Căn góc xoay EXIF theo cảm biến và hướng cầm máy
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int jpegOrientation = getJpegOrientation(sensorOrientation, rotation, isFrontFacing);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation);

            captureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    // Sau khi chụp xong, tiếp tục duy trì luồng preview
                    updatePreviewSettings();
                }
            }, backgroundHandler);

        } catch (Exception e) {
            isCapturing = false;
            e.printStackTrace();
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

                // Chuyển sang luồng xử lý nướng màu ảnh gốc (ColorEngine Baking Pipeline)
                processExecutor.execute(() -> bakeAndSavePhoto(bytes));

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (image != null) {
                    image.close();
                }
                isCapturing = false;
            }
        }
    };

    /**
     * Nướng màu phim & 3D LUT trực tiếp lên ảnh độ phân giải đầy đủ của cảm biến máy ảnh.
     */
    private void bakeAndSavePhoto(byte[] rawBytes) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap original = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.length, opts);
            if (original == null) return;

            int w = original.getWidth();
            int h = original.getHeight();

            // 1. Nướng màu qua ColorEngine với công thức Recipe và LUT đang chọn
            Recipe r = (activeRecipe != null) ? activeRecipe : cameraRecipes.get(0);
            Settings settings = r.adjustments(new ArrayList<>());
            CubeLut lut = activeLut;

            int[] pixels = new int[w * h];
            original.getPixels(pixels, 0, w, 0, 0, w, h);
            original.recycle();

            int[] processedPixels = ColorEngine.process(pixels, w, h, settings, lut);
            Bitmap baked = Bitmap.createBitmap(processedPixels, w, h, Bitmap.Config.ARGB_8888);

            // 2. Chèn khung đóng dấu Leica Watermark (nếu bật)
            Bitmap finalPhoto = isWatermarkEnabled ? drawLeicaWatermark(baked, r) : baked;
            if (finalPhoto != baked) {
                baked.recycle();
            }

            // 3. Lưu ảnh vào Thư viện hệ thống (DCIM/IRO Studio)
            Uri savedUri = saveToGallery(finalPhoto);
            lastCapturedUri = savedUri;

            // 4. Tạo thumbnail nhỏ góc trái và thông báo
            final Bitmap thumb = Bitmap.createScaledBitmap(finalPhoto, dp(44), dp(44), true);

            runOnUiThread(() -> {
                if (recentThumbBtn != null) {
                    recentThumbBtn.setImageBitmap(thumb);
                }
                showCaptureSuccessDialog(r, savedUri);
            });

        } catch (Exception | OutOfMemoryError e) {
            e.printStackTrace();
            runOnUiThread(() -> Toast.makeText(this, "Lỗi xử lý ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private Bitmap drawLeicaWatermark(Bitmap photo, Recipe recipe) {
        int w = photo.getWidth();
        int h = photo.getHeight();

        int footH = Math.max(dp(60), Math.round(w * 0.09f));
        Bitmap framed = Bitmap.createBitmap(w, h + footH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(framed);

        // Vẽ ảnh chính
        c.drawColor(0xffffffff);
        c.drawBitmap(photo, 0, 0, null);

        // Dải trắng dưới đáy kiểu Leica
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xffffffff);
        c.drawRect(0, h, w, h + footH, p);

        // Vạch phân cách mỏng
        p.setColor(0x14000000);
        c.drawLine(w * 0.05f, h, w * 0.95f, h, p);

        // Chấm đỏ Leica
        float dotRadius = footH * 0.16f;
        float dotX = w * 0.055f;
        float dotY = h + footH * 0.48f;
        p.setColor(0xffd41428); // Leica Red
        c.drawCircle(dotX, dotY, dotRadius, p);

        // Chữ "LEICA" trong chấm đỏ hoặc cạnh chấm đỏ
        p.setColor(0xff16181c);
        p.setTextSize(footH * 0.28f);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        String cameraName = "LEICA M9 · " + recipe.film.toUpperCase(Locale.ROOT);
        c.drawText(cameraName, dotX + dotRadius * 2.2f, dotY + footH * 0.09f, p);

        // Thông số khẩu độ, tốc độ, ISO bên phải
        p.setColor(0xff737987);
        p.setTextSize(footH * 0.22f);
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        p.setTextAlign(Paint.Align.RIGHT);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd  HH:mm", Locale.ROOT);
        String dateStr = sdf.format(new Date());
        String exifStr = "35mm  f/2.0  1/250s  ISO 200  |  " + dateStr;
        c.drawText(exifStr, w * 0.95f, dotY + footH * 0.08f, p);

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
            bitmap.compress(Bitmap.CompressFormat.JPEG, 97, out);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
        }

        return uri;
    }

    private void showCaptureSuccessDialog(Recipe r, Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("📸 Đã chụp & nướng màu phim")
                .setMessage("Tác phẩm đã nướng thành công phong cách: " + r.name + " và lưu vào Thư viện ảnh.")
                .setPositiveButton("Chỉnh sửa trong IRO Studio", (dialog, which) -> {
                    Intent res = new Intent();
                    res.setData(uri);
                    setResult(RESULT_OK, res);
                    finish();
                })
                .setNegativeButton("Chụp tiếp", null)
                .show();
    }

    // =========================================================================
    // CAMERA2 INITIALIZATION & LIFECYCLE
    // =========================================================================

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

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
                    if (Math.abs(dy) > dp(10)) {
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
            focusReticle.show(x, y);

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

            captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);

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
            evBadge.setVisibility(View.VISIBLE);
            evBadge.setText(String.format(Locale.ROOT, "%+.1f EV", displayEv));
            updatePreviewSettings();
            evBadge.removeCallbacks(hideEvBadgeRunnable);
            evBadge.postDelayed(hideEvBadgeRunnable, 2000);
        }
    }

    private final Runnable hideEvBadgeRunnable = () -> {
        if (evBadge != null) evBadge.setVisibility(View.GONE);
    };

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
            Toast.makeText(this, "Không thể truy cập máy ảnh", Toast.LENGTH_SHORT).show();
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
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

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

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Cần cấp quyền máy ảnh để chụp ảnh", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

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
    // CUSTOM VIEWS CHO KHUNG NGẮM (OVERLAYS)
    // =========================================================================

    private static class GridOverlayView extends View {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public GridOverlayView(Context context) {
            super(context);
            linePaint.setColor(0x38ffffff);
            linePaint.setStrokeWidth(1.5f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();

            // 2 đường dọc (1/3 và 2/3)
            canvas.drawLine(w / 3f, 0, w / 3f, h, linePaint);
            canvas.drawLine(2f * w / 3f, 0, 2f * w / 3f, h, linePaint);

            // 2 đường ngang (1/3 và 2/3)
            canvas.drawLine(0, h / 3f, w, h / 3f, linePaint);
            canvas.drawLine(0, 2f * h / 3f, w, 2f * h / 3f, linePaint);
        }
    }

    private static class FocusReticleView extends View {
        private final Paint reticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float fx = -1, fy = -1;

        public FocusReticleView(Context context) {
            super(context);
            reticlePaint.setColor(0xfff59e0b); // Amber reticle
            reticlePaint.setStyle(Paint.Style.STROKE);
            reticlePaint.setStrokeWidth(2f);
        }

        public void show(float x, float y) {
            this.fx = x;
            this.fy = y;
            setAlpha(1f);
            setScaleX(1.3f);
            setScaleY(1.3f);
            invalidate();
            animate().scaleX(1f).scaleY(1f).alpha(0f).setDuration(1200).start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (fx >= 0 && fy >= 0) {
                float size = 48f;
                canvas.drawRect(fx - size, fy - size, fx + size, fy + size, reticlePaint);
                canvas.drawCircle(fx, fy, 4f, reticlePaint);
            }
        }
    }
}
