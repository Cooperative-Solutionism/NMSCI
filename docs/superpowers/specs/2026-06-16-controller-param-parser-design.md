# Controller Parameter Parser Design

## Goal

Reduce duplicated request-parameter parsing code in controllers without changing public API behavior.

The current controllers repeat the same small rules in private helpers:

- blank or missing query parameters mean "not supplied";
- non-blank UUID parameters are parsed with `UUID.fromString`;
- non-blank hex public-key parameters are parsed with `ByteArrayUtil.hexToBytes`;
- invalid values keep flowing through the existing global error handling path.

This design extracts those rules into one focused utility and replaces local controller helpers with that utility.

## Scope

In scope:

- Add a small controller request-parameter parsing utility.
- Replace duplicated helper methods in:
  - `FlowNodeRegisterMsgController`
  - `CentralPubkeyEmpowerMsgController`
  - `FlowNodeLockedMsgController`
  - `CentralPubkeyLockedMsgController`
  - `ConsumeChainController`
  - `TransactionRecordMsgController`
  - `TransactionMountMsgController`
  - `ReturningFlowRateController`
- Add focused unit coverage for the extracted utility.
- Run targeted tests and full unit tests.

Out of scope:

- REST parameter name changes.
- Response structure changes.
- HTTP status-code contract changes.
- Introducing new exception types.
- Write-service template refactors.
- Protocol byte-length constant refactors.

## Proposed API

Create `com.cooperativesolutionism.nmsci.util.RequestParamParser` as a final utility class with static methods:

```java
public final class RequestParamParser {
    public static boolean notBlank(String value);
    public static UUID uuidOrNull(String value);
    public static byte[] hexBytesOrNull(String value);
}
```

Behavior:

- `notBlank(null)` and `notBlank(blank)` return `false`.
- `uuidOrNull(null)` and `uuidOrNull(blank)` return `null`.
- `uuidOrNull(nonBlank)` delegates to `UUID.fromString(value)`.
- `hexBytesOrNull(null)` and `hexBytesOrNull(blank)` return `null`.
- `hexBytesOrNull(nonBlank)` delegates to `ByteArrayUtil.hexToBytes(value)`.

This preserves existing behavior because the current controller helper methods already follow these exact rules.

## Controller Changes

`TransactionRecordMsgController`:

- Replace private `hexToBytesOrNull` with `RequestParamParser.hexBytesOrNull`.

`FlowNodeRegisterMsgController`:

- Replace private `hexToBytesOrNull` with `RequestParamParser.hexBytesOrNull`.

`CentralPubkeyEmpowerMsgController`:

- Replace private `hexToBytesOrNull` with `RequestParamParser.hexBytesOrNull`.

`FlowNodeLockedMsgController`:

- Replace private `hexToBytesOrNull` with `RequestParamParser.hexBytesOrNull`.

`CentralPubkeyLockedMsgController`:

- Replace private `hexToBytesOrNull` with `RequestParamParser.hexBytesOrNull`.

`TransactionMountMsgController`:

- Replace private `hexToBytesOrNull` and `uuidOrNull` with `RequestParamParser.hexBytesOrNull` and `RequestParamParser.uuidOrNull`.

`ConsumeChainController`:

- Replace private `notBlank`, `uuid`, and `pubkey` helpers with `RequestParamParser.notBlank`, `RequestParamParser.uuidOrNull`, and `RequestParamParser.hexBytesOrNull`.

`ReturningFlowRateController`:

- Replace private `notBlank` with `RequestParamParser.notBlank`.
- Replace direct non-blank pubkey and UUID parsing calls with `RequestParamParser.hexBytesOrNull` and `RequestParamParser.uuidOrNull` where the code has already enforced required parameters.

Path-variable parsing such as `get...ById(@PathVariable String id)` may keep direct `UUID.fromString(id)` calls. This design targets optional request-parameter helpers and grouped query-parameter parsing, not a broad controller rewrite.

## Error Handling

No error mapping changes are intended.

- Invalid UUID values continue to throw `IllegalArgumentException` from `UUID.fromString`.
- Invalid hex values continue to throw through `ByteArrayUtil.hexToBytes`.
- Missing required target parameters still use existing explicit `BadRequestException` messages in `ConsumeChainController` and `ReturningFlowRateController`.

The utility must not catch and wrap parse exceptions.

## Testing

Add `RequestParamParserTest` covering:

- `notBlank`: null, blank, non-blank.
- `uuidOrNull`: null, blank, valid UUID, invalid UUID.
- `hexBytesOrNull`: null, blank, valid hex, invalid hex.

Run:

- focused parser test;
- existing controller-related tests that cover pagination/query contracts where relevant;
- full `.\mvnw.cmd test`.

## Risk

The behavioral risk is low because this is extraction-only and delegates to the same parsing functions used today.

The main risk is accidentally changing required-parameter checks in `ConsumeChainController` or `ReturningFlowRateController`. The implementation should keep those checks structurally equivalent and only replace parsing helpers after the same presence decisions have been made.

## Acceptance Criteria

- The target controllers no longer define local `notBlank`, `uuidOrNull`/`uuid`, or `hexToBytesOrNull`/`pubkey` helpers for optional request-parameter parsing.
- Existing path-variable UUID parsing may remain direct when no duplicated optional helper is involved.
- The new utility has focused unit coverage.
- Invalid UUID and hex inputs still use the existing exception path.
- Full unit tests pass.
