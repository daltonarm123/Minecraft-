package com.community.servercore.audit;

import java.util.List;

public interface AuditSink {
    void publish(AuditEvent event);

    List<AuditEvent> recent(int limit);
}
