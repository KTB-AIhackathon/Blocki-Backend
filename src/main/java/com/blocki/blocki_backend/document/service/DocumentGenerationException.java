package com.blocki.blocki_backend.document.service;

import java.util.UUID;

public class DocumentGenerationException extends RuntimeException {

    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
    public static final String JOB_ALREADY_RUNNING = "JOB_ALREADY_RUNNING";
    public static final String JOB_NOT_FOUND = "JOB_NOT_FOUND";
    public static final String FORBIDDEN = "FORBIDDEN";

    private final String code;
    private final UUID jobId;

    private DocumentGenerationException(String code, UUID jobId) {
        super(code);
        this.code = code;
        this.jobId = jobId;
    }

    public static DocumentGenerationException idempotencyKeyReused() {
        return new DocumentGenerationException(IDEMPOTENCY_KEY_REUSED, null);
    }

    public static DocumentGenerationException jobAlreadyRunning(UUID jobId) {
        return new DocumentGenerationException(JOB_ALREADY_RUNNING, jobId);
    }

    public static DocumentGenerationException jobNotFound() {
        return new DocumentGenerationException(JOB_NOT_FOUND, null);
    }

    public static DocumentGenerationException forbidden() {
        return new DocumentGenerationException(FORBIDDEN, null);
    }

    public String getCode() {
        return code;
    }

    public UUID getJobId() {
        return jobId;
    }
}
