package io.huahill.jfoundry.application.outbox;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/// Runtime identifier utilities shared by framework adapters.
public final class OutboxRuntimeIds {

    private OutboxRuntimeIds() {
    }

    public static String generateClaimerId() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "unknown";
        }
        return hostname + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
