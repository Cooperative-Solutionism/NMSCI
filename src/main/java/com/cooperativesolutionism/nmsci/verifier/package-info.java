/**
 * 独立区块链验证器：仅依赖落盘的 {@code blk*.dat} 区块文件（以及可选的源码包/源码哈希），
 * 重新解析二进制区块、重算哈希链与默克尔根、校验中心签名与各消息的工作量证明/成员签名/中心签名，
 * 从而让第三方在不信任本服务进程与数据库的前提下独立核验链的完整性。
 *
 * <p>本包为只读、纯增量能力，不改动 {@code PROTOCOL.md} 定义的字节协议与区块二进制格式；
 * 所有哈希/签名/PoW 运算复用写入侧同一批工具类（{@link com.cooperativesolutionism.nmsci.util.Sha256Util}、
 * {@link com.cooperativesolutionism.nmsci.util.MerkleTreeUtil}、
 * {@link com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil}、
 * {@link com.cooperativesolutionism.nmsci.util.PoWUtil}），以保证与生成侧逐字节一致。
 */
package com.cooperativesolutionism.nmsci.verifier;
