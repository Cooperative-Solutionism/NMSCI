# Consume Chain Query Unification Design

## Goal

Reduce duplication in consume-chain collection queries by replacing the six start/end/node repository methods with one unified node-filter query.

The selected approach is the broader repository-level unification. The change must preserve the existing public service methods, controller routes, request parameters, pagination return types, sorting inputs, HTTP status behavior, exception types, and Chinese error messages.

## Current State

`ConsumeChainQueryService` currently has duplicated logic across two dimensions:

- Node role: start, end, or any node.
- Optional loop filter: no `isLoop` filter or exact `isLoop` match.

This produces six repository methods:

- `findByStart`
- `findByStartAndIsLoop`
- `findByEnd`
- `findByEndAndIsLoop`
- `findDistinctByNode`
- `findDistinctByNodeAndIsLoop`

The service repeats the same flow for id and pubkey paths:

1. Validate exactly one filter.
2. Resolve a `FlowNodeRegisterMsg` by id or pubkey.
3. Pick one of the six repository methods.
4. Convert the resulting `Slice<ConsumeChain>` to `Slice<ConsumeChainResponseDTO>`.

`findDistinctByRelatedTransactionMount` is a separate mounted-transaction path and should remain separate.

## Proposed Design

Introduce `com.cooperativesolutionism.nmsci.enumeration.ConsumeChainNodeFilter`, a small node-filter type that expresses the three supported consume-chain node roles:

```java
public enum ConsumeChainNodeFilter {
    START,
    END,
    NODE
}
```

The enum should not carry persistence behavior; it is a stable query selector passed to the repository.

Replace the six start/end/node repository methods with one method:

```java
@Query("""
        select distinct c
        from ConsumeChain c
        where (:isLoop is null or c.isLoop = :isLoop)
            and (
                (:filter = com.cooperativesolutionism.nmsci.enumeration.ConsumeChainNodeFilter.START
                    and c.start = :node)
                or (:filter = com.cooperativesolutionism.nmsci.enumeration.ConsumeChainNodeFilter.END
                    and c.end = :node)
                or (:filter = com.cooperativesolutionism.nmsci.enumeration.ConsumeChainNodeFilter.NODE
                    and (
                        c.start = :node
                        or c.end = :node
                        or exists (
                            select 1
                            from ConsumeChainEdge e
                            where e.chain = c
                                and (e.source = :node or e.target = :node)
                        )
                    ))
            )
        """)
Slice<ConsumeChain> findByNodeFilter(
        @Param("filter") ConsumeChainNodeFilter filter,
        @Param("node") FlowNodeRegisterMsg node,
        @Param("isLoop") Boolean isLoop,
        Pageable pageable
);
```

The important behavior is:

- `isLoop == null` means no loop filter.
- `isLoop != null` means exact loop filtering.
- `START` matches only `c.start`.
- `END` matches only `c.end`.
- `NODE` matches `c.start`, `c.end`, or any edge where the node is source or target.
- The method returns `Slice<ConsumeChain>` and accepts `Pageable`.

Keep `findDistinctByRelatedTransactionMount(TransactionMountMsg, Pageable)` unchanged.

## Service Design

Keep all existing public methods in `ConsumeChainQueryService`:

- `getConsumeChainByStart`
- `getConsumeChainByStartAndIsLoop`
- `getConsumeChainByEnd`
- `getConsumeChainByEndAndIsLoop`
- `getConsumeChainByNode`
- `getConsumeChainByNodeAndIsLoop`
- `getConsumeChainByRelatedId`
- `getConsumeChainByPubkey`

Add private helpers that centralize the repeated work:

- `queryByNodeId(ConsumeChainNodeFilter filter, UUID id, Boolean isLoop, String label, Pageable pageable)`
- `queryByNodePubkey(ConsumeChainNodeFilter filter, byte[] pubkey, Boolean isLoop, String label, Pageable pageable)`
- `queryByNode(ConsumeChainNodeFilter filter, FlowNodeRegisterMsg node, Boolean isLoop, Pageable pageable)`

The label must preserve the current Chinese error messages:

- start id null: `起点ID不能为空`
- start id not found: `起点ID不存在`
- end id null: `终点ID不能为空`
- end id not found: `终点ID不存在`
- node id null: `节点ID不能为空`
- node id not found: `节点ID不存在`
- pubkey role names: `起点`, `终点`, `节点`

`getConsumeChainByRelatedId` should still enforce exactly one of `start`, `end`, or `node`. `getConsumeChainByPubkey` should still enforce exactly one of `startPubkey`, `endPubkey`, or `nodePubkey`.

The DTO conversion path must continue using `ConsumeChainSupport.getEdgesByChainId(...)` so edge hydration remains batched.

## Testing Strategy

Use test-driven implementation around the behavior that may change.

Add or update focused unit/contract tests:

- `ConsumeChainPaginationTest` should still assert all public `ConsumeChainQueryService` methods return `Slice`, but should now assert only the unified repository node-filter method for start/end/node queries.
- `ConsumeChainQueryOptimizationTest` should verify service id/pubkey paths call `findByNodeFilter(...)` with the correct filter, resolved node, `isLoop`, and pageable.
- Add repository/query coverage that exercises the unified JPQL query against real persisted data:
  - `START` returns chains whose `start` is the node.
  - `END` returns chains whose `end` is the node.
  - `NODE` returns chains where the node is start, end, edge source, or edge target.
  - `isLoop == null` returns both looped and unlooped chains.
  - `isLoop == true` and `false` filter correctly.

Focused commands:

```powershell
.\mvnw.cmd "-Dtest=ConsumeChainQueryOptimizationTest,ConsumeChainPaginationTest" test
.\mvnw.cmd "-Dtest=ConsumeChainRepositoryNodeFilterTest" test
```

Final verification:

```powershell
.\mvnw.cmd test
```

## Out Of Scope

- No controller route or request parameter changes.
- No response wrapper or DTO shape changes.
- No edge-query refactor under `/consume-chains/edges`.
- No returning-flow-rate refactor.
- No change to mounted-transaction query behavior.
- No global exception mapping changes.
- No change to the `PageRequestUtil` limits or sort definitions.

## Risks And Mitigations

- The unified JPQL query is broader than derived repository methods. Mitigate with repository-level tests against persisted start, end, node, edge, and loop-filter cases.
- JPQL enum constants inside query strings can be brittle. If Spring Data rejects direct enum constants, use a parameterized enum selector and compare against `:filter`.
- `NODE` matching can duplicate a chain when multiple edges match. Use `select distinct c`.
- Removing old repository methods may break reflection tests or mocks. Update tests to protect the public service contract and the new repository contract instead.
- The service must preserve existing Chinese messages. Tests should assert representative null/not-found messages for start/end/node id paths if implementation touches helper construction.

## Audit Update

After implementation and verification, update `docs/code-quality-audit-status.md`:

- Move or narrow the delayed item about `ConsumeChainQueryService` duplicate `getConsumeChainBy*` methods.
- Add a current-round fix entry describing the unified node-filter repository query and service helper consolidation.
- Record focused and full test commands with actual counts.
