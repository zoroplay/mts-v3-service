package com.sbe.gateway;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protobuf.BettingGrpc;
import protobuf.GeneralAck;
import protobuf.MTSConnectionStatus;
import protobuf.MTSResponse;

import java.util.concurrent.TimeUnit;

public class BettingClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(BettingClient.class);

    private final ManagedChannel channel;
    private final BettingGrpc.BettingBlockingStub blocking;

    public BettingClient() {
        String host = System.getenv("BETTING_SERVICE_HOST");
        int port = Integer.parseInt(System.getenv("BETTING_SERVICE_GRPC_PORT"));
        boolean useTls   = Boolean.parseBoolean(System.getenv("BETTING_GRPC_TLS"));

        log.info("BettingEndpoint:{}:{}", host, port);

        ManagedChannelBuilder<?> b = Grpc.newChannelBuilderForAddress(host, port, InsecureChannelCredentials.create());
//        if (!useTls) b.usePlaintext();
        this.channel = b.build();
        this.blocking = BettingGrpc.newBlockingStub(channel);
    }

    public void betAcceptedResponse(Integer code, String description, String betId) {
        MTSResponse req = MTSResponse.newBuilder()
                .setBetID(betId)
                .setCode(code)
                .setDescription(description)
                .build();
        GeneralAck ack = blocking.betAcceptedResponse(req);
    }

    public void betCancelledResponse(Integer code, String description,String betId) {
        MTSResponse req = MTSResponse.newBuilder()
                .setBetID(betId)
                .setCode(code)
                .setDescription(description)
                .build();
        GeneralAck ack = blocking.betCancelledResponse(req);
    }

    public GeneralAck cancelBetAcceptedResponse(Integer code, String description,String betId) {
        MTSResponse req = MTSResponse.newBuilder()
                .setBetID(betId)
                .setCode(code)
                .setDescription(description)
                .build();
        return blocking.cancelBetAcceptedResponse(req);
    }

    public void cancelBetRejectedResponse(Integer code, String description,String betId) {
        MTSResponse req = MTSResponse.newBuilder()
                .setBetID(betId)
                .setCode(code)
                .setDescription(description)
                .build();
        GeneralAck ack =  blocking.cancelBetRejectedResponse(req);
    }

    public GeneralAck mtsConnectionChangeResponse(Integer status, String description) throws StatusRuntimeException {
        MTSConnectionStatus req = MTSConnectionStatus.newBuilder()
                .setStatus(status)
                .setDescription(description)
                .build();
        return blocking.mtsConnectionChangeResponse(req);
    }

    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
    }
}