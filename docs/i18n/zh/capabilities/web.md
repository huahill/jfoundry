# Web

`jfoundry-web` 是 JFoundry 运行时无关的 Web 能力基础。它负责共享的 HTTP 问题语义，运行时适配器再通过各自的 HTTP 技术栈渲染这些语义。当前已发布的 Web 能力包括 RFC 9457 Problem Details、安全诊断 HTTP 日志，以及统一的[请求关联](request-correlation.md)。

## 选择 Web 能力

| 需求 | Spring Boot | Quarkus | Helidon MP |
|---|---|---|---|
| 为 HTTP API 提供 RFC 9457 Problem Details | `jfoundry-webmvc-spring-boot-starter` | `jfoundry-web-quarkus-runtime` | `jfoundry-web-helidon` |
| 入站 HTTP 诊断日志 | `jfoundry-webmvc-spring-boot-starter` | `jfoundry-web-quarkus-runtime` | `jfoundry-web-helidon` |
| 出站 HTTP 诊断日志 | `jfoundry-restclient-spring-boot-starter` 或 `jfoundry-restclient-spring` | `jfoundry-restclient-quarkus-runtime` | `jfoundry-restclient-helidon` |
| 入站请求关联 | 与 `jfoundry-webmvc-spring-boot-starter` 一起选择 | 与 `jfoundry-web-quarkus-runtime` 一起选择 | 与 `jfoundry-web-helidon` 一起选择 |

`jfoundry-restclient-spring` 要求应用自行提供 Spring Web API。Spring Boot 应用可以加入
`jfoundry-restclient-spring-boot-starter`，它提供 Spring Boot `RestClient` 集成以及
`RestClient` 集成。Quarkus 与 Helidon 的 REST Client 模块会引入对应运行时的 MicroProfile REST Client
实现，并自动注册 JFoundry 日志 provider。

## Problem Details（RFC 9457）

当 HTTP API 需要为 JFoundry 业务失败提供稳定的 RFC 9457 `application/problem+json` 响应时，使用此能力。它在运行时边界把受支持的应用层或领域层异常转换为 HTTP 响应；领域和应用代码不应选择 HTTP 状态码。

### 添加运行时入口

| 运行时 | 使用方依赖 | HTTP 集成 |
|---|---|---|
| Spring Boot | `jfoundry-webmvc-spring-boot-starter` | Spring MVC |
| Quarkus | `jfoundry-web-quarkus-runtime` | 带 Jackson 的 Quarkus REST |
| Helidon MP | `jfoundry-web-helidon` | JAX-RS |

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

### 问题消息国际化

问题响应可以在不把表现层关注点带进领域层和应用核心的前提下实现国际化。预期的失败可以用稳定的
消息 code 加插值参数来构造，而不是一句成品文案；HTTP 边界在响应时按请求 locale 从消息包中解析该
code：

```java
throw new DomainRuleViolationException("order.quota-exceeded", 2, 2);
```

日志消息保持语言无关（`order.quota-exceeded [2, 2]`）。在边界处，框架解析 code、按 `MessageFormat`
插值参数，并把 `code` 与 `args` 作为 RFC 9457 扩展成员输出，客户端也可以据此自行渲染文案：

```json
{
  "type": "urn:jfoundry:problem:domain-rule-violation",
  "title": "违反领域规则",
  "status": 422,
  "detail": "配额已超出：当前 2，上限 2",
  "code": "order.quota-exceeded",
  "args": [2, 2]
}
```

`detail` 按以下顺序解析：先查应用消息包中的 code，查不到则使用语言无关的异常消息。框架自身的标题和
通用兜底文案从随 `jfoundry-web` 发布的 `jfoundry-problems*.properties` 消息包解析（英文根包加简体中文
包）；缺失翻译时回退到英文根包。参数必须是非空的 `String`、`Number` 或 `Boolean` 值。

消息包查找方式因运行时而异：

- Spring MVC 先通过 Spring 的 `MessageSource` 解析，因此应用的 code 可以直接放在标准的
  `messages*.properties` 消息包中，然后再查框架消息包。locale 来自 Spring 的 locale 上下文。
- Quarkus REST 与 Helidon MP 先查 classpath 上的 `messages*.properties`，再查框架消息包。locale 来自
  `Accept-Language`。

按具体 locale 解析出的响应会携带 `Content-Language` 头。没有偏好语言时使用英文根包文案，不输出该
头。针对 Native Image，`jfoundry-web` 附带资源配置，使 `jfoundry-problems*.properties` 和
`messages*.properties` 消息包在原生镜像中保持可达；`jfoundry-web-helidon` 会注册其异常映射器
所需的请求头代理。

两个限制是有意为之：

- 单 `String` 参数的构造器始终表示字面量消息，因此 code 形态至少携带一个插值参数；确实没有参数的
  code 需要显式传入空的 `Object[]`。
- `ExternalAccessException` 默认保持屏蔽语义：只有经过评审的 public-detail code 构造器才会向调用方
  暴露 code 和参数。

### 明确边界

- 未知异常和受支持状态集合之外的 HTTP 失败会保留运行时原有处理。此能力不是应用的通用异常策略。
- 认证与授权仍由选定的安全集成负责。安全适配器可以通过运行时渲染器输出自己的 `401` 或 `403` 描述符。
- 当前发布的适配器覆盖 Spring MVC、Quarkus REST 和 Helidon MP JAX-RS，不对其他 HTTP 技术栈作支持声明。

### 运行时参考

- [Spring Boot 运行时装配](../implementations/spring-boot.md)说明自动配置与 Spring MVC 替换规则。
- [Quarkus 运行时集成](../implementations/quarkus.md)说明扩展组合与 Quarkus REST 行为。
- [Helidon MP 运行时集成](../implementations/helidon.md)说明 CDI 与 JAX-RS 行为。

## 请求关联

Web 运行时入口默认注册请求关联。最终校验后的值可通过 `RequestCorrelationContext.current()` 读取，默认回写
`X-Request-Id`；Spring MVC 与 Quarkus 会将它投影到 SLF4J MDC。Helidon MP 使用没有标准 MDC API 的
`System.Logger`，因此 Helidon 应用日志必须显式加入该值，但请求上下文和响应 Header 仍保持一致。

Spring 使用 `jfoundry.web.mvc.request-correlation.*`，Quarkus 使用
`jfoundry.web.quarkus.request-correlation.*`，Helidon 使用
`jfoundry.web.helidon.request-correlation.*`。三个运行时都支持 `enabled`、`header-name`、
`accept-incoming`、`write-response`、`maximum-length`（36-64）和 `excluded-paths`。排除项使用应用路径与
Ant 风格 `*`、`**`、`?` pattern；Spring 会先移除 Servlet context path，Jakarta REST 则匹配请求 URI path。
非法或超长值会被替换为服务端生成的 UUID。生命周期与 tracing 边界见[请求关联](request-correlation.md)。

## HTTP 集成与诊断日志

`jfoundry-restclient-spring` 为选定的出站 `RestClient` 调用提供显式集成。只对该集成拥有的 builder 使用
`RestClientSupport.configure(builder)`，并通过 `RestClientSupport.execute(...)` 执行调用。非成功响应会转换为
只包含状态码的 `HttpResponseException`；传输和响应解码失败会转换为带有安全失败类别的
`HttpRequestException`，同时将原始异常保留为 cause，供服务端诊断。

API 现在按抽象层级组织。跨运行时的 `HttpLoggingLevel` 与 `HttpLoggingFormat` 位于 `org.jfoundry.http`，Spring 专属的
`HttpLoggingSupport` 位于 `org.jfoundry.http.spring`，`HttpLoggingInterceptor` 位于 `org.jfoundry.http.spring.client`，
`RestClient` 外观与转换后的异常位于 `org.jfoundry.web.spring.client`。这些新位置替代原来的
`org.jfoundry.web.spring` 位置，不提供兼容别名；`ProblemDetailRenderer` 仍位于原包。

出站日志默认使用 `NONE`。应用可通过 `RestClientSupport.configure(builder, HttpLoggingLevel)` 选择四种级别；
Spring Boot 管理的 builder 使用同样默认值为 `NONE` 的
`jfoundry.web.rest-client.logging.level`。布局通过 `jfoundry.web.rest-client.logging.format` 选择，默认值为 `HUMAN`。客户端 `duration` 字段在 `INLINE` 布局下以 `duration=30ms` 形式输出，计时从调用
`ClientHttpRequestExecution.execute(...)` 前开始，到响应 header 可用或执行失败时结束，不包含响应 body
消费与解码，也不是端到端延迟。

Web MVC 启动器还通过 `HttpLoggingFilter` 提供入站 Servlet 日志。Quarkus 与 Helidon 的 Web 运行时模块会
注册等价的 JAX-RS provider。入站日志默认关闭，可通过对应运行时的配置项选择 `BASIC`、`HEADERS` 或 `FULL`：

| 运行时 | 入站明细 | 入站布局 | 默认明细 |
|---|---|---|---|
| Spring MVC | `jfoundry.web.mvc.logging.level` | `jfoundry.web.mvc.logging.format` | `NONE` |
| Quarkus REST | `jfoundry.web.quarkus.logging.level` | `jfoundry.web.quarkus.logging.format` | `NONE` |
| Helidon MP REST | `jfoundry.web.helidon.logging.level` | `jfoundry.web.helidon.logging.format` | `NONE` |

Spring MVC 默认从入站日志中排除 `/actuator/health/**`。应用可通过
`jfoundry.web.mvc.logging.excluded-paths` 配置 Ant 风格的应用内路径，以替换默认列表并增加更多排除项。
匹配前会先移除 Servlet context path 与 servlet path，因此 servlet path 为 `/api` 时，
`/api/actuator/health/liveness` 会按 `/actuator/health/liveness` 进行匹配。

Spring `RestClient` 与 MicroProfile REST Client 的出站日志统一使用
`jfoundry.web.rest-client.logging.level`，默认值为 `NONE`。Spring 应用也可以通过
`RestClientSupport.configure(builder, HttpLoggingLevel)` 为手工 builder 选择级别。JFoundry 当前不集成
Spring `WebClient`，响应式调用不属于此契约。

所有运行时都以 `INFO` 输出 HTTP 交换事件，`NONE` 会将其关闭。`BASIC` 记录 request 与 response 事件，
包含移除 query 后的 method/URI、状态和耗时，且不创建 body 包装器。`HEADERS` 额外记录脱敏后的 request
与 response header，并以不区分大小写的方式脱敏授权信息、凭证、cookie、token、secret 与
API key。`FULL` 再额外记录 JSON body；JSON body 会执行嵌套字段脱敏，最多
保留 8 KiB，非 JSON、格式错误、未完整消费或超限 body 只记录安全描述。捕获会立即转发字节，且日志失败不能
改变 HTTP 处理。

布局与明细相互独立。启用日志后默认使用 `HUMAN`：按 Feign 的方式输出，请求行以 `-->` 开头，响应行以 `<--` 开头，
JSON body 仍保持在一行。并发请求仍可能交错，需要靠 logger 的线程名或 MDC 来归组。`INLINE` 保留历史的单行
`key=value` 事件。入站布局使用上表中的 `logging.format` 配置项，出站布局使用
`jfoundry.web.rest-client.logging.format`。`HUMAN` 会省略空 body，`INLINE` 仍输出 `<empty>`。

入站 `duration` 的计时在同步完成或运行时的终态响应阶段结束，不表示调用方已经收到全部流式字节。客户端
`duration` 的计时在响应 header 可用时结束，不包含后续 body 消费与解码。Jakarta REST 客户端过滤器没有可移植的
传输失败回调，因此 Quarkus 与 Helidon 不依赖运行时私有 hook 来伪造 Spring 专属的传输失败事件。响应 body
日志只会在 body 被消费或关闭后出现。

这些诊断访问日志不能替代 Micrometer 指标或追踪，也不会发布或替代由应用拥有的业务审计事件。Spring 专属的
各运行时指南进一步说明组合方式与 logger 配置。
