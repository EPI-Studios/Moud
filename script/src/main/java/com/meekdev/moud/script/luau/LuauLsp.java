package com.meekdev.moud.script.luau;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class LuauLsp {

    private static final String INITIALIZE = "moud-initialize";
    private static final int CONTENT_MODIFIED = -32801;
    private static final long SETTLE = 300;
    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private final List<String> command;
    private final List<Path> definitions;
    private final OutputStream out = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out));
    private final Map<String, Doc> docs = new LinkedHashMap<>();
    private final Map<String, Object> pending = new LinkedHashMap<>();
    private final Map<Long, Relay> relays = new ConcurrentHashMap<>();
    private final Set<String> registrations = ConcurrentHashMap.newKeySet();
    private final AtomicLong ids = new AtomicLong();
    private final List<Map<String, Object>> replay = new ArrayList<>();
    private Map<String, Object> initialize;
    private String initializeId;
    private Map<String, Object> initialized;
    private Map<String, Object> configuration;
    private String encoding = "utf-16";
    private volatile Child child;
    private int generation;

    private LuauLsp(List<String> command, List<Path> definitions) {
        this.command = command;
        this.definitions = definitions;
    }

    public static void main(String[] args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(binary());
        command.addAll(Arrays.asList(args));
        new LuauLsp(command, definitions(args)).run();
    }

    private void run() throws IOException {
        child = spawn();
        Thread watcher = new Thread(this::watch, "luau-lsp-watch");
        watcher.setDaemon(true);
        watcher.start();
        InputStream in = new BufferedInputStream(new FileInputStream(FileDescriptor.in));
        while (true) {
            String body = Frame.read(in);
            if (body == null) break;
            fromClient(Json.object(body));
        }
        child.process.destroy();
        System.exit(0);
    }

    private synchronized void fromClient(Map<String, Object> message) {
        Object id = message.get("id");
        String method = (String) message.get("method");
        if (method == null) {
            answer(id, message);
            return;
        }
        if (message.containsKey("id")) {
            if (method.equals("initialize")) {
                initialize = object(message.get("params"));
                initializeId = key(id);
            }
            pending.put(key(id), id);
        } else {
            remember(method, message);
        }
        child.send(message);
        if (method.equals("exit")) exit();
    }

    private void answer(Object id, Map<String, Object> message) {
        Relay relay = id instanceof Json.Num number ? relays.remove(number.longValue()) : null;
        if (relay == null || relay.generation != child.generation) return;
        message.put("id", relay.id);
        child.send(message);
    }

    private void remember(String method, Map<String, Object> message) {
        Map<String, Object> params = object(message.get("params"));
        switch (method) {
            case "initialized" -> initialized = message;
            case "workspace/didChangeConfiguration" -> configuration = message;
            case "$/setTrace", "workspace/didChangeWorkspaceFolders" -> replay.add(message);
            case "textDocument/didOpen" -> {
                Map<String, Object> document = object(params.get("textDocument"));
                docs.put((String) document.get("uri"), new Doc(document.get("languageId"), document.get("version"), (String) document.get("text")));
            }
            case "textDocument/didClose" -> docs.remove((String) object(params.get("textDocument")).get("uri"));
            case "textDocument/didChange" -> {
                Map<String, Object> document = object(params.get("textDocument"));
                Doc doc = docs.get((String) document.get("uri"));
                if (doc == null) return;
                doc.version = document.get("version");
                for (Object change : (List<?>) params.get("contentChanges")) doc.text = edit(doc.text, object(change));
            }
            default -> {}
        }
    }

    private String edit(String text, Map<String, Object> change) {
        String replacement = (String) change.get("text");
        Map<String, Object> range = object(change.get("range"));
        if (range == null) return replacement;
        int start = offset(text, object(range.get("start")));
        int end = offset(text, object(range.get("end")));
        return text.substring(0, start) + replacement + text.substring(Math.max(start, end));
    }

    private int offset(String text, Map<String, Object> position) {
        int line = (int) ((Json.Num) position.get("line")).longValue();
        long character = ((Json.Num) position.get("character")).longValue();
        int at = 0;
        for (int i = 0; i < line && at >= 0; i++) {
            at = text.indexOf('\n', at);
            if (at >= 0) at++;
        }
        if (at < 0) return text.length();
        int lineEnd = text.indexOf('\n', at);
        if (lineEnd < 0) lineEnd = text.length();
        if (encoding.equals("utf-16")) return (int) Math.min(at + character, lineEnd);
        while (at < lineEnd && character > 0) {
            int point = text.codePointAt(at);
            character -= encoding.equals("utf-8") ? new String(Character.toChars(point)).getBytes(StandardCharsets.UTF_8).length : 1;
            at += Character.charCount(point);
        }
        return at;
    }

    private void fromServer(Child from, Map<String, Object> message) {
        if (from != child) return;
        Object id = message.get("id");
        String method = (String) message.get("method");
        if (method == null) {
            if (INITIALIZE.equals(id)) {
                from.ready.complete(message);
                return;
            }
            synchronized (this) {
                if (from != child) return;
                pending.remove(key(id));
                if (key(id).equals(initializeId) && message.get("result") != null) fullSync(message);
                send(message);
            }
            return;
        }
        if (message.containsKey("id")) {
            if (registered(from, method, message)) return;
            long relayed = ids.incrementAndGet();
            relays.put(relayed, new Relay(from.generation, id));
            message.put("id", new Json.Num(Long.toString(relayed)));
            send(message);
            return;
        }
        if (method.equals("window/showMessage") && String.valueOf(object(message.get("params")).get("message")).contains("reload your workspace")) return;
        if (method.equals("$/progress")) from.progress(object(message.get("params")));
        send(message);
    }

    private void fullSync(Map<String, Object> response) {
        Map<String, Object> capabilities = object(object(response.get("result")).get("capabilities"));
        if (capabilities == null) return;
        if (capabilities.get("positionEncoding") instanceof String negotiated) encoding = negotiated;
        Object sync = capabilities.get("textDocumentSync");
        if (sync instanceof Map<?, ?>) object(sync).put("change", new Json.Num("1"));
        else capabilities.put("textDocumentSync", new Json.Num("1"));
    }

    private boolean registered(Child from, String method, Map<String, Object> message) {
        Map<String, Object> params = object(message.get("params"));
        String list = method.equals("client/registerCapability") ? "registrations" : method.equals("client/unregisterCapability") ? "unregisterations" : null;
        if (list == null || params == null || !(params.get(list) instanceof List<?> entries)) return false;
        boolean known = true;
        for (Object entry : entries) {
            String id = String.valueOf(object(entry).get("id"));
            if (list.equals("registrations")) known &= !registrations.add(id);
            else registrations.remove(id);
        }
        if (!known || from.generation == 0 || list.equals("unregisterations")) return false;
        Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("jsonrpc", "2.0");
        reply.put("id", message.get("id"));
        reply.put("result", null);
        from.send(reply);
        return true;
    }

    private void watch() {
        Map<Path, String> seen = new LinkedHashMap<>();
        Map<Path, String> stamps = new LinkedHashMap<>();
        for (Path file : definitions) {
            seen.put(file, read(file));
            stamps.put(file, stamp(file));
        }
        long changed = 0;
        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
            for (Path file : definitions) {
                String stamp = stamp(file);
                if (stamp.equals(stamps.get(file))) continue;
                stamps.put(file, stamp);
                changed = System.currentTimeMillis();
            }
            if (changed == 0 || System.currentTimeMillis() - changed < SETTLE) continue;
            changed = 0;
            boolean different = false;
            for (Path file : definitions) {
                String text = read(file);
                if (text.equals(seen.get(file))) continue;
                seen.put(file, text);
                different = true;
            }
            if (different) restart();
        }
    }

    private void restart() {
        List<Map<String, Object>> refresh = new ArrayList<>();
        synchronized (this) {
            if (initialize == null || initialized == null) return;
            Child old = child;
            Child next;
            try {
                next = spawn();
            } catch (IOException e) {
                System.err.println("luau-lsp did not restart: " + e.getMessage());
                return;
            }
            child = next;
            old.process.destroy();
            for (String token : old.working) send(notification("$/progress", Map.of("token", token, "value", Map.of("kind", "end"))));
            for (Object id : pending.values()) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("jsonrpc", "2.0");
                error.put("id", id);
                error.put("error", Map.of("code", new Json.Num(Integer.toString(CONTENT_MODIFIED)), "message", "luau types changed"));
                send(error);
            }
            pending.clear();
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", INITIALIZE);
            request.put("method", "initialize");
            request.put("params", initialize);
            next.send(request);
            try {
                next.ready.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.err.println("luau-lsp did not answer initialize");
            }
            next.send(initialized);
            for (Map<String, Object> message : replay) next.send(message);
            if (configuration != null) next.send(configuration);
            for (Map.Entry<String, Doc> entry : docs.entrySet()) {
                Doc doc = entry.getValue();
                Map<String, Object> document = new LinkedHashMap<>();
                document.put("uri", entry.getKey());
                document.put("languageId", doc.language);
                document.put("version", doc.version);
                document.put("text", doc.text);
                next.send(notification("textDocument/didOpen", Map.of("textDocument", document)));
            }
            Map<String, Object> workspace = object(object(initialize.get("capabilities")).get("workspace"));
            if (workspace != null) {
                for (String capability : List.of("diagnostics", "semanticTokens", "inlayHint", "codeLens")) {
                    Map<String, Object> support = object(workspace.get(capability));
                    if (support == null || !Boolean.TRUE.equals(support.get("refreshSupport"))) continue;
                    String method = "workspace/" + (capability.equals("diagnostics") ? "diagnostic" : capability) + "/refresh";
                    long relayed = ids.incrementAndGet();
                    relays.put(relayed, new Relay(-1, null));
                    Map<String, Object> message = new LinkedHashMap<>();
                    message.put("jsonrpc", "2.0");
                    message.put("id", new Json.Num(Long.toString(relayed)));
                    message.put("method", method);
                    refresh.add(message);
                }
            }
        }
        for (Map<String, Object> message : refresh) send(message);
    }

    private Child spawn() throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT);
        Child next = new Child(builder.start(), generation++);
        Thread reader = new Thread(() -> next.read(this), "luau-lsp-" + next.generation);
        reader.setDaemon(true);
        reader.start();
        return next;
    }

    private void died(Child from) {
        if (from != child) return;
        synchronized (this) {
            if (from != child) return;
            try {
                out.flush();
            } catch (IOException ignored) {
            }
            System.exit(from.process.exitValue());
        }
    }

    private void exit() {
        Child last = child;
        try {
            last.process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        last.process.destroy();
        System.exit(0);
    }

    private void send(Map<String, Object> message) {
        synchronized (out) {
            try {
                Frame.write(out, Json.write(message));
            } catch (IOException e) {
                System.exit(0);
            }
        }
    }

    private static Map<String, Object> notification(String method, Object params) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("method", method);
        message.put("params", params);
        return message;
    }

    private static String key(Object id) {
        return id instanceof String text ? "s" + text : "n" + id;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            return "";
        }
    }

    private static String stamp(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis() + ":" + Files.size(file);
        } catch (IOException e) {
            return "";
        }
    }

    private static List<Path> definitions(String[] args) {
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--definitions")) continue;
            String value = args[i].length() > 13 ? args[i].substring(14) : i + 1 < args.length ? args[++i] : "";
            int split = value.indexOf('=');
            files.add(Path.of(split < 0 ? value : value.substring(split + 1)).toAbsolutePath());
        }
        return files;
    }

    private static String binary() {
        String given = System.getenv("MOUD_LUAU_LSP");
        if (given != null && !given.isBlank()) return given;
        String exe = WINDOWS ? ".exe" : "";
        Path home = Path.of(System.getProperty("user.home"));
        List<Path> zed = new ArrayList<>();
        for (Path root : zedRoots(home)) zed.addAll(found(root.resolve("extensions/work/luau/luau-lsp-binaries"), "luau-lsp-", "luau-lsp" + exe));
        List<Path> code = new ArrayList<>();
        for (String root : List.of(".vscode", ".vscode-insiders", ".vscode-oss", ".cursor", ".windsurf", ".vscode-server", ".cursor-server", ".windsurf-server")) {
            code.addAll(found(home.resolve(root).resolve("extensions"), "johnnymorganz.luau-lsp-", "bin/server" + exe));
        }
        boolean fromCode = System.getenv("VSCODE_PID") != null || System.getenv("VSCODE_IPC_HOOK") != null || System.getenv("ELECTRON_RUN_AS_NODE") != null;
        List<Path> order = new ArrayList<>(fromCode ? code : zed);
        order.addAll(fromCode ? zed : code);
        if (!order.isEmpty()) return order.getFirst().toString();
        String path = System.getenv("PATH");
        if (path != null) {
            for (String directory : path.split(File.pathSeparator)) {
                if (directory.isBlank()) continue;
                Path candidate = Path.of(directory).resolve("luau-lsp" + exe);
                if (Files.isExecutable(candidate)) return candidate.toString();
            }
        }
        Path downloaded = download(exe);
        return downloaded == null ? "luau-lsp" + exe : downloaded.toString();
    }

    private static List<Path> zedRoots(Path home) {
        List<Path> roots = new ArrayList<>();
        String data = System.getenv("XDG_DATA_HOME");
        if (data != null && !data.isBlank()) roots.add(Path.of(data, "zed"));
        roots.add(home.resolve(".local/share/zed"));
        roots.add(home.resolve(".var/app/dev.zed.Zed/data/zed"));
        roots.add(home.resolve("Library/Application Support/Zed"));
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) roots.add(Path.of(local, "Zed"));
        return roots;
    }

    private static List<Path> found(Path directory, String prefix, String inside) {
        List<Path> matches = new ArrayList<>();
        if (!Files.isDirectory(directory)) return matches;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, prefix + "*")) {
            for (Path entry : entries) {
                Path binary = entry.resolve(inside);
                if (Files.isRegularFile(binary)) matches.add(binary);
            }
        } catch (IOException ignored) {
        }
        matches.sort(Comparator.comparing((Path binary) -> version(directory.relativize(binary).getName(0).toString())).reversed());
        return matches;
    }

    private static String version(String name) {
        StringBuilder key = new StringBuilder();
        for (String part : name.split("[^0-9]+")) {
            if (part.isEmpty()) continue;
            key.append(String.format("%08d", Long.parseLong(part.length() > 8 ? part.substring(0, 8) : part)));
        }
        return key.toString();
    }

    private static Path download(String exe) {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        String asset = WINDOWS ? "luau-lsp-win64.zip" : os.contains("mac") ? "luau-lsp-macos.zip"
                : arch.contains("aarch64") || arch.contains("arm64") ? "luau-lsp-linux-arm64.zip" : "luau-lsp-linux-x86_64.zip";
        try {
            Path jar = Path.of(LuauLsp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path target = jar.resolveSibling("bin").resolve("luau-lsp" + exe);
            if (Files.isRegularFile(target)) return target;
            HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://github.com/JohnnyMorganz/luau-lsp/releases/latest/download/" + asset)).build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) return null;
            Files.createDirectories(target.getParent());
            Path partial = target.resolveSibling(target.getFileName() + ".part");
            try (ZipInputStream zip = new ZipInputStream(response.body())) {
                for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (!Path.of(entry.getName()).getFileName().toString().equals("luau-lsp" + exe)) continue;
                    Files.copy(zip, partial, StandardCopyOption.REPLACE_EXISTING);
                    partial.toFile().setExecutable(true);
                    Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
                    return target;
                }
            }
        } catch (Exception e) {
            System.err.println("could not download luau-lsp: " + e.getMessage());
        }
        return null;
    }

    private static final class Doc {
        private final Object language;
        private Object version;
        private String text;

        private Doc(Object language, Object version, String text) {
            this.language = language;
            this.version = version;
            this.text = text;
        }
    }

    private record Relay(int generation, Object id) {}

    private static final class Child {
        private final Process process;
        private final int generation;
        private final OutputStream in;
        private final CompletableFuture<Map<String, Object>> ready = new CompletableFuture<>();
        private final Set<String> working = ConcurrentHashMap.newKeySet();

        private Child(Process process, int generation) {
            this.process = process;
            this.generation = generation;
            this.in = new BufferedOutputStream(process.getOutputStream());
        }

        private synchronized void send(Map<String, Object> message) {
            try {
                Frame.write(in, Json.write(message));
            } catch (IOException ignored) {
            }
        }

        private void progress(Map<String, Object> params) {
            if (params == null || !(object(params.get("value")) instanceof Map<String, Object> value)) return;
            String token = String.valueOf(params.get("token"));
            if ("begin".equals(value.get("kind"))) working.add(token);
            else if ("end".equals(value.get("kind"))) working.remove(token);
        }

        private void read(LuauLsp lsp) {
            InputStream from = new BufferedInputStream(process.getInputStream());
            try {
                while (true) {
                    String body = Frame.read(from);
                    if (body == null) break;
                    lsp.fromServer(this, Json.object(body));
                }
            } catch (IOException ignored) {
            }
            try {
                process.waitFor();
            } catch (InterruptedException ignored) {
            }
            lsp.died(this);
        }
    }

    private static final class Frame {

        private static String read(InputStream in) throws IOException {
            int length = -1;
            while (true) {
                String line = line(in);
                if (line == null) return null;
                if (line.isEmpty()) break;
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("content-length")) length = Integer.parseInt(line.substring(colon + 1).trim());
            }
            if (length < 0) throw new IOException("no content length");
            byte[] body = in.readNBytes(length);
            if (body.length < length) return null;
            return new String(body, StandardCharsets.UTF_8);
        }

        private static String line(InputStream in) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (true) {
                int next = in.read();
                if (next < 0) return null;
                if (next == '\n') break;
                if (next != '\r') bytes.write(next);
            }
            return bytes.toString(StandardCharsets.US_ASCII);
        }

        private static void write(OutputStream out, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            out.write(("Content-Length: " + bytes.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
            out.flush();
        }
    }

    private static final class Json {

        private record Num(String text) {
            long longValue() {
                return (long) Double.parseDouble(text);
            }

            @Override
            public String toString() {
                return text;
            }
        }

        private final String text;
        private int at;

        private Json(String text) {
            this.text = text;
        }

        private static Map<String, Object> object(String text) {
            Object value = new Json(text).value();
            if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("not a json object");
            return LuauLsp.object(value);
        }

        private Object value() {
            space();
            char c = text.charAt(at);
            if (c == '{') {
                at++;
                Map<String, Object> map = new LinkedHashMap<>();
                space();
                if (text.charAt(at) == '}') {
                    at++;
                    return map;
                }
                while (true) {
                    space();
                    String name = string();
                    space();
                    at++;
                    map.put(name, value());
                    space();
                    if (text.charAt(at++) == '}') return map;
                }
            }
            if (c == '[') {
                at++;
                List<Object> list = new ArrayList<>();
                space();
                if (text.charAt(at) == ']') {
                    at++;
                    return list;
                }
                while (true) {
                    list.add(value());
                    space();
                    if (text.charAt(at++) == ']') return list;
                }
            }
            if (c == '"') return string();
            if (text.startsWith("true", at)) {
                at += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", at)) {
                at += 5;
                return Boolean.FALSE;
            }
            if (text.startsWith("null", at)) {
                at += 4;
                return null;
            }
            int start = at;
            while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
            if (start == at) throw new IllegalArgumentException("bad json at " + at);
            return new Num(text.substring(start, at));
        }

        private String string() {
            at++;
            StringBuilder builder = new StringBuilder();
            while (true) {
                char c = text.charAt(at++);
                if (c == '"') return builder.toString();
                if (c != '\\') {
                    builder.append(c);
                    continue;
                }
                char escape = text.charAt(at++);
                switch (escape) {
                    case 'n' -> builder.append('\n');
                    case 't' -> builder.append('\t');
                    case 'r' -> builder.append('\r');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'u' -> {
                        builder.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                        at += 4;
                    }
                    default -> builder.append(escape);
                }
            }
        }

        private void space() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
        }

        private static String write(Object value) {
            StringBuilder builder = new StringBuilder();
            write(builder, value);
            return builder.toString();
        }

        private static void write(StringBuilder builder, Object value) {
            switch (value) {
                case null -> builder.append("null");
                case String text -> quote(builder, text);
                case Map<?, ?> map -> {
                    builder.append('{');
                    boolean first = true;
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (!first) builder.append(',');
                        first = false;
                        quote(builder, String.valueOf(entry.getKey()));
                        builder.append(':');
                        write(builder, entry.getValue());
                    }
                    builder.append('}');
                }
                case List<?> list -> {
                    builder.append('[');
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) builder.append(',');
                        write(builder, list.get(i));
                    }
                    builder.append(']');
                }
                default -> builder.append(value);
            }
        }

        private static void quote(StringBuilder builder, String text) {
            builder.append('"');
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '"' -> builder.append("\\\"");
                    case '\\' -> builder.append("\\\\");
                    case '\n' -> builder.append("\\n");
                    case '\r' -> builder.append("\\r");
                    case '\t' -> builder.append("\\t");
                    default -> {
                        if (c < 0x20) builder.append(String.format("\\u%04x", (int) c));
                        else builder.append(c);
                    }
                }
            }
            builder.append('"');
        }
    }
}
