package com.cooperativesolutionism.nmsci.verifier;

/**
 * 验证项分类，决定该检查所需的信息范围。
 */
public enum CheckCategory {

    /** 结构性：.dat 帧格式、区块/消息定长布局、字段良构性，仅凭单块字节即可判定。 */
    STRUCTURAL,

    /** 密码学：哈希链衔接、中心区块签名、默克尔根重算，仅凭单块（及前一块 id）即可判定。 */
    CRYPTO,

    /** 单条消息无状态校验：msgType、金额/币种、low-S、工作量证明、成员签名与中心签名验证。 */
    STATELESS_MESSAGE,

    /** 源码哈希绑定：区块头声明的源码包哈希与给定期望值是否一致。 */
    SOURCE_HASH,

    /** 有状态回放：需按链顺序重建注册/授权/挂载等状态后才能判定的规则。 */
    STATEFUL_REPLAY
}
