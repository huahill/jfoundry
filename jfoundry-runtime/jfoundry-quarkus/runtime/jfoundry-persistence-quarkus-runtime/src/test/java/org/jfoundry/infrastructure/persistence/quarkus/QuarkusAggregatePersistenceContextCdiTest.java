package org.jfoundry.infrastructure.persistence.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.PersistenceStateKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class QuarkusAggregatePersistenceContextCdiTest {

    @Inject
    AggregatePersistenceContext persistenceContext;

    @Test
    void allowsAnApplicationBeanToReplaceTheDefaultPersistenceContext() {
        assertThat(persistenceContext).isInstanceOf(ApplicationPersistenceContext.class);
    }

    @ApplicationScoped
    public static class ApplicationPersistenceContext implements AggregatePersistenceContext {

        @Override
        public <S> void attach(Object aggregate, PersistenceStateKey<S> key, S state) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S> S require(Object aggregate, PersistenceStateKey<S> key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S> void replace(Object aggregate, PersistenceStateKey<S> key, S state) {
            throw new UnsupportedOperationException();
        }
    }
}
