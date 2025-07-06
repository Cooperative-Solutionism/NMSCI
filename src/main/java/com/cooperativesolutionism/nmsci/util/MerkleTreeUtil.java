package com.cooperativesolutionism.nmsci.util;

import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.core.PartialMerkleTree;

import java.util.ArrayList;
import java.util.Arrays;
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
        byte[] doubleHash = Sha256Hash.hashTwice(txData);
        return Sha256Hash.wrapReversed(doubleHash).getBytes();
    }

    /**
     * 计算Merkle树的根哈希值
     *
     * @param leafArr 包含叶子节点哈希值的字节数组
     *                如果数组为空或为null，则返回一个全0的32字节数组
     * @return Merkle树的根哈希值，作为32字节的字节数组
     */
    public static byte[] calcMerkleRoot(List<byte[]> leafArr) {
        if (leafArr == null || leafArr.isEmpty()) {
            return new byte[32];
        }

        int leafCount = leafArr.size();
        final List<Sha256Hash> hashes = new ArrayList<>();
        final byte[] bits = new byte[(leafCount + 7) / 8];
        Arrays.fill(bits, (byte) 0xFF);

        for (byte[] bytes : leafArr) {
            if (bytes == null) {
                throw new IllegalArgumentException("叶子节点哈希值不能为null");
            }
            hashes.add(Sha256Hash.wrap(bytes));
        }

        PartialMerkleTree pmt = new PartialMerkleTree(leafCount, hashes, bits);
        Sha256Hash rootHash = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        return rootHash.getBytes();
    }

}
