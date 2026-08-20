package com.blocki.blocki_backend.document.dto;

public record DocumentGenerationAutomationResponse(boolean enabled, Schedule schedule) {

    private static final String DEFAULT_TIMEZONE = "Asia/Seoul";

    public static DocumentGenerationAutomationResponse of(boolean enabled, String dayOfWeek, String time) {
        return new DocumentGenerationAutomationResponse(enabled, new Schedule(dayOfWeek, time, DEFAULT_TIMEZONE));
    }

    public record Schedule(String dayOfWeek, String time, String timezone) {
    }
}
