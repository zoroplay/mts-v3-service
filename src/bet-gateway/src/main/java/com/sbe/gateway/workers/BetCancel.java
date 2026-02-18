package com.sbe.gateway.workers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sbe.gateway.BettingClient;
import com.sbe.gateway.handlers.BetCancelResponseHandler;
import com.sportradar.mbs.sdk.MbsSdk;
import com.sportradar.mbs.sdk.entities.cancellation.TicketCancelDetails;
import com.sportradar.mbs.sdk.entities.common.*;
import com.sportradar.mbs.sdk.entities.request.CancelAckRequest;
import com.sportradar.mbs.sdk.entities.request.CancelRequest;
import com.sportradar.mbs.sdk.protocol.TicketProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protobuf.BetCancelRequest;

import java.util.concurrent.CompletableFuture;

public class BetCancel implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(BetCancel.class);
    MbsSdk mbsSdk;
    BettingClient bettingClient;
    public BetCancelRequest message;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)        // pretty-print
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    public BetCancel(MbsSdk mbsSdk, BettingClient bettingClient, BetCancelRequest message){
        this.mbsSdk = mbsSdk;
        this.message = message;
        this.bettingClient = bettingClient;
    }

    @Override
    public void run() {
        // 1. Get the TicketProtocol
        TicketProtocol ticketProtocol = mbsSdk.getTicketProtocol();
        BetCancelResponseHandler responseHandler = new BetCancelResponseHandler(bettingClient);

        // 2. Build the TicketRequest
        BetCancelRequest object = message;
        String ticketId = System.getenv("mts_bookmaker_id") + "_" + object.getBetID();
        long code = object.getCode();

        CancelRequest ticketRequest = CancelRequest.newBuilder()
                .setDetails(
                        TicketCancelDetails.newBuilder()
                                .setTicketId(ticketId)
                                .setTicketSignature(object.getSignature())
                                .setCode((int) code)
                                .build()
                )
                .build();

//        String json = MAPPER.writeValueAsString(ticketRequest);
//        log.info("BetPending thread started: sending ticket request for ticketId {}:\n{}", ticketId, json);

        ticketProtocol
                .sendCancelAsync(ticketRequest)
                .thenCompose(resp -> {
                    // 1) Notify betting-service first (your existing step) AND get internal success boolean
                    boolean internalOk = responseHandler.onTicketResponse(ticketId, resp);

                    // 2) Only send cancel-ack when MTS accepted the cancellation
                    if (resp.getStatus() != AcceptanceStatus.ACCEPTED) {
                        return CompletableFuture.completedFuture(null);
                    }

                    // 3) Send ACK/non-ACK back to MTS
                    CancelAckRequest ackReq = CancelAckRequest.newBuilder()
                            .setTicketId(ticketId)
                            .setCancellationId(resp.getCancellationId())
                            .setCancellationSignature(resp.getSignature())
                            .setAcknowledged(internalOk) // true iff betting-service processed successfully
                            .build();

                    try {
                        String json = MAPPER.writeValueAsString(ackReq);
                        log.info("BetCancel request for ticketId {}:\n{}", ticketId, json);
                    } catch (JsonProcessingException ignored) {

                    }

                    return ticketProtocol.sendCancelAckAsync(ackReq)
                            .thenAccept(ackResp -> log.info(
                                    "CancelAckReply ticketId={} status={} code={} msg={}",
                                    ticketId, ackResp.getStatus(), ackResp.getCode(), ackResp.getMessage()
                            ));
                })
                .exceptionally(ex -> {
                    log.error("BetCancel: cancel/ack flow exception", ex);

                    // best-effort: notify betting-service of failure
                    responseHandler.onTicketError(ticketId);

                    // NOTE: if sendCancelAsync threw before we got a response, we cannot send cancel-ack
                    return null;
                });
    }
}
