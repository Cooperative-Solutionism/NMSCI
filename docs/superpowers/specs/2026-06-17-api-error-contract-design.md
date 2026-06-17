# API Error Contract Design

## Goal

Tighten the externally visible API error contract without broad service rewrites.

The selected approach is a small, contract-focused pass:

- Failed responses put the concrete client-facing error text in `message`.
- Failed responses keep `data` as `null`.
- Arbitrary `IllegalArgumentException` is no longer globally treated as HTTP 400.
- API boundary code converts known request parse and protocol validation failures to `BadRequestException`.
- Binary write endpoints declare `consumes = application/octet-stream`.

This pass intentionally does not change successful response shapes, route names, query parameter names, POST status codes, or deeper service exception taxonomy.

## Current State

`ResponseResult.failure(ResponseCode, T message)` stores the detail value in `data` while `message` remains the generic enum text such as `Bad Request`.

For example, a bad request currently returns:

```json
{
  "code": 400,
  "message": "Bad Request",
  "data": "分页大小必须大于0"
}
```

`GlobalExceptionHandler` also maps all `IllegalArgumentException` values to HTTP 400. That keeps many current request errors usable, but it also means internal programming mistakes can be mislabeled as client errors and their raw messages can leak through the API.

The six binary write endpoints accept `byte[]` request bodies but their `@PostMapping` declarations do not explicitly require `application/octet-stream`, even though the API documentation describes those endpoints as binary protocol inputs.

## Target Contract

Failed response bodies should have one stable shape:

```json
{
  "code": 400,
  "message": "分页大小必须大于0",
  "data": null
}
```

Rules:

- `code` remains the numeric `ResponseCode`.
- `message` is the specific client-facing detail for failures.
- `data` is reserved for successful payloads and remains `null` for failures.
- When a failure has no specific detail, `message` may fall back to the `ResponseCode` default text.
- Success responses are unchanged: `message = "Success"` and `data` carries the payload.

## Response Wrapper Design

Keep `ResponseResult.success(T data)` unchanged.

Keep `ResponseResult.failure(ResponseCode responseCode)` for generic failures.

Replace the misleading generic failure overload with a detail-message overload:

```java
public static ResponseResult<Void> failure(ResponseCode responseCode, String detailMessage)
```

The method should:

- reject `ResponseCode.SUCCESS`;
- use `detailMessage` as `message` when non-blank;
- otherwise use `responseCode.getMessage()`;
- always set `data` to `null`.

`GlobalExceptionHandler` should return `ResponseEntity<ResponseResult<Void>>` for failures. If type friction is high in a narrow test helper, wildcard return types are acceptable, but the JSON contract is the important boundary.

## Exception Mapping Design

Keep explicit business exceptions:

- `BadRequestException` -> HTTP 400.
- `NotFoundException` -> HTTP 404.
- `ConflictException` -> HTTP 409.
- `DataIntegrityViolationException` -> HTTP 409 with the existing sanitized unique-constraint message.
- unexpected `Exception` -> HTTP 500 with `服务器内部错误`.

Narrow validation-style framework exceptions to HTTP 400:

- `HandlerMethodValidationException`
- `MethodArgumentNotValidException`
- `BindException`
- `ConstraintViolationException`
- `MethodArgumentTypeMismatchException`
- `HttpMessageNotReadableException`

Remove `IllegalArgumentException` from the global 400 handler. After this change, an unwrapped `IllegalArgumentException` that reaches `GlobalExceptionHandler` is treated as an unexpected error and returns the sanitized HTTP 500 body.

This is intentional: request errors should be made explicit with `BadRequestException` at the API boundary, while internal bugs should not be exposed as raw 400 messages.

## API Boundary Conversion

Because the codebase currently uses `IllegalArgumentException` for several legitimate request errors, the implementation must add focused conversion at external boundaries before removing the global mapping.

In scope:

- Update request parameter/path parsing helpers so invalid UUID and invalid hex values throw `BadRequestException`.
- Convert controller-local `UUID.fromString(...)` and `ByteArrayUtil.hexToBytes(...)` calls that parse user input to the same helper path.
- For the six binary write endpoints, convert protocol decoding and request validation `IllegalArgumentException` values into `BadRequestException`.

The conversion should be narrow and local to controllers or controller request parsing utilities. Do not change low-level utilities such as `ByteArrayUtil`, `PoWUtil`, `MerkleTreeUtil`, or `Secp256k1EncryptUtil` to throw web exceptions.

If a service method is already documented and tested as throwing `IllegalArgumentException` for invalid protocol/request data, the controller may catch that exception and rethrow `BadRequestException` for the external HTTP contract. A deeper migration from service-level `IllegalArgumentException` to typed domain exceptions is out of scope for this round.

## Binary Write Endpoint Design

Add `consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE` to these six endpoints:

- `FlowNodeRegisterMsgController.saveFlowNodeRegisterMsg`
- `CentralPubkeyEmpowerMsgController.saveCentralPubkeyEmpowerMsg`
- `FlowNodeLockedMsgController.saveFlowNodeLockedMsg`
- `CentralPubkeyLockedMsgController.saveCentralPubkeyLockedMsg`
- `TransactionRecordMsgController.saveTransactionRecordMsg`
- `TransactionMountMsgController.saveTransactionMountMsg`

The endpoints should keep returning HTTP 200 with the existing `ResponseResult.success(...)` body. Changing POST success status to 201 is a separate API migration and remains out of scope.

## Out Of Scope

- No route changes.
- No query parameter name changes.
- No success response shape changes.
- No POST 200 -> 201 migration.
- No idempotency or retry semantics changes.
- No DTO getter naming changes.
- No broad service exception taxonomy rewrite.
- No full conversion of internal utility exceptions to web exceptions.
- No `docs/code-quality-audit-status.md` update until the implementation round is complete.

## Testing Strategy

Add or update focused contract tests before implementation:

- `ResponseResult` failure tests:
  - detail message is serialized as `message`;
  - `data` is `null`;
  - `ResponseCode.SUCCESS` is still rejected for failures.
- `GlobalExceptionHandlerTest`:
  - `BadRequestException`, validation exceptions, not found, conflict, data-integrity conflict, and unknown exception responses use the new `message`/`data` contract;
  - a plain `IllegalArgumentException` thrown by a controller is no longer mapped to 400 and is sanitized as 500.
- Controller/request parsing tests:
  - invalid UUID or hex input from request parameters/path variables still returns 400 through `BadRequestException`;
  - error body uses specific `message` and `data == null`.
- Binary endpoint mapping test:
  - each of the six write methods declares `application/octet-stream` in `@PostMapping.consumes`.

Focused commands:

```powershell
.\mvnw.cmd "-Dtest=GlobalExceptionHandlerTest,ResponseResultTest,RequestParamParserTest" test
```

If controller contract tests are added or updated, include them in the focused command. Final verification remains:

```powershell
.\mvnw.cmd test
```

## Risks And Mitigations

- **Risk: invalid request input becomes HTTP 500 after removing global `IllegalArgumentException` mapping.**
  Mitigation: convert known request parsing and protocol validation exceptions at controller/request-parser boundaries before changing the handler.

- **Risk: old clients read error details from `data`.**
  Mitigation: this is an intentional contract cleanup selected for this round. Success payloads remain unchanged, and failure status codes remain unchanged.

- **Risk: wrapping too broadly in controllers hides internal bugs as 400.**
  Mitigation: keep wrappers only around explicit request parsing, protocol decoding, and current request-validation calls. Do not add a generic catch-all around every controller method.

- **Risk: Spring's media type matching may reject clients that omitted `Content-Type`.**
  Mitigation: this aligns implementation with the documented binary contract. Tests should verify correct media type acceptance; clients must send `application/octet-stream` for write endpoints.

## Acceptance Criteria

- Failure details appear in `message`, not `data`.
- Failed responses have `data == null`.
- `IllegalArgumentException` is not globally mapped to HTTP 400.
- Existing malformed request inputs covered by tests still return HTTP 400 through explicit `BadRequestException` conversion.
- The six binary write endpoints require `application/octet-stream`.
- Focused tests and full unit tests pass.
