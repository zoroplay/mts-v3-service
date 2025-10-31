package com.sportradar.mbs.sdk.internal.config;

import java.net.URI;
import java.time.Duration;

public interface TokenProviderConfig {
    URI getAuthServer();

    String getAuthClientId();

    String getAuthClientSecret();

    String getAuthAudience();

    Duration getAuthRequestTimeout();

    Duration getAuthRetryDelay();
}
