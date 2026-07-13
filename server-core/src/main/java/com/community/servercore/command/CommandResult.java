package com.community.servercore.command;

import java.util.List;
import java.util.Objects;

public record CommandResult(boolean successful, List<String> messages) {
    public CommandResult {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
    }

    public static CommandResult success(String message) {
        return new CommandResult(true, List.of(Objects.requireNonNullElse(message, "")));
    }

    public static CommandResult success(List<String> messages) {
        return new CommandResult(true, messages);
    }

    public static CommandResult failure(String message) {
        return new CommandResult(false, List.of(Objects.requireNonNullElse(message, "")));
    }
}
