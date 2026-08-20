package com.blocki.blocki_backend.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
        name = "document_generation_automations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_document_generation_automations_user",
                columnNames = "user_id"))
public class DocumentGenerationAutomation {

    private static final DayOfWeek DEFAULT_SCHEDULE_DAY_OF_WEEK = DayOfWeek.MONDAY;
    private static final LocalTime DEFAULT_SCHEDULE_TIME = LocalTime.of(21, 0);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_day_of_week", length = 10)
    private DayOfWeek scheduleDayOfWeek;

    @Column(name = "schedule_time")
    private LocalTime scheduleTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentGenerationAutomation() {
    }

    public static DocumentGenerationAutomation create(UUID userId, boolean enabled, Instant now) {
        return create(userId, enabled, DEFAULT_SCHEDULE_DAY_OF_WEEK, DEFAULT_SCHEDULE_TIME, now);
    }

    public static DocumentGenerationAutomation create(
            UUID userId,
            boolean enabled,
            DayOfWeek scheduleDayOfWeek,
            LocalTime scheduleTime,
            Instant now) {
        DocumentGenerationAutomation automation = new DocumentGenerationAutomation();
        automation.userId = userId;
        automation.enabled = enabled;
        automation.scheduleDayOfWeek = scheduleDayOfWeek;
        automation.scheduleTime = scheduleTime;
        automation.createdAt = now;
        automation.updatedAt = now;
        return automation;
    }

    public void changeEnabled(boolean enabled, Instant updatedAt) {
        this.enabled = enabled;
        this.updatedAt = updatedAt;
    }

    public void changeSchedule(DayOfWeek scheduleDayOfWeek, LocalTime scheduleTime, Instant updatedAt) {
        this.scheduleDayOfWeek = scheduleDayOfWeek;
        this.scheduleTime = scheduleTime;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public DayOfWeek getScheduleDayOfWeek() {
        return scheduleDayOfWeek == null ? DEFAULT_SCHEDULE_DAY_OF_WEEK : scheduleDayOfWeek;
    }

    public LocalTime getScheduleTime() {
        return scheduleTime == null ? DEFAULT_SCHEDULE_TIME : scheduleTime;
    }
}
