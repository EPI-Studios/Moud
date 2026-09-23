package com.meekdev.moud.mod.client.debug;

import com.meekdev.amnetic.client.pipeline.PassProfiler;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.text.RichText;
import com.meekdev.moud.core.ui.HorizontalAlign;
import com.meekdev.moud.mod.adapter.text.TextLook;
import com.meekdev.moud.mod.adapter.text.TextPainter;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class FrameView {

    private static final int ROWS = 16;
    private static final float WIDTH = 185;
    private static final float ROW = 9;
    private static final int CPU = 0xFF73B3FF;
    private static final int GPU = 0xFFFF9940;
    private static final TextLook WHITE = new TextLook(Color.WHITE, "", true, Color.BLACK, 0);
    private static final TextLook DIM = new TextLook(new Color(0.7f, 0.7f, 0.7f, 1), "", true, Color.BLACK, 0);

    private static KeyMapping toggle;
    private static boolean on;

    private FrameView() {}

    public static void register(KeyMapping.Category category) {
        toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.moud.passes",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, category));
    }

    public static void install() {
        Surfaces.hud().onDraw(FrameView::draw);
    }

    public static void show(boolean shown) {
        on = shown;
    }

    public static void tick() {
        while (toggle != null && toggle.consumeClick()) on = !on;
    }

    private static void draw(UiDraw d) {
        if (!on) return;
        PassProfiler profiler = PassProfiler.INSTANCE;
        profiler.watch();
        List<PassProfiler.Entry> entries = profiler.sorted();
        float frame = profiler.frameMs();
        float largest = 0;
        for (PassProfiler.Entry e : entries) largest = Math.max(largest, e.costMs());
        float scale = Math.max(frame, largest);
        int shown = Math.min(ROWS, entries.size());

        float x = 4, y = 4;
        d.rect(x, y, WIDTH, ROW * (shown + 1) + 5, 0xA0000000);
        String head = frame > 0 ? String.format("frame %.2f ms  %.0f fps", frame, 1000 / frame) : "frame -";
        text(d, head, x + 3, y + 2, WIDTH - 6, WHITE, HorizontalAlign.LEFT);
        column(d, "cpu", "gpu", x, y + 2, DIM);
        y += ROW + 2;
        for (int i = 0; i < shown; i++) {
            PassProfiler.Entry e = entries.get(i);
            text(d, e.label, x + 3, y, WIDTH - 66, WHITE, HorizontalAlign.LEFT);
            column(d, String.format("%.2f", e.avgMs), e.avgGpuMs >= 0 ? String.format("%.2f", e.avgGpuMs) : "-", x, y, DIM);
            if (scale > 0) {
                d.rect(x + 3, y + 7, (WIDTH - 6) * Math.min(1, e.avgMs / scale), 0.7f, CPU);
                if (e.avgGpuMs > 0) d.rect(x + 3, y + 7.8f, (WIDTH - 6) * Math.min(1, e.avgGpuMs / scale), 0.7f, GPU);
            }
            y += ROW;
        }
    }

    private static void column(UiDraw d, String cpu, String gpu, float x, float y, TextLook look) {
        text(d, cpu, x + WIDTH - 63, y, 28, look, HorizontalAlign.RIGHT);
        text(d, gpu, x + WIDTH - 33, y, 28, look, HorizontalAlign.RIGHT);
    }

    private static void text(UiDraw d, String line, float x, float y, float width, TextLook look, HorizontalAlign align) {
        TextPainter.draw(d, RichText.escape(line), x, y, width, 6, look, 1, align);
    }
}
