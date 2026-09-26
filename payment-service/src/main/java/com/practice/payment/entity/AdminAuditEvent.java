package com.practice.payment.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "admin_audit_events")
public class AdminAuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String adminSubject;
    @Column(nullable = false) private String action;
    @Column(nullable = false) private String entityType;
    @Column(nullable = false) private String entityId;
    @Column(columnDefinition = "text") private String oldValue;
    @Column(columnDefinition = "text") private String newValue;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    protected AdminAuditEvent() { }
    public AdminAuditEvent(String subject, String action, String type, String id, String oldValue, String newValue) {
        this.adminSubject = subject; this.action = action; this.entityType = type; this.entityId = id;
        this.oldValue = oldValue; this.newValue = newValue;
    }
}
