package com.meekdev.moud.script.host;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Tasks {

    private Tasks() {}

    static void install(Host host) {
        Members task = new Members("Task")
                .function("wait", "(seconds: number?) -> number", a -> Suspend.seconds(a.number(0, 0)))
                .function("spawn", "(handler: (...any) -> (), ...any) -> number", a -> (double) spawn(host, a.callable(0), a.from(1)))
                .function("delay", "(seconds: number, handler: () -> ()) -> number",
                        a -> (double) delay(host, a.number(0), a.callable(1)))
                .function("defer", "(handler: (...any) -> (), ...any) -> number", a -> (double) defer(host, a.callable(0), a.from(1)))
                .function("cancel", "(handle: number) -> ()", a -> {
                    host.scheduler().cancel(a.integer(0));
                    return null;
                })
                .function("every", "(seconds: number, handler: () -> ()) -> TaskHandle", a -> every(host, a.number(0), a.callable(1)))
                .function("after", "(seconds: number, handler: () -> ()) -> TaskHandle", a -> after(host, a.number(0), a.callable(1)))
                .function("debounce", "(handler: (...any) -> (), seconds: number) -> (...any) -> ()", a -> debounce(host, a.callable(0), a.number(1)))
                .function("throttle", "(handler: (...any) -> ...any, seconds: number) -> (...any) -> ...any", a -> throttle(host, a.callable(0), a.number(1)));
        host.global("task", "Task", task);
        host.declare(task);
        host.api().declare(handle(() -> {}).decl());

        host.global("clock", "() -> number", new Builtin("clock", a -> now()));

        Members os = new Members("Os")
                .function("time", "() -> number", a -> (double) (System.currentTimeMillis() / 1000))
                .function("clock", "() -> number", a -> now())
                .function("difftime", "(later: number, earlier: number) -> number", a -> a.number(0) - a.number(1))
                .function("date", "(format: string?, when: number?) -> string", a -> {
                    String pattern = a.string(0, "%Y-%m-%d %H:%M:%S");
                    Instant when = Instant.ofEpochSecond((long) a.number(1, System.currentTimeMillis() / 1000.0));
                    boolean local = !pattern.startsWith("!");
                    ZonedDateTime moment = when.atZone(local ? ZoneId.systemDefault() : ZoneOffset.UTC);
                    return stamp(local ? pattern : pattern.substring(1), moment);
                });
        host.global("os", "Os", os);
        host.declare(os);

        Map<String, Double> until = new HashMap<>();
        Members cooldown = new Members("Cooldown")
                .method("ready", "(key: any, name: string, seconds: number) -> boolean", a -> {
                    String id = host.text(a.get(1)) + "\0" + a.string(2);
                    double now = now();
                    Double ready = until.get(id);
                    if (ready != null && now < ready) return false;
                    until.put(id, now + a.number(3));
                    return true;
                })
                .method("remaining", "(key: any, name: string) -> number", a -> {
                    Double ready = until.get(host.text(a.get(1)) + "\0" + a.string(2));
                    return ready == null ? 0.0 : Math.max(0, ready - now());
                })
                .method("reset", "(key: any, name: string) -> ()", a -> {
                    until.remove(host.text(a.get(1)) + "\0" + a.string(2));
                    return null;
                });
        host.global("cooldown", "Cooldown", cooldown);
        host.declare(cooldown);
    }

    public static double now() {
        return System.nanoTime() / 1e9;
    }

    public static int spawn(Host host, Callable fn, Object... args) {
        Callable kept = fn.retain();
        Fiber fiber = host.engine() == null ? null : host.engine().fiber(kept);
        if (fiber == null) {
            host.call(kept, "task", args);
            kept.release();
            return 0;
        }
        return host.scheduler().start(released(fiber, kept), host.ownership().current(), args);
    }

    public static int delay(Host host, double seconds, Callable fn) {
        Callable kept = fn.retain();
        Fiber fiber = host.engine() == null ? null : host.engine().fiber(kept);
        double[] waited = {0};
        Suspend wait = new Suspend(dt -> (waited[0] += dt) >= seconds ? new Object[0] : null);
        if (fiber == null) {
            fiber = new Fiber() {
                @Override
                public Suspend resume(Object... values) {
                    host.call(kept, "task.delay");
                    return null;
                }

                @Override
                public void cancel() {}
            };
        }
        return host.scheduler().later(released(fiber, kept), host.ownership().current(), wait);
    }

    private static Fiber released(Fiber fiber, Callable fn) {
        return new Fiber() {
            @Override
            public Suspend resume(Object... values) {
                Suspend next;
                try {
                    next = fiber.resume(values);
                } catch (RuntimeException e) {
                    fn.release();
                    throw e;
                }
                if (next == null) fn.release();
                return next;
            }

            @Override
            public void cancel() {
                fiber.cancel();
                fn.release();
            }
        };
    }

    private static Members handle(Runnable cancel) {
        return new Members("TaskHandle").method("cancel", "() -> ()", a -> {
            cancel.run();
            return null;
        });
    }

    private static Object every(Host host, double seconds, Callable fn) {
        Callable kept = fn.retain();
        boolean[] cancelled = {false};
        double[] elapsed = {0};
        Object owner = host.ownership().current();
        host.onStep(dt -> {
            if (cancelled[0]) return;
            elapsed[0] += dt;
            if (elapsed[0] < seconds) return;
            elapsed[0] = 0;
            Object before = host.ownership().enter(owner);
            try {
                spawn(host, kept);
            } finally {
                host.ownership().leave(before);
            }
        });
        Runnable cancel = () -> {
            if (cancelled[0]) return;
            cancelled[0] = true;
            kept.release();
        };
        host.ownership().onRelease(owner, cancel);
        return handle(cancel);
    }

    private static Object after(Host host, double seconds, Callable fn) {
        Callable kept = fn.retain();
        boolean[] cancelled = {false};
        delay(host, seconds, args -> {
            if (!cancelled[0]) host.call(kept, "task.after", args);
            kept.release();
            return null;
        });
        return handle(() -> cancelled[0] = true);
    }

    private static Callable debounce(Host host, Callable fn, double seconds) {
        Callable kept = fn.retain();
        int[] generation = {0};
        return args -> {
            int mine = ++generation[0];
            delay(host, seconds, ignored -> {
                if (mine == generation[0]) host.call(kept, "task.debounce", args);
                return null;
            });
            return new Object[0];
        };
    }

    private static Callable throttle(Host host, Callable fn, double seconds) {
        Callable kept = fn.retain();
        double[] last = {Double.NEGATIVE_INFINITY};
        return args -> {
            double now = now();
            if (now - last[0] < seconds) return new Object[0];
            last[0] = now;
            Object[] out = kept.call(args);
            return out == null ? new Object[0] : out;
        };
    }

    private static int defer(Host host, Callable fn, Object... args) {
        Callable kept = fn.retain();
        return delay(host, 0, kept);
    }

    private static String stamp(String pattern, ZonedDateTime moment) {
        StringBuilder out = new StringBuilder();
        for (int n = 0; n < pattern.length(); n++) {
            char c = pattern.charAt(n);
            if (c != '%' || n + 1 >= pattern.length()) {
                out.append(c);
                continue;
            }
            char field = pattern.charAt(++n);
            out.append(switch (field) {
                case 'Y' -> String.format("%04d", moment.getYear());
                case 'y' -> String.format("%02d", moment.getYear() % 100);
                case 'm' -> String.format("%02d", moment.getMonthValue());
                case 'd' -> String.format("%02d", moment.getDayOfMonth());
                case 'H' -> String.format("%02d", moment.getHour());
                case 'M' -> String.format("%02d", moment.getMinute());
                case 'S' -> String.format("%02d", moment.getSecond());
                case 'j' -> String.format("%03d", moment.getDayOfYear());
                case 'A' -> moment.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                case 'a' -> moment.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                case 'B' -> moment.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                case 'b' -> moment.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                case 'p' -> moment.getHour() < 12 ? "AM" : "PM";
                case 'I' -> String.format("%02d", (moment.getHour() % 12 == 0 ? 12 : moment.getHour() % 12));
                case '%' -> "%";
                default -> "%" + field;
            });
        }
        return out.toString();
    }
}
