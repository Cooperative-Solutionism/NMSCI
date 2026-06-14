package com.cooperativesolutionism.nmsci.dto;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;

public class MsgTypeMetadataDTO {

    private String code;

    private short value;

    private String hexValue;

    /** 落库/上链时的最终字节数（含中心签名与确认时间戳）。 */
    private int size;

    /** 入站 POST 请求体的字节数（中心签名之前）。对不可中心签名的注册类型，与 {@code size} 相等。 */
    private int inboundSize;

    private String sizeUnit;

    private String name;

    public static MsgTypeMetadataDTO from(MsgTypeEnum msgType) {
        MsgTypeMetadataDTO dto = new MsgTypeMetadataDTO();
        dto.setCode(msgType.name());
        dto.setValue(msgType.getValue());
        dto.setHexValue(String.format("0x%04X", msgType.getValue() & 0xFFFF));
        dto.setSize(msgType.getSize());
        dto.setInboundSize(msgType.getInboundSize());
        dto.setSizeUnit("字节");
        dto.setName(msgType.getName());
        return dto;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public short getValue() {
        return value;
    }

    public void setValue(short value) {
        this.value = value;
    }

    public String getHexValue() {
        return hexValue;
    }

    public void setHexValue(String hexValue) {
        this.hexValue = hexValue;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getInboundSize() {
        return inboundSize;
    }

    public void setInboundSize(int inboundSize) {
        this.inboundSize = inboundSize;
    }

    public String getSizeUnit() {
        return sizeUnit;
    }

    public void setSizeUnit(String sizeUnit) {
        this.sizeUnit = sizeUnit;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
