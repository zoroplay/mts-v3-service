package com.sbe.gateway.handlers;

import com.sbe.gateway.BettingClient;
import com.sportradar.mbs.sdk.entities.common.AcceptanceStatus;
import com.sportradar.mbs.sdk.entities.response.CancelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record BetCancelResponseHandler(BettingClient bettingClient) {
    private static final Logger log = LoggerFactory.getLogger(BetCancelResponseHandler.class);

    /**
     * Called when MTS replies to a ticket submission.
     */
    public void onTicketResponse(String ticketId, CancelResponse resp) {
        log.debug("BetCancelResponseHandler | Received ticket response for ticketId={}", resp);
        int code = resp.getCode();

        // get back real ticket id
        String[] split = ticketId.split("_");
        ticketId = split[split.length - 1];

        if (resp.getStatus() == AcceptanceStatus.ACCEPTED) {
            bettingClient.cancelBetAcceptedResponse(code, "Ok", ticketId);
        } else {
            bettingClient.cancelBetRejectedResponse(code, resp.getMessage(), ticketId);
        }
    }

    /**
     * On transport/SDK error.
     */
    public void onTicketError(String ticketId) {
        // get back real ticket id
        String[] split = ticketId.split("_");
        ticketId = split[split.length - 1];

        bettingClient.cancelBetRejectedResponse(102, "timeout sending ticket", ticketId);
    }
}

