# ArchUnit 规则清单

## 快速启用（推荐）

```java
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import org.jfoundry.test.archunit.HexagonalAdapterPackageConvention;
import org.jfoundry.test.archunit.JFoundryRules;

@AnalyzeClasses(packages = "com.mysoft.ci")
class CiArchitectureTest {

    @ArchTest
    ArchTests jfoundryRules = JFoundryRules.hexagonalStrict();

    @ArchTest
    ArchRule adapterPackages = JFoundryRules.hexagonalAdapterPackageConvention(
            HexagonalAdapterPackageConvention.IN_OUT);

    @ArchTest
    ArchTests jmoleculesDddRules = JFoundryRules.jmoleculesDdd();
}
```

`JFoundryRules.hexagonalStrict()` 启用 JFoundry 基础守护规则 + jMolecules Hexagonal 主架构规则 + JFoundry Hexagonal 推荐落地约定。
`JFoundryRules.jmoleculesDdd()` 引入 jMolecules 官方 DDD 规则。

所有规则组入口都返回 ArchUnit 原生 `ArchTests`，JUnit 5 引擎会逐条发现嵌套规则，不会再把 `ArchRule[]` 数组误当成单条规则。

## jfoundry 自有规则

### PersistenceRules

| 规则 | 作用 |
|------|------|
| `persistence_repository_must_not_use_transactional` | 持久化实现包下零 `@Transactional` |
| `autoconfig_must_not_use_component` | autoconfig 模块禁止 `@Component`（用 `@AutoConfiguration` + `@Bean`） |

### ValueObjectRules

| 规则 | 作用 |
|------|------|
| `value_objects_must_be_final` | ValueObject 实现类必须为 final 或 record |
| `value_object_fields_must_be_final` | ValueObject 字段必须全部为 final |
| `value_objects_must_implement_equals_and_hashCode` | ValueObject 必须实现 equals/hashCode |

### ArchitectureStyleRules

| 规则 | 作用 |
|------|------|
| `hexagonal_must_be_declared` | Hexagonal 主架构入口要求分析范围内至少存在一个 Hexagonal 角色注解 |
| `onion_simple_must_be_declared` | Onion Simple 主架构入口要求分析范围内至少存在一个简化环注解 |
| `onion_classical_must_be_declared` | Onion Classical 主架构入口要求分析范围内至少存在一个经典环注解 |
| `hexagonal_and_onion_must_not_be_mixed` | 同一个 ArchUnit 分析范围内禁止同时使用 Hexagonal 与 Onion 主架构风格 |

### FrameworkModuleRules

| 规则 | 作用 |
|------|------|
| `framework_should_use_jmolecules_architecture_annotations_internally` | JFoundry 框架内部直接使用 jMolecules 架构注解，不依赖 JFoundry 包装注解；包装注解作为业务项目门面保留 |
| `domain_packages_should_be_onion_domain_ring` | `org.jfoundry.domain..` 必须标注 Onion 简化 `DomainRing` |
| `application_packages_should_be_onion_application_ring` | `org.jfoundry.application..` 必须标注 Onion 简化 `ApplicationRing` |
| `infrastructure_packages_should_be_onion_infrastructure_ring` | `org.jfoundry.infrastructure..` 必须标注 Onion 简化 `InfrastructureRing` |

### CqrsRules 与 HexagonalConventionRules

`CqrsRules` 只包含与具体主架构风格无关的 CQRS 命令、查询模型、处理器和派发器
位置及依赖规则，不再导入 Hexagonal 的 Port/Adapter 角色。Secondary Port 与 Secondary
Adapter 不应暴露 CQRS 入口模型这一规则属于 `HexagonalConventionRules`。因此 Onion 项目
可以组合环规则与通用 CQRS 规则，而不需要虚构 Hexagonal 角色。

`HexagonalConventionRules` 同时接受全局和能力内嵌的 `port.in` / `port.out` 包。它还禁止
Primary Port 依赖出站 Port 包，并禁止 Secondary Port 依赖入站 Port 包；两个方向共享的
模型应放在中立的应用能力包。这两条方向检查由
`hexagonalConventions()` 和 `hexagonalStrict()` 启用，Onion 入口不会导入。

Adapter 的方向包名是独立的项目约定。项目应在 `adapter.in` / `adapter.out` 与
`adapter.primary` / `adapter.secondary` 中选择一套，并以对应枚举值启用
`hexagonalAdapterPackageConvention(...)`。两套名称表达的是相同的 Primary/Secondary Adapter
角色；选择其一可避免别名成为混杂的包组织轴。该规则刻意不纳入 `hexagonalStrict()`，因为
Hexagonal 架构本身不强制任一包名；Onion 项目不使用该规则。

### AggregateRepositoryConventionRules

这组规则是可选约定，不会进入 `JFoundryRules.hexagonal()`、`onionSimple()` 等主架构入口。它同时识别直接继承 jMolecules `Repository` 和继承 jfoundry `AggregateRepository` 的接口，只守护明确的技术类型泄漏，不通过类名猜测某个返回值是不是读模型。

| 规则 | 作用 |
|------|------|
| `aggregate_repositories_must_not_expose_query_condition_types` | `AggregateRepository` 子接口方法签名和继承关系中禁止暴露 MyBatis-Plus `Wrapper`、Spring Data JPA `Specification` 等通用条件 API |
| `aggregate_repositories_must_not_expose_paging_types` | `AggregateRepository` 子接口禁止暴露 `Page`、`IPage`、`Pageable` 等分页 API |
| `aggregate_repositories_must_not_expose_persistence_service_types` | `AggregateRepository` 子接口禁止暴露 `BaseMapper`、`IService`、Spring Data Repository 等持久化服务或映射器 API |

启用方式：

```java
@ArchTest
ArchTests aggregateRepositoryRules = JFoundryRules.aggregateRepositoryConventions();
```

说明：

- 该规则不禁止业务具名方法，例如 `findCurrentOperation` 或 `findByBusinessKey`。这类方法是否属于命令侧聚合定位，需要项目按业务语义评审。
- 该规则不根据 `*View`、`*Summary`、`*Record`、`*Response`、`*DTO` 等后缀判断读模型，因为这些名称也可能是合法聚合或领域概念。
- 如果项目希望禁止应用层或领域层直接依赖 MyBatis-Plus `Wrapper`、本地历史框架 `Specification` 或其它条件对象，应在业务侧按自己的模块/包名补充本地 ArchUnit 规则，避免 JFoundry 对开源用户做过度假设。

## 架构风格入口

如果业务项目选择明确的架构风格，可使用显式主风格入口：

```java
@ArchTest
ArchTests onionRules = JFoundryRules.onionSimple();

@ArchTest
ArchTests onionClassicalRules = JFoundryRules.onionClassical();

@ArchTest
ArchTests hexagonalRules = JFoundryRules.hexagonalStrict();
```

- `JFoundryRules.onionSimple()`：基础守护规则 + Onion Simple 主风格入口。
- `JFoundryRules.onionClassical()`：基础守护规则 + Onion Classical 主风格入口。
- `JFoundryRules.hexagonalStrict()`：基础守护规则 + Hexagonal 主风格入口 + JFoundry Hexagonal 推荐落地约定；推荐业务项目使用。
- `JFoundryRules.hexagonal()`：基础守护规则 + jMolecules Hexagonal 主风格入口；适合只需要原生 Hexagonal 依赖规则、不需要 JFoundry 包名和类型形态约定的场景。
- `JFoundryRules.hexagonalConventions()`：只启用 JFoundry Hexagonal 推荐落地约定，可与自定义主架构规则组合使用。
- `JFoundryRules.hexagonalAdapterPackageConvention(...)`：守护所选的一套 Hexagonal Adapter 方向包名。
- `JFoundryRules.noMixedHexagonalAndOnion()`：单独启用 Hexagonal/Onion 互斥规则。

Hexagonal、Onion Simple 和 Onion Classical 主架构入口会在分析范围内完全没有对应架构注解时
直接失败，避免规则因没有匹配对象而空集通过。该守卫只要求所选风格至少被声明一次，不要求局部
分析范围必须同时包含所有角色或环。

若只想启用某一类底层规则，请直接使用 `PersistenceRules`、`ValueObjectRules` 或
`ArchitectureStyleRules` 等具体规则类；`JFoundryRules` 只提供主架构风格入口。

## jMolecules 官方 DDD 规则

`JFoundryRules.jmoleculesDdd()` 聚合 `jmolecules-archunit` `0.33.0` 提供的 DDD 原生规则：

- `JMoleculesDddRules.aggregateReferencesShouldBeViaIdOrAssociation()` —— 聚合之间只能通过 Id 或 Association 引用，避免直接对象引用导致的边界穿透
- `JMoleculesDddRules.valueObjectsMustNotReferToIdentifiables()` —— 值对象不得引用具备身份的实体或聚合

jMolecules 原生架构规则不再通过一个混合入口暴露，而是随 `JFoundryRules.hexagonal()`、`JFoundryRules.hexagonalStrict()`、
`JFoundryRules.onionSimple()` 或 `JFoundryRules.onionClassical()` 进入对应主风格入口。

> 说明：`jmolecules-integrations` 的版本由 `jmolecules-bom` `2025.0.2` 统一锁定为 `0.33.0`（`0.34.0` 并不存在于 Maven Central，`jmolecules-integrations` 的版本号从 `0.33.0` 直接跳到 `1.6.0`）。业务侧无需在 pom 中单独声明此版本。

## 精细控制

如果只想启用某一组规则：

```java
@ArchTest
ArchRule noTransactionalInPersistence = PersistenceRules.persistence_repository_must_not_use_transactional;

@ArchTest
ArchTests valueObjectRules = ArchTests.in(ValueObjectRules.class);

@ArchTest
ArchTests aggregateRepositoryRules = JFoundryRules.aggregateRepositoryConventions();
```

## Maven 依赖

业务模块只需依赖 `jfoundry-architecture-test`（测试期）：

```xml
<dependency>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-architecture-test</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```
