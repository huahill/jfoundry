# 接入指南

JFoundry 将领域、应用和架构决策与应用运行时分开。先确定边界，再选择一个运行时和所需能力。

## 1. 选择架构风格

按业务需要选择 Hexagonal、Onion Simple、Onion Classical，或暂不采用完整风格。不要在同一个分析范围内混用 Hexagonal 与 Onion；CQRS 只是可选模式。使用 `jfoundry-architecture-test` 固化所选风格，详见[架构风格指南](../framework/architecture-styles.md)。

需要 AI 辅助时，可选使用 [`domain-architecture-skills`](https://github.com/huahill/domain-architecture-skills) 工作流，从需求和项目证据完成同一决策。

## 2. 添加核心 BOM

所有应用使用 `jfoundry-dependencies` 管理 DDD、架构风格、应用契约和框架无关适配器。Spring Boot Parent 通过 Boot 消费者 BOM 间接包含它；其他应用与运行时 BOM 一起显式导入。

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.huahill</groupId>
            <artifactId>jfoundry-dependencies</artifactId>
            <version>${jfoundry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

按需选择 `jfoundry-domain`、`jfoundry-application-core`、一个架构风格模块和测试范围的 `jfoundry-architecture-test`。持久化、消息、Outbox、Inbox 和锁都应显式选择。

## 3. 选择运行时

| 运行时 | 消费者入口 | 指南 |
|---|---|---|
| Spring Boot | `jfoundry-spring-boot-parent` 或 `jfoundry-spring-boot-dependencies` | [Spring Boot](../runtimes/spring-boot.md) |
| Quarkus | `jfoundry-dependencies` 加 `jfoundry-quarkus-dependencies` | [Quarkus](../runtimes/quarkus.md) |
| Helidon MP | `jfoundry-dependencies` 加 `jfoundry-helidon-dependencies` | [Helidon MP](../runtimes/helidon.md) |

每个应用选择一条运行时路径。运行时模块不应进入领域和应用代码。

## 4. 选择能力

从[能力目录](../capabilities/index.md)开始，再选择对应运行时入口和持久化实现。JPA 与 MyBatis-Plus 见[持久化实现指南](../persistence/index.md)。生产采用前请查看[采用就绪度与已验证范围](adoption-readiness.md)。
