# REST Problem Details

当 HTTP API 需要为 JFoundry 业务失败提供稳定的 RFC 9457 `application/problem+json` 响应时，使用此能力。它在运行时边界把受支持的应用层或领域层异常转换为 HTTP 响应；领域和应用代码不应选择 HTTP 状态码。

## 添加运行时入口

| 运行时 | 使用方依赖 | HTTP 集成 |
|---|---|---|
| Spring Boot | `jfoundry-webmvc-spring-boot-starter` | Spring MVC |
| Quarkus | `jfoundry-web-quarkus-runtime` | 带 Jackson 的 Quarkus REST |
| Helidon MP | `jfoundry-web-helidon-runtime` | JAX-RS |

这些入口都会引入运行时无关的 `jfoundry-web-problem-details` 模块。应用通常只添加上表所列的入口。先按[接入指南](../integration/getting-started.md)导入核心 BOM 与对应运行时 BOM。

## 共享契约

受支持的响应包含 RFC 9457 的 `type`、`title`、`status` 和 `detail` 成员，以及稳定的 JFoundry `code` 扩展字段。自定义扩展会保留 JSON 标量、数组和对象类型，且不能覆盖 RFC 9457 保留成员。

内置目录映射以下 JFoundry 异常：`InvalidArgumentException`、`NotFoundException`、`ConflictException`、`ExternalAccessException`、`DomainRuleViolationException` 和 `DomainStateException`。运行时报告 `400`、`404`、`405`、`406`、`413`、`415` 或 `503` 时，也会使用共享契约。

应用可以提供 `ProblemMapper`，将自己拥有的异常映射为 `ProblemDescriptor`。这用于稳定的应用专属错误，避免泄露实现异常，也不应把 HTTP 关注点放入领域模型。

## 明确边界

- 未知异常和受支持状态集合之外的 HTTP 失败会保留运行时原有处理。此能力不是应用的通用异常策略。
- 认证与授权仍由选定的安全集成负责。安全适配器可以通过运行时渲染器输出自己的 `401` 或 `403` 描述符。
- 当前发布的适配器覆盖 Spring MVC、Quarkus REST 和 Helidon MP JAX-RS，不对其他 HTTP 技术栈作支持声明。

## 运行时参考

- [Spring Boot 运行时装配](../implementations/spring-boot.md)说明自动配置与 Spring MVC 替换规则。
- [Quarkus 运行时集成](../implementations/quarkus.md)说明扩展组合与 Quarkus REST 行为。
- [Helidon MP 运行时集成](../implementations/helidon.md)说明 CDI 与 JAX-RS 行为。
