package com.netwatch.phone.api;

public final class ScreenDecision {
    public enum Action { ALLOW, SILENCE, BLOCK, SCREEN }

    public final Action action;
    public final String displayName;
    public final String reason;
    public final String greeting;

    public ScreenDecision(Action action, String displayName, String reason, String greeting) {
        this.action = action;
        this.displayName = displayName == null ? "" : displayName;
        this.reason = reason == null ? "" : reason;
        this.greeting = greeting == null ? "" : greeting;
    }

    public static ScreenDecision allow(String reason) {
        return new ScreenDecision(Action.ALLOW, "", reason, "");
    }
}
