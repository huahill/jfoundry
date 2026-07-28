package org.jfoundry.autoconfigure;

import org.jfoundry.autoconfigure.persistence.AggregatePersistenceContextAutoConfiguration;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContextAware;
import org.jfoundry.infrastructure.persistence.PersistenceStateKey;
import org.jfoundry.infrastructure.persistence.spring.SpringTransactionAggregatePersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

class AggregatePersistenceContextAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AggregatePersistenceContextAutoConfiguration.class));

    @Test
    void registersSpringTransactionContextByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AggregatePersistenceContext.class);
            assertThat(context.getBean(AggregatePersistenceContext.class))
                    .isInstanceOf(SpringTransactionAggregatePersistenceContext.class);
        });
    }

    @Test
    void userContextOverridesDefault() {
        AggregatePersistenceContext custom = new NoOpContext();

        contextRunner.withBean(AggregatePersistenceContext.class, () -> custom)
                .run(context -> assertThat(context.getBean(AggregatePersistenceContext.class))
                        .isSameAs(custom));
    }

    @Test
    void injectsSelectedContextIntoAwareBeans() {
        AggregatePersistenceContext custom = new NoOpContext();

        contextRunner.withBean(AggregatePersistenceContext.class, () -> custom)
                .withBean(ContextAwareBean.class, ContextAwareBean::new)
                .run(context -> assertThat(context.getBean(ContextAwareBean.class).context)
                        .isSameAs(custom));
    }

    @Test
    void backsOffWithoutSpringTransactionSupport() {
        contextRunner.withClassLoader(new FilteredClassLoader(TransactionSynchronizationManager.class))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(AggregatePersistenceContext.class));
    }

    private static final class NoOpContext implements AggregatePersistenceContext {
        @Override
        public <S> void attach(Object aggregate, PersistenceStateKey<S> key, S state) {
        }

        @Override
        public <S> S require(Object aggregate, PersistenceStateKey<S> key) {
            return null;
        }

        @Override
        public <S> void replace(Object aggregate, PersistenceStateKey<S> key, S state) {
        }
    }

    private static final class ContextAwareBean implements AggregatePersistenceContextAware {

        private AggregatePersistenceContext context;

        @Override
        public void setAggregatePersistenceContext(AggregatePersistenceContext context) {
            this.context = context;
        }
    }
}
