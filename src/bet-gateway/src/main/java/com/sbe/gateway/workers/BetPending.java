package com.sbe.gateway.workers;

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
import com.sportradar.mbs.sdk.entities.stake.Stake;
import com.sportradar.mbs.sdk.protocol.TicketProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protobuf.MTSBet;
import protobuf.MTSBetSlip;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BetPending implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(BetPending.class);
    MbsSdk mbsSdk;
    BettingClient bettingClient;
    public MTSBet message;
//    private static final ObjectMapper MAPPER = new ObjectMapper()
//            .enable(SerializationFeature.INDENT_OUTPUT)        // pretty-print
//            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
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
        List<Selection> ufSelections = new ArrayList<>();
        for (int i = 0; i < object.getBetsCount(); i++) {
            MTSBetSlip slip = object.getBets(i);

            String eventUrn = "sr:match:" + slip.getMatchID();

            UfSelection.Builder sb = UfSelection.newBuilder()
                    .setEventId(eventUrn)
                    .setProductId(String.valueOf(slip.getProducerID()))
                    .setMarketId(String.valueOf(slip.getMarketID()))
                    .setOutcomeId(slip.getOutcomeID())
                    .setOdds(
                            Odds.newDecimalOddsBuilder()
                                    .setValue(new BigDecimal(String.valueOf(slip.getOdds())))
                                    .build()
                    );

            // only set specifiers if market requires it
            String specifier = slip.getSpecifier();
            specifier = specifier.replace("?", "&");
            if (!specifier.isBlank()) {
                sb.setSpecifiers(specifier);
            }

            ufSelections.add(sb.build());
        }

        List<Bet> bets = new ArrayList<>();
        if ("system".equalsIgnoreCase(object.getBetType())) {

            // systemSizes => the "size" array in JSON, e.g. [3] for 3/4
            List<Integer> sizes = new ArrayList<>();
            for (int i = 0; i < object.getSystemSizesCount(); i++) {
                sizes.add((int) object.getSystemSizes(i));
            }

            SystemSelection systemSelection = SystemSelection.newBuilder()
                    .setSelections(ufSelections)
                    .setSize(sizes)
                    .build();

            List<Selection> selections = new ArrayList<>();
            selections.add(systemSelection);

            Bet bet = Bet.newBuilder()
                    .setStake(stake)
                    .setSelections(selections)
                    .setContext(
                            BetContext.newBuilder()
                                    .setOddsChange(OddsChange.ANY)
                                    .build()
                    )
                    .build();

            bets.add(bet);

        } else {

            Bet bet = Bet.newBuilder()
                    .setStake(stake)
                    .setSelections(ufSelections)
                    .setContext(
                            BetContext.newBuilder()
                                    .setOddsChange(OddsChange.ANY)
                                    .build()
                    ).build();

            bets.add(bet);
        }

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
}
