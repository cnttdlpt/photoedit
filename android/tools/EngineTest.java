package vn.iro.editor;

import java.io.*;
import java.util.*;

/**
 * Bộ kiểm thử tự động (Unit Test) cho các mô-đun cốt lõi:
 * - {@link CubeLut}: đọc và nội suy 3D LUT (.cube)
 * - {@link ColorEngine}: pipeline xử lý điểm ảnh (EV tuyến tính, LUT, Grain, NR)
 * - {@link AutoBalancer}: thuật toán tự động cân bằng sáng & màu
 * - {@link UndoState}: quản lý lịch sử Undo/Redo
 */
public class EngineTest {
    static int checks = 0;

    static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }

    static final String IDENTITY =
        "TITLE \"Identity\"\nLUT_3D_SIZE 2\n" +
        "0 0 0\n1 0 0\n0 1 0\n1 1 0\n0 0 1\n1 0 1\n0 1 1\n1 1 1\n";

    static void invalid(String text) {
        try {
            CubeLut.parse(new StringReader(text));
            throw new AssertionError("Invalid LUT accepted");
        } catch (IOException good) {
            checks++;
        }
    }

    public static void main(String[] args) throws Exception {
        // 1. Kiểm thử CubeLut 3D trilinear
        CubeLut lut = CubeLut.parse(new StringReader(IDENTITY));
        double[] out = new double[3];
        lut.sample(.25, .67, .9, out);
        check(
            Math.abs(out[0] - .25) < 1e-6 &&
            Math.abs(out[1] - .67) < 1e-6 &&
            Math.abs(out[2] - .9) < 1e-6,
            "trilinear identity"
        );
        lut.sample(-10, 3, 0, out);
        check(Arrays.equals(out, new double[]{0, 1, 0}), "clamp outside domain");

        invalid(IDENTITY + "0 0 0");
        invalid(IDENTITY.replace("1 1 1\n", "NaN 1 1\n"));
        invalid("LUT_1D_SIZE 2");
        invalid("DOMAIN_MIN 1 0 0\n" + IDENTITY);
        invalid(IDENTITY.replace("LUT_3D_SIZE 2", "LUT_3D_SIZE 66"));

        // 2. Kiểm thử ColorEngine
        int[] p = {
            0x00124398, 0xffabcdef, 0xff020301,
            0x80526284, 0xff000000, 0xffffffff,
            0xff808080, 0xff8fa5bb, 0xff413721
        };
        check(Arrays.equals(p, ColorEngine.process(p, 3, 3, new Settings(), null)), "neutral preserves every byte and alpha");
        check(Arrays.equals(p, ColorEngine.process(p, 3, 3, new Settings(), lut)), "identity LUT preserves pixels");

        Settings s = new Settings();
        s.film = "Classic Chrome";
        check(Arrays.equals(p, ColorEngine.process(p, 3, 3, s, null)), "no fake film transform when profile is absent");

        s.exposure = 1;
        int result = ColorEngine.process(new int[]{0xff808080}, 1, 1, s, null)[0];
        check((result & 255) > 170 && (result & 255) < 180, "EV is applied in linear light");

        s = new Settings();
        s.grain = 18;
        check(Arrays.equals(ColorEngine.process(p, 3, 3, s, null), ColorEngine.process(p, 3, 3, s, null)), "grain deterministic between renders");

        s = new Settings();
        s.nr = 4;
        int[] noisy = {
            0xff000000, 0xff000000, 0xff000000,
            0xff000000, 0xffffffff, 0xff000000,
            0xff000000, 0xff000000, 0xff000000
        };
        check((ColorEngine.process(noisy, 3, 3, s, null)[4] & 255) < 255, "NR smooths a center impulse");

        // 3. Kiểm thử AutoBalancer (port từ iOS ColorEngine.swift)
        int[] darkCoolLowSat = new int[16];
        Arrays.fill(darkCoolLowSat, 0xff202840); // Ảnh tối, ám xanh lạnh, độ bão hòa thấp
        Settings balanced = AutoBalancer.analyze(darkCoolLowSat, 4, 4, new Settings());
        check(balanced.exposure > 0.0, "AutoBalancer brightens dark image");
        check(balanced.temperature > 0.0, "AutoBalancer warms cool image");
        check(balanced.clarity == 1.0 && balanced.sharpness == 1.0, "AutoBalancer adds subtle clarity and sharpness");

        // 4. Kiểm thử UndoState.History (port từ iOS EditorModel.swift)
        UndoState.History history = new UndoState.History();
        Settings s0 = new Settings();
        Settings s1 = new Settings();
        s1.exposure = 0.7;
        UndoState state0 = new UndoState(s0, null, 0, new PhotoInfo(), Collections.emptyList(), false);
        UndoState state1 = new UndoState(s1, null, 2, new PhotoInfo(), Collections.singletonList("Test"), true);
        history.record(state0);
        check(history.canUndo() && !history.canRedo(), "Undo available after change");
        UndoState restored = history.popUndo(state1);
        check(restored != null && Math.abs(restored.settings.exposure - 0.0) < 1e-6 && restored.frameStyle == 0, "Undo restores previous settings");
        check(!history.canUndo() && history.canRedo(), "Redo available after undo");
        UndoState redone = history.popRedo(restored);
        check(redone != null && Math.abs(redone.settings.exposure - 0.7) < 1e-6 && redone.frameStyle == 2 && redone.isAutoBalanced, "Redo restores target state");

        // 5. Kiểm thử hủy render đa luồng
        Thread.currentThread().interrupt();
        try {
            ColorEngine.process(p, 3, 3, new Settings(), null);
            throw new AssertionError("render not cancelled");
        } catch (java.util.concurrent.CancellationException expected) {
            checks++;
        } finally {
            Thread.interrupted();
        }

        // 6. Kiểm thử LightCraftEngine (port từ cnttdlpt/lightcraft)
        List<float[]> sCurvePts = Arrays.asList(
                new float[]{0.0f, 0.0f},
                new float[]{0.25f, 0.15f},
                new float[]{0.75f, 0.85f},
                new float[]{1.0f, 1.0f}
        );
        LightCraftEngine.MonotoneCurve mc = new LightCraftEngine.MonotoneCurve(sCurvePts);
        double prevY = -1.0;
        boolean monotoneOk = true;
        for (int i = 0; i <= 100; i++) {
            double y = mc.eval(i / 100.0);
            if (y < prevY - 1e-9 || y < 0.0 || y > 1.0) {
                monotoneOk = false;
                break;
            }
            prevY = y;
        }
        check(monotoneOk, "LightCraft MonotoneCurve Fritsch-Carlson preserves monotonicity without overshoot");

        LightCraftEngine.State lcState = new LightCraftEngine.State();
        check(lcState.isIdentity(), "LightCraft default state is identity");
        check(LightCraftEngine.PRESETS.size() == 12, "LightCraft includes 12 built-in Darkroom presets");
        LightCraftEngine.PRESETS.get(0).applyTo(lcState, new Settings());
        check(!lcState.isIdentity(), "LightCraft Teal & Orange preset modifies grading state");
        float[][] luts = LightCraftEngine.buildToneCurveLuts(lcState, 256);
        check(luts.length == 4 && luts[0].length == 256, "LightCraft buildToneCurveLuts generates 4x256 LUTs");

        System.out.println("PASS: " + checks + " native engine checks");
    }
}
