package org.jfoundry.infrastructure.persistence;

import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Identifier;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThatCode;

class NonEventAggregateRepositoryTest {

    @Test
    void acceptsAnAggregateThatDoesNotRecordDomainEvents() {
        assertThatCode(() -> new PlainRepository().add(new PlainAggregate(new PlainId("plain"))))
                .doesNotThrowAnyException();
    }

    private static final class PlainRepository
            extends AbstractAggregateRepository<PlainAggregate, PlainId> {

        @Override
        protected PlainAggregate doFindById(PlainId id) {
            return null;
        }

        @Override
        protected void doAdd(PlainAggregate aggregate) {
        }

        @Override
        protected void doModify(PlainAggregate aggregate) {
        }

        @Override
        protected void doRemove(PlainAggregate aggregate) {
        }
    }

    private record PlainAggregate(PlainId id) implements AggregateRoot<PlainAggregate, PlainId> {

        @Override
        public PlainId getId() {
            return id;
        }
    }

    private record PlainId(String value) implements Identifier, Serializable {
    }
}
