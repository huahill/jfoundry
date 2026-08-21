package org.jfoundry.infrastructure.persistence.mybatis;

import org.jfoundry.domain.event.EventRecordable;
import org.jfoundry.infrastructure.persistence.mybatis.support.TestOrder;
import org.jfoundry.infrastructure.persistence.mybatis.support.TestOrderId;
import org.jfoundry.infrastructure.persistence.mybatis.support.TestOrderMapper;
import org.jfoundry.infrastructure.persistence.mybatis.support.TestOrderRepository;
import org.jfoundry.infrastructure.persistence.mybatis.support.TestOrderStatus;
import org.jmolecules.event.types.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.TypeVariable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Integration tests for MybatisPlusAggregateRepository.
/// <p>
/// Runs the full path with embedded H2 and covers:
/// - add / addAll creation path plus primary-key conflict defense.
/// - modify / modifyAll update path plus zero-row defense.
/// - remove path plus zero-row defense.
/// - findById loading path.
/// - context registration path, registering aggregates with DomainEventContext after successful
///   add/modify/remove.
@SpringBootTest(classes = PersistenceTestConfig.class)
class MybatisPlusAggregateRepositoryIntegrationTest {

    @Autowired
    private TestOrderRepository repository;

    @Autowired
    private TestOrderMapper mapper;

    @Autowired
    private PersistenceTestConfig.TestDomainEventContext domainEventContext;

    @Test
    void mybatisPlusRepositoryShouldUseJavaStyleTypeParameterNames() {
        assertThat(typeParameterNames(MybatisPlusAggregateRepository.class))
                .containsExactly("T", "ID", "D", "K");
    }

    @BeforeEach
    void cleanDb() {
        mapper.delete(null);
        domainEventContext.drainRegistered();
    }

    private static List<String> typeParameterNames(Class<?> type) {
        return List.of(type.getTypeParameters()).stream().map(TypeVariable::getName).toList();
    }

    // ---- add path ----

    @Test
    void addNewAggregateInsertsOneRowRegistersAggregateAndKeepsEvents() {
        TestOrderId id = new TestOrderId("ORD-001");
        TestOrder order = TestOrder.create(id, 100);
        List<DomainEvent> expectedEvents = order.drainEvents();
        assertThat(expectedEvents).hasSize(1);
        TestOrder orderWithEvents = TestOrder.create(id, 100);

        repository.add(orderWithEvents);

        TestOrder loaded = repository.findById(id);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getStatus()).isEqualTo(TestOrderStatus.CREATED);
        assertThat(loaded.getAmount()).isEqualTo(100);

        assertThat(domainEventContext.drainRegistered()).containsExactly(orderWithEvents);
        assertThat(orderWithEvents.drainEvents())
                .singleElement()
                .isInstanceOf(expectedEvents.getFirst().getClass());
    }

    @Test
    void addDuplicateIdThrowsAndDoesNotRegisterAggregate() {
        TestOrderId id = new TestOrderId("ORD-DUP");
        TestOrder first = TestOrder.create(id, 100);
        repository.add(first);
        domainEventContext.drainRegistered();

        TestOrder second = TestOrder.create(id, 200);
        assertThatThrownBy(() -> repository.add(second))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(domainEventContext.drainRegistered()).isEmpty();
        assertThat(second.drainEvents()).hasSize(1);
    }

    // ---- modify path ----

    @Test
    void modifyExistingAggregateUpdatesRegistersAggregateAndKeepsEvents() {
        TestOrderId id = new TestOrderId("ORD-MOD-1");
        repository.add(TestOrder.create(id, 100));
        domainEventContext.drainRegistered();

        TestOrder loaded = repository.findById(id);
        loaded.markPaid();

        repository.modify(loaded);

        TestOrder afterModify = repository.findById(id);
        assertThat(afterModify.getStatus()).isEqualTo(TestOrderStatus.PAID);

        assertThat(domainEventContext.drainRegistered()).containsExactly(loaded);
        assertThat(loaded.drainEvents())
                .singleElement()
                .isInstanceOfSatisfying(DomainEvent.class,
                        event -> assertThat(event.getClass().getSimpleName()).isEqualTo("TestOrderStatusChangedEvent"));
    }

    @Test
    void modifyMissingAggregateAffectsZeroRowsAndThrowsIllegalStateException() {
        TestOrderId ghostId = new TestOrderId("ORD-GHOST");
        TestOrder ghost = TestOrder.create(ghostId, 100);

        assertThatThrownBy(() -> repository.modify(ghost))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("modify affected 0 rows");

        assertThat(domainEventContext.drainRegistered()).isEmpty();
        assertThat(ghost.drainEvents()).hasSize(1);
    }

    // ---- addAll / modifyAll path ----

    @Test
    void addAllInsertsBatchRegistersAllAggregatesInOrderAndKeepsEvents() {
        TestOrder o1 = TestOrder.create(new TestOrderId("ORD-B-1"), 10);
        TestOrder o2 = TestOrder.create(new TestOrderId("ORD-B-2"), 20);
        TestOrder o3 = TestOrder.create(new TestOrderId("ORD-B-3"), 30);

        repository.addAll(List.of(o1, o2, o3));

        assertThat(repository.findById(o1.getId())).isNotNull();
        assertThat(repository.findById(o2.getId())).isNotNull();
        assertThat(repository.findById(o3.getId())).isNotNull();

        assertThat(domainEventContext.drainRegistered()).containsExactly(o1, o2, o3);
        assertThat(o1.drainEvents()).hasSize(1);
        assertThat(o2.drainEvents()).hasSize(1);
        assertThat(o3.drainEvents()).hasSize(1);
    }

    @Test
    void modifyAllUpdatesBatchRegistersAllAggregatesInOrderAndKeepsEvents() {
        TestOrderId id1 = new TestOrderId("ORD-MB-1");
        TestOrderId id2 = new TestOrderId("ORD-MB-2");
        repository.addAll(List.of(
                TestOrder.create(id1, 10),
                TestOrder.create(id2, 20)
        ));
        domainEventContext.drainRegistered();

        TestOrder loaded1 = repository.findById(id1);
        TestOrder loaded2 = repository.findById(id2);
        loaded1.markPaid();
        loaded2.cancel();

        repository.modifyAll(List.of(loaded1, loaded2));

        assertThat(repository.findById(id1).getStatus()).isEqualTo(TestOrderStatus.PAID);
        assertThat(repository.findById(id2).getStatus()).isEqualTo(TestOrderStatus.CANCELLED);

        assertThat(domainEventContext.drainRegistered()).containsExactly(loaded1, loaded2);
        assertThat(loaded1.drainEvents()).hasSize(1);
        assertThat(loaded2.drainEvents()).hasSize(1);
    }

    @Test
    void sameAggregatePersistedMultipleTimesInOneContextRegistersOnceAndKeepsAccumulatingEvents() {
        TestOrderId id = new TestOrderId("ORD-DEDUP-1");
        TestOrder order = TestOrder.create(id, 100);

        repository.add(order);
        order.markPaid();
        repository.modify(order);

        assertThat(domainEventContext.drainRegistered()).containsExactly(order);
        assertThat(order.drainEvents()).hasSize(2);
    }

    @Test
    void modifyAllWithMissingElementThrowsDoesNotRegisterAggregatesAndKeepsEvents() {
        TestOrderId id1 = new TestOrderId("ORD-MB-OK");
        repository.add(TestOrder.create(id1, 10));
        domainEventContext.drainRegistered();

        TestOrder loaded1 = repository.findById(id1);
        loaded1.markPaid();
        TestOrder ghost = TestOrder.create(new TestOrderId("ORD-MB-GHOST"), 999);

        assertThatThrownBy(() -> repository.modifyAll(List.of(loaded1, ghost)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("modify affected 0 rows");

        assertThat(domainEventContext.drainRegistered()).isEmpty();
        assertThat(loaded1.drainEvents()).hasSize(1);
        assertThat(ghost.drainEvents()).hasSize(1);
    }

    // ---- remove path ----

    @Test
    void removeExistingAggregateDeletesRegistersAggregateAndKeepsEvents() {
        TestOrderId id = new TestOrderId("ORD-RM-1");
        TestOrder order = TestOrder.create(id, 100);
        repository.add(order);
        domainEventContext.drainRegistered();

        TestOrder toRemove = repository.findById(id);
        toRemove.cancel();
        repository.remove(toRemove);

        assertThat(repository.findById(id)).isNull();
        assertThat(domainEventContext.drainRegistered()).containsExactly(toRemove);
        assertThat(toRemove.drainEvents())
                .singleElement()
                .isInstanceOfSatisfying(DomainEvent.class,
                        event -> assertThat(event.getClass().getSimpleName()).isEqualTo("TestOrderStatusChangedEvent"));
    }

    @Test
    void removeMissingAggregateAffectsZeroRowsAndThrowsIllegalStateException() {
        TestOrderId ghostId = new TestOrderId("ORD-RM-GHOST");
        TestOrder ghost = TestOrder.create(ghostId, 100);

        assertThatThrownBy(() -> repository.remove(ghost))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("remove affected 0 rows");

        assertThat(domainEventContext.drainRegistered()).isEmpty();
        assertThat(ghost.drainEvents()).hasSize(1);
    }

    // ---- findById path ----

    @Test
    void findByIdExistingReturnsAggregate() {
        TestOrderId id = new TestOrderId("ORD-FIND-1");
        repository.add(TestOrder.create(id, 100));

        TestOrder loaded = repository.findById(id);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getId()).isEqualTo(id);
    }

    @Test
    void findByIdMissingReturnsNull() {
        assertThat(repository.findById(new TestOrderId("ORD-NOPE"))).isNull();
    }

    @Test
    void findByIdNullArgumentThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> repository.findById(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
