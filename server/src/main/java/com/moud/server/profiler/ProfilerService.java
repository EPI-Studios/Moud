package com.moud.server.profiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.diagnostics.NetworkProbe;
import com.moud.server.profiler.model.ProfilerCapture;
import com.moud.server.profiler.model.ProfilerFrame;
import com.moud.server.profiler.model.ProfilerSnapshot;
import com.moud.server.profiler.model.ScriptAggregate;
import com.moud.server.profiler.model.ScriptSample;
import com.moud.server.profiler.script.ScriptProfiler;
import com.moud.server.shared.SharedValueManager;
import com.moud.server.shared.diagnostics.SharedStoreSnapshot;
import com.sun.management.OperatingSystemMXBean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class ProfilerService {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            ProfilerService.class,
            LogContext.builder().put("subsystem", "profiler").build()
    );

    private static ProfilerService instance;
    private static final int FRAME_LIMIT = 600;
    private static final int SAMPLE_LIMIT = 2048;
    private static final ObjectMapper CAPTURE_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final ObjectWriter CAPTURE_WRITER = CAPTURE_MAPPER.writerWithDefaultPrettyPrinter();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "MoudProfilerSampler");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong frameCounter = new AtomicLong(0);
    private final Deque<ProfilerFrame> frames = new ArrayDeque<>();
    private final Deque<ScriptSample> scriptSamples = new ArrayDeque<>();
    private final CopyOnWriteArrayList<ProfilerListener> listeners = new CopyOnWriteArrayList<>();
    private final ScriptProfiler scriptProfiler = new ScriptProfiler(this);

    private final OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private volatile CaptureBuilder activeCapture;

    public static synchronized void install(ProfilerService profilerService) {
        instance = Objects.requireNonNull(profilerService, "profilerService");
    }

    public ProfilerService() {
    }

    public static ProfilerService getInstance() {
        if (instance == null) {
            instance = new ProfilerService();
        }
        return instance;
    }

    public ScriptProfiler scriptProfiler() {
        return scriptProfiler;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            LOGGER.info("Profiler service started");
            scheduler.scheduleAtFixedRate(this::sample, 0, 1, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdownNow();
            LOGGER.info("Profiler service stopped");
        }
    }

    public void addListener(ProfilerListener listener) {
        listeners.add(listener);
        synchronized (frames) {
            for (ProfilerFrame frame : frames) {
                listener.onFrame(frame);
            }
        }
        synchronized (scriptSamples) {
            for (ScriptSample sample : scriptSamples) {
                listener.onScriptSample(sample);
            }
        }
    }

    public void removeListener(ProfilerListener listener) {
        listeners.remove(listener);
    }

    public List<ProfilerFrame> recentFrames() {
        synchronized (frames) {
            return new ArrayList<>(frames);
        }
    }

    public List<ScriptSample> recentScriptSamples() {
        synchronized (scriptSamples) {
            return new ArrayList<>(scriptSamples);
        }
    }

    public boolean isCapturing() {
        return capturing.get();
    }

    public void startCapture(String name) {
        if (capturing.compareAndSet(false, true)) {
            activeCapture = new CaptureBuilder(name == null || name.isBlank() ? "Capture" : name);
            LOGGER.info(LogContext.builder()
                    .put("capture_name", activeCapture.name)
                    .build(), "Profiler capture started");
        }
    }

    public ProfilerCapture stopCapture() {
        if (capturing.compareAndSet(true, false)) {
            CaptureBuilder builder = activeCapture;
            activeCapture = null;
            if (builder != null) {
                ProfilerCapture capture = builder.build();
                LOGGER.info(LogContext.builder()
                        .put("capture_name", capture.name())
                        .put("frames", capture.frames().size())
                        .put("samples", capture.scriptSamples().size())
                        .build(), "Profiler capture finished");
                return capture;
            }
        }
        return null;
    }

    public void exportCapture(Path file, ProfilerCapture capture) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        CAPTURE_WRITER.writeValue(file.toFile(), capture);
    }

    public ProfilerCapture importCapture(Path file) throws IOException {
        return CAPTURE_MAPPER.readValue(file.toFile(), ProfilerCapture.class);
    }

    private void sample() {
        try {
            ProfilerFrame frame = buildFrame();
            recordFrame(frame);
        } catch (Exception e) {
            LOGGER.warn("Profiler sampler failed", e);
        }
    }

    private ProfilerFrame buildFrame() {
        double processCpu = osBean != null ? osBean.getProcessCpuLoad() : -1d;
        double systemCpu = osBean != null ? osBean.getSystemCpuLoad() : -1d;

        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long heapUsed = heapUsage.getUsed();
        long heapCommitted = heapUsage.getCommitted();
        int liveThreads = threadBean.getThreadCount();

        NetworkProbe.NetworkSnapshot networkSnapshot = NetworkProbe.getInstance().snapshot();
        long outboundBytes = networkSnapshot.outboundBytes();
        long inboundBytes = networkSnapshot.inboundBytes();
        long outboundPackets = networkSnapshot.outboundCount();
        long inboundPackets = networkSnapshot.inboundCount();

        List<SharedStoreSnapshot> stores = SharedValueManager.getInstance().snapshotAllStores();
        int storeCount = stores.size();
        long sharedValueCount = stores.stream()
                .mapToLong(SharedStoreSnapshot::totalKeys)
                .sum();

        return new ProfilerFrame(
                frameCounter.incrementAndGet(),
                Instant.now(),
                processCpu,
                systemCpu,
                heapUsed,
                heapCommitted,
                liveThreads,
                outboundBytes,
                inboundBytes,
                outboundPackets,
                inboundPackets,
                storeCount,
                sharedValueCount
        );
    }

    private void recordFrame(ProfilerFrame frame) {
        synchronized (frames) {
            frames.addLast(frame);
            while (frames.size() > FRAME_LIMIT) {
                frames.removeFirst();
            }
        }

        CaptureBuilder builder = activeCapture;
        if (builder != null) {
            builder.addFrame(frame);
        }

        ProfilerSnapshot snapshot = buildSnapshot(frame);

        for (ProfilerListener listener : listeners) {
            listener.onFrame(frame);
            listener.onSnapshot(snapshot);
        }
    }

    private ProfilerSnapshot buildSnapshot(ProfilerFrame frame) {
        List<ScriptAggregate> aggregates = scriptProfiler.snapshotAggregates();
        List<ScriptSample> samplesSnapshot = recentScriptSamples();
        NetworkProbe.NetworkSnapshot networkSnapshot = NetworkProbe.getInstance().snapshot();
        List<SharedStoreSnapshot> sharedStores = SharedValueManager.getInstance().snapshotAllStores();
        return new ProfilerSnapshot(frame, aggregates, samplesSnapshot, networkSnapshot, sharedStores);
    }

    public void recordScriptSample(ScriptSample sample) {
        synchronized (scriptSamples) {
            scriptSamples.addLast(sample);
            while (scriptSamples.size() > SAMPLE_LIMIT) {
                scriptSamples.removeFirst();
            }
        }

        CaptureBuilder builder = activeCapture;
        if (builder != null) {
            builder.addSample(sample);
        }

        for (ProfilerListener listener : listeners) {
            listener.onScriptSample(sample);
        }
    }

    private static final class CaptureBuilder {
        private final String name;
        private final Instant startedAt = Instant.now();
        private final List<ProfilerFrame> frames = new ArrayList<>();
        private final List<ScriptSample> samples = new ArrayList<>();

        private CaptureBuilder(String name) {
            this.name = name;
        }

        void addFrame(ProfilerFrame frame) {
            frames.add(frame);
        }

        void addSample(ScriptSample sample) {
            samples.add(sample);
        }

        ProfilerCapture build() {
            return new ProfilerCapture(
                    name,
                    startedAt,
                    Instant.now(),
                    Collections.unmodifiableList(new ArrayList<>(frames)),
                    Collections.unmodifiableList(new ArrayList<>(samples))
            );
        }
    }
}
