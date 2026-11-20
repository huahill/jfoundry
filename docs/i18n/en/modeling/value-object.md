# Value Object Guide

jfoundry value objects are ordinary Java types that implement
`io.huahill.jfoundry.domain.valueobject.ValueObject`. Records are the preferred representation because
they are final, immutable, and provide `equals`/`hashCode` by default.

```java
public record Money(BigDecimal amount, String currency) implements ValueObject {

    public Money {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }
}
```

## Rules

- Prefer records for immutable value objects.
- Class-based value objects must be final.
- Fields must be final.
- Value objects must implement value equality.
- Validation belongs in the constructor or factory, close to the invariant.

## ArchUnit

Value object rules are included in the primary jfoundry architecture entries:

```java
@ArchTest
ArchTests rules = JFoundryRules.hexagonalStrict();
```

The value object marker extends `org.jmolecules.ddd.types.ValueObject`, so jMolecules integrations
can also recognize jfoundry value objects.
