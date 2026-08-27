# 接入指南

JFoundry 的核心模块不依赖 Spring、Quarkus 或 Helidon。接入应先从业务需求出发完成领域与架构决策，再选择
核心能力和应用最外层的运行时集成。

## 1. 选择接入方式

架构风格决定代码边界、依赖方向和架构测试规则。它是业务项目的决策，不由 Spring、Quarkus 或 Helidon 决定。

使用 AI 辅助编程时，推荐安装
[`domain-architecture-skills`](https://github.com/xfoundries/domain-architecture-skills)，并从
`$domain-architecture-workflow` 开始。用户无需预先选择架构风格；工作流会从需求和项目证据出发完成领域建模，判断
是否需要完整架构风格，需要时选择 Hexagonal 或 Onion，并单独判断是否需要 CQRS。随后，它会生成可追溯的
JFoundry 落地指导。

不使用 AI 辅助工作流时，再手工选择架构风格：

| 选择 | 适用情况 | JFoundry 落地入口 |
|---|---|---|
| Hexagonal | 需要明确区分输入、输出、端口和适配器的方向 | `jfoundry-hexagonal` 与 `JFoundryRules.hexagonalStrict()` |
| Onion Simple | 重点是依赖向内和保护领域核心 | `jfoundry-onion` 与 `JFoundryRules.onionSimple()` |
| Onion Classical | 团队明确需要细分领域模型、领域服务和应用服务环 | `jfoundry-onion` 与 `JFoundryRules.onionClassical()` |
| 暂不采用完整风格 | 简单 CRUD 或业务不变量较少的短期原型 | 只保留项目实际需要的边界 |

无论通过 AI 工作流还是手工决策，选择架构风格后都应使用 `jfoundry-architecture-test` 固化约束。不要在同一个
分析范围内混用 Hexagonal 与 Onion。CQRS 只在命令和查询确有差异时按需叠加，不是第三种主架构风格。完整的
手工判断标准见[架构风格指南](../framework/architecture-styles.md)。

## 2. 配置核心 BOM 与架构依赖

所有外部应用都必须让 `jfoundry-dependencies` 参与依赖管理。它是 JFoundry 公共 BOM，用于管理核心模块、架构模块和
框架无关适配器的版本；使用 `jfoundry-spring-boot-parent` 时由该 Parent 自动导入，否则应用需要显式导入。它放在
`<dependencyManagement>` 中，不是运行时依赖：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-dependencies</artifactId>
            <version>${jfoundry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

然后按职责添加依赖：

| 职责 | 起始依赖 |
|---|---|
| 领域模型 | `jfoundry-domain` |
| 应用服务 | `jfoundry-application-core` |
| 主架构风格 | 二选一：`jfoundry-hexagonal` 或 `jfoundry-onion` |
| 架构验证 | 测试范围使用 `jfoundry-architecture-test` |
| 技术实现 | 按需选择 JPA、MyBatis-Plus、消息等框架无关适配器 |

例如，选择 Hexagonal 时，业务模块至少直接依赖领域模块和架构门面，测试模块再依赖架构规则；选择 Onion 时将
`jfoundry-hexagonal` 替换为 `jfoundry-onion`：

```xml
<dependencies>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-domain</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-hexagonal</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-architecture-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

领域和应用模块不应依赖具体运行时。持久化、消息、Outbox、Inbox 等能力也应按需选择，不由基础模块隐式引入。

## 3. 选择运行时

每个运行时都有独立的 BOM 和基础入口，不应混用。应用保留自有 Maven Parent 时，先导入对应的运行时 BOM，再导入
`jfoundry-dependencies`；使用 JFoundry Boot Parent 时由 Parent 管理这两个 BOM。具体 Parent、BOM 和依赖组合见各运行时指南。

| 运行时 | 基础入口 | 接入说明 |
|---|---|---|
| Spring Boot | 按需选择 Spring Boot 能力启动器；基础 starter 通常由能力启动器传递引入 | [Spring Boot 运行时装配](../implementations/spring-boot.md) |
| Quarkus | `jfoundry-transaction-quarkus-runtime` 及其他能力扩展 | [Quarkus 运行时集成](../implementations/quarkus.md) |
| Helidon MP | 按能力选择 `jfoundry-transaction-helidon`、`jfoundry-domain-event-helidon` 与 `jfoundry-persistence-helidon` | [Helidon MP 运行时集成](../implementations/helidon.md) |

运行时入口只负责装配，不应进入领域或应用代码。

## 4. 按需添加能力

从[能力目录](../capabilities/index.md)选择聚合持久化、事务、Web、消息、Outbox/Inbox、分布式锁或可观测性。
每个能力页面都会列出不同运行时的对应入口和当前支持范围。

开始实现前，可继续阅读 [Repository 与读侧契约](../modeling/repository-vs-read-contracts.md)。生产采用前请查看
[采用就绪度与已验证范围](adoption-readiness.md)。
