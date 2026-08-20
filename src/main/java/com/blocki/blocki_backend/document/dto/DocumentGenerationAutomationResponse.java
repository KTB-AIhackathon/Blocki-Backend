package com.blocki.blocki_backend.document.dto;

import com.blocki.blocki_backend.document.entity.DocumentGenerationAutomation;

public record DocumentGenerationAutomationResponse(boolean enabled, Schedule schedule) {

    private static final Schedule DEFAULT_SCHEDULE = new Schedule("MONDAY", "21:00", "Asia/Seoul");

    public static DocumentGenerationAutomationResponse of(boolean enabled) {
        return new DocumentGenerationAutomationResponse(enabled, DEFAULT_SCHEDULE);
    }

    public static DocumentGenerationAutomationResponse of(DocumentGenerationAutomation automation) {
        return new DocumentGenerationAutomationResponse(
                automation.isEnabled(),
                new Schedule(
                        automation.getScheduleDayOfWeek().name(),
                        automation.getScheduleTime().toString(),
                        "Asia/Seoul"));
    }

    public record Schedule(String dayOfWeek, String time, String timezone) {
    }
}
