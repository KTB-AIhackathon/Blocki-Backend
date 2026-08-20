package com.blocki.blocki_backend.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "document_generation_automations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_document_generation_automations_user",
                columnNames = "user_id"))
public class DocumentGenerationAutomation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentGenerationAutomation() {
    }

    public static DocumentGenerationAutomation create(UUID userId, boolean enabled, Instant now) {
        DocumentGenerationAutomation automation = new DocumentGenerationAutomation();
        automation.userId = userId;
        automation.enabled = enabled;
        automation.createdAt = now;
        automation.updatedAt = now;
        return automation;
    }

    public void changeEnabled(boolean enabled, Instant updatedAt) {
        this.enabled = enabled;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
