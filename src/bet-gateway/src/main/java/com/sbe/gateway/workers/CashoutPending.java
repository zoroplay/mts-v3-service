package com.sbe.gateway.workers;

import com.sportradar.mbs.sdk.MbsSdk;
import com.sportradar.mbs.sdk.entities.cashout.TicketCashoutDetails;
import com.sportradar.mbs.sdk.entities.cashout.TicketPartialCashoutDetails;
import com.sportradar.mbs.sdk.entities.channel.Channel;
import com.sportradar.mbs.sdk.entities.common.CashoutSuggestions;
import com.sportradar.mbs.sdk.entities.common.EndCustomer;
import com.sportradar.mbs.sdk.entities.common.TicketContext;
import com.sportradar.mbs.sdk.entities.payout.CashPayout;
import com.sportradar.mbs.sdk.entities.payout.Payout;
import com.sportradar.mbs.sdk.entities.request.CashoutBuildRequest;
import com.sportradar.mbs.sdk.entities.request.CashoutPlacementRequest;
import com.sportradar.mbs.sdk.entities.request.CashoutRequest;
import com.sportradar.mbs.sdk.entities.response.CashoutBuildResponse;
import com.sportradar.mbs.sdk.entities.response.CashoutPlacementResponse;
import com.sportradar.mbs.sdk.protocol.TicketProtocol;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protobuf.MTSCashoutRequest;
import protobuf.MTSCashoutResponse;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

public class CashoutPending implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CashoutPending.class);

    MbsSdk mbsSdk;
    MTSCashoutRequest message;
    StreamObserver<MTSCashoutResponse> responseObserver;

    public CashoutPending(MbsSdk mbsSdk,
                          MTSCashoutRequest message,
                          StreamObserver<MTSCashoutResponse> responseObserver) {
        this.mbsSdk = mbsSdk;
        this.message = message;
        this.responseObserver = responseObserver;
    }

    @Override
    public void run() {
        MTSCashoutRequest req = message;
        TicketProtocol cashoutProtocol = mbsSdk.getTicketProtocol();

        String ticketId = System.getenv("mts_bookmaker_id") + "_" + req.getBetID();
        long profileId = req.getProfileID();
        long source = req.getBetSource();
        String ipAddress = req.getIpAddress();
        boolean isBuild = req.getIsBuild();
        double requestedPayout = req.getRequestedPayout();
        double requestedPercent = req.getRequestedPercent();

        int limitID = Integer.parseInt(System.getenv("mts_limit_id"));

        try {
            EndCustomer endCustomer = EndCustomer.newBuilder()
                    .setId(String.valueOf(profileId))
                    .build();

            Channel channel = getChannel(source, ipAddress);

            TicketContext ticketContext = TicketContext.newBuilder()
                    .setEndCustomer(endCustomer)
                    .setChannel(channel)
                    .setLimitId(limitID)
                    .build();
            // NOTE: ticketContext is currently not set on CashoutRequest – add it there if your SDK supports it.

            // --- Build CashoutRequest payload ---
            CashoutRequest.Builder cashoutBuilder = CashoutRequest.newBuilder()
                    .setCashoutId(ticketId);

            if (isBuild) {
                // CASHOUT-BUILD (quote only)
                TicketCashoutDetails details = TicketCashoutDetails.newBuilder()
                        .setTicketId(ticketId)
                        .setCode(100) // end customer initiated; adjust if needed
                        .build();

                cashoutBuilder.setDetails(details);
            } else {
                // CASHOUT-PLACEMENT
                if (requestedPercent > 0 && requestedPercent < 1.0) {
                    // Partial cashout by percent
                    TicketPartialCashoutDetails details = TicketPartialCashoutDetails.newBuilder()
                            .setTicketId(ticketId)
                            .setCode(100)
                            .setPercentage(BigDecimal.valueOf(requestedPercent))
                            .build();
                    cashoutBuilder.setDetails(details);

                } else if (requestedPayout > 0) {
                    // Partial/Full cashout by payout amount
                    CashPayout payout = CashPayout.newBuilder()
                            .setAmount(BigDecimal.valueOf(requestedPayout))
                            .build();

                    TicketPartialCashoutDetails details = TicketPartialCashoutDetails.newBuilder()
                            .setTicketId(ticketId)
                            .setCode(100)
                            .setPayout(payout)
                            .build();
                    cashoutBuilder.setDetails(details);

                } else {
                    // Full ticket cashout with default strategy
                    TicketCashoutDetails details = TicketCashoutDetails.newBuilder()
                            .setTicketId(ticketId)
                            .setCode(100)
                            .build();
                    cashoutBuilder.setDetails(details);
                }
            }

            CashoutRequest cashoutRequest = cashoutBuilder.build();

            if (isBuild) {
                // -------- BUILD: send cashout-build request and return quote --------
                CashoutBuildRequest buildRequest = CashoutBuildRequest.newBuilder()
                        .setCashout(cashoutRequest)
                        .build();

                CompletableFuture<CashoutBuildResponse> buildFuture =
                        cashoutProtocol.sendCashoutBuildAsync(buildRequest);

                buildFuture.thenAccept(resp -> {
                    MTSCashoutResponse out = mapBuildResponse(resp, ticketId);
                    responseObserver.onNext(out);
                    responseObserver.onCompleted();
                }).exceptionally(ex -> {
                    log.error("Cashout build error for ticket {}", ticketId, ex);
                    MTSCashoutResponse out = MTSCashoutResponse.newBuilder()
                            .setBetID(ticketId)
                            .setIsBuild(true)
                            .setSuccess(false)
                            .setStatus("ERROR")
                            .setReason("Exception sending cashout build: " + ex.getMessage())
                            .build();
                    responseObserver.onNext(out);
                    responseObserver.onCompleted();
                    return null;
                });

            } else {
                // -------- PLACEMENT: send cashout placement request --------
                CashoutPlacementRequest buildRequest = CashoutPlacementRequest.newBuilder()
                        .setCashout(cashoutRequest)
                        .build();

                CompletableFuture<CashoutPlacementResponse> placementFuture =
                        cashoutProtocol.sendCashoutPlacementAsync(buildRequest);

                placementFuture.thenAccept(resp -> {
                    MTSCashoutResponse out = mapPlacementResponse(resp, ticketId);
                    responseObserver.onNext(out);
                    responseObserver.onCompleted();
                }).exceptionally(ex -> {
                    log.error("Cashout placement error for ticket {}", ticketId, ex);
                    MTSCashoutResponse out = MTSCashoutResponse.newBuilder()
                            .setBetID(ticketId)
                            .setIsBuild(false)
                            .setSuccess(false)
                            .setStatus("ERROR")
                            .setReason("Exception sending cashout placement: " + ex.getMessage())
                            .build();
                    responseObserver.onNext(out);
                    responseObserver.onCompleted();
                    return null;
                });
            }

        } catch (Exception e) {
            log.error("Unexpected error building cashout request for ticket {}", ticketId, e);
            MTSCashoutResponse out = MTSCashoutResponse.newBuilder()
                    .setBetID(ticketId)
                    .setIsBuild(isBuild)
                    .setSuccess(false)
                    .setStatus("ERROR")
                    .setReason("Unexpected error: " + e.getMessage())
                    .build();
            responseObserver.onNext(out);
            responseObserver.onCompleted();
        }
    }

    /**
     * Map a CashoutBuildResponse (cashout-build) to your gRPC MTSCashoutResponse.
     * CashoutBuildResponse has status, message, ticketId, cashoutId, and a CashoutSuggestions object
     * where you can later pull suggested payout values if you need them. :contentReference[oaicite:2]{index=2}
     */
    private MTSCashoutResponse mapBuildResponse(CashoutBuildResponse resp, String ticketId) {
        MTSCashoutResponse.Builder out = MTSCashoutResponse.newBuilder()
                .setBetID(ticketId)
                .setIsBuild(true);

        // --- Status / success ---
        if (resp.getStatus() != null) {
            String status = resp.getStatus().name();
            out.setStatus(status);
            out.setSuccess("ACCEPTED".equalsIgnoreCase(status));
        } else {
            out.setStatus("UNKNOWN");
            out.setSuccess(false);
        }

        if (resp.getMessage() != null) {
            out.setReason(resp.getMessage());
        }

        // --- Suggestions from MTS ---
        CashoutSuggestions suggestions = resp.getCashout();
        if (suggestions == null) {
            return out.build();
        }

        // Cashout type + id
        if (suggestions.getCashoutType() != null) {
            out.setCashoutType(suggestions.getCashoutType());
        }
        if (suggestions.getCashoutId() != null) {
            out.setCashoutId(suggestions.getCashoutId());
        }

        // 1) Suggested cashout
        CashPayout suggested = extractFirstCashPayout(suggestions.getCashout());
        if (suggested != null && suggested.getAmount() != null) {
            BigDecimal amt = suggested.getAmount();
            out.setPayout(amt.doubleValue());
        }

        // 2) Fair cashout
        CashPayout fair = extractFirstCashPayout(suggestions.getFairCashout());
        if (fair != null && fair.getAmount() != null) {
            out.setFairPayout(fair.getAmount().doubleValue());
            // You could override currency from here if needed
        }

        // 3) Max payout
        CashPayout max = extractFirstCashPayout(suggestions.getMaxPayout());
        if (max != null && max.getAmount() != null) {
            out.setMaxPayout(max.getAmount().doubleValue());
        }

        return out.build();
    }

    /**
     * Map a CashoutResponse (cashout placement) to your gRPC MTSCashoutResponse.
     * Note: CashoutResponse in this SDK does NOT carry a payout amount, only status/message/etc. :contentReference[oaicite:3]{index=3}
     */
    private MTSCashoutResponse mapPlacementResponse(CashoutPlacementResponse resp, String ticketId) {
        MTSCashoutResponse.Builder out = MTSCashoutResponse.newBuilder()
                .setBetID(ticketId)
                .setIsBuild(false);

        if (resp.getStatus() != null) {
            String status = resp.getStatus().name(); // ACCEPTED / REJECTED / etc.
            out.setStatus(status);
            out.setSuccess("ACCEPTED".equalsIgnoreCase(status));
        } else {
            out.setStatus("UNKNOWN");
            out.setSuccess(false);
        }

        if (resp.getMessage() != null) {
            out.setReason(resp.getMessage());
        }

        // No payout on CashoutResponse type in this SDK, so we leave payout as default (0.0)
        return out.build();
    }

    private CashPayout extractFirstCashPayout(Payout[] payouts) {
        if (payouts == null) {
            return null;
        }

        for (Payout p : payouts) {
            if (p instanceof CashPayout cash) {
                return cash;
            }
        }

        return null;
    }

    private Channel getChannel(long source, String ipAddress) {
        Channel channel = Channel.newInternetChannelBuilder()
                .setIp(ipAddress)
                .setLang("EN")
                .build();

        if (source == 2 || source == 3) {
            channel = Channel.newMobileChannelBuilder()
                    .setIp(ipAddress)
                    .setLang("EN")
                    .build();
        }
        if (source == 4) {
            channel = Channel.newSmsChannelBuilder()
                    .setLang("EN")
                    .build();
        }
        if (source == 5) {
            channel = Channel.newRetailChannelBuilder()
                    .setLang("EN")
                    .build();
        }
        if (source == 6) {
            channel = Channel.newTerminalChannelBuilder()
                    .setLang("EN")
                    .build();
        }
        if (source == 7) {
            channel = Channel.newCallCentreChannelBuilder()
                    .setLang("EN")
                    .build();
        }
        if (source == 8) {
            channel = Channel.newAgentChannelBuilder()
                    .setLang("EN")
                    .build();
        }
        if (source == 9) {
            channel = Channel.newTvAppChannelBuilder()
                    .setIp(ipAddress)
                    .setLang("EN")
                    .build();
        }
        return channel;
    }
}
