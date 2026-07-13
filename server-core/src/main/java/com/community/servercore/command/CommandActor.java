package com.community.servercore.command;

import com.community.servercore.selection.WorldPosition;

import java.util.UUID;

public interface CommandActor {
    UUID id();

    String name();

    WorldPosition position();

    boolean hasPermission(String permission);
}
