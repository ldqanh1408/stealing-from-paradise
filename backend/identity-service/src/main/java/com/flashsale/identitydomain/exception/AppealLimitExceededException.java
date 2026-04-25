package com.flashsale.identitydomain.exception;

public class AppealLimitExceededException extends RuntimeException {
    public AppealLimitExceededException(String message) {
        super(message);
    }
}
