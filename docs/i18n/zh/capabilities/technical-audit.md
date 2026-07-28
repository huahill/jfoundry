# 技术审计

技术审计元数据属于持久化快照，不属于领域实体或聚合。因此 JFoundry 不再在领域模块提供
`Auditable`、`AuditableEntity` 或 `AuditableAggregateRoot`。

`jfoundry-persistence-core` 定义 `AuditStamp`、`AuditActorProvider` 和 `AuditStamping`。审计快照只包含
`createdAt`、`createdBy`、`lastModifiedAt` 与 `lastModifiedBy`。时间使用 `Instant`；操作者字段仅保存稳定
标识。没有可用操作者时字段为 `null`，JFoundry 不会伪造 `unknown` 操作者。

`AuditStamping` 接收 `Clock` 和 `AuditActorProvider`。插入时同时填充创建与最后修改元数据；更新时保留
创建元数据，只更新最后修改元数据。持久化适配器只在实际更新时调用更新填充。

## MyBatis-Plus

`jfoundry-persistence-mybatis-plus` 提供 `MybatisPlusAuditData` 和
`MybatisPlusAuditMetaObjectHandler`。应用使用自身配置的 `AuditStamping` 注册该处理器。处理器只作用于
继承 `MybatisPlusAuditData` 的数据对象，不会从任意 MyBatis-Plus 数据对象推断审计字段。

## Jakarta Persistence

`jfoundry-persistence-jpa` 提供 `JpaAuditData` 和 `JpaAuditStamping`。应用以自身的
`AuditStamping` 创建 `JpaAuditStamping`，并将其传入 `JpaAggregateRepository` 构造器以显式启用。
仓储会在持久化和更新前填充继承 `JpaAuditData` 的实体；它不依赖 JPA 监听器的依赖注入或全局审计上下文。

业务上有意义的提交人、审批人等事实仍应作为显式领域状态或领域事件建模。软删除和仅追加的合规审计轨迹是
独立能力。
