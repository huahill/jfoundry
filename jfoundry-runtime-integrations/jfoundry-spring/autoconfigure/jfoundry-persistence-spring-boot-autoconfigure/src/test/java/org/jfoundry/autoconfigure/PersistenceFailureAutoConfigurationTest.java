package org.jfoundry.autoconfigure;

import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.autoconfigure.persistence.PersistenceFailureAutoConfiguration;
import org.jfoundry.domain.entity.agg.BaseAggregateRoot;
import org.jfoundry.infrastructure.persistence.AbstractAggregateRepository;
import org.jfoundry.infrastructure.persistence.AbstractPersistenceAdapter;
import org.jfoundry.infrastructure.persistence.PersistenceFailureTranslator;
import org.jfoundry.infrastructure.persistence.spring.SpringDataAccessFailureTranslator;
import org.jmolecules.ddd.types.Identifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceFailureAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PersistenceFailureAutoConfiguration.class))
            .withUserConfiguration(RepositoryConfiguration.class);

    @Test
    void registersAndInjectsDefaultSpringTranslator() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PersistenceFailureTranslator.class);
            assertThat(context.getBean(PersistenceFailureTranslator.class))
                    .isInstanceOf(SpringDataAccessFailureTranslator.class);

            FailingRepository repository = context.getBean(FailingRepository.class);
            assertThatThrownBy(() -> repository.add(TestAggregate.create("one")))
                    .isInstanceOf(ExternalAccessException.class)
                    .hasCauseInstanceOf(DataAccessResourceFailureException.class);

            FailingQueryAdapter queryAdapter = context.getBean(FailingQueryAdapter.class);
            assertThatThrownBy(queryAdapter::findTitle)
                    .isInstanceOf(ExternalAccessException.class)
                    .hasCauseInstanceOf(DataAccessResourceFailureException.class);
        });
    }

    @Test
    void userTranslatorOverridesDefaultAndIsInjected() {
        contextRunner
                .withBean(PersistenceFailureTranslator.class,
                        () -> (operation, failure) -> new IllegalArgumentException("custom", failure))
                .run(context -> {
                    assertThat(context).hasSingleBean(PersistenceFailureTranslator.class);
                    FailingRepository repository = context.getBean(FailingRepository.class);
                    assertThatThrownBy(() -> repository.add(TestAggregate.create("one")))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessage("custom")
                            .hasCauseInstanceOf(DataAccessResourceFailureException.class);
                });
    }

    @Configuration
    static class RepositoryConfiguration {

        @Bean
        FailingRepository failingRepository() {
            return new FailingRepository();
        }

        @Bean
        FailingQueryAdapter failingQueryAdapter() {
            return new FailingQueryAdapter();
        }
    }

    static class FailingRepository extends AbstractAggregateRepository<TestAggregate, TestAggregateId> {

        @Override
        protected TestAggregate doFindById(TestAggregateId id) {
            return null;
        }

        @Override
        protected void doAdd(TestAggregate aggregate) {
            throw new DataAccessResourceFailureException("database unavailable");
        }

        @Override
        protected void doModify(TestAggregate aggregate) {
        }

        @Override
        protected void doRemove(TestAggregate aggregate) {
        }
    }

    static class FailingQueryAdapter extends AbstractPersistenceAdapter {

        String findTitle() {
            return query(() -> {
                throw new DataAccessResourceFailureException("database unavailable");
            });
        }
    }

    static final class TestAggregate extends BaseAggregateRoot<TestAggregate, TestAggregateId> {

        private TestAggregate(TestAggregateId id) {
            super(id);
        }

        static TestAggregate create(String id) {
            return new TestAggregate(new TestAggregateId(id));
        }
    }

    record TestAggregateId(String value) implements Identifier, Serializable {
    }
}
