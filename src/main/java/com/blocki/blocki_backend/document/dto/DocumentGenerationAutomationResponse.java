package com.blocki.blocki_backend.document.dto;

import com.blocki.blocki_backend.document.entity.DocumentGenerationAutomation;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public record DocumentGenerationAutomationResponse(boolean enabled, Schedule schedule) {

    private static final Schedule DEFAULT_SCHEDULE = new Schedule("MONDAY", "21:00", "Asia/Seoul");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    public static DocumentGenerationAutomationResponse of(boolean enabled) {
        return new DocumentGenerationAutomationResponse(enabled, DEFAULT_SCHEDULE);
    }

    public static DocumentGenerationAutomationResponse of(DocumentGenerationAutomation automation) {
        return new DocumentGenerationAutomationResponse(
                automation.isEnabled(),
                new Schedule(
                        automation.getScheduleDayOfWeek().name(),
                        clockOf(automation.getScheduleTime()),
                        "Asia/Seoul"));
    }

    private static String clockOf(LocalTime time) {
        return time.truncatedTo(ChronoUnit.MINUTES).format(CLOCK);
    }

    public record Schedule(String dayOfWeek, String time, String timezone) {
    }
}
