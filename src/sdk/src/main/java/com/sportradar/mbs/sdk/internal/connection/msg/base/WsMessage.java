package com.sportradar.mbs.sdk.internal.connection.msg.base;

public abstract class WsMessage {

    private final String correlationId;

    public WsMessage(final String correlationId) {
        this.correlationId = correlationId == null ? "" : correlationId;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
