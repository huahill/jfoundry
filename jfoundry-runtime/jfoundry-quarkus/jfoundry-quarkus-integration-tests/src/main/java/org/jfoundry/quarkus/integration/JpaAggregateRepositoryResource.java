package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jfoundry.application.transaction.TransactionRunner;

import java.util.UUID;

@Path("/jfoundry/jpa")
@ApplicationScoped
public class JpaAggregateRepositoryResource {

    private final TransactionRunner transactionRunner;
    private final QuarkusJpaOrderRepository repository;

    public JpaAggregateRepositoryResource(
            TransactionRunner transactionRunner,
            QuarkusJpaOrderRepository repository) {
        this.transactionRunner = transactionRunner;
        this.repository = repository;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String persistAndModify() throws Exception {
        String orderId = UUID.randomUUID().toString();
        transactionRunner.run(() -> repository.add(QuarkusJpaOrder.create(orderId)));
        transactionRunner.run(() -> {
            QuarkusJpaOrder order = repository.findById(new QuarkusJpaOrderId(orderId));
            order.markPaid();
            repository.modify(order);
        });
        return transactionRunner.call(() ->
                repository.findById(new QuarkusJpaOrderId(orderId)).status());
    }
}
