package com.sbe.gateway.workers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sbe.gateway.BettingClient;
import com.sbe.gateway.handlers.BetPendingResponseHandler;
import com.sportradar.mbs.sdk.MbsSdk;
import com.sportradar.mbs.sdk.entities.channel.Channel;
import com.sportradar.mbs.sdk.entities.common.*;
import com.sportradar.mbs.sdk.entities.odds.Odds;
import com.sportradar.mbs.sdk.entities.request.TicketRequest;
import com.sportradar.mbs.sdk.entities.selection.Selection;
import com.sportradar.mbs.sdk.entities.selection.SystemSelection;
import com.sportradar.mbs.sdk.entities.selection.UfSelection;
import com.sportradar.mbs.sdk.entities.selection.WaysSelection;
import com.sportradar.mbs.sdk.entities.stake.Stake;
import com.sportradar.mbs.sdk.protocol.TicketProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protobuf.MTSBet;
import protobuf.MTSSelection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BetPending implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(BetPending.class);
    MbsSdk mbsSdk;
    BettingClient bettingClient;
    public MTSBet message;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)        // pretty-print
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    public BetPending(MbsSdk mbsSdk,BettingClient bettingClient, MTSBet message){
        this.mbsSdk = mbsSdk;
        this.message = message;
        this.bettingClient = bettingClient;
    }

    @Override
    public void run() {
        // 1. Get the TicketProtocol
        TicketProtocol ticketProtocol = mbsSdk.getTicketProtocol();
        BetPendingResponseHandler responseHandler = new BetPendingResponseHandler(bettingClient);

        // 2. Build the TicketRequest
        MTSBet object = message;
        String ticketId = System.getenv("mts_bookmaker_id") + "_" + object.getBetID();
        String mts_currency = System.getenv("mts_currency");
        int limitID = Integer.parseInt(System.getenv("mts_limit_id"));
        long profile_id = object.getProfileID();
        long source = object.getBetSource();

        // build customer, channel and ticket context
        EndCustomer endCustomer = EndCustomer.newBuilder()
                .setId(String.valueOf(profile_id))
                .build();

        Channel channel = getChannel(source, object.getIpAddress());

        TicketContext ticketContext = TicketContext.newBuilder()
                .setEndCustomer(endCustomer)
                .setChannel(channel)
                .setLimitId(limitID)
                .build();

        // build bets array
        Stake stake = Stake.newCashStakeBuilder()
                .setAmount(BigDecimal.valueOf(object.getStake()))
                .setCurrency(mts_currency)
                .build();

        // Create a bet builder and loop through selections
        List<Selection> betSelections = new ArrayList<>();

        if (object.getSelectionsCount() > 0) {
            // structured mode (supports system/ways/bankers/multisystem)
            for (int i = 0; i < object.getSelectionsCount(); i++) {
                betSelections.add(buildSelectionTree(object.getSelections(i)));
            }
        }

        List<Bet> bets = new ArrayList<>();
        Bet bet = Bet.newBuilder()
                .setStake(stake)
                .setSelections(betSelections)
                .setContext(
                        BetContext.newBuilder()
                                .setOddsChange(OddsChange.ANY)
                                .build()
                )
                .build();

        bets.add(bet);

        TicketRequest ticketRequest = TicketRequest.newBuilder()
                .setTicketId(ticketId)
                .setContext(ticketContext)
                .setBets(bets)
                .build();

//        String json = MAPPER.writeValueAsString(ticketRequest);
//        log.info("BetPending thread started: sending ticket request for ticketId {}:\n{}", ticketId, json);

        ticketProtocol
                .sendTicketAsync(ticketRequest)  // returns CompletableFuture<TicketResponse>
                .thenAccept(resp -> {
                    responseHandler.onTicketResponse(ticketId, resp);
                })
                .exceptionally(ex -> {
                    log.error("BetPending thread finished: sending ticket exception", ex);
                    responseHandler.onTicketError(ticketId);
                    return null;
                });
    }

    private Channel getChannel(long source, String ipAddress){
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

    private UfSelection toUf(MTSSelection s) {
        String eventUrn = "sr:match:" + s.getMatchID();

        UfSelection.Builder sb = UfSelection.newBuilder()
                .setEventId(eventUrn)
                .setProductId(String.valueOf(s.getProducerID()))
                .setMarketId(String.valueOf(s.getMarketID()))
                .setOutcomeId(s.getOutcomeID())
                .setOdds(
                        Odds.newDecimalOddsBuilder()
                                .setValue(new BigDecimal(String.valueOf(s.getOdds())))
                                .build()
                );

        String specifier = s.getSpecifier();
        if (specifier != null) {
            specifier = specifier.replace("?", "&");
            if (!specifier.isBlank()) sb.setSpecifiers(specifier);
        }
        return sb.build();
    }

    private Selection buildSelectionTree(MTSSelection node) {
        switch (node.getType()) {
            case UF: {
                return toUf(node);
            }

            case WAYS: {
                List<Selection> children = new ArrayList<>();
                for (int i = 0; i < node.getSelectionsCount(); i++) {
                    children.add(buildSelectionTree(node.getSelections(i)));
                }

                WaysSelection.Builder wb = Selection.newWaysSelectionBuilder()
                        .setSelections(children);

                return wb.build();
            }

            case SYSTEM: {
                List<Selection> children = new ArrayList<>();
                for (int i = 0; i < node.getSelectionsCount(); i++) {
                    children.add(buildSelectionTree(node.getSelections(i)));
                }

                List<Integer> sizes = new ArrayList<>();
                for (int i = 0; i < node.getSizeCount(); i++) {
                    sizes.add((int) node.getSize(i));
                }

                SystemSelection.Builder sysb = Selection.newSystemSelectionBuilder()
                        .setSelections(children)
                        .setSize(sizes);

                return sysb.build();
            }

            default:
                throw new IllegalArgumentException("Unsupported selection type: " + node.getType());
        }
    }
}
