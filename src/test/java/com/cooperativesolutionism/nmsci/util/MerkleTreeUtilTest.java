package com.cooperativesolutionism.nmsci.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MerkleTreeUtilTest {

    @Test
    void calculatesTxidAsReversedDoubleSha256() {
        byte[] txData = ByteArrayUtil.hexToBytes("01020304");
        byte[] expected = ByteArrayUtil.reverseBytes(Sha256Util.doubleDigest(txData));

        assertArrayEquals(expected, MerkleTreeUtil.calcTxid(txData));
    }

    @Test
    void returnsZeroMerkleRootForNoLeaves() {
        assertArrayEquals(new byte[32], MerkleTreeUtil.calcMerkleRoot(List.of()));
    }

    @Test
    void rejectsInvalidLeaves() {
        assertThrows(IllegalArgumentException.class, () -> MerkleTreeUtil.calcMerkleRoot(List.of(new byte[31])));
    }

    @Test
    void calculatesMerkleRootForTwoLeaves() {
        byte[] left = ByteArrayUtil.hexToBytes("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        byte[] right = ByteArrayUtil.hexToBytes("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        assertEquals(64, ByteArrayUtil.bytesToHex(MerkleTreeUtil.calcMerkleRoot(List.of(left, right))).length());
    }

    @Test
    void duplicatesOddTailWhenBuildingMerkleLevels() {
        byte[] first = ByteArrayUtil.hexToBytes("1111111111111111111111111111111111111111111111111111111111111111");
        byte[] second = ByteArrayUtil.hexToBytes("2222222222222222222222222222222222222222222222222222222222222222");
        byte[] third = ByteArrayUtil.hexToBytes("3333333333333333333333333333333333333333333333333333333333333333");

        byte[] leftParent = merkleParent(first, second);
        byte[] duplicatedTailParent = merkleParent(third, third);
        byte[] expectedRoot = merkleParent(leftParent, duplicatedTailParent);

        assertArrayEquals(expectedRoot, MerkleTreeUtil.calcMerkleRoot(List.of(first, second, third)));
    }

    private static byte[] merkleParent(byte[] left, byte[] right) {
        byte[] combined = new byte[64];
        System.arraycopy(ByteArrayUtil.reverseBytes(left), 0, combined, 0, 32);
        System.arraycopy(ByteArrayUtil.reverseBytes(right), 0, combined, 32, 32);
        return ByteArrayUtil.reverseBytes(Sha256Util.doubleDigest(combined));
    }
}
