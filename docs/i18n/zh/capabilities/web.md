# Web

`jfoundry-web` 是 JFoundry 运行时无关的 Web 能力基础。它负责共享的 HTTP 问题语义，运行时适配器再通过各自的 HTTP 技术栈渲染这些语义。当前已发布的 Web 能力包括面向 HTTP API 的 RFC 9457 Problem Details，以及面向 Spring 应用的显式出站 HTTP 客户端支持。

## 选择 Web 能力

| 需求 | Spring Boot | Quarkus | Helidon MP |
|---|---|---|---|
| 为 HTTP API 提供 RFC 9457 Problem Details | `jfoundry-webmvc-spring-boot-starter` | `jfoundry-web-quarkus-runtime` | `jfoundry-web-helidon-runtime` |
| 出站 HTTP 客户端支持 | `jfoundry-web-spring-boot-starter` 或 `jfoundry-web-spring` | 暂未提供 | 暂未提供 |

`jfoundry-web-spring` 要求应用自行提供 Spring Web API。Spring Boot 应用可以加入
`jfoundry-web-spring-boot-starter`，它提供 Spring Boot `RestClient` 集成以及
`jfoundry.web.rest-client.logging-level` 配置项。“暂未提供”表示 JFoundry 当前未发布该运行时的适配器，不构成隐含的支持声明。

## Problem Details（RFC 9457）

当 HTTP API 需要为 JFoundry 业务失败提供稳定的 RFC 9457 `application/problem+json` 响应时，使用此能力。它在运行时边界把受支持的应用层或领域层异常转换为 HTTP 响应；领域和应用代码不应选择 HTTP 状态码。

### 添加运行时入口

| 运行时 | 使用方依赖 | HTTP 集成 |
|---|---|---|
| Spring Boot | `jfoundry-webmvc-spring-boot-starter` | Spring MVC |
| Quarkus | `jfoundry-web-quarkus-runtime` | 带 Jackson 的 Quarkus REST |
| Helidon MP | `jfoundry-web-helidon-runtime` | JAX-RS |

这些入口都会引入运行时无关的 `jfoundry-web` 模块。应用通常只添加上表所列的入口。先按[接入指南](../integration/getting-started.md)导入核心 BOM 与对应运行时 BOM。

### 共享契约

受支持的响应包含 RFC 9457 的 `type`、`title`、`status` 和 `detail` 成员。`type` URI 是稳定的机器可读问题标识。自定义扩展会保留 JSON 标量、数组和对象类型，且不能覆盖 RFC 9457 保留成员；只有当扩展字段能为特定 problem type 提供额外语义时才应定义它。

内置目录映射以下 JFoundry 异常：`InvalidArgumentException`、`NotFoundException`、`ConflictException`、`ExternalAccessException`、`DomainRuleViolationException` 和 `DomainStateException`。运行时报告 `400`、`404`、`405`、`406`、`413`、`415` 或 `503` 时，也会使用共享契约。

`InvalidArgumentException`、`NotFoundException`、`ConflictException`、`DomainRuleViolationException`
和 `DomainStateException` 的消息会成为面向调用方的 `detail`。这些消息应使用业务语言，不得包含凭证、
内部地址、SQL 或其它诊断数据。

`ExternalAccessException` 的语义不同：其诊断消息默认会被隐藏。具体的转换后异常在拥有稳定、可操作且
经过审查的提示时，可以通过受保护的构造方法显式提供公开详情：

```java
final class MksAuthenticationException extends ExternalAccessException {

    MksAuthenticationException(Throwable cause) {
        super(
                "MKS deployment JWT signing failed",
                cause,
                "Deployment authorization is temporarily unavailable."
        );
    }
}
```

内置目录会把该显式详情用于 `urn:jfoundry:problem:external-access` 响应，但绝不会从诊断消息、cause 或
`cause.getMessage()` 推导公开详情。现有构造方法仍保持默认脱敏，并继续返回
`The requested operation is temporarily unavailable.`。

应用可以提供 `ProblemMapper`，将自己拥有的异常映射为 `ProblemDescriptor`。这用于稳定的应用专属错误，避免泄露实现异常，也不应把 HTTP 关注点放入领域模型。

### 请求校验问题

Spring MVC、Quarkus REST 和 Helidon MP 对受支持的请求入参校验失败统一使用独立的
`urn:jfoundry:problem:request-validation` type。其 `errors` 扩展遵循 RFC 9457 的 validation error
示例：每一项都包含面向调用方的 `detail`；当错误在 JSON 请求文档中具有可靠位置时，还会包含以 JSON Pointer
URI fragment 编码的 `pointer`：

```json
{
  "type": "urn:jfoundry:problem:request-validation",
  "title": "Request validation failed",
  "status": 400,
  "detail": "The request failed validation. See 'errors' for details.",
  "errors": [
    {
      "detail": "不能为空",
      "pointer": "#/services"
    }
  ]
}
```

pointer token 会按照 RFC 6901 转义 `~` 和 `/`；URI fragment 表示还会按照 RFC 3986 对其它字符进行
百分号编码。

`pointer` 描述的是 JSON 请求文档中的位置，而不是一般意义上的 Java 属性路径。因此，确认来自 JSON body 的字段
与容器元素错误可以使用 `#/services/0` 之类的 pointer；query、path、header、cookie、matrix、form、
model attribute 和 multipart 请求参数即使带有嵌套 Java 属性路径，也只包含 `detail`。对象级约束和跨参数约束
无法定位到单个 JSON 值，同样只包含 `detail`。

JSON 格式错误、消息转换失败以及其它发生在校验之前的失败会保留运行时的 HTTP bad request problem type，
不会伪装成请求校验问题。响应绝不会包含被拒绝的值，因为请求字段可能携带凭证、令牌或体积较大的数据。
各运行时适配器会明确排除返回值校验和内部服务校验失败，不会把它们转换成客户端错误。Spring MVC、
Quarkus REST 和 Helidon MP 分别依据自身 HTTP 技术栈提供的请求来源元数据执行这些来源判定，同时保持相同的
公开响应结构。

Spring MVC 通过常规 Web MVC 集成获得校验能力。Quarkus 应用必须添加
`quarkus-hibernate-validator`；JFoundry 只在检测到该 capability 时注册映射器。Helidon MP 应用必须添加
`helidon-microprofile-bean-validation`。应用仍需自行选择用于反序列化请求体的 JSON provider。

### 明确边界

- 未知异常和受支持状态集合之外的 HTTP 失败会保留运行时原有处理。此能力不是应用的通用异常策略。
- 认证与授权仍由选定的安全集成负责。安全适配器可以通过运行时渲染器输出自己的 `401` 或 `403` 描述符。
- 当前发布的适配器覆盖 Spring MVC、Quarkus REST 和 Helidon MP JAX-RS，不对其他 HTTP 技术栈作支持声明。

### 运行时参考

- [Spring Boot 运行时装配](../implementations/spring-boot.md)说明自动配置与 Spring MVC 替换规则。
- [Quarkus 运行时集成](../implementations/quarkus.md)说明扩展组合与 Quarkus REST 行为。
- [Helidon MP 运行时集成](../implementations/helidon.md)说明 CDI 与 JAX-RS 行为。

## Spring HTTP 集成与诊断日志

`jfoundry-web-spring` 为选定的出站 `RestClient` 调用提供显式集成。只对该集成拥有的 builder 使用
`RestClientSupport.configure(builder)`，并通过 `RestClientSupport.execute(...)` 执行调用。非成功响应会转换为
只包含状态码的 `HttpResponseException`；传输和响应解码失败会转换为带有安全失败类别的
`HttpRequestException`，同时将原始异常保留为 cause，供服务端诊断。

API 现在按抽象层级组织。`HttpLoggingLevel` 与 `HttpLoggingSupport` 位于
`org.jfoundry.http.spring`，`HttpLoggingInterceptor` 位于 `org.jfoundry.http.spring.client`，
`RestClient` 外观与转换后的异常位于 `org.jfoundry.web.spring.client`。这些新位置替代原来的
`org.jfoundry.web.spring` 位置，不提供兼容别名；`ProblemDetailRenderer` 仍位于原包。

出站日志默认使用 `BASIC`。应用可通过 `RestClientSupport.configure(builder, HttpLoggingLevel)` 选择四种级别；
Spring Boot 管理的 builder 使用同样默认值为 `BASIC` 的
`jfoundry.web.rest-client.logging-level`。客户端 `durationMs` 从调用
`ClientHttpRequestExecution.execute(...)` 前开始，到响应 header 可用或执行失败时结束，不包含响应 body
消费与解码，也不是端到端延迟。

Web MVC 启动器还通过 `HttpLoggingFilter` 提供入站 Servlet 日志。该能力默认以
`jfoundry.web.server.logging-level=NONE` 关闭；可设置为 `BASIC`、`HEADERS` 或 `FULL`。入站
`durationMs` 从 Filter 初始入口持续到同步完成，或异步 complete、error、timeout 终态；它不表示客户端已经收到全部响应字节。

两个方向都只在对应 logger 开启 `DEBUG` 时输出。`BASIC` 记录移除 query 后的 method/URI、状态或失败以及
`durationMs`，且不创建 body 包装器。`HEADERS` 增加 header，并以不区分大小写的方式脱敏授权信息、凭证、
cookie、token、secret 与 API key。`FULL` 增加经过嵌套字段脱敏的 JSON body，最多保留 8 KiB；非 JSON、
格式错误、未完整消费或超限 body 只记录安全描述。客户端可能在关闭响应时读取未消费的错误响应 body；Servlet
捕获会立即转发字节，不会延迟流式输出。

这些诊断访问日志不能替代 Micrometer 指标或追踪，也不会发布或替代由应用拥有的业务审计事件。Spring 专属的
组合方式与替换规则见 [Spring Boot 运行时装配](../implementations/spring-boot.md)。
