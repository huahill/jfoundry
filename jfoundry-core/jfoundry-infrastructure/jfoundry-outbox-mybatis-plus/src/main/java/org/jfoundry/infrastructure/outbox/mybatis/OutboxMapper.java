package org.jfoundry.infrastructure.outbox.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/// MyBatis-Plus mapper for the Outbox table.
/// <p>
/// Inherits only standard {@link BaseMapper} capabilities such as insert, selectById, updateById,
/// selectPage, and deleteByIds. It no longer contains custom {@code @Update}, {@code @Select}, or
/// {@code @Delete} SQL.
/// <p>
/// Design principle: all cross-dialect capabilities such as pagination, CRUD, and condition
/// construction are generated at runtime by MyBatis-Plus and PaginationInnerInterceptor according
/// to {@link com.baomidou.mybatisplus.annotation.DbType}. Source code does not maintain separate
/// SQL copies for each database such as MySQL, H2, PostgreSQL, or Oracle.
/// <p>
/// Key operation strategies, all implemented in {@link MybatisPlusOutboxMessageStore}:
/// <ul>
///   <li><b>Atomic batch claim</b>: avoids MySQL-specific {@code UPDATE...ORDER BY...LIMIT N} and
///       uses {@code selectPage(N) → per-row CAS UPDATE}. selectPage LIMIT is generated per dialect
///       by PaginationInnerInterceptor; CAS UPDATE is standard ANSI SQL
///       ({@code WHERE event_id=? AND status=?}) and is cross-dialect. Rows taken by concurrent
///       claimers fail CAS naturally and are skipped without sending.</li>
///   <li><b>Stuck recovery</b>: an {@code UPDATE} that restores {@code PENDING} status and clears
///       claim metadata,
///       a standard conditional UPDATE.</li>
///   <li><b>Batch cleanup</b>: loops with {@code selectPage(N) + removeByIds}, using cross-dialect
///       pagination.</li>
/// </ul>
/// <p>
/// The entity type is {@link OutboxData}, the MP persistence view. SPI-level {@code OutboxMessage}
/// objects are converted at the boundary by {@link MybatisPlusOutboxMessageStore}.
@Mapper
public interface OutboxMapper extends BaseMapper<OutboxData> {
}
