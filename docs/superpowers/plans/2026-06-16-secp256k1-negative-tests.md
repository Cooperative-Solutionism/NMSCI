# Secp256k1 Negative Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Secp256k1 primitive negative-path coverage and tighten low-level input guards for malformed signatures, invalid keys, high-S signatures, and invalid DER scalars.

**Architecture:** Keep `Secp256k1EncryptUtil` as the cryptographic primitive boundary and avoid service-layer/API changes. Drive the change from focused JUnit tests, then add only local guard helpers and DER scalar validation inside the utility class.

**Tech Stack:** Java 21, JUnit 5, BouncyCastle ASN.1/secp256k1 APIs, bitcoinj `ECKey`, Maven Surefire/Failsafe.

---

## Scope Check

This plan implements `docs/superpowers/specs/2026-06-16-secp256k1-negative-tests-design.md`.

In scope:
- Negative-path tests in `Secp256k1EncryptUtilTest`.
- Primitive-level guards for `null`, wrong-length RS signatures, invalid raw private keys, invalid compressed public keys, malformed DER, and invalid DER `r/s` values.
- High-S detection test at the primitive level.
- Audit status documentation update after verification.

Out of scope:
- No replacement of bitcoinj or BouncyCastle.
- No change to double SHA-256 signing/verifying semantics.
- No API error-contract rewrite.
- No changes to `rawBytes` output policy.
- No concurrent allocation tests or broad structural refactors.

## File Structure

- Modify `src/test/java/com/cooperativesolutionism/nmsci/util/Secp256k1EncryptUtilTest.java`: expand from happy-path tests to focused primitive negative-path coverage.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/util/Secp256k1EncryptUtil.java`: add small local validation helpers and tighten the methods named in the spec.
- Modify `docs/code-quality-audit-status.md`: mark Secp256k1 primitive negative-path coverage as completed and record verification results.

---

## Task 1: Add Failing Secp256k1 Primitive Negative-Path Tests

**Files:**
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/util/Secp256k1EncryptUtilTest.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/util/Secp256k1EncryptUtilTest.java`

- [ ] **Step 1: Replace the test file with expanded primitive coverage**

Replace the full contents of `src/test/java/com/cooperativesolutionism/nmsci/util/Secp256k1EncryptUtilTest.java` with:

```java
package com.cooperativesolutionism.nmsci.util;

import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.Security;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Secp256k1EncryptUtilTest {

    private static final BigInteger CURVE_ORDER = SECNamedCurves.getByName("secp256k1").getN();
    private static final BigInteger FIELD_PRIME = new BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F",
            16
    );

    @BeforeAll
    static void addProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    void signsAndVerifiesRsSignature() throws Exception {
        byte[] data = ByteArrayUtil.hexToBytes("01020304");
        byte[] signature = Secp256k1EncryptUtil.derToRs(
                Secp256k1EncryptUtil.signData(data, Secp256k1EncryptUtil.rawToPrivateKey(TestKeyPairs.FLOW_NODE_A.prikey()))
        );

        assertFalse(Secp256k1EncryptUtil.isNotLowS(signature));
        assertTrue(Secp256k1EncryptUtil.verifySignature(
                data,
                signature,
                Secp256k1EncryptUtil.compressedToPublicKey(TestKeyPairs.FLOW_NODE_A.pubkey())
        ));
    }

    @Test
    void convertsRsSignatureToDerAndBack() throws Exception {
        byte[] data = ByteArrayUtil.hexToBytes("05060708");
        byte[] rs = Secp256k1EncryptUtil.derToRs(
                Secp256k1EncryptUtil.signData(data, Secp256k1EncryptUtil.rawToPrivateKey(TestKeyPairs.CONSUME_NODE_A.prikey()))
        );

        assertArrayEquals(rs, Secp256k1EncryptUtil.derToRs(Secp256k1EncryptUtil.rsToDer(rs)));
    }

    @Test
    void verifySignatureRejectsWrongPublicKey() throws Exception {
        byte[] data = ByteArrayUtil.hexToBytes("01020304");
        byte[] signature = signRs(data, TestKeyPairs.FLOW_NODE_A.prikey());

        assertFalse(Secp256k1EncryptUtil.verifySignature(
                data,
                signature,
                Secp256k1EncryptUtil.compressedToPublicKey(TestKeyPairs.FLOW_NODE_B.pubkey())
        ));
    }

    @Test
    void verifySignatureRejectsTamperedData() throws Exception {
        byte[] data = ByteArrayUtil.hexToBytes("01020304");
        byte[] signature = signRs(data, TestKeyPairs.FLOW_NODE_A.prikey());
        byte[] tamperedData = ByteArrayUtil.hexToBytes("01020305");

        assertFalse(Secp256k1EncryptUtil.verifySignature(
                tamperedData,
                signature,
                Secp256k1EncryptUtil.compressedToPublicKey(TestKeyPairs.FLOW_NODE_A.pubkey())
        ));
    }

    @Test
    void rsToDerRejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.rsToDer(null));
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.rsToDer(new byte[63]));
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.rsToDer(new byte[65]));
    }

    @Test
    void isNotLowSDetectsHighS() throws Exception {
        byte[] highS = rsWithS(CURVE_ORDER.shiftRight(1).add(BigInteger.ONE));

        assertTrue(Secp256k1EncryptUtil.isNotLowS(highS));
    }

    @Test
    void isNotLowSRejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.isNotLowS(null));
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.isNotLowS(new byte[63]));
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.isNotLowS(new byte[65]));
    }

    @Test
    void derToRsRejectsMalformedDer() throws Exception {
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(null));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(new byte[]{0x01, 0x02}));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(new byte[]{0x30, 0x03, 0x02, 0x01}));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(new ASN1Integer(BigInteger.ONE).getEncoded()));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSequence()));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSequence(
                new ASN1Integer(BigInteger.ONE),
                new ASN1Integer(BigInteger.TWO),
                new ASN1Integer(BigInteger.valueOf(3))
        )));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSequence(
                new DEROctetString(new byte[]{1}),
                new ASN1Integer(BigInteger.ONE)
        )));
    }

    @Test
    void derToRsRejectsInvalidScalars() {
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(BigInteger.ZERO, BigInteger.ONE)));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(BigInteger.ONE, BigInteger.ZERO)));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(BigInteger.valueOf(-1), BigInteger.ONE)));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(BigInteger.ONE, BigInteger.valueOf(-1))));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(CURVE_ORDER, BigInteger.ONE)));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(BigInteger.ONE, CURVE_ORDER)));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(CURVE_ORDER.add(BigInteger.ONE), BigInteger.ONE)));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(BigInteger.ONE, CURVE_ORDER.add(BigInteger.ONE))));
    }

    @Test
    void compressedToPublicKeyRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.compressedToPublicKey(null));
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.compressedToPublicKey(new byte[32]));

        byte[] invalidPrefix = Arrays.copyOf(TestKeyPairs.FLOW_NODE_A.pubkey(), 33);
        invalidPrefix[0] = 0x04;
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.compressedToPublicKey(invalidPrefix));

        byte[] invalidPoint = new byte[33];
        invalidPoint[0] = 0x02;
        System.arraycopy(toFixed32(FIELD_PRIME), 0, invalidPoint, 1, 32);
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.compressedToPublicKey(invalidPoint));
    }

    @Test
    void rawPrivateKeyConversionRejectsInvalidInput() {
        assertRawPrivateKeyRejected(null);
        assertRawPrivateKeyRejected(new byte[31]);
        assertRawPrivateKeyRejected(new byte[33]);
        assertRawPrivateKeyRejected(new byte[32]);
        assertRawPrivateKeyRejected(toFixed32(CURVE_ORDER));
        assertRawPrivateKeyRejected(toFixed32(CURVE_ORDER.add(BigInteger.ONE)));
    }

    private static byte[] signRs(byte[] data, byte[] rawPrivateKey) throws Exception {
        return Secp256k1EncryptUtil.derToRs(
                Secp256k1EncryptUtil.signData(data, Secp256k1EncryptUtil.rawToPrivateKey(rawPrivateKey))
        );
    }

    private static byte[] derSignature(BigInteger r, BigInteger s) throws IOException {
        return derSequence(new ASN1Integer(r), new ASN1Integer(s));
    }

    private static byte[] derSequence(ASN1Primitive... values) throws IOException {
        return new DERSequence(values).getEncoded();
    }

    private static byte[] rsWithS(BigInteger s) {
        byte[] rs = new byte[64];
        byte[] sBytes = toFixed32(s);
        System.arraycopy(sBytes, 0, rs, 32, 32);
        return rs;
    }

    private static byte[] toFixed32(BigInteger value) {
        byte[] valueBytes = value.toByteArray();
        if (valueBytes.length > 32) {
            valueBytes = Arrays.copyOfRange(valueBytes, valueBytes.length - 32, valueBytes.length);
        }
        byte[] fixed = new byte[32];
        System.arraycopy(valueBytes, 0, fixed, 32 - valueBytes.length, valueBytes.length);
        return fixed;
    }

    private static void assertRawPrivateKeyRejected(byte[] rawPrivateKey) {
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.rawToPrivateKey(rawPrivateKey));
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.rawToECKey(rawPrivateKey));
    }
}
```

- [ ] **Step 2: Run focused test to verify red state**

Run:

```powershell
.\mvnw.cmd -Dtest=Secp256k1EncryptUtilTest test
```

Expected: FAIL. Existing code should fail at least these new expectations:
- `rsToDer(null)` currently throws `NullPointerException`, not `IllegalArgumentException`.
- `isNotLowS(null/non64)` currently depends on array slicing behavior.
- `derToRs` currently accepts invalid scalar values or throws unchecked type-cast errors for malformed ASN.1.
- `rawToPrivateKey(null)` currently throws `NullPointerException`.

Do not commit the red test state.

---

## Task 2: Tighten Secp256k1 Primitive Guards

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/util/Secp256k1EncryptUtil.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/util/Secp256k1EncryptUtilTest.java`

- [ ] **Step 1: Replace `derToRs` with checked ASN.1 parsing and scalar validation**

In `Secp256k1EncryptUtil.java`, replace the full `derToRs` method with:

```java
public static byte[] derToRs(byte[] derSignature) throws IOException {
    if (derSignature == null) {
        throw new IOException("Invalid DER signature format");
    }

    ASN1Primitive primitive;
    try {
        primitive = ASN1Primitive.fromByteArray(derSignature);
    } catch (IOException e) {
        throw new IOException("Invalid DER signature format", e);
    }

    if (!(primitive instanceof ASN1Sequence seq) || seq.size() != 2) {
        throw new IOException("Invalid DER signature format");
    }
    if (!(seq.getObjectAt(0) instanceof ASN1Integer rInteger)
            || !(seq.getObjectAt(1) instanceof ASN1Integer sInteger)) {
        throw new IOException("Invalid DER signature format");
    }

    BigInteger r = rInteger.getValue();
    BigInteger s = sInteger.getValue();
    validateSignatureScalar(r, "r");
    validateSignatureScalar(s, "s");

    byte[] rs = new byte[64];
    byte[] rBytes = toFixed32Bytes(r);
    byte[] sBytes = toFixed32Bytes(s);
    System.arraycopy(rBytes, 0, rs, 0, 32);
    System.arraycopy(sBytes, 0, rs, 32, 32);
    return rs;
}
```

- [ ] **Step 2: Add DER scalar helper methods**

Add these private helpers immediately after `derToRs`:

```java
private static void validateSignatureScalar(BigInteger value, String name) throws IOException {
    if (value.compareTo(BigInteger.ZERO) <= 0 || value.compareTo(CURVE_ORDER) >= 0) {
        throw new IOException("Invalid DER signature " + name + " value");
    }
}

private static byte[] toFixed32Bytes(BigInteger value) throws IOException {
    byte[] valueBytes = value.toByteArray();
    if (valueBytes.length == 33 && valueBytes[0] == 0) {
        valueBytes = Arrays.copyOfRange(valueBytes, 1, 33);
    }
    if (valueBytes.length > 32) {
        throw new IOException("Invalid DER signature scalar length");
    }

    byte[] fixed = new byte[32];
    System.arraycopy(valueBytes, 0, fixed, 32 - valueBytes.length, valueBytes.length);
    return fixed;
}
```

- [ ] **Step 3: Add shared RS signature validation and use it in `rsToDer`**

Add this private helper near the other validation helpers:

```java
private static void validateRsSignature(byte[] rsSignature) {
    if (rsSignature == null || rsSignature.length != 64) {
        throw new IllegalArgumentException("rsSignature must be 64 bytes long");
    }
}
```

Then replace the first four lines of `rsToDer`:

```java
if (rsSignature.length != 64) {
    throw new IllegalArgumentException("rsSignature must be 64 bytes long");
}
```

with:

```java
validateRsSignature(rsSignature);
```

- [ ] **Step 4: Add shared raw private key validation and use it in both private-key conversion methods**

Add this private helper near `validateRsSignature`:

```java
private static BigInteger validateRawPrivateKey(byte[] rawPrivateKey) {
    if (rawPrivateKey == null || rawPrivateKey.length != 32) {
        throw new IllegalArgumentException("Raw private key must be 32 bytes long");
    }

    BigInteger privateKeyValue = new BigInteger(1, rawPrivateKey);
    if (privateKeyValue.compareTo(BigInteger.ZERO) <= 0 || privateKeyValue.compareTo(CURVE_ORDER) >= 0) {
        throw new IllegalArgumentException("Invalid private key value");
    }
    return privateKeyValue;
}
```

In `rawToPrivateKey`, replace:

```java
if (rawPrivateKey.length != 32) {
    throw new IllegalArgumentException("Raw private key must be 32 bytes long");
}

BigInteger privateKeyValue = new BigInteger(1, rawPrivateKey);
if (privateKeyValue.compareTo(BigInteger.ZERO) <= 0 || privateKeyValue.compareTo(CURVE_ORDER) >= 0) {
    throw new IllegalArgumentException("Invalid private key value");
}

KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", "BC");
ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(new BigInteger(1, rawPrivateKey), EC_SPEC);
```

with:

```java
BigInteger privateKeyValue = validateRawPrivateKey(rawPrivateKey);

KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", "BC");
ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(privateKeyValue, EC_SPEC);
```

In `rawToECKey`, replace:

```java
if (rawPrivateKey == null || rawPrivateKey.length != 32) {
    throw new IllegalArgumentException("Raw private key must be 32 bytes long");
}
BigInteger privateKeyValue = new BigInteger(1, rawPrivateKey);
if (privateKeyValue.compareTo(BigInteger.ZERO) <= 0 || privateKeyValue.compareTo(CURVE_ORDER) >= 0) {
    throw new IllegalArgumentException("Invalid private key value");
}
return ECKey.fromPrivate(privateKeyValue);
```

with:

```java
return ECKey.fromPrivate(validateRawPrivateKey(rawPrivateKey));
```

- [ ] **Step 5: Add null defense to compressed public-key parsing**

In `compressedToPublicKey`, replace:

```java
if (compressedPubKey.length != 33) {
    throw new IllegalArgumentException("Compressed public key must be 33 bytes long");
}
```

with:

```java
if (compressedPubKey == null || compressedPubKey.length != 33) {
    throw new IllegalArgumentException("Compressed public key must be 33 bytes long");
}
```

- [ ] **Step 6: Use RS validation in `isNotLowS`**

Replace the full `isNotLowS` method with:

```java
public static boolean isNotLowS(byte[] rsSignature) throws IOException {
    validateRsSignature(rsSignature);
    BigInteger s = new BigInteger(1, Arrays.copyOfRange(rsSignature, 32, 64));
    return s.compareTo(HALF_CURVE_ORDER) > 0;
}
```

- [ ] **Step 7: Run focused test to verify green state**

Run:

```powershell
.\mvnw.cmd -Dtest=Secp256k1EncryptUtilTest test
```

Expected: PASS with zero failures and zero errors.

- [ ] **Step 8: Commit the primitive tests and implementation**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/util/Secp256k1EncryptUtil.java src/test/java/com/cooperativesolutionism/nmsci/util/Secp256k1EncryptUtilTest.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "test: 补齐Secp256k1原语负路径"
```

---

## Task 3: Update Audit Status and Run Full Verification

**Files:**
- Modify: `docs/code-quality-audit-status.md`
- Test: full Maven verification

- [ ] **Step 1: Mark the Secp256k1 primitive item as fixed**

In `docs/code-quality-audit-status.md`, move or rewrite the current delayed item:

```markdown
- **Secp256k1 原语负路径测试缺失**（Medium，Test）：高-S 拒绝、错误密钥、篡改数据、畸形 DER 等（`validateLowS`/`PoW`/`bytesToHex` 已补，但原语层仍弱）。
```

Under `### 2.4 本轮新增修复（2026-06-16）`, add:

```markdown
- ✅ **Secp256k1 原语负路径测试与边界守卫**：补充错误公钥、篡改数据、high-S、非 64 字节 RS、畸形 DER、非法 `r/s`、非法压缩公钥、非法原始私钥等 primitive 单元测试；`derToRs` 拒绝非法 ASN.1/标量，`isNotLowS`/`rsToDer`/密钥转换方法增加稳定输入守卫。
```

In `## 7. 下一轮建议优先级`, remove `Secp256k1 原语负路径测试` from the next-priority sentence while leaving the real two-thread concurrency test as a remaining priority.

- [ ] **Step 2: Run focused verification**

Run:

```powershell
.\mvnw.cmd -Dtest=Secp256k1EncryptUtilTest test
```

Expected: PASS. Record the final Surefire test count for `Secp256k1EncryptUtilTest`.

- [ ] **Step 3: Run full unit tests**

Run:

```powershell
.\mvnw.cmd test
```

Expected: PASS with zero failures and zero errors. Record the final Surefire test count from Maven output.

- [ ] **Step 4: Run full Maven verification**

Run:

```powershell
.\mvnw.cmd verify
```

Expected: PASS if Docker/Testcontainers is available. Record Surefire and Failsafe counts. If Docker, image registry, or network access fails outside the Java code, copy the exact failure text into the audit document and final report.

- [ ] **Step 5: Update verification notes in the audit document**

In `docs/code-quality-audit-status.md`, update the verification section to mention:

```markdown
- focused `Secp256k1EncryptUtilTest` passed with the count from Step 2.
- full `mvnw test` passed with the count from Step 3.
- full `mvnw verify` passed with the surefire/failsafe counts from Step 4, or record the exact external failure if it could not complete.
```

Use the exact counts from the command output. Do not guess.

- [ ] **Step 6: Commit audit status update**

Run:

```powershell
git add docs/code-quality-audit-status.md
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "docs: 更新Secp256k1负路径审计状态"
```

---

## Task 4: Final Review and Completion

**Files:**
- No planned code changes. Fix only issues found by review or verification.

- [ ] **Step 1: Search for remaining stale audit wording**

Run:

```powershell
rg -n "Secp256k1 原语负路径测试缺失|高-S 拒绝|畸形 DER|原语层仍弱" docs src
```

Expected:
- No current audit-status wording still claims the Secp256k1 primitive item is unresolved.
- Historical design/plan files may still mention the original problem as background.

- [ ] **Step 2: Check for whitespace and worktree state**

Run:

```powershell
git diff --check
git status --short
```

Expected:
- `git diff --check` has no output.
- `git status --short` is clean after all commits.

- [ ] **Step 3: Verify commit authors and Chinese messages**

Run:

```powershell
git log --format="%h %an <%ae> %s" -6
```

Expected: new commits from this task use `GPT5.5XH <gpt5.5xh@example.local>` and Chinese commit messages.

- [ ] **Step 4: Final report**

Report:

```text
已完成：Secp256k1 primitive 负路径测试，DER r/s 标量校验，RS 长度/null 守卫，压缩公钥和原始私钥输入守卫，审计状态更新。
验证：列出 focused Secp256k1EncryptUtilTest、mvnw test、mvnw verify 的结果和测试数量。
提交：列出本轮新增提交哈希和中文提交信息。
残余：真实两线程并发测试仍按审计清单后续推进；Docker/Testcontainers 如因外部网络失败未完成，需要说明。
```
