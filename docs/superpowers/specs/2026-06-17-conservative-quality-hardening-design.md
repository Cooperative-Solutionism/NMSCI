# Conservative Quality Hardening Design

## Goal

Implement the remaining selected optimization items 4, 5, 6, and 7 using the conservative scope approved for JPA hardening. The work should improve API contract consistency, repository boundaries, defensive tests, and build/test hygiene without introducing schema changes or broad entity identity rewrites.

## Current Scope Choice

The approved JPA scope is **A: conservative hardening**.

This means the work will not:

- Add `@Version` fields.
- Rewrite entity `equals` or `hashCode`.
- Change `byte[] @Id` mappings.
- Add Flyway migrations.
- Change Merkle tree semantics.
- Change production advisory lock behavior.
- Modify `docs/code-quality-audit-status.md`.

## Batch 1: Flow Node State DTO Contract

`FlowNodeStateResponseDTO` has the same boolean response shape as DTOs that were already hardened in the previous round. This batch will make its JSON contract explicit and test it.

Planned behavior:

- Preserve JSON field names:
  - `registered`
  - `authorized`
  - `locked`
  - `currentCentralPubkeyAuthorized`
- Do not emit alternate boolean names such as `isLocked`.
- Add focused JSON serialization tests.
- Keep controller and service response bodies unchanged.

## Batch 2: Repository Projection Boundary

`FlowNodeRegisterMsgRepository` currently imports projection interfaces from the `protocol` package. That creates a reverse dependency from persistence to protocol-facing types.

Planned behavior:

- Move `FlowNodeState` and `FlowNodeStateOverview` projection interfaces into a repository-owned projection package, such as `com.cooperativesolutionism.nmsci.repository.projection`.
- Update repository and service/controller tests to reference the new projection package.
- Keep the method names, query SQL, and response DTO mapping unchanged.
- Preserve endpoint JSON behavior for `/flow-nodes/{flowNodePubkey}`.

`FlowNodeListItemDTO` is intentionally not moved in this batch. It is constructed directly by a JPQL constructor expression and is already an API response DTO. Moving it would require a broader mapping layer change and is left for a separate architecture cleanup.

## Batch 3: Conservative JPA Defensive Coverage

The current scope excludes `@Version`, entity equality rewrites, and `byte[] @Id` mapping changes. This batch will only add low-risk defensive code/tests where behavior is already intended.

Planned behavior:

- Harden `LoopMarker` to compare node identity by persistent id rather than object reference.
- Add a test proving that two different `FlowNodeRegisterMsg` instances with the same id are treated as the same node for loop detection.
- Add source-level or unit-level contract coverage documenting that entity-wide `equals/hashCode`, `@Version`, and `byte[] @Id` changes are intentionally not part of this conservative pass.

## Batch 4: Build and Test Hygiene

Several low-risk cleanup items remain in build/test infrastructure.

Planned behavior:

- Make the Docker API version fallback in `NmsciIntegrationTestBase` explicitly tied to the Maven `docker.api.version` property value when tests are run under Maven, while keeping a documented fallback for direct IDE execution.
- Evaluate the empty Spring Boot `<requiresUnpack/>` configuration and remove it if `mvnw test`, `mvnw verify`, and `mvnw package` still pass.
- Add lightweight documentation or contract coverage for the block-generation advisory lock invariant, without changing lock behavior.
- Add lightweight documentation or test coverage for Merkle duplicate-tail behavior, without changing `MerkleTreeUtil` output.

## Testing Strategy

Each batch should start with the narrowest failing or coverage test appropriate for the change:

- JSON contract tests for `FlowNodeStateResponseDTO`.
- Repository/service contract tests for the moved projection interfaces.
- `LoopMarker` tests for id-based node comparison.
- Build/test hygiene source or behavior tests for Docker API version fallback, advisory lock documentation, Merkle behavior, and `requiresUnpack` removal.

After each batch, run focused tests first. Before each commit, run `git diff --check`. Before final completion, run at least:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd -DskipTests package
```

Docker build-stage verification may still be blocked by Docker Hub token/network access. If it fails before project build execution, report the exact external failure and do not claim Docker verification passed.

## Commit Strategy

Use one Chinese commit per cohesive batch. Use this author and committer:

```text
GPT5.5XH <gpt5.5xh@example.local>
```

Do not create standalone commits for `docs/code-quality-audit-status.md`, and do not modify that file in this work.
