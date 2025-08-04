package com.paipi.exception;

public class DbAdapterException extends RuntimeException {

    public DbAdapterException(String message) {
        super(message);
    }

    public DbAdapterException(Throwable cause) {
        super(cause);
    }

    public DbAdapterException(String message, Throwable cause) {
        super(message, cause);
    }
}
