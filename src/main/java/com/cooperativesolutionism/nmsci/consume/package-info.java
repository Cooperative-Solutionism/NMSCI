/**
 * 消费链限界上下文：消费链的领域逻辑（分配、计划、环检测、查找）及其事务持久化。
 *
 * <p>面向 HTTP 的编排入口（{@code ConsumeChainAllocationService}、
 * {@code ConsumeChainQueryService}）放在 {@code service} 包；本包持有可复用的领域机制
 * （{@code ConsumeChainAllocator}、{@code LoopMarker}、{@code ConsumeChainSupport}）与
 * 其多表事务持久化（{@code ConsumeChainPersistenceService}）。</p>
 *
 * <p>命名约定：持久化编排助手统一命名 {@code {实体}PersistenceService}，与其所属业务/领域同包。</p>
 */
package com.cooperativesolutionism.nmsci.consume;
