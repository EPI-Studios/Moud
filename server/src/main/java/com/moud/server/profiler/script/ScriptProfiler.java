package com.moud.server.profiler.script;

import com.moud.server.profiler.ProfilerService;
import com.moud.server.profiler.model.ScriptAggregate;
import com.moud.server.profiler.model.ScriptExecutionMetadata;
import com.moud.server.profiler.model.ScriptExecutionType;
import com.moud.server.profiler.model.ScriptSample;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class ScriptProfiler {
    private final ProfilerService service;
    private final AtomicLong spanIds = new AtomicLong(0);
    private final ThreadLocal<Deque<ActiveSpan>> spanStack = ThreadLocal.withInitial(ArrayDeque::new);
    private final ConcurrentMap<AggregateKey, AggregateStats> aggregates = new ConcurrentHashMap<>();

    public ScriptProfiler(ProfilerService service) {
        this.service = service;
    }

    public ActiveSpan open(Value callback, ScriptExecutionMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");

        String functionName = resolveFunctionName(callback);
        SourceSection location = callback != null ? callback.getSourceLocation() : null;

        String scriptName = (location != null && location.getSource() != null)
                ? location.getSource().getName()
                : "<unknown>";

        int line = (location != null) ? location.getStartLine() : -1;
        long spanId = spanIds.incrementAndGet();

        Deque<ActiveSpan> stack = spanStack.get();
        long parentId = stack.isEmpty() ? -1L : stack.peek().spanId();

        ActiveSpan span = new ActiveSpan(
                spanId,
                parentId,
                metadata,
                functionName,
                scriptName,
                line,
                System.currentTimeMillis()
        );

        stack.push(span);
        return span;
    }

    public void close(ActiveSpan span, long durationNanos, boolean success, String errorMessage) {
        Deque<ActiveSpan> stack = spanStack.get();
        if (!stack.isEmpty() && stack.peek() == span) {
            stack.pop();
        } else {
            stack.remove(span);
        }

        ScriptSample sample = new ScriptSample(
                span.spanId(),
                span.parentSpanId(),
                span.functionName(),
                span.scriptName(),
                span.line(),
                durationNanos,
                span.startEpochMillis(),
                span.metadata().type(),
                span.metadata().label(),
                span.metadata().detail(),
                success,
                errorMessage
        );

        service.recordScriptSample(sample);

        AggregateKey key = new AggregateKey(
                span.functionName(),
                span.scriptName(),
                span.line(),
                span.metadata().type(),
                span.metadata().label()
        );
        aggregates.computeIfAbsent(key, k -> new AggregateStats())
                .record(durationNanos);
    }

    public List<ScriptAggregate> snapshotAggregates() {
        List<ScriptAggregate> list = new ArrayList<>(aggregates.size());
        aggregates.forEach((key, stats) -> list.add(new ScriptAggregate(
                key.functionName(),
                key.scriptName(),
                key.line(),
                key.type(),
                key.label(),
                stats.invocationCount().sum(),
                stats.totalDurationNanos().sum(),
                stats.maxDurationNanos().get()
        )));
        list.sort(ScriptAggregate::compareTo);
        return list;
    }

    public void resetAggregates() {
        aggregates.clear();
    }

    private String resolveFunctionName(Value callback) {
        if (callback == null) {
            return "<unknown>";
        }
        try {
            if (callback.canInvokeMember("name")) {
                Value name = callback.invokeMember("name");
                if (name != null && name.isString()) {
                    String result = name.asString();
                    if (!result.isBlank()) {
                        return result;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        try {
            Value nameMember = callback.getMember("name");
            if (nameMember != null && nameMember.isString()) {
                String result = nameMember.asString();
                if (!result.isBlank()) {
                    return result;
                }
            }
        } catch (Exception ignored) {
        }

        String display = callback.toString();
        if (display != null && display.length() > 64) {
            display = display.substring(0, 64);
        }
        return display != null ? display : "<callback>";
    }

    public record ActiveSpan(
            long spanId,
            long parentSpanId,
            ScriptExecutionMetadata metadata,
            String functionName,
            String scriptName,
            int line,
            long startEpochMillis
    ) {
    }

    private record AggregateKey(
            String functionName,
            String scriptName,
            int line,
            ScriptExecutionType type,
            String label
    ) {
    }

    private static final class AggregateStats {
        private final LongAdder invocationCount = new LongAdder();
        private final LongAdder totalDurationNanos = new LongAdder();
        private final AtomicLong maxDurationNanos = new AtomicLong();

        void record(long durationNanos) {
            invocationCount.increment();
            totalDurationNanos.add(durationNanos);
            maxDurationNanos.accumulateAndGet(durationNanos, Math::max);
        }

        LongAdder invocationCount() {
            return invocationCount;
        }

        LongAdder totalDurationNanos() {
            return totalDurationNanos;
        }

        AtomicLong maxDurationNanos() {
            return maxDurationNanos;
        }
    }
}
