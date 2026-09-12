package org.jfoundry.infrastructure.persistence;

import org.jfoundry.domain.entity.agg.BaseAggregateRoot;
import org.jmolecules.ddd.types.Identifier;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractAggregateRepositoryTest {

    @Test
    void addNotifiesObserverOnlyAfterCompletePersistence() {
        List<String> operations = new ArrayList<>();
        TestRepository repository = repository(operations);
        TestAggregate aggregate = TestAggregate.create("one");

        repository.add(aggregate);

        assertThat(operations).containsExactly("add:one", "observe:one");
    }

    @Test
    void failedAddDoesNotNotifyObserver() {
        List<String> operations = new ArrayList<>();
        TestRepository repository = repository(operations);
        repository.failAdd = true;

        assertThatThrownBy(() -> repository.add(TestAggregate.create("failed")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("persistence failed");

        assertThat(operations).containsExactly("add:failed");
    }

    @Test
    void failedPersistenceOperationsAreTranslatedWithTheirLifecycleOperation() {
        List<String> operations = new ArrayList<>();
        TestRepository repository = repository(operations);
        TestAggregate aggregate = TestAggregate.create("failed");
        IllegalArgumentException translated = new IllegalArgumentException("translated");
        repository.setPersistenceFailureTranslator((operation, failure) -> {
            operations.add("translate:" + operation);
            assertThat(failure).isInstanceOf(IllegalStateException.class)
                    .hasMessage("persistence failed");
            return translated;
        });

        repository.failure = PersistenceOperation.FIND;
        assertThatThrownBy(() -> repository.findById(aggregate.getId())).isSameAs(translated);
        repository.failure = PersistenceOperation.ADD;
        assertThatThrownBy(() -> repository.add(aggregate)).isSameAs(translated);
        repository.failure = PersistenceOperation.MODIFY;
        assertThatThrownBy(() -> repository.modify(aggregate)).isSameAs(translated);
        repository.failure = PersistenceOperation.REMOVE;
        assertThatThrownBy(() -> repository.remove(aggregate)).isSameAs(translated);

        assertThat(operations).containsExactly(
                "find:failed", "translate:FIND",
                "add:failed", "translate:ADD",
                "modify:failed", "translate:MODIFY",
                "remove:failed", "translate:REMOVE");
    }

    @Test
    void batchPersistsEveryCompleteAggregateBeforeNotifyingAnyObserver() {
        List<String> operations = new ArrayList<>();
        TestRepository repository = repository(operations);

        repository.addAll(List.of(TestAggregate.create("one"), TestAggregate.create("two")));

        assertThat(operations).containsExactly(
                "add:one", "add:two", "observe:one", "observe:two");
    }

    @Test
    void addDoesNotNotifyWhenPersistenceObserverIsMissing() {
        List<String> operations = new ArrayList<>();
        TestRepository repository = new TestRepository(operations);

        repository.add(TestAggregate.create("one"));

        assertThat(operations).containsExactly("add:one");
    }

    @Test
    void setAggregatePersistenceObserverRejectsNull() {
        TestRepository repository = new TestRepository(new ArrayList<>());

        assertThatThrownBy(() -> repository.setAggregatePersistenceObserver(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("AggregatePersistenceObserver must not be null.");
    }

    @Test
    void delegatesFindModifyAndRemoveToStorageOperations() {
        List<String> operations = new ArrayList<>();
        TestRepository repository = repository(operations);
        TestAggregate aggregate = TestAggregate.create("one");

        assertThat(repository.findById(aggregate.getId())).isSameAs(repository.loaded);
        repository.modify(aggregate);
        repository.remove(aggregate);

        assertThat(operations).containsExactly(
                "find:one", "modify:one", "observe:one", "remove:one", "observe:one");
    }

    private static TestRepository repository(List<String> operations) {
        TestRepository repository = new TestRepository(operations);
        repository.setAggregatePersistenceObserver(new RecordingContext(operations));
        return repository;
    }

    private static final class TestRepository
            extends AbstractAggregateRepository<TestAggregate, TestAggregateId> {

        private final List<String> operations;
        private final TestAggregate loaded = TestAggregate.create("loaded");
        private boolean failAdd;
        private PersistenceOperation failure;

        private TestRepository(List<String> operations) {
            this.operations = operations;
        }

        @Override
        protected TestAggregate doFindById(TestAggregateId id) {
            operations.add("find:" + id.value());
            failIfConfigured(PersistenceOperation.FIND);
            return loaded;
        }

        @Override
        protected void doAdd(TestAggregate aggregate) {
            operations.add("add:" + aggregate.getId().value());
            if (failAdd) {
                throw new IllegalStateException("persistence failed");
            }
            failIfConfigured(PersistenceOperation.ADD);
        }

        @Override
        protected void doModify(TestAggregate aggregate) {
            operations.add("modify:" + aggregate.getId().value());
            failIfConfigured(PersistenceOperation.MODIFY);
        }

        @Override
        protected void doRemove(TestAggregate aggregate) {
            operations.add("remove:" + aggregate.getId().value());
            failIfConfigured(PersistenceOperation.REMOVE);
        }

        private void failIfConfigured(PersistenceOperation operation) {
            if (failure == operation) {
                throw new IllegalStateException("persistence failed");
            }
        }
    }

    private static final class RecordingContext implements AggregatePersistenceObserver {

        private final List<String> operations;

        private RecordingContext(List<String> operations) {
            this.operations = operations;
        }

        @Override
        public void afterPersisted(org.jmolecules.ddd.types.AggregateRoot<?, ?> aggregate) {
            TestAggregate testAggregate = (TestAggregate) aggregate;
            operations.add("observe:" + testAggregate.getId().value());
        }
    }

    private static final class TestAggregate extends BaseAggregateRoot<TestAggregate, TestAggregateId> {

        private TestAggregate(TestAggregateId id) {
            super(id);
        }

        private static TestAggregate create(String id) {
            return new TestAggregate(new TestAggregateId(id));
        }
    }

    private record TestAggregateId(String value) implements Identifier, Serializable {
    }
}
