# 请求关联

请求关联能力为 HTTP 入站请求建立稳定、短小且经过校验的请求标识。它用于跨访问日志、错误响应和应用拥有的审计记录关联一次入口请求；它不是分布式追踪协议，也不替代 OpenTelemetry 的 `trace_id`、`span_id` 或 W3C `traceparent`。

## 选择与默认行为

请求关联是 Web 能力的一个可选组成项。对选择 JFoundry HTTP Web 入口的应用，运行时入口默认启用请求关联；应用仍可以通过配置关闭能力或排除路径。默认启用不会打开 HTTP 访问日志，访问日志仍由 `HttpLoggingFilter` 的独立日志级别控制且默认关闭。

默认语义如下：

- 从 `X-Request-Id` 读取入站值；
- 只接受受限字符集和长度的值，非法值、空值或缺失值由服务端重新生成；
- 将最终值放入运行时请求上下文，并在启用响应回写时返回 `X-Request-Id`；
- 请求结束后清理线程或运行时上下文中的日志投影；
- 请求标识只用于关联，不得用于认证、授权、幂等判断或访问控制。

允许的请求标识字符集严格为 `[A-Za-z0-9._~-]`，绝对最大长度为 64；服务端生成的 UUID 长度为 36。
因此 `RequestCorrelationOptions.maximumLength` 只能配置为 36 到 64，确保生成值始终符合限制。
业务代码通过公开的 `RequestCorrelationContext.current()` 读取当前请求线程可见的上下文。

应用可以调整 Header 名称、是否接受入站值、是否回写响应、最大长度和路径排除规则，但不得放宽为可注入日志控制字符的任意字符串。

## 运行时无关契约

运行时无关模块只表达以下语义，不依赖 Servlet、Spring、Quarkus、Helidon、Logback、Log4j2 或业务审计类型：

- 不可变的请求关联标识及其校验、生成规则；
- Header、请求上下文和响应回写的配置模型；
- 将最终标识暴露给外层适配器读取的上下文契约；
- 不定义日志上下文投影 SPI；运行时适配器通过各自的日志设施完成投影。

建议将契约放在现有运行时无关 Web 基础设施模块 `jfoundry-web`。不要把 HTTP 过滤器或容器生命周期 API 放入 `jfoundry-domain`、`jfoundry-application` 或其它核心业务模块。

## 运行时适配

每个运行时实现自己的入站适配器和自动装配：

| 运行时 | 适配位置 | 适配职责 |
|---|---|---|
| Spring Boot / MVC | `jfoundry-webmvc-spring` 与对应 Boot 自动配置 | Servlet Filter、Filter 顺序、异步和错误分派、响应 Header、SLF4J 日志投影 |
| Quarkus REST | `jfoundry-web-quarkus-runtime` | Quarkus REST 请求上下文、响应 Header、运行时日志上下文投影 |
| Helidon MP | `jfoundry-web-helidon` | JAX-RS/Helidon 请求上下文和响应 Header；`System.Logger` 没有等价的 MDC 投影 |

三个运行时必须保持相同的外部语义：输入校验、生成规则、Header 行为、上下文可见范围、异步清理和路径排除。Spring 会在 `ASYNC` 与 `ERROR` 再分派时重新建立状态，但不会把 MDC 自动复制到任意工作线程。Jakarta REST 适配器在响应 filter 中恢复请求线程状态，也不代表会向应用自行管理的工作线程传播上下文。每个运行时都需要自己的装配测试和异步/错误分派测试。

## 配置

Spring Boot 在 `jfoundry.web.mvc.request-correlation` 下绑定 `enabled`、`header-name`、
`accept-incoming`、`write-response`、`maximum-length` 和 `excluded-paths`。Spring 排除项使用应用路径和
Ant 风格 pattern；匹配前会移除 Servlet context path。Quarkus 和 Helidon 分别使用
`jfoundry.web.quarkus.request-correlation` 与 `jfoundry.web.helidon.request-correlation` 前缀；逗号分隔的
`excluded-paths` 使用相同的 Ant 风格 `*`、`**` 和 `?` pattern。三个运行时默认使用 `X-Request-Id`、接受入站值、
回写响应 Header、最大长度 64，且不排除路径。

Spring MVC 的请求关联适配器必须早于 HTTP 诊断日志过滤器建立上下文。建议顺序为：

```text
Request correlation     HIGHEST_PRECEDENCE + 10
HttpLoggingFilter       HIGHEST_PRECEDENCE + 20
Spring Security          later runtime registration
Application audit        security chain or application boundary
```

该顺序由自动配置显式注册，不能依赖应用中偶然的 `@Order` 排序。其它运行时也必须保证请求上下文早于其入站 HTTP 诊断日志建立。

## 日志上下文

请求上下文是请求标识的权威来源；日志上下文只是它的投影。运行时适配器可以把 `request_id` 投影到该运行时保证的日志门面，但运行时无关核心不应依赖具体日志实现。

Spring 适配器使用 `org.slf4j.MDC`，不直接依赖 Logback 或 Log4j2：

```text
应用代码 -> SLF4J MDC -> Logback
应用代码 -> SLF4J MDC -> Log4j2
```

只要 Log4j2 使用 SLF4J 绑定，应用代码无需改用 `ThreadContext`。Quarkus 使用 SLF4J MDC。Helidon MP 当前使用 `System.Logger`，没有标准 MDC 或线程上下文 API，因此不声明任意应用日志记录会自动出现 `request_id` 字段；应用可以读取 `RequestCorrelationContext.current()`，在结构化诊断中显式加入该值。所有适配器都会在终态响应回调清理投影或请求上下文；线程切换时不得假设 MDC 自动传播，应使用运行时提供的上下文传播机制。

日志可以同时输出：

```json
{
  "request_id": "request_1234",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7"
}
```

其中 `request_id` 来自请求关联能力，`trace_id` 和 `span_id` 来自宿主应用的 OpenTelemetry/Micrometer tracing 配置。JFoundry 不负责把两者互相改名或写入对方协议。

## 与 OpenTelemetry 的边界

请求关联和分布式追踪同时存在时，含义保持分离：

```text
X-Request-Id / request_id = 一次 HTTP 入口请求的应用关联编号
trace_id                  = 一次分布式操作的链路编号
span_id                   = 当前服务或操作的 span 编号
traceparent               = W3C 链路上下文传播协议
```

请求关联能力不得把 `trace_id` 填入 `X-Request-Id`，不得把 `X-Request-Id` 写入 `traceparent` 或 OpenTelemetry baggage，也不得自动把请求号写入业务审计表。出站 HTTP 的链路传播由 OpenTelemetry instrumentation 负责；出站 `X-Request-Id` 如有业务需求，应另行定义客户端能力。

## 与 HTTP 诊断日志及业务审计的边界

- HTTP 诊断日志负责方法、脱敏 URI、状态、耗时以及按级别选择的 Header/body 诊断；它默认关闭，不能替代追踪或审计。
- 请求关联能力只提供通用请求上下文和可选响应 Header，不理解 API Key、操作者、operation、审计表或业务错误码。
- 业务应用继续拥有审计事件及其脱敏策略。审计可以保存 `request_id`，也可以另存可选的 `trace_id`，但不得因此把原始 URL、Authorization 或请求体放入长期审计记录。

## 安全与兼容性

`X-Request-Id` 是不可信输入。实现必须限制长度和字符集，拒绝换行、控制字符和过大值；日志输出使用结构化参数，不拼接未经校验的 Header。请求号不能作为秘密，也不能授予调用方跨请求的权限。

默认开启会新增响应 Header 和日志上下文字段，因此属于可观察的兼容性变化。发布时应提供全局关闭开关和路径排除；升级已有应用时，必须检查其是否已经注册同名 Filter，避免两个实现互相覆盖。应用迁移到公共能力后应删除本地 Filter，只保留业务审计对统一上下文的读取。

## 验证矩阵

每个运行时至少验证：

1. 缺失、合法、非法、超长和包含控制字符的入站 Header；
2. 生成值、请求上下文和响应 Header 一致；
3. Spring 和 Quarkus 的诊断日志在请求关联之后看到 `request_id`；Helidon 因 `System.Logger` 没有 MDC API，应用必须显式加入该值；
4. 401、403、404 和路径不匹配仍然产生关联值；
5. 同步、异步、超时和错误分派结束后正确清理日志上下文；
6. 与 OpenTelemetry 同时启用时 `request_id`、`trace_id`、`span_id` 各自保持原有语义；
7. Spring 的 Logback 与 Log4j2 绑定均能输出同一字段，不要求核心模块依赖任一日志实现。

## 实施顺序

1. 在 `jfoundry-web` 定义运行时无关契约和默认配置语义。
2. 实现并测试 Spring、Quarkus、Helidon 入站适配器及各自自动装配。
3. 将请求关联注册顺序固定在各运行时 HTTP 诊断日志之前。
4. 更新能力目录、运行时指南、配置元数据和采用就绪度说明。
5. 在 `rdc-openapi` 与 `rdc-openapi-ops` 接入公共入口，删除重复的 `RequestIdFilter`/`ManagementRequestIdFilter`。
6. 验证审计、响应 Header、HTTP 诊断日志和 tracing 的关联结果，再发布兼容性说明。

该能力默认启用的前提是三个运行时的语义和测试均已完成；在此之前，不应宣称跨运行时生产支持。
