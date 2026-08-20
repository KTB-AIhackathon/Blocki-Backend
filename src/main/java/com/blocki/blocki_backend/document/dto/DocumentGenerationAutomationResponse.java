package com.blocki.blocki_backend.document.dto;

public record DocumentGenerationAutomationResponse(boolean enabled, Schedule schedule) {

    private static final Schedule FIXED_SCHEDULE = new Schedule("MONDAY", "21:00", "Asia/Seoul");

    public static DocumentGenerationAutomationResponse of(boolean enabled) {
        return new DocumentGenerationAutomationResponse(enabled, FIXED_SCHEDULE);
    }

    public record Schedule(String dayOfWeek, String time, String timezone) {
    }
}
