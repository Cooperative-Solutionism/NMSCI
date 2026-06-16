# Protocol Byte Length Constants Design

## Goal

Replace duplicated protocol byte-length literals with shared compile-time constants, covering both selected audit items:

- Write endpoint inbound lengths currently hard-coded in `@ByteArraySize(...)`.
- Protocol field lengths such as compressed public key, RS signature, raw private key, and UUID byte counts currently repeated across production code.

The change must not alter protocol bytes, validation behavior, REST routes, response bodies, or exception messages.

## Current State

The project already centralizes some block-level constants in `com.cooperativesolutionism.nmsci.constant.BlockConstants`. Protocol message byte lengths are still duplicated in multiple places:

- 6 write controllers use literal `@ByteArraySize(123/148/115/263/269)`.
- 6 converters return the same inbound lengths from `expectedSize()`.
- `MsgTypeEnum` repeats stored size and inbound size values.
- Models, services, crypto utilities, validators, and raw-byte builders repeat protocol field lengths such as `33`, `64`, `32`, and `16`.

`ProtocolMessageCodec` already validates converter `expectedSize()` against `MsgTypeEnum.getInboundSize()` at startup, but both sides currently read from duplicated literals.

## Proposed Design

Add `com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths` as a final utility-style constants class. It will contain only `public static final int` compile-time constants so they can be used in annotation attributes.

Core field constants:

```java
public static final int UUID_BYTES = 16;
public static final int RAW_PRIVATE_KEY_BYTES = 32;
public static final int COMPRESSED_PUBLIC_KEY_BYTES = 33;
public static final int RS_SIGNATURE_BYTES = 64;
```

Message length constants:

```java
public static final int FLOW_NODE_REGISTER_INBOUND_BYTES = 123;
public static final int CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES = 148;
public static final int CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES = 115;
public static final int FLOW_NODE_LOCKED_INBOUND_BYTES = 148;
public static final int TRANSACTION_RECORD_INBOUND_BYTES = 263;
public static final int TRANSACTION_MOUNT_INBOUND_BYTES = 269;

public static final int FLOW_NODE_REGISTER_STORED_BYTES = 123;
public static final int CENTRAL_PUBKEY_EMPOWER_STORED_BYTES = 220;
public static final int CENTRAL_PUBKEY_LOCKED_STORED_BYTES = 187;
public static final int FLOW_NODE_LOCKED_STORED_BYTES = 220;
public static final int TRANSACTION_RECORD_STORED_BYTES = 335;
public static final int TRANSACTION_MOUNT_STORED_BYTES = 341;
```

The names use `PUBLIC_KEY` rather than `PUBKEY` for clarity, while controller and entity field names remain unchanged.

## Replacement Scope

Replace protocol-length literals in production code when the literal clearly means one of the constants above:

- `@ByteArraySize(...)` on write controller request bodies.
- `expectedSize()` implementations in message converters.
- `MsgTypeEnum` stored and inbound size constructor arguments.
- `@ByteArraySize(33/64)` on message model fields.
- Production validation checks for compressed public keys, RS signatures, raw private keys, and UUID byte buffers.
- `ProtocolRawBytesBuilder` internal `UUID_BYTES` / `PUBKEY_BYTES` constants.

Keep local arithmetic and slicing readable. For RS signature split points in `Secp256k1EncryptUtil`, use constants only where they clarify meaning; avoid replacing every `32` if it makes half-signature slicing less obvious. If needed, introduce a local `RS_COMPONENT_BYTES = RS_SIGNATURE_BYTES / 2` inside the utility.

## Out of Scope

- No message layout changes.
- No converter parsing rewrite beyond returning constants from `expectedSize()`.
- No `ByteArraySize` annotation redesign.
- No global `IllegalArgumentException` mapping change.
- No API `consumes` changes.
- No mechanical replacement of unrelated numeric literals in tests, hash-length assertions, pagination, file indexes, or block constants.
- No broad service-template or query-service refactor.

## Testing Strategy

Add focused contract tests for the constants and their existing integration points:

- Verify `MsgTypeEnum.getInboundSize()` and `getSize()` match `ProtocolByteLengths`.
- Verify every converter `expectedSize()` matches its `MsgTypeEnum.getInboundSize()`.
- Verify all 6 write controller annotations reference the expected inbound values through `@ByteArraySize` runtime metadata.

Run existing protocol and full regression checks:

- Focused new constants test.
- Existing converter/protocol tests affected by constants.
- `mvnw test`.
- `mvnw verify`.

## Audit Update

After verification, update `docs/code-quality-audit-status.md`:

- Add this repair under the existing `### 2.4` current-round fixes section.
- Remove or narrow the delayed bullets for controller `@ByteArraySize` hardcoding and protocol length constants, depending on what remains after implementation.
- Update verification counts if tests are added.

## Risks And Mitigations

- Annotation values require compile-time constants. The constants class must use literal or constant-expression `public static final int` fields, not values derived from enum getters.
- Some `33/64/32/16` occurrences are not the same protocol concept. The implementation must classify each replacement rather than rely on blind search-and-replace.
- Test code may intentionally use wrong lengths to exercise validation. Such values can remain literal when that improves test intent.
