# Remaining Quality Optimizations Design

## Goal

Complete the remaining quality optimizations that still apply to the current codebase, while avoiding stale audit-document churn and keeping each behavioral change test-first, reviewable, and independently revertible.

## Current-State Filter

The implementation must use the current code as the source of truth. The following audit items are already resolved and are not part of this work:

- Search/list pubkey length validation.
- `WebMvcConfig` disabling Spring Boot MVC auto-configuration.
- `BlockAssembler` message-type branch dispatch.
- Block payload ordering for `findPayloadByIdIn`.
- `BlockInfo.rawBytes` JSON exposure.

## Scope

### Batch 1: Freeze Drain Reliability

`CentralPubkeyLockedMsgService` currently persists the freeze message in one transaction and then drains pending messages before requesting graceful shutdown. The remaining risk is the middle state where the freeze record has committed but the drain fails before shutdown is requested.

The implementation will make this transition explicit and recoverable:

- Preserve the existing rule that a successfully saved freeze message freezes the current central key.
- Convert drain failure into a clear logged failure state instead of an implicit half-success.
- Ensure repeated freeze handling or explicit drain retry can safely attempt the remaining drain work without corrupting already persisted state.
- Cover the failure path with service tests.

### Batch 2: API Contract Consistency

The implementation will make response serialization and status semantics more consistent without changing existing JSON field names:

- Add null-safe behavior to custom Jackson serializers.
- Keep boolean response field names stable by using standard getters or explicit `@JsonProperty`.
- Return HTTP 201 for successful create endpoints where the request creates a new resource.
- Preserve conflict responses for duplicate or non-idempotent writes.

### Batch 3: Query Transaction and Performance Boundaries

`ConsumeChainQueryService` read paths will be marked read-only where they perform read-only query orchestration. The repository `NODE` filter path will be reviewed with focused tests before any query rewrite, so the result set semantics stay unchanged.

The implementation will:

- Add `@Transactional(readOnly = true)` around read-only query service methods.
- Keep write and allocation paths out of this change.
- Optimize only query shapes that are covered by repository or integration tests.

### Batch 4: Signature Work and Lock Window

The transaction-mount save path should avoid expensive validation work while holding pessimistic allocation locks whenever the validation can be performed earlier without changing behavior.

The implementation will:

- Identify validation that is independent of locked database state.
- Move independent PoW/signature/key conversion work before the lock-sensitive allocation step.
- Preserve all existing validation messages and exception types.
- Add tests that prove validation still happens before persistence/allocation side effects.

### Batch 5: Test and Build Hygiene

The implementation will add missing low-level protocol coverage and remove low-risk configuration duplication:

- Add focused tests for `ProtocolMessageCodec`.
- Keep `*Test` and `*IT` naming stable unless a failing build proves a naming boundary is wrong.
- De-duplicate repeated Docker API version configuration if it can be done without changing build behavior.
- Attempt Docker build-stage verification when the network allows it; if Docker Hub token/network access fails, report the exact failure and do not claim Docker verification passed.

## Non-Goals

- Do not update or commit `docs/code-quality-audit-status.md` as part of this work.
- Do not hide ordinary message `rawBytes`.
- Do not perform broad entity equality/hashCode migration unless a batch test exposes a concrete bug.
- Do not rewrite repository projection package boundaries unless required by a batch already being implemented.
- Do not force Docker verification success when external registry/network access is unavailable.

## Testing Strategy

Each batch starts with failing tests and then the minimal production change needed to pass:

- Service tests for freeze drain failure and retry behavior.
- MVC or controller tests for create status codes and response contract stability.
- Repository/service tests for consume-chain query semantics.
- Service tests for transaction-mount validation ordering around allocation.
- Unit tests for protocol codec round-trips and malformed inputs.

After each batch, run the narrow test command first. Before any commit, run `git diff --check` and the relevant Maven test target. Before the final answer, run a full test command that matches the actual touched surface; run full `mvnw test` unless the batch requires failsafe integration coverage.

## Commit Strategy

Use one Chinese commit per cohesive batch. Use author and committer:

```text
GPT5.5XH <gpt5.5xh@example.local>
```

Do not create standalone commits for `docs/code-quality-audit-status.md`.
