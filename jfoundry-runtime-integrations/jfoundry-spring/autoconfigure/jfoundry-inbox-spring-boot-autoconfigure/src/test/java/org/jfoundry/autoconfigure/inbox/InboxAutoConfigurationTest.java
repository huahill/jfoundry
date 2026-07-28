package org.jfoundry.autoconfigure.inbox;

import org.jfoundry.application.inbox.InboxMessageStore;
import org.jfoundry.application.inbox.InboxClaim;
import org.jfoundry.application.inbox.InboxExecutionResult;
import org.jfoundry.application.inbox.InboxTemplate;
import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jfoundry.infrastructure.inbox.mybatis.InboxMessageMapper;
import org.jfoundry.infrastructure.inbox.mybatis.MybatisPlusInboxMessageStore;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InboxAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    InboxAutoConfiguration.class,
                    InboxMybatisPlusAutoConfiguration.class))
            .withBean(CountingTransactionRunner.class, CountingTransactionRunner::new);

    @Test
    void createsInboxTemplateWhenMessageStoreExists() {
        runner.withBean(InboxMessageStore.class, StubInboxMessageStore::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(InboxTemplate.class);
                    assertThat(context.getBean(InboxTemplate.class)).isInstanceOf(InboxTemplate.class);
                });
    }

    @Test
    void backsOffWhenUserProvidesInboxTemplate() {
        runner.withBean(InboxMessageStore.class, StubInboxMessageStore::new)
                .withBean(InboxTemplate.class, () -> new InboxTemplate(new StubInboxMessageStore()))
                .run(context -> assertThat(context).hasSingleBean(InboxTemplate.class));
    }

    @Test
    void createsMybatisPlusInboxMessageStoreWhenMapperExists() {
        runner.withBean(InboxMessageMapper.class, () -> mock(InboxMessageMapper.class))
                .withBean(SqlSessionFactory.class, () -> mock(SqlSessionFactory.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(InboxMessageStore.class);
                    assertThat(context.getBean(InboxMessageStore.class))
                            .isInstanceOf(MybatisPlusInboxMessageStore.class);
                });
    }

    @Test
    void createsTemplateFromAutoConfiguredMybatisPlusStore() {
        runner.withBean(InboxMessageMapper.class, () -> mock(InboxMessageMapper.class))
                .withBean(SqlSessionFactory.class, () -> mock(SqlSessionFactory.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(InboxMessageStore.class);
                    assertThat(context).hasSingleBean(InboxTemplate.class);
                });
    }

    @Test
    void usesTransactionRunnerForTheAutoConfiguredTemplate() {
        runner.withBean(InboxMessageStore.class, StubInboxMessageStore::new)
                .run(context -> {
                    assertThat(context.getBean(InboxTemplate.class)
                            .executeOnce("evt-1", "projection", () -> {}))
                            .isEqualTo(InboxExecutionResult.PROCESSED);
                    assertThat(context.getBean(CountingTransactionRunner.class).calls).isEqualTo(2);
                });
    }

    static class StubInboxMessageStore implements InboxMessageStore {

        @Override
        public InboxClaim claim(String messageId, String consumerName, java.time.Instant now,
                                java.time.Duration leaseDuration) {
            return InboxClaim.fresh("claim-token");
        }

        @Override
        public boolean markProcessed(String messageId, String consumerName, String claimToken,
                                     java.time.Instant now) {
            return true;
        }

        @Override
        public boolean markFailed(String messageId, String consumerName, String claimToken,
                                  String errorMessage, java.time.Instant now) {
            return true;
        }
    }

    static final class CountingTransactionRunner implements TransactionRunner {
        private int calls;

        @Override
        public <T> T call(TransactionOptions options, TransactionCallback<T> callback) throws Exception {
            calls++;
            return callback.execute();
        }
    }
}
