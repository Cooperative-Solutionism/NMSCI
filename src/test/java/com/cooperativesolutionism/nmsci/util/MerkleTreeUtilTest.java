package com.cooperativesolutionism.nmsci.util;

import org.bitcoinj.base.Sha256Hash;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MerkleTreeUtilTest {

    @Test
    void calcMerkleRoot() {
        // Bitcoin Block 100,000
        // https://www.blockchain.com/explorer/blocks/btc/100000
        List<byte[]> testData = List.of(
                ByteArrayUtil.hexToBytes("8c14f0db3df150123e6f3dbbf30f8b955a8249b62ac1d1ff16284aefa3d06d87"),
                ByteArrayUtil.hexToBytes("fff2525b8931402dd09222c50775608f75787bd2b87e56995a7bdd30f79702c4"),
                ByteArrayUtil.hexToBytes("6359f0868171b1d194cbee1af2f16ea598ae8fad666d9b012c8ed2b79a236ec4"),
                ByteArrayUtil.hexToBytes("e9a66845e05d5abc0ad04ec80f774a7e585c6e8db975962d069a522137b80c1d")
        );

        // 比特币计算出的Merkle Root就是大端序的，储存时需要反转字节序
        byte[] merkleRoot = MerkleTreeUtil.calcMerkleRoot(testData);
        assertEquals("f3e94742aca4b5ef85488dc37c06c3282295ffec960994b2c0d5ac2a25a95766", ByteArrayUtil.bytesToHex(merkleRoot));
        System.out.println("merkleRoot = " + ByteArrayUtil.bytesToHex(merkleRoot));
    }

    @Test
    void txidTest() {
        // https://www.blockchain.com/explorer/transactions/btc/e9a66845e05d5abc0ad04ec80f774a7e585c6e8db975962d069a522137b80c1d
        byte[] testData = ByteArrayUtil.hexToBytes(
                "01000000010b6072b386d4a773235237f64c1126ac3b240c84b917a3909ba1c43ded5f51f4000000008c493046022100bb1ad26df930a51cce110cf44f7a48c3c561fd977500b1ae5d6b6fd13d0b3f4a022100c5b42951acedff14abba2736fd574bdb465f3e6f8da12e2c5303954aca7f78f3014104a7135bfe824c97ecc01ec7d7e336185c81e2aa2c41ab175407c09484ce9694b44953fcb751206564a9c24dd094d42fdbfdd5aad3e063ce6af4cfaaea4ea14fbbffffffff0140420f00000000001976a91439aa3d569e06a1d7926dc4be1193c99bf2eb9ee088ac00000000"
        );

        // 比特币中计算出的txid是双hash后翻转的，但在之后储存时需要再次翻转，所以储存时的txid实际上就是双hash的原始结果
        byte[] doubleHash = Sha256Hash.hashTwice(testData);
        System.out.println("doubleHash = " + ByteArrayUtil.bytesToHex(doubleHash));
        byte[] txid = MerkleTreeUtil.calcTxid(testData);
        assertEquals("e9a66845e05d5abc0ad04ec80f774a7e585c6e8db975962d069a522137b80c1d", ByteArrayUtil.bytesToHex(txid));
        System.out.println("txid = " + ByteArrayUtil.bytesToHex(txid));
    }


}