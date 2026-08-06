package org.jfoundry.integration.nativeimage.jobrunr;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/// HTTP operation that exposes the dispatched Outbox message state for the Native Image test.
@RestController
class NativeJobRunrResource {

    private final JdbcTemplate jdbcTemplate;
    private final NativeJobRunrPublicationTracker publicationTracker;

    NativeJobRunrResource(JdbcTemplate jdbcTemplate, NativeJobRunrPublicationTracker publicationTracker) {
        this.jdbcTemplate = jdbcTemplate;
        this.publicationTracker = publicationTracker;
    }

    @GetMapping("/jfoundry/native/jobrunr/dispatch")
    NativeJobRunrDispatchResult dispatchResult() {
        boolean published = Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                select exists (
                    select 1 from jfoundry_outbox_event
                    where event_id = ? and status = 'PUBLISHED'
                )
                """, Boolean.class, NativeJobRunrApplication.EVENT_ID));
        return new NativeJobRunrDispatchResult(publicationTracker.published(), published);
    }
}
