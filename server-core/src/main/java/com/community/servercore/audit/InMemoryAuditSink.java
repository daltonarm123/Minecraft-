package com.community.servercore.audit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public final class InMemoryAuditSink implements AuditSink {
    private final Deque<AuditEvent> events = new ArrayDeque<>();
    private final int capacity;

    public InMemoryAuditSink() {
        this(1_000);
    }

    public InMemoryAuditSink(int capacity) {
        if (capacity < 1 || capacity > 1_000_000) {
            throw new IllegalArgumentException("capacity must be between 1 and 1000000");
        }
        this.capacity = capacity;
    }

    @Override
    public synchronized void publish(AuditEvent event) {
        events.addFirst(Objects.requireNonNull(event, "event"));
        while (events.size() > capacity) {
            events.removeLast();
        }
    }

    @Override
    public synchronized List<AuditEvent> recent(int limit) {
        if (limit < 1 || limit > capacity) {
            throw new IllegalArgumentException("limit must be between 1 and capacity");
        }
        List<AuditEvent> result = new ArrayList<>(Math.min(limit, events.size()));
        int count = 0;
        for (AuditEvent event : events) {
            if (count++ >= limit) {
                break;
            }
            result.add(event);
        }
        return List.copyOf(result);
    }
}
