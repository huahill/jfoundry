package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

import java.util.Objects;

/// Aggregated entrypoint for jfoundry architecture rules.
/// <p>
/// Recommended application-side usage:
/// <pre>
/// import com.tngtech.archunit.junit.AnalyzeClasses;
/// import com.tngtech.archunit.junit.ArchTest;
/// import com.tngtech.archunit.junit.ArchTests;
/// import io.huahill.jfoundry.test.archunit.JFoundryRules;
///
/// &#64;AnalyzeClasses(packages = "com.mysoft.ci")
/// class CiArchitectureTest {
///     &#64;ArchTest
///     ArchTests hexagonalRules = JFoundryRules.hexagonal();
///
///     &#64;ArchTest
///     ArchTests jmoleculesDddRules = JFoundryRules.jmoleculesDdd();
///
///     &#64;ArchTest
///     ArchTests cqrsRules = JFoundryRules.cqrs();
/// }
/// </pre>
/// <p>
/// {@link #hexagonal()}, {@link #onionSimple()}, and {@link #onionClassical()} return JFoundry
/// baseline guard rules plus one primary architecture style rule set.
/// {@link #jmoleculesDdd()} returns selected official jMolecules DDD rules.
/// {@link #cqrs()} returns optional CQRS rules;
/// {@link #aggregateRepositoryConventions()} returns optional aggregate repository boundary conventions.
public final class JFoundryRules {

    private JFoundryRules() {
    }

    /// Hexagonal Architecture / Ports and Adapters rules.
    /// <p>
    /// Includes JFoundry baseline guard rules, native jMolecules Hexagonal rules, and the
    /// Hexagonal/Onion mutual-exclusion rule.
    public static ArchTests hexagonal() {
        return ArchTests.in(JFoundryRuleSets.Hexagonal.class);
    }

    /// Recommended implementation conventions for Hexagonal Architecture / Ports and Adapters.
    /// <p>
    /// This entrypoint adds type-shape, package-name, and adapter-isolation conventions that are
    /// outside the native jMolecules role dependency rules.
    public static ArchTests hexagonalConventions() {
        return ArchTests.in(HexagonalConventionRules.class);
    }

    /// Strict Hexagonal Architecture rules.
    /// <p>
    /// Includes the dependency rules from {@link #hexagonal()} and the JFoundry recommended
    /// implementation conventions from {@link #hexagonalConventions()}.
    public static ArchTests hexagonalStrict() {
        return ArchTests.in(JFoundryRuleSets.HexagonalStrict.class);
    }

    /// Verifies that Hexagonal adapters use one selected primary/secondary package vocabulary.
    /// <p>
    /// This is an opt-in project convention, not a universal Hexagonal Architecture rule.
    public static ArchRule hexagonalAdapterPackageConvention(
            HexagonalAdapterPackageConvention convention) {
        return HexagonalConventionRules.adapterPackageConvention(
                Objects.requireNonNull(convention, "Hexagonal adapter package convention must not be null."));
    }

    /// Simplified Onion Architecture rules.
    /// <p>
    /// Includes JFoundry baseline guard rules, native jMolecules Onion Simple rules, and the
    /// Hexagonal/Onion mutual-exclusion rule.
    public static ArchTests onionSimple() {
        return ArchTests.in(JFoundryRuleSets.OnionSimple.class);
    }

    /// Classical Onion Architecture rules.
    /// <p>
    /// Includes JFoundry baseline guard rules, native jMolecules Onion Classical rules, and the
    /// Hexagonal/Onion mutual-exclusion rule.
    public static ArchTests onionClassical() {
        return ArchTests.in(JFoundryRuleSets.OnionClassical.class);
    }

    /// Mutual-exclusion rule for Hexagonal and Onion as primary architecture styles.
    public static ArchRule noMixedHexagonalAndOnion() {
        return ArchitectureStyleRules.hexagonal_and_onion_must_not_be_mixed;
    }

    /// CQRS rules.
    /// <p>
    /// jMolecules currently does not provide an independent CQRS ArchUnit rule set, so JFoundry
    /// provides this lightweight constraint entrypoint. These rules are not included in the primary
    /// architecture style rules by default; applications must opt in explicitly.
    public static ArchTests cqrs() {
        return ArchTests.in(CqrsRules.class);
    }

    /// Aggregate Repository convention rules.
    /// <p>
    /// This rule group is not included in the primary architecture style entrypoints by default.
    /// Applications should opt in explicitly when they are ready to guard aggregate repositories
    /// against generic query conditions, paging APIs, and persistence service or mapper APIs.
    public static ArchTests aggregateRepositoryConventions() {
        return ArchTests.in(AggregateRepositoryConventionRules.class);
    }

    /// Selected official jMolecules DDD rules.
    /// <p>
    /// Source: {@code org.jmolecules.integrations:jmolecules-archunit}.
    /// <p>
    /// Selected stable native jMolecules rules:
    /// <ul>
    ///   <li>{@code JMoleculesDddRules.aggregateReferencesShouldBeViaIdOrAssociation()} —
    ///       aggregates may reference each other only through IDs or associations, avoiding boundary
    ///       leaks caused by direct object references.</li>
    ///   <li>{@code JMoleculesDddRules.valueObjectsMustNotReferToIdentifiables()} —
    ///       value objects must not refer to identifiable entities or aggregates.</li>
    /// </ul>
    public static ArchTests jmoleculesDdd() {
        return ArchTests.in(JFoundryRuleSets.JmoleculesDdd.class);
    }
}
