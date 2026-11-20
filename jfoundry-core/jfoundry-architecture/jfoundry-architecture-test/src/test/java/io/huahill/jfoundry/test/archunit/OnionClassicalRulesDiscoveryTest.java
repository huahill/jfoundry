package io.huahill.jfoundry.test.archunit;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

@AnalyzeClasses(packages = "io.huahill.jfoundry.test.archunit.fixture.onionclassical")
class OnionClassicalRulesDiscoveryTest {

    @ArchTest
    static final ArchTests architecture = JFoundryRules.onionClassical();
}
