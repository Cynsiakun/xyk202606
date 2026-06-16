package com.cd.common.exception;

public class LicenseAccessDeniedException extends RuntimeException {

    public LicenseAccessDeniedException(String message) {
        super(message);
    }
}
