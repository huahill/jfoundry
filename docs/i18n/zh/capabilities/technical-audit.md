# 技术审计

技术审计元数据属于持久化快照，不属于领域实体或聚合。因此 JFoundry 不再在领域模块提供
`Auditable`、`AuditableEntity` 或 `AuditableAggregateRoot`。

`jfoundry-persistence-core` 定义 `AuditStamp`、`AuditStampHolder`、`AuditActorProvider` 和 `AuditStamping`。审计快照只包含
`createdAt`、`createdBy`、`lastModifiedAt` 与 `lastModifiedBy`。时间使用 `Instant`；操作者字段仅保存稳定
标识。没有可用操作者时字段为 `null`，JFoundry 不会伪造 `unknown` 操作者。

`AuditStamping` 接收 `Clock` 和 `AuditActorProvider`。插入时同时填充创建与最后修改元数据；更新时保留
创建元数据，只更新最后修改元数据。持久化适配器只在实际更新时调用更新填充。

## 运行时装配

`jfoundry-persistence-spring-boot-autoconfigure` 会在 Spring Boot 中提供 UTC `Clock`、空的
`AuditActorProvider` 和 `AuditStamping`。应用定义的 `Clock`、`AuditActorProvider` 或
`AuditStamping` Bean 会分别覆盖默认值。安全集成通常只需提供 `AuditActorProvider`；认证仍由它自己负责，
并将当前 principal 映射为稳定的操作者标识。

`jfoundry-quarkus-runtime` 与 `jfoundry-persistence-helidon` 通过 CDI 提供相同的 UTC 默认值。存在且仅存在一个
应用 `AuditActorProvider` 时会使用它。Quarkus 应用可用 CDI Bean 替换默认 `AuditStamping`。Helidon 使用优先级
为 `1` 的已启用 CDI alternative；应用若要替换整个服务，必须声明优先级更高的已启用 `@Alternative`。这两个运行时
目前都没有 JFoundry MyBatis-Plus 集成。

## MyBatis-Plus

`jfoundry-persistence-mybatis-plus` 提供 `MybatisPlusAuditMetaObjectHandler`。Spring Boot MyBatis-Plus
持久化启动器会使用已配置的 `AuditStamping` 注册该处理器，除非应用已经定义 `MetaObjectHandler`。
在该 Spring Boot 装配之外，应用使用自身配置的 `AuditStamping` 注册处理器。处理器只作用于实现
`AuditStampHolder` 的数据对象；字段及 MyBatis-Plus 映射注解由应用拥有。

## Jakarta Persistence

`jfoundry-persistence-jpa` 提供 `JpaAuditData` 和 `JpaAuditStamping`。应用以自身的
`AuditStamping` 创建 `JpaAuditStamping`，并将其传入 `JpaAggregateRepository` 构造器以显式启用。
仓储会在持久化和更新前填充继承 `JpaAuditData` 的实体；它不依赖 JPA 监听器的依赖注入或全局审计上下文。

业务上有意义的提交人、审批人等事实仍应作为显式领域状态或领域事件建模。软删除和仅追加的合规审计轨迹是
独立能力。
