# 聚合持久化

聚合持久化在允许业务项目选择技术实现的同时保留聚合边界。领域模型不是持久化 schema：注解、类型处理器、逻辑删除字段和生成式 ID 都应位于聚合之外。

## 契约

`DataMapper` 是聚合与持久化表示之间的显式边界，负责 ID 转换、聚合还原以及当前聚合状态映射。它可以是纯 Java、在合适场景由 MapStruct 生成，或在应用需要时作为依赖注入组件。

持久化适配器负责完整的聚合操作，但不会提供通用的多表集合同步策略。整体替换、差量更新、只追加写入、级联和删除顺序都是业务决策。业务适配器负责从属记录同步；数据库约束有特殊要求时，使用项目级原子删除策略。

## 事务与失败语义

`AggregatePersistenceContext` 在一个运行时管理的事务中追踪所有持久化状态。被追踪的操作如果发生在事务外，或聚合并非在该事务内加载，会快速失败。不支持分离聚合的合并生命周期。

`PersistenceFailureTranslator` 是运行时无关的 SPI。运行时集成可以把已知的可用性故障转换为 `ExternalAccessException`；完整性、锁、SQL、映射器和未知错误保持原始含义，除非业务适配器能够识别出真正的业务冲突。

## 选择实现

| 需求 | 指南 |
|------|------|
| MyBatis-Plus 数据对象、仓储、查询包装器和乐观锁 | [MyBatis-Plus](../implementations/mybatis-plus.md) |
| 每个聚合一个由 JPA 管理的实体图以及 JPA 乐观锁 | [JPA](../implementations/jpa.md) |

Spring Boot 运行时装配和用户 Bean 覆盖见 [Spring Boot](../implementations/spring-boot.md)。启动器目录、配置项和条件查询见 [Spring Boot 自动配置](../reference/spring-boot-autoconfiguration.md)。
