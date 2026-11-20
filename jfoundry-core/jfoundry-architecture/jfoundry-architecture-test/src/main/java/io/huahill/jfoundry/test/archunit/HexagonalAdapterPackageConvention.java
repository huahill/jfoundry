package io.huahill.jfoundry.test.archunit;

/// Package vocabulary selected by one Hexagonal project's adapter boundary.
/// <p>
/// Both vocabularies express the same Primary/Secondary Adapter roles. Select one convention for
/// a project so direction remains locatable without mixing aliases in the same adapter tree.
public enum HexagonalAdapterPackageConvention {

    IN_OUT("..adapter.in..", "..adapter.out.."),
    PRIMARY_SECONDARY("..adapter.primary..", "..adapter.secondary..");

    private final String primaryAdapterPackage;
    private final String secondaryAdapterPackage;

    HexagonalAdapterPackageConvention(String primaryAdapterPackage, String secondaryAdapterPackage) {
        this.primaryAdapterPackage = primaryAdapterPackage;
        this.secondaryAdapterPackage = secondaryAdapterPackage;
    }

    String primaryAdapterPackage() {
        return primaryAdapterPackage;
    }

    String secondaryAdapterPackage() {
        return secondaryAdapterPackage;
    }
}
