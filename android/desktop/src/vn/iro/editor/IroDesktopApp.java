package vn.iro.editor;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.json.JSONArray;
import org.json.JSONObject;

import static vn.iro.editor.DesktopWidgets.*;

/**
 * Ứng dụng <b>IRO Studio — Windows Desktop Edition</b> với giao diện
 * <b>Android Tablet Minimal Rounded Studio UX</b> (thẻ trắng bo tròn 28px nổi trên nền xám nhạt
 * {@code #F5F6F9}, thanh điều hướng viên thuốc bên trái, thước kẻ vạch {@link TickRulerWidget},
 * lưới thẻ thông số 2 cột và lưới ảnh mẫu Fuji X Weekly 3 cột).
 */
public final class IroDesktopApp {

    private static String cliScreenshotPath = null;
    private static String cliInitialTab = null;

    public static void main(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--screenshot".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                cliScreenshotPath = args[++i];
            } else if ("--tab".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                cliInitialTab = args[++i];
            }
        }
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> {
            IroDesktopApp app = new IroDesktopApp();
            app.show();
        });
    }

    private final JFrame frame;
    private final ImageCanvasPanel canvasPanel;
    private final HistogramPanel histogramPanel;
    private final AndroidNavRail navRail;

    private final PillButton topRecipeBadgeBtn;
    private final PillButton splitBtn;
    private final PillButton holdOriginalBtn;
    private final JLabel bottomStatusLabel;

    // Danh sách công thức đang ghim trên tab Màu phim & kho 392 công thức FujiXWeekly
    private final List<Recipe> activeRecipes = new ArrayList<>();
    private final List<Recipe> catalogRecipes = new ArrayList<>();
    private Recipe currentRecipe;
    private Settings currentSettings = new Settings();
    private final LightCraftEngine.State lightCraftState = new LightCraftEngine.State();

    // Lịch sử Undo / Redo (lưu JSON)
    private final List<String> undoStack = new ArrayList<>();
    private final List<String> redoStack = new ArrayList<>();
    private boolean suppressEvents = false;

    // Ảnh gốc & ảnh thu nhỏ để render thời gian thực
    private BufferedImage fullSourceImage;
    private BufferedImage previewSourceImage;
    private int[] previewSourcePixels;
    private int previewW;
    private int previewH;
    private BufferedImage thumbSourceImage;

    private CubeLut activeCubeLut;
    private String currentPhotoName = "FujiXWeekly_Sample.jpg";
    private int currentSampleIndex = 0;

    // Đa luồng render
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "IRO-Desktop-Render");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger renderGeneration = new AtomicInteger(0);
    private final Map<String, BufferedImage> recipeThumbCache = new ConcurrentHashMap<>();

    // Bảng điều khiển bên phải (CardLayout chuyển giữa Tinh chỉnh / Màu phim / Khung ảnh / LightCraft)
    private final CardLayout rightCardLayout = new CardLayout();
    private final JPanel rightCardContainer = new JPanel(rightCardLayout);

    // Thành phần tab Tinh chỉnh (giống hệt Android Tablet Minimal Adjust)
    private JLabel activeParamTitleLabel;
    private PillButton drCyclePillBtn;
    private PillButton activeParamValuePill;
    private TickRulerWidget mainTickRuler;
    private int selectedParamIndex = 0;
    private final List<ParamChipCard> paramChipCards = new ArrayList<>();

    // Thành phần tab Màu phim (giống hệt Android Tablet Fuji Filters 3 cột)
    private JPanel filmGridPanel;
    private boolean useFujiSampleOnCards = true;
    private PillButton modeSamplePill;
    private PillButton modePhotoPill;
    private final List<FilmRecipeCard> filmCards = new ArrayList<>();

    // Thành phần tab Khung ảnh
    private final List<FrameTemplateCard> frameCards = new ArrayList<>();

    // Thành phần tab LightCraft
    private ToneCurveWidget toneCurveWidget;
    private ColorWheelWidget shadowWheel;
    private ColorWheelWidget midtoneWheel;
    private ColorWheelWidget highlightWheel;
    private int activeHslChannel = 0;
    private TickRulerWidget hslHueRuler;
    private TickRulerWidget hslSatRuler;
    private TickRulerWidget hslLumRuler;
    private TickRulerWidget maskExpRuler;
    private TickRulerWidget maskTempRuler;
    private TickRulerWidget maskSatRuler;
    private TickRulerWidget maskFeatherRuler;

    // Định nghĩa 14 thông số Tinh chỉnh giống hệt Android Tablet
    private final ParamDef[] paramDefs = new ParamDef[]{
            new ParamDef("Độ phơi sáng", "EV", -3.0, 3.0, 0.0),
            new ParamDef("Vùng sáng", "Highlight", -2.0, 4.0, 0.0),
            new ParamDef("Vùng tối", "Shadow", -2.0, 4.0, 0.0),
            new ParamDef("Độ bão hòa", "Color", -4.0, 4.0, 0.0),
            new ParamDef("Độ nét", "Sharpness", -4.0, 4.0, 0.0),
            new ParamDef("Khử nhiễu", "NR", -4.0, 4.0, -4.0),
            new ParamDef("Độ rõ (Clarity)", "Clarity", -5.0, 5.0, 0.0),
            new ParamDef("Hạt phim (Grain)", "Grain", 0.0, 30.0, 0.0),
            new ParamDef("Nhiệt độ màu", "Temp", -100.0, 100.0, 0.0),
            new ParamDef("Dịch Đỏ (WB R)", "WB Red", -9.0, 9.0, 0.0),
            new ParamDef("Dịch Xanh (WB B)", "WB Blue", -9.0, 9.0, 0.0),
            new ParamDef("Giả lập Chrome", "CC Effect", 0.0, 2.0, 0.0),
            new ParamDef("Chrome Xanh", "CC Blue", 0.0, 2.0, 0.0),
            new ParamDef("Cường độ LUT", "Strength", 0.0, 100.0, 100.0)
    };

    public IroDesktopApp() {
        loadBundledCatalogRecipes();
        if (!catalogRecipes.isEmpty()) {
            int limit = Math.min(24, catalogRecipes.size());
            for (int i = 0; i < limit; i++) {
                activeRecipes.add(catalogRecipes.get(i));
            }
            currentRecipe = activeRecipes.get(0);
            currentSettings = currentRecipe.adjustments(null);
        }

        frame = new JFrame("IRO Studio — Fujifilm X-Trans V & LightCraft Desktop");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1480, 900);
        frame.setMinimumSize(new Dimension(1120, 700));
        frame.setLocationRelativeTo(null);

        // Nền xám sáng mềm mại chuẩn Android Tablet (#F5F6F9)
        JPanel root = new JPanel(new BorderLayout(14, 0));
        root.setBackground(APP_BG);
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        frame.setContentPane(root);

        // 1. THANH NAVIGATION RAIL BÊN TRÁI (NỔI BO TRÒN 28px)
        navRail = new AndroidNavRail(new AndroidNavRail.NavListener() {
            @Override
            public void onSectionSelected(int index) {
                switchSection(index);
            }

            @Override
            public void onOpenPhotoClicked() {
                openImageDialog();
            }

            @Override
            public void onAutoBalanceClicked() {
                runAutoBalance();
            }
        });
        root.add(navRail, BorderLayout.WEST);

        // 2. VÙNG TRUNG TÂM: THANH PILL NỔI PHÍA TRÊN + KHUNG ẢNH BO TRÒN 28px
        JPanel centerColumn = new JPanel(new BorderLayout(0, 12));
        centerColumn.setOpaque(false);

        canvasPanel = new ImageCanvasPanel();
        histogramPanel = new HistogramPanel();

        topRecipeBadgeBtn = new PillButton("Classic Chrome • Kodachrome 64", false, false);
        topRecipeBadgeBtn.setFont(FONT_BOLD);
        topRecipeBadgeBtn.addActionListener(e -> {
            navRail.setActiveIndex(1);
            switchSection(1);
        });

        splitBtn = new PillButton("Chia đôi", false, true);
        holdOriginalBtn = new PillButton("Giữ xem Gốc", false, true);

        centerColumn.add(buildTopFloatingBar(), BorderLayout.NORTH);
        centerColumn.add(canvasPanel, BorderLayout.CENTER);

        bottomStatusLabel = new JLabel("Sẵn sàng • Lăn chuột để Zoom (100% - 500%) • Nhấp đúp thước kẻ để đặt lại thông số");
        bottomStatusLabel.setFont(FONT_SMALL);
        bottomStatusLabel.setForeground(TEXT_SECONDARY);
        bottomStatusLabel.setBorder(new EmptyBorder(0, 8, 0, 8));
        centerColumn.add(bottomStatusLabel, BorderLayout.SOUTH);

        root.add(centerColumn, BorderLayout.CENTER);

        // 3. THẺ CÔNG CỤ BÊN PHẢI BO TRÒN 28px (RỘNG 436px GIỐNG ANDROID TABLET)
        RoundedCardPanel rightFloatingCard = new RoundedCardPanel(28, CARD_BG, CARD_BORDER);
        rightFloatingCard.setPreferredSize(new Dimension(436, 760));
        rightFloatingCard.setLayout(new BorderLayout());
        rightFloatingCard.setBorder(new EmptyBorder(16, 16, 16, 16));

        rightCardContainer.setOpaque(false);
        rightCardContainer.add(buildAdjustSectionPanel(), "SECTION_0");
        rightCardContainer.add(buildFilmSectionPanel(), "SECTION_1");
        rightCardContainer.add(buildFrameSectionPanel(), "SECTION_2");
        rightCardContainer.add(buildLightCraftSectionPanel(), "SECTION_3");

        rightFloatingCard.add(rightCardContainer, BorderLayout.CENTER);
        root.add(rightFloatingCard, BorderLayout.EAST);

        setupKeyboardShortcuts();
        loadBuiltInSamplePhoto(0);
        syncUiFromState();
        pushHistorySnapshot();

        if ("filters".equalsIgnoreCase(cliInitialTab) || "film".equalsIgnoreCase(cliInitialTab)) {
            navRail.setActiveIndex(1);
            switchSection(1);
        } else if ("lightcraft".equalsIgnoreCase(cliInitialTab) || "curve".equalsIgnoreCase(cliInitialTab)) {
            navRail.setActiveIndex(3);
            switchSection(3);
        } else if ("frame".equalsIgnoreCase(cliInitialTab)) {
            navRail.setActiveIndex(2);
            switchSection(2);
        }

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                renderExecutor.shutdownNow();
            }
        });
    }

    public void show() {
        frame.setVisible(true);
        if (cliScreenshotPath != null && !cliScreenshotPath.trim().isEmpty()) {
            Timer t = new Timer(2200, e -> {
                try {
                    BufferedImage snap = new BufferedImage(
                            frame.getContentPane().getWidth(),
                            frame.getContentPane().getHeight(),
                            BufferedImage.TYPE_INT_RGB);
                    Graphics2D g2 = snap.createGraphics();
                    frame.getContentPane().paint(g2);
                    g2.dispose();
                    File out = new File(cliScreenshotPath);
                    if (out.getParentFile() != null) out.getParentFile().mkdirs();
                    ImageIO.write(snap, "png", out);
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    System.exit(0);
                }
            });
            t.setRepeats(false);
            t.start();
        }
    }

    private void switchSection(int index) {
        if (index == 4) {
            showLibraryDialog();
            return;
        }
        rightCardLayout.show(rightCardContainer, "SECTION_" + index);
        if (index == 1) {
            refreshFilmRecipeCardsAsync();
        }
    }

    // =========================================================================
    // THANH PILL NỔI PHÍA TRÊN (GIỐNG HỆT TABLET ANDROID)
    // =========================================================================
    private JComponent buildTopFloatingBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);

        // Trái: Pill tên công thức hiện tại
        JPanel leftWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        leftWrap.setOpaque(false);
        leftWrap.add(topRecipeBadgeBtn);
        bar.add(leftWrap, BorderLayout.WEST);

        // Giữa: Viên thuốc trắng chứa Undo, Redo, Chia đôi, Giữ xem Gốc
        JPanel centerOuter = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        centerOuter.setOpaque(false);

        RoundedCardPanel centerCapsule = new RoundedCardPanel(999, CARD_BG, CARD_BORDER);
        centerCapsule.setLayout(new FlowLayout(FlowLayout.CENTER, 6, 4));

        CircleIconButton undoBtn = new CircleIconButton("undo", 32, true, "Hoàn tác (Ctrl+Z)");
        undoBtn.addActionListener(e -> undo());

        CircleIconButton redoBtn = new CircleIconButton("redo", 32, true, "Làm lại (Ctrl+Y)");
        redoBtn.addActionListener(e -> redo());

        splitBtn.addActionListener(e -> {
            boolean next = !canvasPanel.isSplitView();
            canvasPanel.setSplitView(next);
            splitBtn.setActiveDark(next);
        });

        holdOriginalBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                canvasPanel.setShowOriginalOnly(true);
                holdOriginalBtn.setActiveDark(true);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                canvasPanel.setShowOriginalOnly(false);
                holdOriginalBtn.setActiveDark(false);
            }
        });

        centerCapsule.add(undoBtn);
        centerCapsule.add(redoBtn);
        centerCapsule.add(splitBtn);
        centerCapsule.add(holdOriginalBtn);
        centerOuter.add(centerCapsule);
        bar.add(centerOuter, BorderLayout.CENTER);

        // Phải: Nút Xuất ảnh đen đậm + nút (...) tròn
        JPanel rightWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        rightWrap.setOpaque(false);

        PillButton exportBtn = new PillButton("Xuất ảnh", true, false);
        exportBtn.addActionListener(e -> exportImageDialog());

        CircleIconButton moreBtn = new CircleIconButton("more", 36, false, "Thêm công cụ (Đổi ảnh mẫu, LUT, Đặt lại)");
        moreBtn.addActionListener(e -> showMorePopupMenu(moreBtn));

        rightWrap.add(exportBtn);
        rightWrap.add(moreBtn);
        bar.add(rightWrap, BorderLayout.EAST);

        return bar;
    }

    private void showMorePopupMenu(Component invoker) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem openItem = new JMenuItem("Mở ảnh từ máy tính (Ctrl+O)...");
        openItem.addActionListener(e -> openImageDialog());

        JMenuItem nextSampleItem = new JMenuItem("Đổi ảnh mẫu Fuji X Weekly kế tiếp");
        nextSampleItem.addActionListener(e -> loadBuiltInSamplePhoto(currentSampleIndex + 1));

        JMenuItem libItem = new JMenuItem("Mở Kho 392 công thức FujiXWeekly...");
        libItem.addActionListener(e -> showLibraryDialog());

        JMenuItem lutItem = new JMenuItem("Nạp tệp 3D LUT (.cube)...");
        lutItem.addActionListener(e -> importCubeLutDialog());

        JMenuItem resetItem = new JMenuItem("Đặt lại toàn bộ thông số về gốc");
        resetItem.addActionListener(e -> resetAllAdjustments());

        menu.add(openItem);
        menu.add(nextSampleItem);
        menu.addSeparator();
        menu.add(libItem);
        menu.add(lutItem);
        menu.addSeparator();
        menu.add(resetItem);
        menu.show(invoker, 0, invoker.getHeight() + 4);
    }

    // =========================================================================
    // SECTION 0: TINH CHỈNH (GIỐNG HỆT ANDROID TABLET MINIMAL ADJUST)
    // =========================================================================
    private JComponent buildAdjustSectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);

        // Khối cố định phía trên: Histogram + Tên thông số đang chọn + Thước kẻ vạch + Tự động cân bằng
        JPanel topFixed = new JPanel();
        topFixed.setOpaque(false);
        topFixed.setLayout(new BoxLayout(topFixed, BoxLayout.Y_AXIS));

        histogramPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        topFixed.add(histogramPanel);
        topFixed.add(Box.createVerticalStrut(12));

        // Hàng tiêu đề thông số + DR100 + giá trị + nút reset tròn
        JPanel paramHeaderRow = new JPanel(new BorderLayout(8, 0));
        paramHeaderRow.setOpaque(false);
        paramHeaderRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        paramHeaderRow.setMaximumSize(new Dimension(500, 34));

        activeParamTitleLabel = new JLabel("Độ phơi sáng");
        activeParamTitleLabel.setFont(FONT_TITLE);
        activeParamTitleLabel.setForeground(TEXT_PRIMARY);
        paramHeaderRow.add(activeParamTitleLabel, BorderLayout.WEST);

        JPanel badgesRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        badgesRight.setOpaque(false);

        drCyclePillBtn = new PillButton("DR100", false, true);
        drCyclePillBtn.setFont(FONT_SMALL_BOLD);
        drCyclePillBtn.setMargin(new Insets(4, 10, 4, 10));
        drCyclePillBtn.setToolTipText("Nhấn để chuyển Dải tương phản động: DR100 / DR200 / DR400");
        drCyclePillBtn.addActionListener(e -> {
            int nextDr = currentSettings.dr == 100 ? 200 : (currentSettings.dr == 200 ? 400 : 100);
            currentSettings.dr = nextDr;
            drCyclePillBtn.setText("DR" + nextDr);
            pushHistorySnapshot();
            triggerPreviewRender();
        });

        activeParamValuePill = new PillButton("0.0", false, true);
        activeParamValuePill.setFont(FONT_MONO);
        activeParamValuePill.setMargin(new Insets(4, 10, 4, 10));
        activeParamValuePill.setToolTipText("Nhấn để đặt thông số đang chọn về mặc định");
        activeParamValuePill.addActionListener(e -> resetSelectedParameter());

        CircleIconButton resetAllCircle = new CircleIconButton("reset", 30, true, "Đặt lại mọi thông số về gốc");
        resetAllCircle.addActionListener(e -> resetAllAdjustments());

        badgesRight.add(drCyclePillBtn);
        badgesRight.add(activeParamValuePill);
        badgesRight.add(resetAllCircle);
        paramHeaderRow.add(badgesRight, BorderLayout.EAST);

        topFixed.add(paramHeaderRow);
        topFixed.add(Box.createVerticalStrut(8));

        // Thước kẻ vạch TickRulerWidget giống hệt Android
        mainTickRuler = new TickRulerWidget(val -> {
            if (suppressEvents) return;
            setParameterValue(selectedParamIndex, val);
            updateParameterBadges();
            triggerPreviewRender();
        });
        mainTickRuler.setAlignmentX(Component.LEFT_ALIGNMENT);
        topFixed.add(mainTickRuler);
        topFixed.add(Box.createVerticalStrut(8));

        // Nút viên thuốc "Tự động cân bằng ảnh"
        JPanel autoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        autoRow.setOpaque(false);
        autoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        PillButton autoPillBtn = new PillButton("Tự động cân bằng ảnh", false, true);
        autoPillBtn.addActionListener(e -> runAutoBalance());
        autoRow.add(autoPillBtn);
        topFixed.add(autoRow);

        panel.add(topFixed, BorderLayout.NORTH);

        // Lưới 2 cột các thẻ viên thuốc thông số (14 thông số chuẩn Fujifilm X-Trans V)
        JPanel grid = new JPanel(new GridLayout(0, 2, 10, 10));
        grid.setOpaque(false);
        paramChipCards.clear();
        for (int i = 0; i < paramDefs.length; i++) {
            final int idx = i;
            ParamChipCard card = new ParamChipCard(paramDefs[i].title, "0.0");
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        selectedParamIndex = idx;
                        resetSelectedParameter();
                    } else {
                        selectParameter(idx);
                    }
                }
            });
            paramChipCards.add(card);
            grid.add(card);
        }

        JPanel gridWrap = new JPanel(new BorderLayout());
        gridWrap.setOpaque(false);
        gridWrap.add(grid, BorderLayout.NORTH);

        panel.add(createMinimalScrollPane(gridWrap), BorderLayout.CENTER);
        return panel;
    }

    private void selectParameter(int index) {
        selectedParamIndex = index;
        ParamDef def = paramDefs[index];
        activeParamTitleLabel.setText(def.title);
        double cur = getParameterValue(index);
        suppressEvents = true;
        mainTickRuler.configure(def.min, def.max, def.defaultVal, cur);
        suppressEvents = false;
        updateParameterBadges();
    }

    private void resetSelectedParameter() {
        ParamDef def = paramDefs[selectedParamIndex];
        setParameterValue(selectedParamIndex, def.defaultVal);
        selectParameter(selectedParamIndex);
        pushHistorySnapshot();
        triggerPreviewRender();
    }

    private void updateParameterBadges() {
        activeParamValuePill.setText(formatParamValue(selectedParamIndex, getParameterValue(selectedParamIndex)));
        drCyclePillBtn.setText("DR" + currentSettings.dr);
        for (int i = 0; i < paramChipCards.size(); i++) {
            double v = getParameterValue(i);
            boolean modified = Math.abs(v - paramDefs[i].defaultVal) > 0.04;
            paramChipCards.get(i).updateState(
                    formatParamValue(i, v),
                    i == selectedParamIndex,
                    modified
            );
        }
    }

    private double getParameterValue(int idx) {
        switch (idx) {
            case 0: return currentSettings.exposure;
            case 1: return currentSettings.highlight;
            case 2: return currentSettings.shadow;
            case 3: return currentSettings.color;
            case 4: return currentSettings.sharpness;
            case 5: return currentSettings.nr;
            case 6: return currentSettings.clarity;
            case 7: return currentSettings.grain;
            case 8: return currentSettings.temperature;
            case 9: return currentSettings.red;
            case 10: return currentSettings.blue;
            case 11: return currentSettings.colorChrome;
            case 12: return currentSettings.chromeBlue;
            case 13: return currentSettings.lutAmount;
            default: return 0.0;
        }
    }

    private void setParameterValue(int idx, double v) {
        switch (idx) {
            case 0: currentSettings.exposure = v; break;
            case 1: currentSettings.highlight = v; break;
            case 2: currentSettings.shadow = v; break;
            case 3: currentSettings.color = v; break;
            case 4: currentSettings.sharpness = v; break;
            case 5: currentSettings.nr = v; break;
            case 6: currentSettings.clarity = v; break;
            case 7: currentSettings.grain = Math.round(v); break;
            case 8: currentSettings.temperature = Math.round(v); break;
            case 9: currentSettings.red = Math.round(v); break;
            case 10: currentSettings.blue = Math.round(v); break;
            case 11: currentSettings.colorChrome = Math.round(v); break;
            case 12: currentSettings.chromeBlue = Math.round(v); break;
            case 13: currentSettings.lutAmount = Math.round(v); break;
        }
    }

    private String formatParamValue(int idx, double v) {
        switch (idx) {
            case 11: // Color Chrome
            case 12: // Chrome Blue
                int iv = (int) Math.round(v);
                return iv == 0 ? "Tắt" : (iv == 1 ? "Nhẹ" : "Mạnh");
            case 7: // Grain
            case 8: // Temp
            case 9: // WB Red
            case 10: // WB Blue
                int wb = (int) Math.round(v);
                return (wb > 0 && idx >= 8 ? "+" : "") + wb;
            case 13:
                return ((int) Math.round(v)) + "%";
            default:
                double r = Math.round(v * 10.0) / 10.0;
                return (r > 0 ? "+" : "") + String.format(Locale.US, "%.1f", r);
        }
    }

    private static final class ParamDef {
        final String title;
        final String shortCode;
        final double min;
        final double max;
        final double defaultVal;

        ParamDef(String title, String shortCode, double min, double max, double defaultVal) {
            this.title = title;
            this.shortCode = shortCode;
            this.min = min;
            this.max = max;
            this.defaultVal = defaultVal;
        }
    }

    /** Thẻ thông số bo tròn 18px trong lưới 2 cột (giống hệt Android Tablet Minimal Adjust). */
    private static final class ParamChipCard extends JPanel {
        private final JLabel nameLbl;
        private final JLabel valLbl;
        private boolean selected;
        private boolean modified;
        private boolean hover;

        ParamChipCard(String title, String valueText) {
            setOpaque(false);
            setPreferredSize(new Dimension(175, 50));
            setLayout(new BorderLayout(6, 0));
            setBorder(new EmptyBorder(10, 14, 10, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            nameLbl = new JLabel(title);
            nameLbl.setFont(FONT_BOLD);
            nameLbl.setForeground(TEXT_PRIMARY);

            valLbl = new JLabel(valueText);
            valLbl.setFont(FONT_MONO);
            valLbl.setForeground(TEXT_SECONDARY);

            add(nameLbl, BorderLayout.WEST);
            add(valLbl, BorderLayout.EAST);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        void updateState(String valText, boolean selected, boolean modified) {
            this.selected = selected;
            this.modified = modified;
            valLbl.setText(valText);
            nameLbl.setForeground(selected ? INK_DARK : TEXT_PRIMARY);
            valLbl.setForeground(selected ? INK_DARK : (modified ? ACCENT_BLUE : TEXT_SECONDARY));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            if (selected) {
                g2.setColor(new Color(0xEC, 0xF0, 0xF7));
                g2.fillRoundRect(0, 0, w - 1, h - 1, 18, 18);
                g2.setColor(INK_DARK);
                g2.setStroke(new BasicStroke(1.8f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 18, 18);
            } else {
                g2.setColor(hover ? SURFACE_HOVER : SURFACE_SOFT);
                g2.fillRoundRect(0, 0, w - 1, h - 1, 18, 18);
                if (modified) {
                    g2.setColor(new Color(0xB8, 0xCC, 0xEC));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, 18, 18);
                }
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =========================================================================
    // SECTION 1: GIẢ LẬP MÀU PHIM (GIỐNG HỆT TABLET_FUJI_FILTERS.PNG 3 CỘT)
    // =========================================================================
    private JComponent buildFilmSectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);

        JPanel topHeader = new JPanel();
        topHeader.setOpaque(false);
        topHeader.setLayout(new BoxLayout(topHeader, BoxLayout.Y_AXIS));

        // Hàng 1: Tiêu đề "Giả lập Màu phim" + nút "+ Kho 392 màu"
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel("Giả lập Màu phim");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_PRIMARY);
        row1.add(title, BorderLayout.WEST);

        PillButton openCatalogPill = new PillButton("+ Kho 392 màu", false, true);
        openCatalogPill.addActionListener(e -> showLibraryDialog());
        row1.add(openCatalogPill, BorderLayout.EAST);
        topHeader.add(row1);
        topHeader.add(Box.createVerticalStrut(10));

        // Hàng 2: Viên thuốc chuyển chế độ "Ảnh mẫu FujiXWeekly" | "Ảnh đang mở"
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row2.setOpaque(false);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);

        modeSamplePill = new PillButton("Ảnh mẫu FujiXWeekly", true, true);
        modeSamplePill.setFont(FONT_SMALL_BOLD);
        modePhotoPill = new PillButton("Ảnh đang mở", false, true);
        modePhotoPill.setFont(FONT_SMALL_BOLD);

        modeSamplePill.addActionListener(e -> {
            useFujiSampleOnCards = true;
            modeSamplePill.setActiveDark(true);
            modePhotoPill.setActiveDark(false);
            recipeThumbCache.clear();
            refreshFilmRecipeCardsAsync();
        });
        modePhotoPill.addActionListener(e -> {
            useFujiSampleOnCards = false;
            modeSamplePill.setActiveDark(false);
            modePhotoPill.setActiveDark(true);
            recipeThumbCache.clear();
            refreshFilmRecipeCardsAsync();
        });

        row2.add(modeSamplePill);
        row2.add(modePhotoPill);
        topHeader.add(row2);

        panel.add(topHeader, BorderLayout.NORTH);

        // Lưới 3 cột thẻ công thức màu phim bo tròn (giống hệt tablet_fuji_filters.png)
        filmGridPanel = new JPanel(new GridLayout(0, 3, 10, 10));
        filmGridPanel.setOpaque(false);
        rebuildFilmGridCards();

        JPanel gridWrap = new JPanel(new BorderLayout());
        gridWrap.setOpaque(false);
        gridWrap.add(filmGridPanel, BorderLayout.NORTH);

        panel.add(createMinimalScrollPane(gridWrap), BorderLayout.CENTER);
        return panel;
    }

    private void rebuildFilmGridCards() {
        if (filmGridPanel == null) return;
        filmGridPanel.removeAll();
        filmCards.clear();
        for (Recipe r : activeRecipes) {
            FilmRecipeCard card = new FilmRecipeCard(r);
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    applyRecipeSelection(r);
                }
            });
            filmCards.add(card);
            filmGridPanel.add(card);
        }
        filmGridPanel.revalidate();
        filmGridPanel.repaint();
        refreshFilmRecipeCardsAsync();
    }

    private void applyRecipeSelection(Recipe r) {
        currentRecipe = r;
        currentSettings = r.adjustments(null);
        syncUiFromState();
        pushHistorySnapshot();
        triggerPreviewRender();
        for (FilmRecipeCard c : filmCards) {
            c.setSelected(currentRecipe != null && c.recipe.id.equals(currentRecipe.id));
        }
    }

    private void refreshFilmRecipeCardsAsync() {
        for (FilmRecipeCard card : filmCards) {
            card.setSelected(currentRecipe != null && card.recipe.id.equals(currentRecipe.id));
            String cacheKey = (useFujiSampleOnCards ? "sample:" : "photo:") + card.recipe.id;
            BufferedImage cached = recipeThumbCache.get(cacheKey);
            if (cached != null) {
                card.setThumbnail(cached);
                continue;
            }
            if (useFujiSampleOnCards) {
                DesktopFujiSampleLoader.loadRecipeSampleAsync(card.recipe, img -> {
                    if (img != null) {
                        recipeThumbCache.put(cacheKey, img);
                        card.setThumbnail(img);
                    } else if (thumbSourceImage != null) {
                        BufferedImage rendered = renderRecipeThumb(thumbSourceImage, card.recipe);
                        recipeThumbCache.put(cacheKey, rendered);
                        card.setThumbnail(rendered);
                    }
                });
                // Nếu chưa có trong cache ngay lập tức nhưng có thumbSourceImage, hiển thị tạm
                if (card.thumbnail == null && thumbSourceImage != null) {
                    BufferedImage instant = DesktopFujiSampleLoader.getCachedForRecipe(card.recipe);
                    if (instant != null) {
                        recipeThumbCache.put(cacheKey, instant);
                        card.setThumbnail(instant);
                    } else {
                        card.setThumbnail(renderRecipeThumb(thumbSourceImage, card.recipe));
                    }
                }
            } else if (thumbSourceImage != null) {
                BufferedImage rendered = renderRecipeThumb(thumbSourceImage, card.recipe);
                recipeThumbCache.put(cacheKey, rendered);
                card.setThumbnail(rendered);
            }
        }
    }

    private static BufferedImage renderRecipeThumb(BufferedImage src, Recipe r) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);
        Settings s = r.adjustments(null);
        int[] outPx = ColorEngine.process(px, w, h, s, null);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, outPx, 0, w);
        return out;
    }

    /** Thẻ công thức màu phim bo tròn 20px có ảnh thumbnail ở trên và nhãn ở dưới. */
    private static final class FilmRecipeCard extends JPanel {
        final Recipe recipe;
        BufferedImage thumbnail;
        private boolean selected;
        private boolean hover;

        FilmRecipeCard(Recipe recipe) {
            this.recipe = recipe;
            setOpaque(false);
            setPreferredSize(new Dimension(122, 134));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        void setThumbnail(BufferedImage img) {
            this.thumbnail = img;
            repaint();
        }

        void setSelected(boolean s) {
            this.selected = s;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            g2.setColor(selected ? INK_DARK : (hover ? SURFACE_HOVER : SURFACE_SOFT));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 20, 20);

            int imgPad = 5;
            int imgW = w - imgPad * 2;
            int imgH = h - 42;
            Shape oldClip = g2.getClip();
            g2.setClip(new java.awt.geom.RoundRectangle2D.Float(imgPad, imgPad, imgW, imgH, 14, 14));
            if (thumbnail != null) {
                double scale = Math.max((double) imgW / thumbnail.getWidth(), (double) imgH / thumbnail.getHeight());
                int dw = (int) Math.round(thumbnail.getWidth() * scale);
                int dh = (int) Math.round(thumbnail.getHeight() * scale);
                int dx = imgPad + (imgW - dw) / 2;
                int dy = imgPad + (imgH - dh) / 2;
                g2.drawImage(thumbnail, dx, dy, dw, dh, null);
            } else {
                g2.setColor(new Color(0x52, 0x64, 0x7A));
                g2.fillRect(imgPad, imgPad, imgW, imgH);
            }
            g2.setClip(oldClip);

            g2.setFont(FONT_SMALL_BOLD);
            g2.setColor(selected ? Color.WHITE : TEXT_PRIMARY);
            String name = ellipsize(recipe.name, g2.getFontMetrics(), w - 14);
            int nx = (w - g2.getFontMetrics().stringWidth(name)) / 2;
            g2.drawString(name, nx, imgPad + imgH + 17);

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g2.setColor(selected ? new Color(0xC8, 0xCE, 0xDA) : TEXT_MUTED);
            String sub = ellipsize(recipe.film, g2.getFontMetrics(), w - 12);
            int sx = (w - g2.getFontMetrics().stringWidth(sub)) / 2;
            g2.drawString(sub, sx, imgPad + imgH + 31);

            if (!selected) {
                g2.setColor(CARD_BORDER);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 20, 20);
            }
            g2.dispose();
        }

        private static String ellipsize(String s, java.awt.FontMetrics fm, int maxW) {
            if (s == null) return "";
            if (fm.stringWidth(s) <= maxW) return s;
            String out = s;
            while (out.length() > 3 && fm.stringWidth(out + "...") > maxW) {
                out = out.substring(0, out.length() - 1);
            }
            return out + "...";
        }
    }

    // =========================================================================
    // SECTION 2: KHUNG ẢNH EXIF & CÔNG CỤ LUT / JSON
    // =========================================================================
    private JComponent buildFrameSectionPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Khung ảnh Triển lãm & Công cụ LUT");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(12));

        String[][] frameDefs = {
                {"Không khung (Ảnh gốc tràn viền)", "Xuất ảnh nguyên bản không chèn viền ngoài"},
                {"Khung Trắng Fujifilm EXIF", "Viền trắng sang trọng kèm thông số ống kính & màu phim"},
                {"Khung Đen Cinema Darkroom", "Viền đen phòng tối điện ảnh làm nổi bật vùng sáng"},
                {"Khung Polaroid Cổ điển", "Viền vuông cổ điển kèm nhãn công thức ở cạnh dưới"}
        };

        frameCards.clear();
        for (int i = 0; i < frameDefs.length; i++) {
            final int mode = i;
            FrameTemplateCard card = new FrameTemplateCard(frameDefs[i][0], frameDefs[i][1]);
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    canvasPanel.setFrameMode(mode);
                    for (int j = 0; j < frameCards.size(); j++) {
                        frameCards.get(j).setSelected(j == mode);
                    }
                }
            });
            frameCards.add(card);
            panel.add(card);
            panel.add(Box.createVerticalStrut(8));
        }
        frameCards.get(0).setSelected(true);

        panel.add(Box.createVerticalStrut(14));
        JLabel lutHeader = new JLabel("3D LUT (.cube) & Sao lưu Công thức JSON");
        lutHeader.setFont(FONT_BOLD);
        lutHeader.setForeground(TEXT_PRIMARY);
        lutHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(lutHeader);
        panel.add(Box.createVerticalStrut(10));

        JPanel btnGrid = new JPanel(new GridLayout(0, 2, 8, 8));
        btnGrid.setOpaque(false);
        btnGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnGrid.setMaximumSize(new Dimension(500, 130));

        PillButton loadCubeBtn = new PillButton("Nạp .CUBE LUT", false, true);
        loadCubeBtn.addActionListener(e -> importCubeLutDialog());

        PillButton clearLutBtn = new PillButton("Gỡ 3D LUT", false, true);
        clearLutBtn.addActionListener(e -> {
            activeCubeLut = null;
            bottomStatusLabel.setText("Đã gỡ 3D LUT (.cube).");
            triggerPreviewRender();
        });

        PillButton copyJsonBtn = new PillButton("Sao chép JSON", false, true);
        copyJsonBtn.addActionListener(e -> copySettingsJson());

        PillButton importJsonBtn = new PillButton("Nhập JSON", false, true);
        importJsonBtn.addActionListener(e -> importSettingsJsonDialog());

        PillButton nextSampleBtn = new PillButton("Đổi ảnh mẫu", false, true);
        nextSampleBtn.addActionListener(e -> loadBuiltInSamplePhoto(currentSampleIndex + 1));

        PillButton openCatalogBtn = new PillButton("Kho 392 Màu", false, true);
        openCatalogBtn.addActionListener(e -> showLibraryDialog());

        btnGrid.add(loadCubeBtn);
        btnGrid.add(clearLutBtn);
        btnGrid.add(copyJsonBtn);
        btnGrid.add(importJsonBtn);
        btnGrid.add(nextSampleBtn);
        btnGrid.add(openCatalogBtn);

        panel.add(btnGrid);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private static final class FrameTemplateCard extends JPanel {
        private boolean selected;
        private boolean hover;
        private final JLabel titleLbl;
        private final JLabel subLbl;

        FrameTemplateCard(String title, String sub) {
            setOpaque(false);
            setMaximumSize(new Dimension(500, 62));
            setPreferredSize(new Dimension(380, 62));
            setLayout(new BorderLayout(0, 2));
            setBorder(new EmptyBorder(10, 14, 10, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            titleLbl = new JLabel(title);
            titleLbl.setFont(FONT_BOLD);
            titleLbl.setForeground(TEXT_PRIMARY);

            subLbl = new JLabel(sub);
            subLbl.setFont(FONT_SMALL);
            subLbl.setForeground(TEXT_SECONDARY);

            add(titleLbl, BorderLayout.NORTH);
            add(subLbl, BorderLayout.SOUTH);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        void setSelected(boolean s) {
            this.selected = s;
            titleLbl.setForeground(s ? Color.WHITE : TEXT_PRIMARY);
            subLbl.setForeground(s ? new Color(0xD0, 0xD6, 0xE2) : TEXT_SECONDARY);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(selected ? INK_DARK : (hover ? SURFACE_HOVER : SURFACE_SOFT));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =========================================================================
    // SECTION 3: LIGHTCRAFT STUDIO (CURVES, 3-WAY WHEELS, 8-CH HSL, AI MASKS)
    // =========================================================================
    private JComponent buildLightCraftSectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);

        JPanel topBox = new JPanel();
        topBox.setOpaque(false);
        topBox.setLayout(new BoxLayout(topBox, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("LightCraft Studio");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        topBox.add(title);
        topBox.add(Box.createVerticalStrut(8));

        // Hàng tab con viên thuốc: Đường cong | Bánh xe | HSL 8 kênh | Mặt nạ AI
        CardLayout subCards = new CardLayout();
        JPanel subContainer = new JPanel(subCards);
        subContainer.setOpaque(false);

        JPanel subTabsRow = new JPanel(new GridLayout(1, 4, 6, 0));
        subTabsRow.setOpaque(false);
        subTabsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        subTabsRow.setMaximumSize(new Dimension(500, 34));

        String[] lcTabNames = {"Đường cong", "Bánh xe", "HSL 8 kênh", "Mặt nạ AI"};
        PillButton[] lcTabBtns = new PillButton[4];
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            lcTabBtns[i] = new PillButton(lcTabNames[i], i == 0, true);
            lcTabBtns[i].setFont(FONT_SMALL_BOLD);
            lcTabBtns[i].setMargin(new Insets(4, 6, 4, 6));
            lcTabBtns[i].addActionListener(e -> {
                for (int j = 0; j < 4; j++) lcTabBtns[j].setActiveDark(j == idx);
                subCards.show(subContainer, "LC_" + idx);
            });
            subTabsRow.add(lcTabBtns[i]);
        }
        topBox.add(subTabsRow);
        panel.add(topBox, BorderLayout.NORTH);

        subContainer.add(buildLcCurvePane(), "LC_0");
        subContainer.add(buildLcWheelsPane(), "LC_1");
        subContainer.add(buildLcHslPane(), "LC_2");
        subContainer.add(buildLcMaskPane(), "LC_3");

        panel.add(subContainer, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildLcCurvePane() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JPanel chRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        chRow.setOpaque(false);
        chRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        String[] chNames = {"RGB", "Đỏ (R)", "Lục (G)", "Lam (B)"};
        PillButton[] chBtns = new PillButton[4];
        toneCurveWidget = new ToneCurveWidget(lightCraftState, () -> {
            pushHistorySnapshot();
            triggerPreviewRender();
        });

        for (int i = 0; i < 4; i++) {
            final int ch = i;
            chBtns[i] = new PillButton(chNames[i], i == 0, true);
            chBtns[i].setFont(FONT_SMALL_BOLD);
            chBtns[i].setMargin(new Insets(4, 10, 4, 10));
            chBtns[i].addActionListener(e -> {
                for (int j = 0; j < 4; j++) chBtns[j].setActiveDark(j == ch);
                toneCurveWidget.setActiveChannel(ch);
            });
            chRow.add(chBtns[i]);
        }

        CircleIconButton resetCurveBtn = new CircleIconButton("reset", 30, true, "Đặt lại đường cong");
        resetCurveBtn.addActionListener(e -> {
            lightCraftState.resetCurves();
            toneCurveWidget.repaint();
            pushHistorySnapshot();
            triggerPreviewRender();
        });
        chRow.add(resetCurveBtn);
        p.add(chRow);
        p.add(Box.createVerticalStrut(8));

        toneCurveWidget.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(toneCurveWidget);
        p.add(Box.createVerticalStrut(12));

        JLabel presetTitle = new JLabel("12 Preset LightCraft Điện ảnh");
        presetTitle.setFont(FONT_BOLD);
        presetTitle.setForeground(TEXT_PRIMARY);
        presetTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(presetTitle);
        p.add(Box.createVerticalStrut(8));

        JPanel presetGrid = new JPanel(new GridLayout(0, 2, 8, 8));
        presetGrid.setOpaque(false);
        presetGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (LightCraftEngine.BuiltinPreset preset : LightCraftEngine.PRESETS) {
            PillButton pb = new PillButton(preset.name, false, true);
            pb.setFont(FONT_SMALL_BOLD);
            pb.addActionListener(e -> {
                preset.applyTo(lightCraftState, currentSettings);
                syncUiFromState();
                pushHistorySnapshot();
                triggerPreviewRender();
            });
            presetGrid.add(pb);
        }
        p.add(presetGrid);

        return createMinimalScrollPane(p);
    }

    private JComponent buildLcWheelsPane() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        Runnable onWheel = this::triggerPreviewRender;
        shadowWheel = new ColorWheelWidget("Shadows", lightCraftState.shadowsWheel, onWheel);
        midtoneWheel = new ColorWheelWidget("Midtones", lightCraftState.midtonesWheel, onWheel);
        highlightWheel = new ColorWheelWidget("Highlights", lightCraftState.highlightsWheel, onWheel);

        JPanel wheelsGrid = new JPanel(new GridLayout(1, 3, 8, 8));
        wheelsGrid.setOpaque(false);
        wheelsGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        wheelsGrid.add(shadowWheel);
        wheelsGrid.add(midtoneWheel);
        wheelsGrid.add(highlightWheel);
        p.add(wheelsGrid);

        p.add(Box.createVerticalStrut(12));
        PillButton resetWheels = new PillButton("Đặt lại 3 Bánh xe màu", false, true);
        resetWheels.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetWheels.addActionListener(e -> {
            lightCraftState.resetGrading();
            shadowWheel.repaint();
            midtoneWheel.repaint();
            highlightWheel.repaint();
            pushHistorySnapshot();
            triggerPreviewRender();
        });
        p.add(resetWheels);
        return createMinimalScrollPane(p);
    }

    private JComponent buildLcHslPane() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        String[] chLabels = LightCraftEngine.MIXER_BAND_NAMES;
        JPanel chGrid = new JPanel(new GridLayout(2, 4, 6, 6));
        chGrid.setOpaque(false);
        chGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        chGrid.setMaximumSize(new Dimension(500, 74));

        PillButton[] chPills = new PillButton[8];
        for (int i = 0; i < 8; i++) {
            final int ch = i;
            chPills[i] = new PillButton(chLabels[i], i == 0, true);
            chPills[i].setFont(FONT_SMALL_BOLD);
            chPills[i].setMargin(new Insets(4, 6, 4, 6));
            chPills[i].addActionListener(e -> {
                activeHslChannel = ch;
                for (int j = 0; j < 8; j++) chPills[j].setActiveDark(j == ch);
                syncHslRulers();
            });
            chGrid.add(chPills[i]);
        }
        p.add(chGrid);
        p.add(Box.createVerticalStrut(14));

        hslHueRuler = new TickRulerWidget(val -> {
            if (suppressEvents) return;
            lightCraftState.mixerHue[activeHslChannel] = (float) val;
            triggerPreviewRender();
        });
        hslSatRuler = new TickRulerWidget(val -> {
            if (suppressEvents) return;
            lightCraftState.mixerSat[activeHslChannel] = (float) val;
            triggerPreviewRender();
        });
        hslLumRuler = new TickRulerWidget(val -> {
            if (suppressEvents) return;
            lightCraftState.mixerLum[activeHslChannel] = (float) val;
            triggerPreviewRender();
        });

        p.add(createLabeledRulerBlock("Độ lệch màu (Hue)", hslHueRuler));
        p.add(Box.createVerticalStrut(10));
        p.add(createLabeledRulerBlock("Độ bão hòa (Saturation)", hslSatRuler));
        p.add(Box.createVerticalStrut(10));
        p.add(createLabeledRulerBlock("Độ sáng kênh (Luminance)", hslLumRuler));
        p.add(Box.createVerticalStrut(12));

        PillButton resetHslBtn = new PillButton("Đặt lại 8 kênh HSL", false, true);
        resetHslBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetHslBtn.addActionListener(e -> {
            lightCraftState.resetMixer();
            syncHslRulers();
            pushHistorySnapshot();
            triggerPreviewRender();
        });
        p.add(resetHslBtn);
        syncHslRulers();
        return createMinimalScrollPane(p);
    }

    private void syncHslRulers() {
        if (hslHueRuler == null) return;
        suppressEvents = true;
        hslHueRuler.configure(-100, 100, 0, lightCraftState.mixerHue[activeHslChannel]);
        hslSatRuler.configure(-100, 100, 0, lightCraftState.mixerSat[activeHslChannel]);
        hslLumRuler.configure(-100, 100, 0, lightCraftState.mixerLum[activeHslChannel]);
        suppressEvents = false;
    }

    private JComponent buildLcMaskPane() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        LightCraftEngine.MaskMode[] modes = LightCraftEngine.MaskMode.values();
        JPanel maskGrid = new JPanel(new GridLayout(0, 2, 6, 6));
        maskGrid.setOpaque(false);
        maskGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        maskGrid.setMaximumSize(new Dimension(500, 115));

        PillButton[] maskBtns = new PillButton[modes.length];
        for (int i = 0; i < modes.length; i++) {
            final int m = i;
            maskBtns[i] = new PillButton(modes[i].label, i == 0, true);
            maskBtns[i].setFont(FONT_SMALL_BOLD);
            maskBtns[i].addActionListener(e -> {
                lightCraftState.maskMode = modes[m];
                for (int j = 0; j < maskBtns.length; j++) maskBtns[j].setActiveDark(j == m);
                triggerPreviewRender();
            });
            maskGrid.add(maskBtns[i]);
        }
        p.add(maskGrid);
        p.add(Box.createVerticalStrut(12));

        maskExpRuler = new TickRulerWidget(v -> {
            if (suppressEvents) return;
            lightCraftState.maskExposure = (float) v;
            triggerPreviewRender();
        });
        maskTempRuler = new TickRulerWidget(v -> {
            if (suppressEvents) return;
            lightCraftState.maskTemp = (float) v;
            triggerPreviewRender();
        });
        maskSatRuler = new TickRulerWidget(v -> {
            if (suppressEvents) return;
            lightCraftState.maskSaturation = (float) v;
            triggerPreviewRender();
        });
        maskFeatherRuler = new TickRulerWidget(v -> {
            if (suppressEvents) return;
            lightCraftState.radialFeather = (float) v;
            triggerPreviewRender();
        });

        suppressEvents = true;
        maskExpRuler.configure(-2.0, 2.0, 0.0, 0.0);
        maskTempRuler.configure(-100, 100, 0, 0);
        maskSatRuler.configure(-100, 100, 0, 0);
        maskFeatherRuler.configure(0, 100, 65, 65);
        suppressEvents = false;

        p.add(createLabeledRulerBlock("Phơi sáng vùng chọn (Mask EV)", maskExpRuler));
        p.add(Box.createVerticalStrut(8));
        p.add(createLabeledRulerBlock("Nhiệt độ vùng chọn (Mask Temp)", maskTempRuler));
        p.add(Box.createVerticalStrut(8));
        p.add(createLabeledRulerBlock("Bão hòa vùng chọn (Mask Sat)", maskSatRuler));
        p.add(Box.createVerticalStrut(8));
        p.add(createLabeledRulerBlock("Độ mềm biên (Feather)", maskFeatherRuler));

        return createMinimalScrollPane(p);
    }

    private JPanel createLabeledRulerBlock(String labelText, TickRulerWidget ruler) {
        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(FONT_BOLD);
        lbl.setForeground(TEXT_PRIMARY);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(lbl);
        block.add(Box.createVerticalStrut(4));
        ruler.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(ruler);
        return block;
    }

    // =========================================================================
    // HỘP THOẠI KHO 392 CÔNG THỨC FUJI X WEEKLY (GIỐNG TABLET_FUJI_LIBRARY.PNG)
    // =========================================================================
    private void showLibraryDialog() {
        JDialog dlg = new JDialog(frame, "Kho 392 công thức Fuji X Weekly", true);
        dlg.setSize(740, 680);
        dlg.setLocationRelativeTo(frame);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(CARD_BG);
        root.setBorder(new EmptyBorder(20, 22, 20, 22));

        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);

        JLabel title = new JLabel("Kho " + catalogRecipes.size() + " công thức Fuji X Weekly");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(TEXT_PRIMARY);
        header.add(title, BorderLayout.NORTH);

        JTextField searchField = new JTextField();
        searchField.setFont(FONT_UI);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));

        Set<String> sensors = new LinkedHashSet<>();
        sensors.add("Tất cả cảm biến");
        for (Recipe r : catalogRecipes) {
            if (r.sensors != null && !r.sensors.isEmpty()) sensors.add(r.sensors);
        }
        JComboBox<String> sensorBox = new JComboBox<>(sensors.toArray(new String[0]));
        sensorBox.setFont(FONT_BOLD);

        JPanel filterRow = new JPanel(new BorderLayout(10, 0));
        filterRow.setOpaque(false);
        filterRow.add(searchField, BorderLayout.CENTER);
        filterRow.add(sensorBox, BorderLayout.EAST);
        header.add(filterRow, BorderLayout.CENTER);

        root.add(header, BorderLayout.NORTH);

        JPanel listPanel = new JPanel();
        listPanel.setOpaque(false);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        Runnable rebuildList = () -> {
            listPanel.removeAll();
            String q = searchField.getText().trim().toLowerCase(Locale.ROOT);
            String sen = (String) sensorBox.getSelectedItem();
            int shown = 0;
            for (Recipe r : catalogRecipes) {
                if (sen != null && !"Tất cả cảm biến".equals(sen) && !sen.equalsIgnoreCase(r.sensors)) {
                    continue;
                }
                if (!q.isEmpty()) {
                    String hay = (r.name + " " + r.film + " " + r.sensors).toLowerCase(Locale.ROOT);
                    if (!hay.contains(q)) continue;
                }
                listPanel.add(createCatalogRowCard(r, dlg));
                listPanel.add(Box.createVerticalStrut(8));
                if (++shown >= 90) break;
            }
            listPanel.revalidate();
            listPanel.repaint();
        };

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { rebuildList.run(); }
            @Override public void removeUpdate(DocumentEvent e) { rebuildList.run(); }
            @Override public void changedUpdate(DocumentEvent e) { rebuildList.run(); }
        });
        sensorBox.addActionListener(e -> rebuildList.run());
        rebuildList.run();

        root.add(createMinimalScrollPane(listPanel), BorderLayout.CENTER);
        dlg.setContentPane(root);
        dlg.setVisible(true);
    }

    private JComponent createCatalogRowCard(Recipe r, JDialog parentDlg) {
        RoundedCardPanel row = new RoundedCardPanel(20, SURFACE_SOFT, CARD_BORDER);
        row.setLayout(new BorderLayout(14, 0));
        row.setBorder(new EmptyBorder(10, 12, 10, 14));
        row.setMaximumSize(new Dimension(900, 86));
        row.setPreferredSize(new Dimension(650, 86));

        JLabel thumbLbl = new JLabel();
        thumbLbl.setPreferredSize(new Dimension(88, 64));
        DesktopFujiSampleLoader.loadRecipeSampleAsync(r, img -> {
            if (img != null) {
                BufferedImage scaled = new BufferedImage(88, 64, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = scaled.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, 88, 64, 14, 14));
                g2.drawImage(img, 0, 0, 88, 64, null);
                g2.dispose();
                thumbLbl.setIcon(new javax.swing.ImageIcon(scaled));
            }
        });
        row.add(thumbLbl, BorderLayout.WEST);

        Settings adj = r.adjustments(null);
        JPanel info = new JPanel(new GridLayout(3, 1, 0, 2));
        info.setOpaque(false);
        JLabel name = new JLabel(r.name);
        name.setFont(FONT_BOLD);
        name.setForeground(TEXT_PRIMARY);

        JLabel sub1 = new JLabel(r.sensors + " • Base: " + r.film + " • DR" + adj.dr);
        sub1.setFont(FONT_SMALL);
        sub1.setForeground(TEXT_SECONDARY);

        JLabel sub2 = new JLabel("WB " + adj.wb + " (R" + (adj.red >= 0 ? "+" : "") + (int) adj.red
                + " B" + (adj.blue >= 0 ? "+" : "") + (int) adj.blue + ") • H" + adj.highlight + " S" + adj.shadow);
        sub2.setFont(FONT_SMALL);
        sub2.setForeground(TEXT_MUTED);

        info.add(name);
        info.add(sub1);
        info.add(sub2);
        row.add(info, BorderLayout.CENTER);

        PillButton applyBtn = new PillButton("Áp dụng", true, false);
        applyBtn.addActionListener(e -> {
            boolean exists = false;
            for (Recipe ex : activeRecipes) {
                if (ex.id.equals(r.id)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                activeRecipes.add(0, r);
                rebuildFilmGridCards();
            }
            applyRecipeSelection(r);
            parentDlg.dispose();
        });
        JPanel btnWrap = new JPanel(new GridBagLayout());
        btnWrap.setOpaque(false);
        btnWrap.add(applyBtn);
        row.add(btnWrap, BorderLayout.EAST);

        return row;
    }

    // =========================================================================
    // ĐỒNG BỘ TRẠNG THÁI, XỬ LÝ ẢNH & PHÍM TẮT
    // =========================================================================
    private void syncUiFromState() {
        suppressEvents = true;
        String filmName = currentSettings.film != null ? currentSettings.film : "Classic Chrome";
        String recName = currentRecipe != null ? currentRecipe.name : "Custom";
        topRecipeBadgeBtn.setText(filmName + " • " + recName);
        selectParameter(selectedParamIndex);
        syncHslRulers();
        if (toneCurveWidget != null) toneCurveWidget.repaint();
        if (shadowWheel != null) shadowWheel.repaint();
        if (midtoneWheel != null) midtoneWheel.repaint();
        if (highlightWheel != null) highlightWheel.repaint();
        for (FilmRecipeCard c : filmCards) {
            c.setSelected(currentRecipe != null && c.recipe.id.equals(currentRecipe.id));
        }
        suppressEvents = false;
    }

    private void triggerPreviewRender() {
        if (previewSourcePixels == null || previewW < 1 || previewH < 1) return;
        final int gen = renderGeneration.incrementAndGet();
        final Settings snapSettings = currentSettings.copy();
        final int[] srcPx = previewSourcePixels;
        final int w = previewW;
        final int h = previewH;
        renderExecutor.submit(() -> {
            if (gen != renderGeneration.get()) return;
            long t0 = System.currentTimeMillis();
            int[] outPx = ColorEngine.process(srcPx, w, h, snapSettings, activeCubeLut);
            if (!lightCraftState.isIdentity()) {
                outPx = LightCraftEngine.processPixels(outPx, w, h, lightCraftState);
            }
            BufferedImage outImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            outImg.setRGB(0, 0, w, h, outPx, 0, w);
            final int[] histPx = outPx;
            final long elapsed = System.currentTimeMillis() - t0;
            SwingUtilities.invokeLater(() -> {
                if (gen != renderGeneration.get()) return;
                String recName = currentRecipe != null ? currentRecipe.name : snapSettings.film;
                canvasPanel.setImages(previewSourceImage, outImg, snapSettings.film, recName);
                histogramPanel.updateFromPixels(histPx, w, h);
                bottomStatusLabel.setText(currentPhotoName + " (" + fullSourceImage.getWidth() + "×"
                        + fullSourceImage.getHeight() + " px)  •  Màu phim: " + recName
                        + "  •  DR" + snapSettings.dr + "  •  Render: " + elapsed + " ms");
            });
        });
    }

    private void loadBuiltInSamplePhoto(int index) {
        String[] samples = {
                "/fujixweekly/kodachrome-64.jpg",
                "/fujixweekly/pacific-blues.jpg",
                "/fujixweekly/vintage-kodachrome.jpg",
                "/fujixweekly/kodak-portra-400-v2.jpg",
                "/fujixweekly/reggies-portra.jpg",
                "/fujixweekly/fujicolor-superia-100.jpg",
                "/fujixweekly/1970s-summer.jpg",
                "/fujixweekly/ilford-hp5-plus.jpg"
        };
        currentSampleIndex = ((index % samples.length) + samples.length) % samples.length;
        String resPath = samples[currentSampleIndex];
        BufferedImage bi = DesktopFujiSampleLoader.loadResource(resPath);
        if (bi != null) {
            currentPhotoName = resPath.substring(resPath.lastIndexOf('/') + 1);
            setSourceBufferedImage(bi);
            return;
        }
        // Nếu tên file khác, lấy bất kỳ sampleAsset nào từ catalogRecipes
        for (Recipe r : catalogRecipes) {
            BufferedImage cached = DesktopFujiSampleLoader.getCachedForRecipe(r);
            if (cached != null) {
                currentPhotoName = r.name + ".jpg";
                setSourceBufferedImage(cached);
                return;
            }
        }
        BufferedImage fallback = new BufferedImage(1280, 854, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = fallback.createGraphics();
        g2.setColor(new Color(0x3B, 0x59, 0x78));
        g2.fillRect(0, 0, 1280, 854);
        g2.dispose();
        setSourceBufferedImage(fallback);
    }

    private void setSourceBufferedImage(BufferedImage img) {
        fullSourceImage = toRgbImage(img);
        previewSourceImage = scaleDownIfNeeded(fullSourceImage, 1500);
        previewW = previewSourceImage.getWidth();
        previewH = previewSourceImage.getHeight();
        previewSourcePixels = previewSourceImage.getRGB(0, 0, previewW, previewH, null, 0, previewW);
        thumbSourceImage = scaleDownIfNeeded(fullSourceImage, 240);
        canvasPanel.resetView();
        recipeThumbCache.clear();
        refreshFilmRecipeCardsAsync();
        triggerPreviewRender();
    }

    private static BufferedImage toRgbImage(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = rgb.createGraphics();
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return rgb;
    }

    private static BufferedImage scaleDownIfNeeded(BufferedImage src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxEdge) return toRgbImage(src);
        double s = maxEdge / (double) longest;
        int nw = Math.max(1, (int) Math.round(w * s));
        int nh = Math.max(1, (int) Math.round(h * s));
        BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(src, 0, 0, nw, nh, null);
        g2.dispose();
        return scaled;
    }

    private void openImageDialog() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Chọn ảnh để chỉnh sửa trong IRO Studio");
        fc.setFileFilter(new FileNameExtensionFilter("Tệp hình ảnh (JPG, PNG, WEBP, BMP)", "jpg", "jpeg", "png", "webp", "bmp"));
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                BufferedImage bi = ImageIO.read(f);
                if (bi != null) {
                    currentPhotoName = f.getName();
                    setSourceBufferedImage(bi);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Không thể đọc ảnh: " + ex.getMessage());
            }
        }
    }

    private void exportImageDialog() {
        if (fullSourceImage == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Xuất ảnh chất lượng cao");
        String recId = currentRecipe != null ? currentRecipe.id : "custom";
        fc.setSelectedFile(new File("IRO_" + recId + "_" + currentPhotoName));
        if (fc.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File out = fc.getSelectedFile();
            if (!out.getName().toLowerCase(Locale.ROOT).endsWith(".jpg")
                    && !out.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                out = new File(out.getAbsolutePath() + ".jpg");
            }
            final File target = out;
            final Settings snapSettings = currentSettings.copy();
            final String recName = currentRecipe != null ? currentRecipe.name : snapSettings.film;
            bottomStatusLabel.setText("Đang kết xuất ảnh độ phân giải gốc...");
            renderExecutor.submit(() -> {
                try {
                    int w = fullSourceImage.getWidth();
                    int h = fullSourceImage.getHeight();
                    int[] px = fullSourceImage.getRGB(0, 0, w, h, null, 0, w);
                    int[] outPx = ColorEngine.process(px, w, h, snapSettings, activeCubeLut);
                    if (!lightCraftState.isIdentity()) {
                        outPx = LightCraftEngine.processPixels(outPx, w, h, lightCraftState);
                    }
                    BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    rgb.setRGB(0, 0, w, h, outPx, 0, w);
                    rgb = ImageCanvasPanel.renderFramedImage(rgb, canvasPanel.getFrameMode(), snapSettings.film, recName);
                    String ext = target.getName().toLowerCase(Locale.ROOT).endsWith(".png") ? "png" : "jpg";
                    ImageIO.write(rgb, ext, target);
                    SwingUtilities.invokeLater(() -> bottomStatusLabel.setText("Đã lưu ảnh tại: " + target.getAbsolutePath()));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Lỗi xuất ảnh: " + ex.getMessage()));
                }
            });
        }
    }

    private void runAutoBalance() {
        if (previewSourcePixels == null) return;
        Settings localProposed = AutoBalancer.analyze(previewSourcePixels, previewW, previewH, currentSettings);
        currentSettings = AutoBalanceGuard.protect(previewSourcePixels, previewW, previewH, localProposed);
        syncUiFromState();
        pushHistorySnapshot();
        triggerPreviewRender();
        bottomStatusLabel.setText(String.format(Locale.US,
                "Tự động cân bằng tự nhiên (AutoBalancer + Guard): EV %+.1f, Highlight %+.1f, Shadow %+.1f, Temp %+.0f",
                currentSettings.exposure, currentSettings.highlight, currentSettings.shadow, currentSettings.temperature));
    }

    private void resetAllAdjustments() {
        currentSettings = new Settings();
        if (currentRecipe != null) {
            currentSettings.film = currentRecipe.film;
        }
        lightCraftState.resetAll();
        activeCubeLut = null;
        syncUiFromState();
        pushHistorySnapshot();
        triggerPreviewRender();
    }

    private void pushHistorySnapshot() {
        try {
            String json = SettingsCodec.encode(currentSettings).toString();
            if (!undoStack.isEmpty() && undoStack.get(undoStack.size() - 1).equals(json)) return;
            undoStack.add(json);
            if (undoStack.size() > 40) undoStack.remove(0);
            redoStack.clear();
        } catch (Exception ignored) {
        }
    }

    private void undo() {
        if (undoStack.size() <= 1) return;
        try {
            String cur = undoStack.remove(undoStack.size() - 1);
            redoStack.add(cur);
            String prev = undoStack.get(undoStack.size() - 1);
            currentSettings = SettingsCodec.decode(new JSONObject(prev));
            syncUiFromState();
            triggerPreviewRender();
        } catch (Exception ignored) {
        }
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        try {
            String next = redoStack.remove(redoStack.size() - 1);
            undoStack.add(next);
            currentSettings = SettingsCodec.decode(new JSONObject(next));
            syncUiFromState();
            triggerPreviewRender();
        } catch (Exception ignored) {
        }
    }

    private void importCubeLutDialog() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Chọn tệp 3D LUT (.cube)");
        fc.setFileFilter(new FileNameExtensionFilter("3D LUT (.cube)", "cube"));
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                activeCubeLut = CubeLut.parse(reader);
                bottomStatusLabel.setText("Đã nạp 3D LUT: " + f.getName());
                triggerPreviewRender();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Không thể đọc tệp .cube: " + ex.getMessage());
            }
        }
    }

    private void copySettingsJson() {
        try {
            String json = SettingsCodec.encode(currentSettings).toString(2);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(json), null);
            bottomStatusLabel.setText("Đã sao chép JSON công thức vào Clipboard.");
        } catch (Exception ignored) {
        }
    }

    private void importSettingsJsonDialog() {
        String input = JOptionPane.showInputDialog(frame, "Dán chuỗi JSON công thức IRO vào đây:");
        if (input != null && !input.trim().isEmpty()) {
            try {
                currentSettings = SettingsCodec.decode(new JSONObject(input.trim()));
                syncUiFromState();
                pushHistorySnapshot();
                triggerPreviewRender();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Chuỗi JSON không hợp lệ: " + ex.getMessage());
            }
        }
    }

    private void setupKeyboardShortcuts() {
        JComponent root = (JComponent) frame.getContentPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK), "open");
        root.getActionMap().put("open", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { openImageDialog(); }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), "save");
        root.getActionMap().put("save", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { exportImageDialog(); }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "undo");
        root.getActionMap().put("undo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { undo(); }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK), "redo");
        root.getActionMap().put("redo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { redo(); }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.CTRL_DOWN_MASK), "auto");
        root.getActionMap().put("auto", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { runAutoBalance(); }
        });
    }

    private void loadBundledCatalogRecipes() {
        catalogRecipes.clear();
        try (InputStream in = IroDesktopApp.class.getResourceAsStream("/recipes.json")) {
            InputStream stream = in;
            if (stream == null) {
                File local = new File("app/src/main/assets/recipes.json");
                if (local.isFile()) stream = new FileInputStream(local);
            }
            if (stream != null) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line).append('\n');
                }
                JSONArray arr = new JSONArray(sb.toString());
                // Ưu tiên các công thức có sẵn sampleAsset đóng gói ở đầu danh sách
                List<Recipe> withSample = new ArrayList<>();
                List<Recipe> others = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    try {
                        Recipe r = new Recipe(arr.getJSONObject(i));
                        if (r.sampleAsset != null && !r.sampleAsset.isEmpty()) {
                            withSample.add(r);
                        } else {
                            others.add(r);
                        }
                    } catch (Exception ignored) {
                    }
                }
                catalogRecipes.addAll(withSample);
                catalogRecipes.addAll(others);
            }
        } catch (Exception ignored) {
        }
    }
}
