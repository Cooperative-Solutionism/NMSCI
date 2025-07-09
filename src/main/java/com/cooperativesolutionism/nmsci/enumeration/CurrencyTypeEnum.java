package com.cooperativesolutionism.nmsci.enumeration;

public enum CurrencyTypeEnum {
    GOLD((short) 0, "黄金(微克)"),
    CNY((short) 1, "人民币(分)");

    private final short value;
    private final String description;

    CurrencyTypeEnum(short value, String description) {
        this.value = value;
        this.description = description;
    }

    public short getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据枚举值获取枚举
     *
     * @param value 枚举值
     * @return 对应的枚举
     */
    public static CurrencyTypeEnum fromValue(short value) {
        for (CurrencyTypeEnum type : CurrencyTypeEnum.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("No enum constant with value: " + value);
    }

    public static boolean containsValue(short value) {
        for (CurrencyTypeEnum type : CurrencyTypeEnum.values()) {
            if (type.getValue() == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * 拼接所有枚举与描述的对应关系
     *
     * @return 所有枚举与描述的字符串
     */
    public static String getAllEnumDescriptions() {
        StringBuilder descriptions = new StringBuilder();
        for (CurrencyTypeEnum type : CurrencyTypeEnum.values()) {
            descriptions.append(type.getValue()).append(": ").append(type.getDescription()).append("\n");
        }
        return descriptions.toString();
    }
}
