package com.meekdev.moud.script.host.render;

import com.meekdev.moud.core.image.Blend;
import com.meekdev.moud.core.image.EditableImage;
import com.meekdev.moud.core.image.ImageStore;
import com.meekdev.moud.core.image.Paint;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.script.api.ImagesRef;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostObject;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.Results;
import com.meekdev.moud.script.host.Suspend;
import com.meekdev.moud.script.host.player.Players;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class ImageLibrary {

    private static final String BLEND = "\"over\" | \"replace\" | \"add\" | \"multiply\" | \"erase\"";
    private static final int MOST_PIXELS_READ = EditableImage.LARGEST * EditableImage.LARGEST;

    private ImageLibrary() {}

    public record Image(Host host, int id, EditableImage pixels) implements HostObject {

        private static final Members METHODS = new Members("EditableImage")
                .declare("width", "number")
                .declare("height", "number")
                .declare("uri", "string")
                .method("setPixel", "(x: number, y: number, color: Color, transparency: number?) -> ()", a -> {
                    a.self(Image.class).live().setPixel(a.integer(1), a.integer(2), Paint.of(a.color(3), a.number(4, 0), Blend.REPLACE));
                    return null;
                })
                .method("getPixel", "(x: number, y: number) -> (Color, number)", a -> {
                    int value = a.self(Image.class).live().get(a.integer(1), a.integer(2));
                    return Results.of(EditableImage.color(value), 1.0 - EditableImage.alpha(value));
                })
                .method("fill", "(color: Color, transparency: number?) -> ()", a -> {
                    a.self(Image.class).live().fill(Paint.of(a.color(1), a.number(2, 0), Blend.REPLACE));
                    return null;
                })
                .method("clear", "() -> ()", a -> {
                    a.self(Image.class).live().fill(Paint.of(Color.CLEAR, 0, Blend.REPLACE));
                    return null;
                })
                .method("drawRectangle", "(x: number, y: number, width: number, height: number, color: Color, options: DrawOptions?) -> ()", a -> {
                    Style style = Style.of(a, 6, a.color(5));
                    a.self(Image.class).live().rectangle(a.number(1), a.number(2), a.number(3), a.number(4), style.paint(), style.filled(), style.thickness(), style.corner(), style.smooth());
                    return null;
                })
                .method("drawCircle", "(x: number, y: number, radius: number, color: Color, options: DrawOptions?) -> ()", a -> {
                    Style style = Style.of(a, 5, a.color(4));
                    a.self(Image.class).live().circle(a.number(1), a.number(2), a.number(3), style.paint(), style.filled(), style.thickness(), style.smooth());
                    return null;
                })
                .method("drawEllipse", "(x: number, y: number, radiusX: number, radiusY: number, color: Color, options: DrawOptions?) -> ()", a -> {
                    Style style = Style.of(a, 6, a.color(5));
                    a.self(Image.class).live().ellipse(a.number(1), a.number(2), a.number(3), a.number(4), style.paint(), style.filled(), style.thickness(), style.smooth());
                    return null;
                })
                .method("drawLine", "(x1: number, y1: number, x2: number, y2: number, color: Color, options: DrawOptions?) -> ()", a -> {
                    Style style = Style.of(a, 6, a.color(5));
                    a.self(Image.class).live().line(a.number(1), a.number(2), a.number(3), a.number(4), style.paint(), style.thickness(), style.smooth());
                    return null;
                })
                .method("drawPolygon", "(points: { Vector3 } | { number }, color: Color, options: DrawOptions?) -> ()", a -> {
                    Style style = Style.of(a, 3, a.color(2));
                    a.self(Image.class).live().polygon(points(a.list(1)), style.paint(), style.smooth());
                    return null;
                })
                .method("drawGradient", "(x: number, y: number, width: number, height: number, from: Color, to: Color, options: GradientOptions?) -> ()", a -> {
                    Map<String, Object> options = a.map(7, Map.of());
                    Blend blend = blend(options.get("blend"));
                    a.self(Image.class).live().gradient(a.number(1), a.number(2), a.number(3), a.number(4),
                            Paint.of(a.color(5), number(options, "fromTransparency", 0), blend),
                            Paint.of(a.color(6), number(options, "toTransparency", 0), blend), number(options, "rotation", 0));
                    return null;
                })
                .method("drawImage", "(image: EditableImage, x: number, y: number, options: ImageDrawOptions?) -> ()", a -> {
                    EditableImage target = a.self(Image.class).live();
                    if (!(a.get(1) instanceof Image other)) throw new HostError("drawImage expects an EditableImage");
                    EditableImage source = other.live();
                    Map<String, Object> options = a.map(4, Map.of());
                    int sx = (int) number(options, "sourceX", 0);
                    int sy = (int) number(options, "sourceY", 0);
                    int sw = (int) number(options, "sourceWidth", source.width() - sx);
                    int sh = (int) number(options, "sourceHeight", source.height() - sy);
                    target.image(source, sx, sy, sw, sh, a.number(2), a.number(3), number(options, "width", sw), number(options, "height", sh),
                            number(options, "transparency", 0), blend(options.get("blend")), Boolean.TRUE.equals(options.get("smooth")));
                    return null;
                })
                .method("floodFill", "(x: number, y: number, color: Color, options: FillOptions?) -> number", a -> {
                    Map<String, Object> options = a.map(4, Map.of());
                    return (double) a.self(Image.class).live().flood(a.integer(1), a.integer(2),
                            Paint.of(a.color(3), number(options, "transparency", 0), blend(options.get("blend"))), number(options, "tolerance", 0));
                })
                .method("readPixels", "(x: number?, y: number?, width: number?, height: number?) -> { number }", a -> {
                    EditableImage image = a.self(Image.class).live();
                    int x = a.integer(1, 0);
                    int y = a.integer(2, 0);
                    int w = a.integer(3, image.width() - x);
                    int h = a.integer(4, image.height() - y);
                    if (w < 0 || h < 0 || (long) w * h > MOST_PIXELS_READ) throw new HostError("readPixels was asked for %d by %d pixels", w, h);
                    List<Object> out = new ArrayList<>(w * h * 4);
                    for (int py = y; py < y + h; py++) {
                        for (int px = x; px < x + w; px++) {
                            int value = image.get(px, py);
                            out.add(((value >> 16) & 0xFF) / 255.0);
                            out.add(((value >> 8) & 0xFF) / 255.0);
                            out.add((value & 0xFF) / 255.0);
                            out.add(((value >>> 24) & 0xFF) / 255.0);
                        }
                    }
                    return out;
                })
                .method("writePixels", "(x: number, y: number, width: number, height: number, pixels: { number }) -> ()", a -> {
                    EditableImage image = a.self(Image.class).live();
                    int x = a.integer(1);
                    int y = a.integer(2);
                    int w = a.integer(3);
                    int h = a.integer(4);
                    List<Object> pixels = a.list(5);
                    if (w < 0 || h < 0 || pixels.size() != (long) w * h * 4) {
                        throw new HostError("writePixels wants %d numbers for %d by %d pixels, got %d", Math.max(0, w * h * 4), w, h, pixels.size());
                    }
                    int at = 0;
                    for (int py = y; py < y + h; py++) {
                        for (int px = x; px < x + w; px++) {
                            float r = channel(pixels.get(at++));
                            float g = channel(pixels.get(at++));
                            float b = channel(pixels.get(at++));
                            float alpha = channel(pixels.get(at++));
                            if (px >= 0 && py >= 0 && px < image.width() && py < image.height()) {
                                image.pixels()[py * image.width() + px] = EditableImage.pack(r, g, b, alpha);
                            }
                        }
                    }
                    image.touched();
                    return null;
                })
                .method("resize", "(width: number, height: number, smooth: boolean?) -> ()", a -> {
                    try {
                        a.self(Image.class).live().resize(a.integer(1), a.integer(2), a.has(3) && a.truthy(3));
                    } catch (IllegalArgumentException e) {
                        throw new HostError("%s", e.getMessage());
                    }
                    return null;
                })
                .method("crop", "(x: number, y: number, width: number, height: number) -> EditableImage", a -> {
                    Image self = a.self(Image.class);
                    try {
                        return keep(self.host(), self.live().crop(a.integer(1), a.integer(2), a.integer(3), a.integer(4)));
                    } catch (IllegalArgumentException e) {
                        throw new HostError("%s", e.getMessage());
                    }
                })
                .method("copy", "() -> EditableImage", a -> {
                    Image self = a.self(Image.class);
                    return keep(self.host(), self.live().copy());
                })
                .method("flip", "(direction: \"horizontal\" | \"vertical\") -> ()", a -> {
                    String direction = a.string(1);
                    if (!direction.equals("horizontal") && !direction.equals("vertical")) throw new HostError("flip takes \"horizontal\" or \"vertical\", got %s", direction);
                    a.self(Image.class).live().flip(direction.equals("horizontal"));
                    return null;
                })
                .method("rotate", "(quarterTurns: number) -> ()", a -> {
                    a.self(Image.class).live().rotate(a.integer(1));
                    return null;
                })
                .method("destroy", "() -> ()", a -> {
                    a.self(Image.class).destroy();
                    return null;
                });

        EditableImage live() {
            if (ImageStore.find(id) != pixels) throw new HostError("this image was destroyed");
            return pixels;
        }

        void destroy() {
            ImageStore.remove(id);
        }

        public String uri() {
            return ImageStore.uri(id);
        }

        @Override
        public String typeName() {
            return "EditableImage";
        }

        @Override
        public Object get(String key) {
            return switch (key) {
                case "width" -> (double) pixels.width();
                case "height" -> (double) pixels.height();
                case "uri" -> uri();
                default -> METHODS.get(key);
            };
        }
    }

    private record Style(Paint paint, boolean filled, double thickness, double corner, boolean smooth) {

        static Style of(Args a, int at, Color color) {
            Map<String, Object> options = a.map(at, Map.of());
            return new Style(Paint.of(color, number(options, "transparency", 0), blend(options.get("blend"))),
                    !Boolean.FALSE.equals(options.get("filled")), number(options, "thickness", 1),
                    number(options, "cornerRadius", 0), Boolean.TRUE.equals(options.get("smooth")));
        }
    }

    public static void install(Host host) {
        Set<Integer> owned = new HashSet<>();
        host.api().alias("ImageBlend", BLEND);
        host.api().alias("DrawOptions", "{ transparency: number?, blend: ImageBlend?, smooth: boolean?, filled: boolean?, thickness: number?, cornerRadius: number? }");
        host.api().alias("GradientOptions", "{ rotation: number?, fromTransparency: number?, toTransparency: number?, blend: ImageBlend? }");
        host.api().alias("ImageDrawOptions", "{ width: number?, height: number?, sourceX: number?, sourceY: number?, sourceWidth: number?, sourceHeight: number?, transparency: number?, blend: ImageBlend?, smooth: boolean? }");
        host.api().alias("FillOptions", "{ tolerance: number?, transparency: number?, blend: ImageBlend? }");
        host.api().declare(Image.METHODS.decl());
        Members images = new Members("Images")
                .function("create", "(width: number, height: number, color: Color?, transparency: number?) -> EditableImage", a -> {
                    EditableImage image = made(a.integer(0), a.integer(1));
                    if (a.has(2)) image.fill(Paint.of(a.color(2), a.number(3, 0), Blend.REPLACE));
                    return track(host, owned, image);
                })
                .function("fromPixels", "(width: number, height: number, pixels: { number }) -> EditableImage", a -> {
                    int w = a.integer(0);
                    int h = a.integer(1);
                    List<Object> pixels = a.list(2);
                    if (pixels.size() != (long) w * h * 4) throw new HostError("fromPixels wants %d numbers for %d by %d pixels, got %d", w * h * 4, w, h, pixels.size());
                    EditableImage image = made(w, h);
                    for (int n = 0; n < w * h; n++) {
                        image.pixels()[n] = EditableImage.pack(channel(pixels.get(n * 4)), channel(pixels.get(n * 4 + 1)), channel(pixels.get(n * 4 + 2)), channel(pixels.get(n * 4 + 3)));
                    }
                    image.touched();
                    return track(host, owned, image);
                })
                .function("load", "(source: string) -> EditableImage", a -> waitFor(host, owned, loader(host).load(a.string(0)), "load " + a.string(0)))
                .function("skin", "(player: any, part: (\"head\" | \"full\")?) -> EditableImage", a -> {
                    String id = Players.idOf(a.get(0));
                    if (id == null) throw new HostError("images.skin expects a Player or a body with a player");
                    String part = a.string(1, "full");
                    if (!part.equals("head") && !part.equals("full")) throw new HostError("images.skin takes \"head\" or \"full\", got %s", part);
                    return waitFor(host, owned, loader(host).skin(id, part.equals("head")), "read the skin of " + id);
                });
        host.global("images", "Images", images);
        host.declare(images);
        host.onClose(() -> {
            for (int id : owned) ImageStore.remove(id);
            owned.clear();
        });
    }

    private static Image keep(Host host, EditableImage image) {
        return new Image(host, ImageStore.add(image), image);
    }

    private static Image track(Host host, Set<Integer> owned, EditableImage image) {
        Image made = keep(host, image);
        owned.add(made.id());
        return made;
    }

    private static EditableImage made(int width, int height) {
        try {
            return new EditableImage(width, height);
        } catch (IllegalArgumentException e) {
            throw new HostError("%s", e.getMessage());
        }
    }

    private static ImagesRef loader(Host host) {
        if (host.images() == null) throw new HostError("images can not be loaded here");
        return host.images();
    }

    private static Suspend waitFor(Host host, Set<Integer> owned, CompletableFuture<EditableImage> future, String what) {
        return new Suspend(dt -> {
            if (!future.isDone()) return null;
            try {
                return new Object[] {track(host, owned, future.join())};
            } catch (CompletionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                return Suspend.failed("could not %s: %s", what, cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
            }
        }, () -> future.cancel(true));
    }

    static Blend blend(Object value) {
        if (value == null) return Blend.OVER;
        if (!(value instanceof String name)) throw new HostError("blend is one of %s", BLEND);
        try {
            return Blend.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new HostError("blend is one of %s, not %s", BLEND, name);
        }
    }

    static double number(Map<String, Object> options, String key, double fallback) {
        Object value = options.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) throw new HostError("%s must be a number", key);
        return number.doubleValue();
    }

    private static float channel(Object value) {
        if (!(value instanceof Number number)) throw new HostError("pixels are numbers from 0 to 1");
        return (float) Math.clamp(number.doubleValue(), 0, 1);
    }

    private static List<double[]> points(List<Object> given) {
        List<double[]> points = new ArrayList<>();
        if (!given.isEmpty() && given.getFirst() instanceof Number) {
            if (given.size() % 2 != 0) throw new HostError("drawPolygon takes x, y pairs");
            for (int n = 0; n < given.size(); n += 2) {
                if (!(given.get(n) instanceof Number x) || !(given.get(n + 1) instanceof Number y)) throw new HostError("drawPolygon takes numbers or Vector3s");
                points.add(new double[] {x.doubleValue(), y.doubleValue()});
            }
            return points;
        }
        for (Object point : given) {
            if (!(point instanceof Vector3 v)) throw new HostError("drawPolygon takes numbers or Vector3s");
            points.add(new double[] {v.x(), v.y()});
        }
        return points;
    }
}
