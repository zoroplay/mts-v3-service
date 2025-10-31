package com.sportradar.mbs.sdk.internal.connection.msg.base;

public abstract class WsInputMessage extends WsMessage {

    public WsInputMessage(final String correlationId) {
        super(correlationId);
    }
}
