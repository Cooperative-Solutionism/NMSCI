package com.cooperativesolutionism.nmsci.support;

import com.cooperativesolutionism.nmsci.enumeration.CurrencyTypeEnum;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;
import java.util.UUID;

public class ProtocolMessageBuilder {

    public ProtocolMessageBuilder() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public byte[] flowNodeRegister(UUID id, TestKeyPair flowNode, int registerDifficultyNbits) {
        byte[] prefix = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(MsgTypeEnum.FlowNodeRegisterMsg.getValue()),
                ByteArrayUtil.uuidToBytes(id)
        );
        prefix = ArrayUtils.addAll(prefix, ByteArrayUtil.intToBytes(registerDifficultyNbits));

        final byte[] verifyPrefix = prefix;
        int nonce = PoWTestHelper.findNonce(registerDifficultyNbits, candidateNonce -> {
            byte[] candidate = ArrayUtils.addAll(verifyPrefix, ByteArrayUtil.intToBytes(candidateNonce));
            return ArrayUtils.addAll(candidate, flowNode.pubkey());
        });
        byte[] verifyData = ArrayUtils.addAll(prefix, ByteArrayUtil.intToBytes(nonce));
        verifyData = ArrayUtils.addAll(verifyData, flowNode.pubkey());
        return ArrayUtils.addAll(verifyData, signRs(verifyData, flowNode));
    }

    public byte[] centralPubkeyEmpower(UUID id, TestKeyPair flowNode, TestKeyPair central) {
        byte[] verifyData = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(MsgTypeEnum.CentralPubkeyEmpowerMsg.getValue()),
                ByteArrayUtil.uuidToBytes(id)
        );
        verifyData = ArrayUtils.addAll(verifyData, flowNode.pubkey());
        verifyData = ArrayUtils.addAll(verifyData, central.pubkey());
        return ArrayUtils.addAll(verifyData, signRs(verifyData, flowNode));
    }

    public byte[] transactionRecord(
            UUID id,
            long amount,
            TestKeyPair consumeNode,
            TestKeyPair flowNode,
            TestKeyPair central,
            int transactionDifficultyNbits
    ) {
        byte[] prefix = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(MsgTypeEnum.TransactionRecordMsg.getValue()),
                ByteArrayUtil.uuidToBytes(id)
        );
        prefix = ArrayUtils.addAll(prefix, ByteArrayUtil.longToBytes(amount));
        prefix = ArrayUtils.addAll(prefix, ByteArrayUtil.shortToBytes(CurrencyTypeEnum.CNY.getValue()));
        prefix = ArrayUtils.addAll(prefix, ByteArrayUtil.intToBytes(transactionDifficultyNbits));

        final byte[] verifyPrefix = prefix;
        int nonce = PoWTestHelper.findNonce(transactionDifficultyNbits, candidateNonce -> {
            byte[] candidate = ArrayUtils.addAll(verifyPrefix, ByteArrayUtil.intToBytes(candidateNonce));
            candidate = ArrayUtils.addAll(candidate, consumeNode.pubkey());
            candidate = ArrayUtils.addAll(candidate, flowNode.pubkey());
            return ArrayUtils.addAll(candidate, central.pubkey());
        });
        byte[] verifyData = ArrayUtils.addAll(prefix, ByteArrayUtil.intToBytes(nonce));
        verifyData = ArrayUtils.addAll(verifyData, consumeNode.pubkey());
        verifyData = ArrayUtils.addAll(verifyData, flowNode.pubkey());
        verifyData = ArrayUtils.addAll(verifyData, central.pubkey());

        byte[] body = ArrayUtils.addAll(verifyData, signRs(verifyData, consumeNode));
        return ArrayUtils.addAll(body, signRs(verifyData, flowNode));
    }

    public byte[] transactionMount(
            UUID id,
            UUID mountedTransactionRecordId,
            TestKeyPair consumeNode,
            TestKeyPair flowNode,
            TestKeyPair central,
            int transactionDifficultyNbits
    ) {
        byte[] prefix = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(MsgTypeEnum.TransactionMountMsg.getValue()),
                ByteArrayUtil.uuidToBytes(id)
        );
        prefix = ArrayUtils.addAll(prefix, ByteArrayUtil.uuidToBytes(mountedTransactionRecordId));
        prefix = ArrayUtils.addAll(prefix, ByteArrayUtil.intToBytes(transactionDifficultyNbits));

        final byte[] verifyPrefix = prefix;
        int nonce = PoWTestHelper.findNonce(transactionDifficultyNbits, candidateNonce -> {
            byte[] candidate = ArrayUtils.addAll(verifyPrefix, ByteArrayUtil.intToBytes(candidateNonce));
            candidate = ArrayUtils.addAll(candidate, consumeNode.pubkey());
            candidate = ArrayUtils.addAll(candidate, flowNode.pubkey());
            return ArrayUtils.addAll(candidate, central.pubkey());
        });
        byte[] verifyData = ArrayUtils.addAll(prefix, ByteArrayUtil.intToBytes(nonce));
        verifyData = ArrayUtils.addAll(verifyData, consumeNode.pubkey());
        verifyData = ArrayUtils.addAll(verifyData, flowNode.pubkey());
        verifyData = ArrayUtils.addAll(verifyData, central.pubkey());

        byte[] body = ArrayUtils.addAll(verifyData, signRs(verifyData, consumeNode));
        return ArrayUtils.addAll(body, signRs(verifyData, flowNode));
    }

    public byte[] centralPubkeyLocked(UUID id, TestKeyPair central) {
        byte[] verifyData = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(MsgTypeEnum.CentralPubkeyLockedMsg.getValue()),
                ByteArrayUtil.uuidToBytes(id)
        );
        verifyData = ArrayUtils.addAll(verifyData, central.pubkey());
        return ArrayUtils.addAll(verifyData, signRs(verifyData, central));
    }

    public byte[] withMsgType(byte[] message, short msgType) {
        byte[] copy = message.clone();
        byte[] msgTypeBytes = ByteArrayUtil.shortToBytes(msgType);
        copy[0] = msgTypeBytes[0];
        copy[1] = msgTypeBytes[1];
        return copy;
    }

    public byte[] withBrokenSignature(byte[] message) {
        byte[] copy = message.clone();
        copy[copy.length - 1] = (byte) (copy[copy.length - 1] ^ 0x01);
        return copy;
    }

    private byte[] signRs(byte[] data, TestKeyPair keyPair) {
        try {
            return Secp256k1EncryptUtil.derToRs(
                    Secp256k1EncryptUtil.signData(data, Secp256k1EncryptUtil.rawToPrivateKey(keyPair.prikey()))
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign test protocol message", e);
        }
    }
}
