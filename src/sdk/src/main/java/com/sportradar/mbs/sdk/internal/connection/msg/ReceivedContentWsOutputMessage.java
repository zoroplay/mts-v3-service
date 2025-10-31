package com.sportradar.mbs.sdk.internal.connection.msg;

import com.sportradar.mbs.sdk.internal.connection.msg.base.WsOutputMessage;

public class ReceivedContentWsOutputMessage extends WsOutputMessage {

    private final String content;

    public ReceivedContentWsOutputMessage(final String contebt) {
        super(null);
        this.content = contebt;
    }

    public String getContent() {
        return content;
    }
}
