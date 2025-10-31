package com.sbe.gateway.workers;

import com.sbe.gateway.BettingClient;
import com.sbe.gateway.handlers.BetCancelResponseHandler;
import com.sportradar.mbs.sdk.MbsSdk;
import com.sportradar.mbs.sdk.entities.cancellation.TicketCancelDetails;
import com.sportradar.mbs.sdk.entities.common.*;
import com.sportradar.mbs.sdk.entities.request.CancelRequest;
import com.sportradar.mbs.sdk.protocol.TicketProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protobuf.BetCancelRequest;

public class BetCancel implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(BetCancel.class);
    MbsSdk mbsSdk;
    BettingClient bettingClient;
    public BetCancelRequest message;
//    private static final ObjectMapper MAPPER = new ObjectMapper()
//            .enable(SerializationFeature.INDENT_OUTPUT)        // pretty-print
//            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
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
                .sendCancelAsync(ticketRequest)  // returns CompletableFuture<TicketResponse>
                .thenAccept(resp -> {
                    responseHandler.onTicketResponse(ticketId, resp);
                })
                .exceptionally(ex -> {
                    log.error("BetPending thread finished: sending ticket exception", ex);
                    responseHandler.onTicketError(ticketId);
                    return null;
                });
    }
}
