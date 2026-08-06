package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jfoundry.application.transaction.TransactionRunner;

@Path("/jfoundry/transaction")
@ApplicationScoped
public class TransactionRunnerResource {

    private final TransactionRunner transactionRunner;

    public TransactionRunnerResource(TransactionRunner transactionRunner) {
        this.transactionRunner = transactionRunner;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String execute() throws Exception {
        return transactionRunner.call(() -> "committed");
    }
}
