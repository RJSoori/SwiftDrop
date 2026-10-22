package com.swiftdrop.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID actorId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String target;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant occurredAt;

    protected AuditLogEntry() {
    }

    public AuditLogEntry(UUID actorId, String action, String target) {
        this.actorId = actorId;
        this.action = action;
        this.target = target;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }
}
