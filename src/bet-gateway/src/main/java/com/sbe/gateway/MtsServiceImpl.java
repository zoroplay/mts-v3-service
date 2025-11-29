package com.sbe.gateway;

import com.sbe.gateway.workers.BetCancel;
import com.sbe.gateway.workers.BetPending;
import com.sbe.gateway.workers.CashoutPending;
import com.sportradar.mbs.sdk.MbsSdk;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protobuf.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class MtsServiceImpl extends MtsGrpc.MtsImplBase {
    private static final Logger log = LoggerFactory.getLogger(MtsServiceImpl.class);
    private final MbsSdk sdk;
    ThreadPoolExecutor executor;
    BettingClient bettingClient;

    public MtsServiceImpl(MbsSdk sdk,BettingClient bettingClient) {
        this.sdk = sdk;
        this.bettingClient = bettingClient;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(100);
    }

    @Override
    public void submitBet(MTSBet req, StreamObserver<MtsGeneralAck> out) {
        log.info("Submitting Bet");
        try {
            executor.submit(new BetPending(sdk,bettingClient, req));
            out.onNext(MtsGeneralAck.newBuilder().setDescription("ticket submitted").build());
            out.onCompleted();
        } catch (Exception e) {
            out.onNext(MtsGeneralAck.newBuilder().setDescription("ERR: " + e.getMessage()).build());
            out.onCompleted();
        }
    }

    @Override
    public void submitBetCancel(BetCancelRequest req, StreamObserver<MtsGeneralAck> out) {
        log.info("Cancelling Bet");
        try {
            executor.submit(new BetCancel(sdk,bettingClient, req));
            out.onNext(MtsGeneralAck.newBuilder().setDescription("ticket submitted for cancellation").build());
            out.onCompleted();
        } catch (Exception e) {
            out.onNext(MtsGeneralAck.newBuilder().setDescription("ERR: " + e.getMessage()).build());
            out.onCompleted();
        }
    }

    @Override
    public void submitCashout(MTSCashoutRequest req, StreamObserver<MTSCashoutResponse> out) {
        log.info("Submitting Cashout (isBuild = {})", req.getIsBuild());
        try {
            // For cashout we want to respond *on this RPC*,
            // so we pass the StreamObserver down to the worker
            executor.submit(new CashoutPending(sdk, req, out));
            // Do NOT call out.onCompleted() here – the worker will
        } catch (Exception e) {
            MTSCashoutResponse err = MTSCashoutResponse.newBuilder()
                    .setBetID(req.getBetID())
                    .setIsBuild(req.getIsBuild())
                    .setSuccess(false)
                    .setStatus("ERROR")
                    .setReason("ERR: " + e.getMessage())
                    .build();
            out.onNext(err);
            out.onCompleted();
        }
    }
}
