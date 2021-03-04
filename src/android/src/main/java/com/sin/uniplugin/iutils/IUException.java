package com.sin.uniplugin.iutils;

public class IUException extends RuntimeException {
    int code;

    public IUException(String message) {
        this(message, -1);
    }

    public IUException(String message, int code) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
