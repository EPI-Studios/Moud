package com.meekdev.moud.script.host;

public final class Connection implements HostObject {

    private static final Builtin DISCONNECT = new Builtin("Connection:disconnect", args -> {
        args.self(Connection.class).disconnect();
        return null;
    });

    private final Runnable undo;
    private boolean connected = true;

    public Connection(Runnable undo) {
        this.undo = undo;
    }

    public void disconnect() {
        if (!connected) return;
        connected = false;
        undo.run();
    }

    public boolean connected() {
        return connected;
    }

    @Override
    public String typeName() {
        return "Connection";
    }

    @Override
    public Object get(String key) {
        return switch (key) {
            case "disconnect" -> DISCONNECT;
            case "connected" -> connected;
            default -> throw new HostError("Connection has no member '%s'", key);
        };
    }

    static Api.Decl decl() {
        return new Api.Decl("Connection", null, java.util.List.of(
                new Api.Member("disconnect", Api.Kind.METHOD, "() -> ()"),
                new Api.Member("connected", Api.Kind.FIELD, "boolean")));
    }
}
