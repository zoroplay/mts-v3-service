package com.sbe.gateway;

import com.sportradar.mbs.sdk.MbsSdk;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class GrpcServer {
    private final Server server;

    public GrpcServer(int port, MbsSdk sdk,BettingClient bettingClient) {
        this.server = ServerBuilder.forPort(port)
                .addService(new MtsServiceImpl(sdk,bettingClient))
                .build();
    }

    public void start() throws Exception {
        server.start();
        System.out.println("gRPC server started on :" + server.getPort());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** Shutting down gRPC server");
            server.shutdown();
        }));
    }

    public void blockUntilShutdown() throws InterruptedException {
        server.awaitTermination();
    }
}
