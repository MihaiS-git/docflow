package com.brutecx.docflow_backend.api.error;


import org.springframework.http.HttpStatusCode;

public class InviteDeliveryException extends RuntimeException {

    private final HttpStatusCode status;

    public InviteDeliveryException(HttpStatusCode status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatusCode getStatus() {
        return status;
    }
}

