package com.mobile.diafarms.network.dto;

import java.util.List;

/** Miroir de com.diafarms.ml.others.ApiResponse&lt;T&gt; côté backend. */
public class ApiEnvelope<T> {
    private String message;
    private int status;
    private T data;
    private List<String> errors;

    public String getMessage() { return message; }
    public int getStatus() { return status; }
    public T getData() { return data; }
    public List<String> getErrors() { return errors; }
}
