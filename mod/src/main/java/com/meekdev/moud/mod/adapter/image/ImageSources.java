package com.meekdev.moud.mod.adapter.image;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.image.Blend;
import com.meekdev.moud.core.image.EditableImage;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.features.Feature;
import com.meekdev.moud.mod.place.PlaceToml;
import com.meekdev.moud.script.api.ImagesRef;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

public class ImageSources implements ImagesRef {

    public static final ImageSources SERVER = new ImageSources(PlaceToml::root);

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int LARGEST_FILE = 16 * 1024 * 1024;

    private final Supplier<Path> root;
    private HttpClient client;

    protected ImageSources(Supplier<Path> root) {
        this.root = root;
    }

    @Override
    public CompletableFuture<EditableImage> load(String source) {
        if (source.startsWith(Res.SCHEME)) {
            Path file;
            try {
                file = root.get().resolve(Res.parse(source));
            } catch (IllegalArgumentException e) {
                return CompletableFuture.failedFuture(e);
            }
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (!Files.isRegularFile(file)) throw new IllegalArgumentException("there is no " + source);
                    return decode(Files.readAllBytes(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        if (source.startsWith("https://") || source.startsWith("http://")) return web(source);
        return other(source);
    }

    protected CompletableFuture<EditableImage> other(String source) {
        return CompletableFuture.failedFuture(new IllegalArgumentException(
                "a server Script loads res:// files and web addresses, a LocalScript also loads Minecraft textures, not " + source));
    }

    private CompletableFuture<EditableImage> web(String url) {
        if (!MoudMod.features().isOn(Feature.HTTP_REQUESTS)) {
            return CompletableFuture.failedFuture(new IllegalStateException("images from the web need httpRequests on in place.toml [features]"));
        }
        if (client == null) client = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("the address answered " + response.statusCode());
            if (response.body().length > LARGEST_FILE) throw new IllegalStateException("the image is larger than 16 MB");
            return decode(response.body());
        });
    }

    public static EditableImage decode(byte[] bytes) {
        BufferedImage read;
        try {
            read = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (read == null) throw new IllegalArgumentException("that is not a png, jpeg, gif or bmp image");
        int w = read.getWidth();
        int h = read.getHeight();
        if (w > EditableImage.LARGEST || h > EditableImage.LARGEST) {
            double scale = Math.min((double) EditableImage.LARGEST / w, (double) EditableImage.LARGEST / h);
            int sw = Math.max(1, (int) Math.floor(w * scale));
            int sh = Math.max(1, (int) Math.floor(h * scale));
            BufferedImage smaller = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = smaller.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(read, 0, 0, sw, sh, null);
            g.dispose();
            read = smaller;
            w = sw;
            h = sh;
        }
        return EditableImage.of(w, h, read.getRGB(0, 0, w, h, null, 0, w));
    }

    public static EditableImage head(EditableImage skin) {
        int unit = Math.max(1, skin.width() / 64);
        EditableImage face = skin.crop(8 * unit, 8 * unit, 8 * unit, 8 * unit);
        face.image(skin, 40 * unit, 8 * unit, 8 * unit, 8 * unit, 0, 0, 8 * unit, 8 * unit, 0, Blend.OVER, false);
        return face;
    }
}
