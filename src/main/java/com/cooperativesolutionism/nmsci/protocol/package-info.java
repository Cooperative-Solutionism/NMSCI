/**
 * 协议层：无状态、Spring 托管的协议校验器与编解码器。
 *
 * <p>本包在 {@code util} 包提供的加密原语（{@code Secp256k1EncryptUtil}、
 * {@code Sha256Util}、{@code MerkleTreeUtil}、{@code PoWUtil}）之上施加协议规则
 * （签名低 S 约束、中心公钥状态校验、PoW 难度目标、原始字节拼装、消息编解码分发等）。</p>
 *
 * <p>约定：此处不放加密实现；加密原语一律归 {@code util} 包。</p>
 */
package com.cooperativesolutionism.nmsci.protocol;
