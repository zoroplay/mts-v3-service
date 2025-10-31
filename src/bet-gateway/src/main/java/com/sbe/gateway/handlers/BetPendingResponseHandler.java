package com.sbe.gateway.handlers;

import com.sbe.gateway.BettingClient;
import com.sportradar.mbs.sdk.entities.common.AcceptanceStatus;
import com.sportradar.mbs.sdk.entities.response.TicketResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record BetPendingResponseHandler(BettingClient bettingClient) {
    private static final Logger log = LoggerFactory.getLogger(BetPendingResponseHandler.class);

    /**
     * Called when MTS replies to a ticket submission.
     */
    public void onTicketResponse(String ticketId, TicketResponse resp) {
        log.debug("Received ticket response for ticketId={}", resp);
        int code = resp.getCode();

        // get back real ticket id
        String[] split = ticketId.split("_");
        ticketId = split[split.length - 1];

        if (resp.getStatus() == AcceptanceStatus.ACCEPTED) {
            bettingClient.betAcceptedResponse(code, "Ok", ticketId,resp.getSignature());
        } else {
            bettingClient.betCancelledResponse(code, resp.getMessage(), ticketId);
        }
    }

    /**
     * On transport/SDK error.
     */
    public void onTicketError(String ticketId) {
        // get back real ticket id
        String[] split = ticketId.split("_");
        ticketId = split[split.length - 1];

        bettingClient.betCancelledResponse(102, "timeout sending ticket", ticketId);
    }
}

