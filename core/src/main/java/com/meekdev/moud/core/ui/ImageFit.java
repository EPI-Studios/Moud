package com.meekdev.moud.core.ui;

import java.util.ArrayList;
import java.util.List;

public final class ImageFit {

    public record Piece(double x0, double y0, double x1, double y1, double u0, double v0, double u1, double v1) {}

    private static final int MOST_TILES = 4096;

    private ImageFit() {}

    public static List<Piece> pieces(ImageLabel image, double w, double h, int textureWidth, int textureHeight) {
        List<Piece> out = new ArrayList<>();
        if (w <= 0 || h <= 0 || textureWidth <= 0 || textureHeight <= 0) return out;
        double sx = Math.clamp(image.imageRectOffset.x(), 0, textureWidth);
        double sy = Math.clamp(image.imageRectOffset.y(), 0, textureHeight);
        double sw = image.imageRectSize.x() > 0 ? Math.min(image.imageRectSize.x(), textureWidth - sx) : textureWidth - sx;
        double sh = image.imageRectSize.y() > 0 ? Math.min(image.imageRectSize.y(), textureHeight - sy) : textureHeight - sy;
        if (sw <= 0 || sh <= 0) return out;
        Source source = new Source(sx, sy, sw, sh, textureWidth, textureHeight);
        switch (image.scaleType) {
            case STRETCH -> out.add(source.piece(0, 0, w, h, 0, 0, sw, sh));
            case FIT -> {
                double scale = Math.min(w / sw, h / sh);
                double dw = sw * scale;
                double dh = sh * scale;
                double x = (w - dw) / 2;
                double y = (h - dh) / 2;
                out.add(source.piece(x, y, x + dw, y + dh, 0, 0, sw, sh));
            }
            case CROP -> {
                double scale = Math.max(w / sw, h / sh);
                double vw = w / scale;
                double vh = h / scale;
                double ox = (sw - vw) / 2;
                double oy = (sh - vh) / 2;
                out.add(source.piece(0, 0, w, h, ox, oy, ox + vw, oy + vh));
            }
            case TILE -> tile(out, source, image, w, h);
            case SLICE -> slice(out, source, image, w, h);
        }
        return out;
    }

    private static void tile(List<Piece> out, Source source, ImageLabel image, double w, double h) {
        double tw = image.tileSize.x(w);
        double th = image.tileSize.y(h);
        if (tw <= 0.5 || th <= 0.5) return;
        if (Math.ceil(w / tw) * Math.ceil(h / th) > MOST_TILES) {
            double grow = Math.sqrt(Math.ceil(w / tw) * Math.ceil(h / th) / MOST_TILES);
            tw *= grow;
            th *= grow;
        }
        for (double y = 0; y < h; y += th) {
            for (double x = 0; x < w; x += tw) {
                double x1 = Math.min(w, x + tw);
                double y1 = Math.min(h, y + th);
                out.add(source.piece(x, y, x1, y1, 0, 0, source.w * (x1 - x) / tw, source.h * (y1 - y) / th));
            }
        }
    }

    private static void slice(List<Piece> out, Source source, ImageLabel image, double w, double h) {
        double left = Math.clamp(image.sliceMin.x(), 0, source.w);
        double top = Math.clamp(image.sliceMin.y(), 0, source.h);
        double right = Math.clamp(source.w - image.sliceMax.x(), 0, source.w - left);
        double bottom = Math.clamp(source.h - image.sliceMax.y(), 0, source.h - top);
        if (image.sliceMax.x() <= image.sliceMin.x() || image.sliceMax.y() <= image.sliceMin.y()) {
            out.add(source.piece(0, 0, w, h, 0, 0, source.w, source.h));
            return;
        }
        double scale = image.sliceScale;
        double across = (left + right) * scale;
        double down = (top + bottom) * scale;
        double shrink = Math.min(1, Math.min(across > 0 ? w / across : 1, down > 0 ? h / down : 1));
        double l = left * scale * shrink;
        double r = right * scale * shrink;
        double t = top * scale * shrink;
        double b = bottom * scale * shrink;
        double[] xs = {0, l, w - r, w};
        double[] ys = {0, t, h - b, h};
        double[] us = {0, left, source.w - right, source.w};
        double[] vs = {0, top, source.h - bottom, source.h};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (xs[column + 1] - xs[column] <= 0 || ys[row + 1] - ys[row] <= 0) continue;
                out.add(source.piece(xs[column], ys[row], xs[column + 1], ys[row + 1], us[column], vs[row], us[column + 1], vs[row + 1]));
            }
        }
    }

    private record Source(double x, double y, double w, double h, int textureWidth, int textureHeight) {

        Piece piece(double x0, double y0, double x1, double y1, double su0, double sv0, double su1, double sv1) {
            return new Piece(x0, y0, x1, y1, (x + su0) / textureWidth, (y + sv0) / textureHeight,
                    (x + su1) / textureWidth, (y + sv1) / textureHeight);
        }
    }
}
