package io.huahill.jfoundry.architecture.cqrs;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

class CqrsStereotypesTest {

    @Test
    void commandShouldWrapJmoleculesCommand() throws Exception {
        assertThat(Command.class.getAnnotation(org.jmolecules.architecture.cqrs.Command.class)).isNotNull();
        assertThat(Command.class.getMethod("namespace").getDefaultValue()).isEqualTo("");
        assertThat(Command.class.getMethod("name").getDefaultValue()).isEqualTo("");
    }

    @Test
    void commandHandlerShouldWrapJmoleculesCommandHandler() throws Exception {
        assertThat(CommandHandler.class.getAnnotation(org.jmolecules.architecture.cqrs.CommandHandler.class)).isNotNull();
        assertThat(CommandHandler.class.getAnnotation(Target.class).value())
                .containsExactlyInAnyOrder(ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR);
        assertThat(CommandHandler.class.getMethod("namespace").getDefaultValue()).isEqualTo("");
        assertThat(CommandHandler.class.getMethod("name").getDefaultValue()).isEqualTo("");
    }

    @Test
    void commandDispatcherShouldWrapJmoleculesCommandDispatcher() throws Exception {
        assertThat(CommandDispatcher.class.getAnnotation(org.jmolecules.architecture.cqrs.CommandDispatcher.class)).isNotNull();
        assertThat(CommandDispatcher.class.getAnnotation(Target.class).value())
                .containsExactlyInAnyOrder(ElementType.METHOD, ElementType.ANNOTATION_TYPE);
        assertThat(CommandDispatcher.class.getMethod("dispatches").getDefaultValue()).isEqualTo("");
    }

    @Test
    void queryModelShouldWrapJmoleculesQueryModel() {
        assertThat(QueryModel.class.getAnnotation(org.jmolecules.architecture.cqrs.QueryModel.class)).isNotNull();
    }
}
