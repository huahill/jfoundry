# ValueObject 使用指南

## 快速开始

```java
package com.mysoft.ci.env.valueobject;

import org.jfoundry.domain.valueobject.ValueObject;
import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) implements ValueObject {
    public Money {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }
}
```

## 为什么用 record

Java 21 `record` 天生满足 ValueObject 的三大契约：

1. **不可变** —— 所有字段均为 final，没有设值方法
2. **值相等** —— 自动生成 equals/hashCode，基于字段值而非身份
3. **final** —— record 不能被继承，防止子类破坏不可变性

`ValueObjectRules` 的三条 ArchUnit 规则检查这些契约。如果业务侧因故必须用 class 实现（例如有继承需求或需要 `@JsonCreator`），仍然可以：

```java
public final class Money implements ValueObject {
    private final BigDecimal amount;
    private final String currency;

    public Money(BigDecimal amount, String currency) {
        // Validate constructor arguments.
        this.amount = amount;
        this.currency = currency;
    }

    // Accessors.

    @Override
    public boolean equals(Object o) { /* value-based equals */ }

    @Override
    public int hashCode() { /* value-based hashCode */ }
}
```

## 启用 ArchUnit 规则

在业务模块的 ArchUnit 测试里：

```java
@AnalyzeClasses(packages = "com.mysoft.ci")
class CiArchitectureTest {
    @ArchTest
    ArchTests rules = JFoundryRules.onionSimple();
}
```

包含 ValueObject 的三条规则：

- `value_objects_must_be_final` —— 必须 final 或 record
- `value_object_fields_must_be_final` —— 所有字段 final
- `value_objects_must_implement_equals_and_hashCode` —— 必须实现 equals/hashCode

## jMolecules 生态

`ValueObject` 继承 `org.jmolecules.ddd.types.ValueObject`，所以 jMolecules 工具链（`jmolecules-jackson` 序列化、`jmolecules-archunit` 规则）开箱即用。
