package com.cooperativesolutionism.nmsci.dto;

import com.cooperativesolutionism.nmsci.util.PoWUtil;

import java.math.BigInteger;

/**
 * PoW 难度元数据：注册与交易难度的 nbits（紧凑格式）及其解码后的目标阈值（十进制/十六进制）。
 */
public class DifficultyMetadataDTO {

    private DifficultyTarget register;

    private DifficultyTarget transaction;

    public static DifficultyMetadataDTO from(int registerNbits, int transactionNbits) {
        DifficultyMetadataDTO dto = new DifficultyMetadataDTO();
        dto.setRegister(DifficultyTarget.of(registerNbits));
        dto.setTransaction(DifficultyTarget.of(transactionNbits));
        return dto;
    }

    public DifficultyTarget getRegister() {
        return register;
    }

    public void setRegister(DifficultyTarget register) {
        this.register = register;
    }

    public DifficultyTarget getTransaction() {
        return transaction;
    }

    public void setTransaction(DifficultyTarget transaction) {
        this.transaction = transaction;
    }

    public static class DifficultyTarget {

        private int nbitsInt;

        private String nbitsHex;

        private String targetDecimal;

        private String targetHex;

        static DifficultyTarget of(int nbits) {
            byte[] nbitsBytes = new byte[]{
                    (byte) (nbits >>> 24),
                    (byte) (nbits >>> 16),
                    (byte) (nbits >>> 8),
                    (byte) nbits
            };
            BigInteger target = PoWUtil.calculateTargetFromNBits(nbitsBytes);

            DifficultyTarget dt = new DifficultyTarget();
            dt.setNbitsInt(nbits);
            dt.setNbitsHex(String.format("0x%08x", nbits));
            dt.setTargetDecimal(target.toString());
            dt.setTargetHex("0x" + target.toString(16));
            return dt;
        }

        public int getNbitsInt() {
            return nbitsInt;
        }

        public void setNbitsInt(int nbitsInt) {
            this.nbitsInt = nbitsInt;
        }

        public String getNbitsHex() {
            return nbitsHex;
        }

        public void setNbitsHex(String nbitsHex) {
            this.nbitsHex = nbitsHex;
        }

        public String getTargetDecimal() {
            return targetDecimal;
        }

        public void setTargetDecimal(String targetDecimal) {
            this.targetDecimal = targetDecimal;
        }

        public String getTargetHex() {
            return targetHex;
        }

        public void setTargetHex(String targetHex) {
            this.targetHex = targetHex;
        }
    }
}
