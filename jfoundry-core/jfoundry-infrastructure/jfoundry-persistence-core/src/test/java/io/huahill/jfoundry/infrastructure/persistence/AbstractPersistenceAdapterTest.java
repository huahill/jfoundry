package io.huahill.jfoundry.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractPersistenceAdapterTest {

    @Test
    void translatesEachPersistenceOperationThroughTheConfiguredTranslator() {
        TestAdapter adapter = new TestAdapter();
        List<PersistenceOperation> translatedOperations = new ArrayList<>();
        IllegalArgumentException translated = new IllegalArgumentException("translated");
        adapter.setPersistenceFailureTranslator((operation, failure) -> {
            translatedOperations.add(operation);
            assertThat(failure).isInstanceOf(IllegalStateException.class)
                    .hasMessage("persistence failed");
            return translated;
        });

        assertThatThrownBy(adapter::failFind).isSameAs(translated);
        assertThatThrownBy(adapter::failQuery).isSameAs(translated);
        assertThatThrownBy(adapter::failAdd).isSameAs(translated);
        assertThatThrownBy(adapter::failModify).isSameAs(translated);
        assertThatThrownBy(adapter::failRemove).isSameAs(translated);

        assertThat(translatedOperations).containsExactly(
                PersistenceOperation.FIND,
                PersistenceOperation.QUERY,
                PersistenceOperation.ADD,
                PersistenceOperation.MODIFY,
                PersistenceOperation.REMOVE);
    }

    @Test
    void preservesFailureWhenNoRuntimeTranslatorIsConfigured() {
        TestAdapter adapter = new TestAdapter();

        assertThatThrownBy(adapter::failQuery)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("persistence failed");
    }

    @Test
    void rejectsNullTranslatorResult() {
        TestAdapter adapter = new TestAdapter();
        adapter.setPersistenceFailureTranslator((operation, failure) -> null);

        assertThatThrownBy(adapter::failQuery)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PersistenceFailureTranslator must not return null.")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private static final class TestAdapter extends AbstractPersistenceAdapter {

        private void failFind() {
            find(() -> {
                fail();
                return null;
            });
        }

        private void failQuery() {
            query(() -> {
                fail();
                return null;
            });
        }

        private void failAdd() {
            add(AbstractPersistenceAdapterTest::fail);
        }

        private void failModify() {
            modify(AbstractPersistenceAdapterTest::fail);
        }

        private void failRemove() {
            remove(AbstractPersistenceAdapterTest::fail);
        }
    }

    private static void fail() {
        throw new IllegalStateException("persistence failed");
    }
}
