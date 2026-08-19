package com.blocki.blocki_backend.document.service;

public class DocumentQueryException extends RuntimeException {

    public static final String DOCUMENT_NOT_FOUND = "DOCUMENT_NOT_FOUND";
    public static final String VERSION_NOT_FOUND = "VERSION_NOT_FOUND";
    public static final String FORBIDDEN = "FORBIDDEN";

    private final String code;

    private DocumentQueryException(String code) {
        super(code);
        this.code = code;
    }

    public static DocumentQueryException documentNotFound() {
        return new DocumentQueryException(DOCUMENT_NOT_FOUND);
    }

    public static DocumentQueryException versionNotFound() {
        return new DocumentQueryException(VERSION_NOT_FOUND);
    }

    public static DocumentQueryException forbidden() {
        return new DocumentQueryException(FORBIDDEN);
    }

    public String getCode() {
        return code;
    }
}
