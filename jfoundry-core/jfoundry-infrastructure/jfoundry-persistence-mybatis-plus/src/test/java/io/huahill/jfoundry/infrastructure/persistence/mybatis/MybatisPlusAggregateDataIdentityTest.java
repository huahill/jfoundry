package io.huahill.jfoundry.infrastructure.persistence.mybatis;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the equals/hashCode contract of {@link MybatisPlusAggregateData}.
/// <p>
/// Key regression: non-persisted objects with id == null must not be folded into one element by
/// HashSet/HashMap.
class MybatisPlusAggregateDataIdentityTest {

    @Test
    void aggregateDataShouldBeThePersistenceBaseType() {
        assertEquals(Object.class, MybatisPlusAggregateData.class.getSuperclass());
    }

    @Test
    void newEntitiesWithNullIdShouldNotBeEqual() {
        MybatisPlusAggregateData<String> a = new TestData();
        MybatisPlusAggregateData<String> b = new TestData();

        assertNotEquals(a, b);
        assertFalse(a.equals(b));
    }

    @Test
    void newEntitiesWithNullIdShouldNotCollapseInHashSet() {
        MybatisPlusAggregateData<String> a = new TestData();
        MybatisPlusAggregateData<String> b = new TestData();

        Set<MybatisPlusAggregateData<String>> set = new HashSet<>();
        set.add(a);
        set.add(b);

        assertEquals(2, set.size(), "Two non-persisted objects should coexist in HashSet");
    }

    @Test
    void entitiesWithSameIdShouldBeEqual() {
        MybatisPlusAggregateData<String> a = new TestData();
        a.setId("1");
        MybatisPlusAggregateData<String> b = new TestData();
        b.setId("1");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void entitiesWithDifferentIdShouldNotBeEqual() {
        MybatisPlusAggregateData<String> a = new TestData();
        a.setId("1");
        MybatisPlusAggregateData<String> b = new TestData();
        b.setId("2");

        assertNotEquals(a, b);
    }

    @Test
    void nullIdEntityShouldNotEqualPersistedEntity() {
        MybatisPlusAggregateData<String> a = new TestData();
        MybatisPlusAggregateData<String> b = new TestData();
        b.setId("1");

        assertNotEquals(a, b);
        assertNotEquals(b, a);
    }

    @Test
    void sameReferenceShouldBeEqual() {
        MybatisPlusAggregateData<String> a = new TestData();

        assertTrue(a.equals(a));
    }

    @Test
    void shouldNotEqualNullOrDifferentType() {
        MybatisPlusAggregateData<String> a = new TestData();
        a.setId("1");

        assertFalse(a.equals(null));
        assertFalse(a.equals("not an MybatisPlusAggregateData"));
    }

    private static final class TestData extends MybatisPlusAggregateData<String> {
    }
}
