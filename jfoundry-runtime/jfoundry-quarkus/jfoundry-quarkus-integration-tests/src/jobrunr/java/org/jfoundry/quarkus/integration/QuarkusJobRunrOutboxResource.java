package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jfoundry.application.transaction.TransactionRunner;

import java.util.List;

/// Reports whether JobRunr, not the built-in scheduler, published the seeded Outbox row.
@Path("/jfoundry/native/jobrunr")
@ApplicationScoped
public class QuarkusJobRunrOutboxResource {

    private final TransactionRunner transactionRunner;
    private final EntityManager entityManager;

    @Inject
    public QuarkusJobRunrOutboxResource(TransactionRunner transactionRunner, EntityManager entityManager) {
        this.transactionRunner = transactionRunner;
        this.entityManager = entityManager;
    }

    @GET
    @Path("/dispatch")
    @Produces(MediaType.APPLICATION_JSON)
    public String dispatchResult() {
        return transactionRunner.call(this::dispatchResultInTransaction);
    }

    private String dispatchResultInTransaction() {
        List<?> statuses = entityManager.createNativeQuery(
                        "select status from jfoundry_outbox_event where event_id = ?1")
                .setParameter(1, QuarkusJobRunrOutboxSeed.EVENT_ID)
                .getResultList();
        boolean published = !statuses.isEmpty() && "PUBLISHED".equals(String.valueOf(statuses.get(0)));
        boolean registered = count(
                "select count(*) from jobrunr_recurring_jobs where trim(id) = 'jfoundry-outbox-dispatch'") > 0;
        boolean dispatched = count(
                "select count(*) from jobrunr_jobs where state = 'SUCCEEDED' and trim(recurringjobid) = 'jfoundry-outbox-dispatch'") > 0;
        return "{\"registered\":" + registered
                + ",\"dispatched\":" + dispatched
                + ",\"published\":" + published
                + "}";
    }

    private int count(String sql) {
        Object value = entityManager.createNativeQuery(sql).getSingleResult();
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }
}
