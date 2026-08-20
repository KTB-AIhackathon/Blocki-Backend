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

    public static final String DEFAULT_DAY_OF_WEEK = "MONDAY";
    public static final String DEFAULT_TIME = "21:00";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private boolean enabled;

    // 기존에 이미 저장된 행에 컬럼을 추가하는 것이라(ddl-auto: update) NOT NULL로 선언하면 마이그레이션이
    // 실패할 수 있어 nullable로 두고, 조회 시 DEFAULT_DAY_OF_WEEK/DEFAULT_TIME로 보정한다.
    @Column(name = "day_of_week")
    private String dayOfWeek;

    @Column(name = "time_of_day")
    private String time;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentGenerationAutomation() {
    }

    public static DocumentGenerationAutomation create(UUID userId, boolean enabled, Instant now) {
        return create(userId, enabled, DEFAULT_DAY_OF_WEEK, DEFAULT_TIME, now);
    }

    public static DocumentGenerationAutomation create(
            UUID userId, boolean enabled, String dayOfWeek, String time, Instant now) {
        DocumentGenerationAutomation automation = new DocumentGenerationAutomation();
        automation.userId = userId;
        automation.enabled = enabled;
        automation.dayOfWeek = dayOfWeek;
        automation.time = time;
        automation.createdAt = now;
        automation.updatedAt = now;
        return automation;
    }

    public void changeEnabled(boolean enabled, Instant updatedAt) {
        this.enabled = enabled;
        this.updatedAt = updatedAt;
    }

    public void changeSchedule(String dayOfWeek, String time, Instant updatedAt) {
        this.dayOfWeek = dayOfWeek;
        this.time = time;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public String getTime() {
        return time;
    }
}
