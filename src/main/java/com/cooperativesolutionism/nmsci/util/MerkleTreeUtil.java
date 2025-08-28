package com.cooperativesolutionism.nmsci.util;

import java.util.ArrayList;
import java.util.List;

public class MerkleTreeUtil {

    /**
     * 计算字节数据的txid
     *
     * @param txData 交易数据的字节数组
     * @return 值是交易数据的txid，作为32字节的字节数组
     */
    public static byte[] calcTxid(byte[] txData) {
        if (txData == null || txData.length == 0) {
            throw new IllegalArgumentException("数据不能为空");
        }
        byte[] doubleHash = Sha256Util.doubleDigest(txData);
        return ByteArrayUtil.reverseBytes(doubleHash);
    }

    /**
     * 计算Merkle树的根哈希值
     * 实现Bitcoin标准的Merkle树算法
     *
     * @param leafArr 包含叶子节点哈希值的字节数组
     *                如果数组为空或为null，则返回一个全0的32字节数组
     * @return Merkle树的根哈希值，作为32字节的字节数组
     */
    public static byte[] calcMerkleRoot(List<byte[]> leafArr) {
        if (leafArr == null || leafArr.isEmpty()) {
            return new byte[32];
        }

        List<byte[]> leafByteReverseArr = new ArrayList<>();

        for (byte[] leaf : leafArr) {
            if (leaf == null) {
                throw new IllegalArgumentException("叶子节点哈希值不能为null");
            }
            if (leaf.length != 32) {
                throw new IllegalArgumentException("叶子节点哈希值必须为32字节");
            }
            leafByteReverseArr.add(ByteArrayUtil.reverseBytes(leaf));
        }

        List<byte[]> currentLevel = new ArrayList<>(leafByteReverseArr);

        while (currentLevel.size() > 1) {
            List<byte[]> nextLevel = new ArrayList<>();

            for (int i = 0; i < currentLevel.size(); i += 2) {
                byte[] left = currentLevel.get(i);
                byte[] right;

                if (i + 1 < currentLevel.size()) {
                    right = currentLevel.get(i + 1);
                } else {
                    right = left;
                }

                byte[] combined = new byte[64];
                System.arraycopy(left, 0, combined, 0, 32);
                System.arraycopy(right, 0, combined, 32, 32);

                byte[] parentHash = Sha256Util.doubleDigest(combined);
                nextLevel.add(parentHash);
            }

            currentLevel = nextLevel;
        }

        return ByteArrayUtil.reverseBytes(currentLevel.get(0));
    }

}
