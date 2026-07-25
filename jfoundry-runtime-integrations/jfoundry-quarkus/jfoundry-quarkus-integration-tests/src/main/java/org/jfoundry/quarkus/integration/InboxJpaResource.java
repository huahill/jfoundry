package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jfoundry.application.inbox.InboxTemplate;

import java.util.UUID;

@Path("/jfoundry/inbox")
@ApplicationScoped
public class InboxJpaResource {

    private final InboxTemplate inboxTemplate;

    public InboxJpaResource(InboxTemplate inboxTemplate) {
        this.inboxTemplate = inboxTemplate;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String processAndSkipDuplicate() {
        String messageId = UUID.randomUUID().toString();
        boolean first = inboxTemplate.executeOnce(messageId, "orders", () -> {
        });
        boolean duplicate = inboxTemplate.executeOnce(messageId, "orders", () -> {
        });
        return first + "," + duplicate;
    }

    @GET
    @Path("/retry")
    @Produces(MediaType.TEXT_PLAIN)
    public String failAndRetry() {
        String messageId = UUID.randomUUID().toString();
        try {
            inboxTemplate.executeOnce(messageId, "orders", () -> {
                throw new IllegalStateException("temporary failure");
            });
        } catch (IllegalStateException ignored) {
            // The second delivery verifies the Inbox retry state transition.
        }
        return Boolean.toString(inboxTemplate.executeOnce(messageId, "orders", () -> {
        }));
    }
}
