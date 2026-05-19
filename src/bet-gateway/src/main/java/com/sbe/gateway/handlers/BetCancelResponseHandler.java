package com.sbe.gateway.handlers;

import com.sbe.gateway.BettingClient;
import com.sportradar.mbs.sdk.entities.common.AcceptanceStatus;
import com.sportradar.mbs.sdk.entities.response.CancelResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record BetCancelResponseHandler(BettingClient bettingClient) {
    private static final Logger log = LoggerFactory.getLogger(BetCancelResponseHandler.class);

    /**
     * @return true if betting-service successfully processed the cancellation (so we should ACK=true to MTS)
     */
    public boolean onTicketResponse(String ticketId, CancelResponse resp) {
        log.debug("BetCancelResponseHandler | Received ticket response for ticketId={}", resp);
        int code = resp.getCode();

        // get back real ticket id
        String[] split = ticketId.split("_");
        ticketId = split[split.length - 1];

        try {
            if (resp.getStatus() == AcceptanceStatus.ACCEPTED) {
                bettingClient.cancelBetAcceptedResponse(code, "Ok", ticketId);
                return true;
            } else {
                bettingClient.cancelBetRejectedResponse(code, resp.getMessage(), ticketId);
                return false;
            }
        } catch (Exception e) {
            // If betting-service call failed, return false so BetCancel sends acknowledged=false
            log.error("BetCancelResponseHandler | betting-service call failed for betId={}", ticketId, e);

            // optional best-effort: record as rejected on betting-service
            try {
                bettingClient.cancelBetRejectedResponse(500, "internal cancel processing failed", ticketId);
            } catch (Exception ignored) {}

            return false;
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

