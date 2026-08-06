package org.jfoundry.helidon.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jfoundry.application.transaction.TransactionRunner;

/// Consumer endpoint used by JVM and Native Image Helidon verification.
@Path("/jfoundry/transaction")
@ApplicationScoped
public class TransactionResource {

    private final TransactionRunner transactionRunner;

    @Inject
    public TransactionResource(TransactionRunner transactionRunner) {
        this.transactionRunner = transactionRunner;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String execute() throws Exception {
        return transactionRunner.call(() -> "committed");
    }
}
