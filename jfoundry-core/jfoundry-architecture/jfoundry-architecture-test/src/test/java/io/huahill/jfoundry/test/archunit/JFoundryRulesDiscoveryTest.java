package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

@AnalyzeClasses(packages = "com.example.jfoundryfixture")
class JFoundryRulesDiscoveryTest {

    @ArchTest
    static final ArchTests cqrs = JFoundryRules.cqrs();

    @ArchTest
    static final ArchTests ddd = JFoundryRules.jmoleculesDdd();

    @ArchTest
    static final ArchTests aggregateRepositories = JFoundryRules.aggregateRepositoryConventions();
}
