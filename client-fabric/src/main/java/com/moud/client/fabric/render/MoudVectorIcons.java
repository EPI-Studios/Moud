package com.moud.client.fabric.render;

import com.miry.ui.render.UiRenderer;
import com.miry.ui.vector.FilledVectorIcon;
import com.miry.ui.vector.SvgPath;
import com.miry.ui.vector.VectorIcon;
import com.miry.ui.vector.VectorPath;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MoudVectorIcons {

    private static final Pattern PATH_D = Pattern.compile("\\sd=\"([^\"]+)\"");
    private static final Pattern VIEW_W = Pattern.compile("\\swidth=\"(\\d+(?:\\.\\d+)?)\"");
    private static final Pattern VIEW_H = Pattern.compile("\\sheight=\"(\\d+(?:\\.\\d+)?)\"");
    private static final Pattern STROKE_W = Pattern.compile("stroke-width=\"(\\d+(?:\\.\\d+)?)\"");
    private static final Pattern STROKE_ATTR = Pattern.compile("\\sstroke=\"([^\"]+)\"");
    private static final Pattern FILL_ATTR = Pattern.compile("\\sfill=\"([^\"]+)\"");

    private static final Map<String, Loaded> cache = new HashMap<>();

    private MoudVectorIcons() {}

    public static void drawCentered(UiRenderer r, String name, String resourcePath,
                                    float bx, float by, float bw, float bh, float size, int argb) {
        Loaded loaded = load(name, resourcePath);
        if (r == null || loaded == null) return;
        float x = bx + (bw - size) * 0.5f;
        float y = by + (bh - size) * 0.5f;
        if (loaded.stroked) {
            float strokePx = Math.max(1f, loaded.strokeWidth * (size / loaded.viewW));
            loaded.icon.drawStroke(r, x, y, size, strokePx, argb);
        } else {
            loaded.filled.draw(r, x, y, size, 0f, argb, 0);
        }
    }

    private static Loaded load(String name, String resourcePath) {
        Loaded existing = cache.get(name);
        if (existing != null) return existing;
        try (InputStream is = MoudVectorIcons.class.getResourceAsStream(resourcePath)) {
            if (is == null) return null;
            String svg = new String(is.readAllBytes());
            Loaded loaded = parse(svg);
            if (loaded != null) cache.put(name, loaded);
            return loaded;
        } catch (IOException e) {
            return null;
        }
    }

    private static Loaded parse(String svg) {
        Matcher dm = PATH_D.matcher(svg);
        if (!dm.find()) return null;
        String d = dm.group(1);
        float vw = matchFloat(VIEW_W, svg, 16f);
        float vh = matchFloat(VIEW_H, svg, 16f);
        VectorPath path = SvgPath.parseAndFlatten(d, 0.5f);

        boolean hasStroke = STROKE_ATTR.matcher(svg).find();
        Matcher fm = FILL_ATTR.matcher(svg);
        boolean fillNone = fm.find() && "none".equals(fm.group(1));
        boolean stroked = hasStroke && fillNone;
        float strokeWidth = matchFloat(STROKE_W, svg, 1f);

        Loaded loaded = new Loaded();
        loaded.viewW = vw;
        loaded.viewH = vh;
        loaded.stroked = stroked;
        loaded.strokeWidth = strokeWidth;
        if (stroked) {
            loaded.icon = new VectorIcon(vw, vh, path);
        } else {
            loaded.filled = new FilledVectorIcon(vw, vh, path);
        }
        return loaded;
    }

    private static float matchFloat(Pattern p, String s, float fallback) {
        Matcher m = p.matcher(s);
        if (!m.find()) return fallback;
        try { return Float.parseFloat(m.group(1)); } catch (NumberFormatException e) { return fallback; }
    }

    private static final class Loaded {
        float viewW;
        float viewH;
        boolean stroked;
        float strokeWidth;
        FilledVectorIcon filled;
        VectorIcon icon;
    }
}
