package com.sportradar.mbs.sdk.internal.connection.msg.base;

public abstract class WsOutputMessage extends WsMessage {

    public WsOutputMessage(final String correlationId) {
        super(correlationId);
    }
}
