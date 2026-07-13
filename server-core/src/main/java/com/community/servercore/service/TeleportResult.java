package com.community.servercore.service;

import java.util.Objects;

public record TeleportResult(boolean successful, String message) {
    public TeleportResult {
        message = Objects.requireNonNullElse(message, "");
    }

    public static TeleportResult success(String message) {
        return new TeleportResult(true, message);
    }

    public static TeleportResult failure(String message) {
        return new TeleportResult(false, message);
    }
}
