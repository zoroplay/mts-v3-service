package com.sbe.gateway;

import com.sbe.gateway.workers.BetPending;
import com.sportradar.mbs.sdk.MbsSdk;
import com.sportradar.mbs.sdk.protocol.TicketProtocol;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protobuf.BetCancelRequest;
import protobuf.MtsGrpc;
import protobuf.MTSBet;
import protobuf.MtsGeneralAck;

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
        try {
            TicketProtocol tp = sdk.getTicketProtocol();

            // ===== MAP YOUR gRPC REQUEST -> SDK REQUEST =====
            // The exact types depend on your SDK’s published model classes.
            // Typical pattern (adjust package/class names based on your SDK):
            //
            // CancelTicketRequest cancelReq = new CancelTicketRequest(
            //     req.getBetID(),  // or build with a builder/DTO your SDK provides
            //     ...reason...     // map cancellation reason if present
            // );
            //
            // CancelTicketResponse resp = tp.sendCancelTicket(cancelReq); // or sendCancelTicketAsync(...).get()

            // For now, just ACK so the wiring is proven:
            out.onNext(MtsGeneralAck.newBuilder().setDescription("cancel request submitted").build());
            out.onCompleted();
        } catch (Exception e) {
            out.onNext(MtsGeneralAck.newBuilder().setDescription("ERR: " + e.getMessage()).build());
            out.onCompleted();
        }
    }
}
