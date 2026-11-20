package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.huahill.jfoundry.domain.valueobject.ValueObject;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/// Architecture rules for value objects.
/// <p>
/// Enforces the spec Section 8.1 constraints: value objects must be immutable and must provide
/// equals/hashCode.
/// <p>
/// Recommended application-side usage:
/// <pre>
/// &#64;AnalyzeClasses(packages = "com.mysoft.ci")
/// class CiArchitectureTest {
///     &#64;ArchTest
///     ArchTests valueObjectRules = ArchTests.in(ValueObjectRules.class);
/// }
/// </pre>
public final class ValueObjectRules {

    private ValueObjectRules() {
    }

    /// Value object implementations must be records or final classes.
    /// <p>
    /// Records are final, immutable, and provide equals/hashCode by default, so they are the
    /// preferred representation. Class-based implementations must be explicitly final to prevent
    /// subclasses from breaking immutability.
    /// <p>
    /// {@code allowEmptyShould(true)} is required because this rule is distributed by the
    /// framework and applications may reference it before declaring any ValueObject. In that case,
    /// the rule should pass vacuously instead of failing. ArchUnit's default empty-should failure
    /// helps catch local rule mistakes, but reusable library rules must support legitimate
    /// not-yet-applied scenarios.
    @ArchTest
    public static final ArchRule value_objects_must_be_final =
            classes()
                    .that().implement(ValueObject.class)
                    .should().beRecords()
                    .orShould().haveModifier(JavaModifier.FINAL)
                    .allowEmptyShould(true)
                    .because("ValueObject must be immutable; records are immutable by default, "
                            + "class implementations must be final to prevent subclassing");

    /// All value object fields must be final.
    /// <p>
    /// Record fields are final by default; this rule mainly constrains class-based value objects.
    @ArchTest
    public static final ArchRule value_object_fields_must_be_final =
            classes()
                    .that().implement(ValueObject.class)
                    .should().haveOnlyFinalFields()
                    .allowEmptyShould(true)
                    .because("ValueObject fields must be final to guarantee immutability");

    /// Value objects must implement equals and hashCode.
    /// <p>
    /// Records provide them by default; class-based implementations must override them explicitly.
    /// Equal value objects must produce the same hashCode.
    @ArchTest
    public static final ArchRule value_objects_must_implement_equals_and_hashCode =
            classes()
                    .that().implement(ValueObject.class)
                    .should(haveEqualsAndHashCode())
                    .allowEmptyShould(true)
                    .because("ValueObject must implement equals and hashCode for value semantics");

    /// ArchUnit 1.4.2 accepts only {@link ArchCondition} in {@code classes().should(...)}, not
    /// {@code DescribedPredicate}, so this method uses a custom {@link ArchCondition}, matching the
    /// approach used by {@code PersistenceRules}.
    /// <p>
    /// Detection criteria:
    /// <ul>
    ///   <li>A non-native method named {@code equals} with exactly one parameter exists.</li>
    ///   <li>A non-native method named {@code hashCode} with no parameters exists.</li>
    /// </ul>
    /// Native methods are excluded so JVM-provided methods such as {@code java.lang.Object#hashCode}
    /// are not misclassified as explicit implementations.
    private static ArchCondition<JavaClass> haveEqualsAndHashCode() {
        return new ArchCondition<JavaClass>("implement equals and hashCode") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean hasEquals = item.getMethods().stream()
                        .anyMatch(m -> "equals".equals(m.getName())
                                && m.getRawParameterTypes().size() == 1
                                && !m.getModifiers().contains(JavaModifier.NATIVE));
                boolean hasHashCode = item.getMethods().stream()
                        .anyMatch(m -> "hashCode".equals(m.getName())
                                && m.getRawParameterTypes().isEmpty()
                                && !m.getModifiers().contains(JavaModifier.NATIVE));
                if (!hasEquals || !hasHashCode) {
                    events.add(SimpleConditionEvent.violated(item,
                            item.getSimpleName() + " does not implement both equals(Object) and hashCode()"));
                }
            }
        };
    }
}
