package com.sbe.gateway;

import com.sportradar.mbs.sdk.MbsSdk;
import com.sportradar.mbs.sdk.MbsSdkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        // *** Pull config from env ***
        URI wsServer        = URI.create(getEnv("MBS_WS_SERVER", "wss://wss.dataplane-nonprod.sportradar.dev"));
        URI authServer      = URI.create(getEnv("MBS_AUTH_SERVER", "https://auth.sportradar.com/oauth/token"));
        String clientId     = getEnv("MBS_CLIENT_ID", "0BUCPReZtlxw4OvdAJpUWFSyQf77QeIW");
        String clientSecret = getEnv("MBS_CLIENT_SECRET", "5TtmC7b60RbTytGzZJAzL7szTcg5R_D6_O0wuHkPtDV3N5rbQRh-iH-VFfLjo06-");
        String audience     = getEnv("MBS_AUDIENCE", "mbs-dp-non-prod-wss");
        long operatorId     = Long.parseLong(getEnv("MBS_OPERATOR_ID", "37228"));
        int port = Integer.parseInt(System.getenv("GRPC_PORT"));

        // *** SDK ***
        MbsSdkConfig cfg = new MbsSdkConfig(wsServer, authServer, clientId, clientSecret, audience, operatorId);
        MbsSdk sdk = new MbsSdk(cfg);
        sdk.connect(); // establishes the WebSocket & auth handled by SDK

        // Create Betting client
        BettingClient bettingClient = null;
        try {
            bettingClient = new BettingClient();
        } catch (Exception e) {
            log.error("Failed to establish BettingClient GRPC conn: {}", e.getMessage());
            return;
        }

        // Start gRPC server and pass SDK into the handlers
        GrpcServer server = new GrpcServer(port, sdk,bettingClient);
        server.start();
        server.blockUntilShutdown();
    }

    private static String getEnv(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? def : v;
    }
}
