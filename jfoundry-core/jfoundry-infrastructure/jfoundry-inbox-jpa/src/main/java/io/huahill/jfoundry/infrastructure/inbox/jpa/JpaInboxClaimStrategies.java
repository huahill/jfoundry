package io.huahill.jfoundry.infrastructure.inbox.jpa;

import java.util.Locale;

/// Selects a built-in claim strategy for supported database products.
public final class JpaInboxClaimStrategies {

    private JpaInboxClaimStrategies() {
    }

    public static JpaInboxClaimStrategy forProductName(String productName) {
        String normalizedProductName = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
        if (normalizedProductName.contains("postgresql")) {
            return new PostgreSqlJpaInboxClaimStrategy();
        }
        if (normalizedProductName.contains("mysql")) {
            return new MySqlJpaInboxClaimStrategy();
        }
        throw new IllegalStateException("Unsupported database product: " + productName
                + ". Provide a JpaInboxClaimStrategy for this database.");
    }
}
