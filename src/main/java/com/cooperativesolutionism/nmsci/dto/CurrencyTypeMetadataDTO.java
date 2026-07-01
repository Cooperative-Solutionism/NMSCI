package com.cooperativesolutionism.nmsci.dto;

import com.cooperativesolutionism.nmsci.enumeration.CurrencyTypeEnum;

public class CurrencyTypeMetadataDTO {

    private String code;

    private short value;

    private String description;

    private String unit;

    private String unitDescription;

    public static CurrencyTypeMetadataDTO from(CurrencyTypeEnum currencyType) {
        CurrencyTypeMetadataDTO dto = new CurrencyTypeMetadataDTO();
        dto.setCode(currencyType.name());
        dto.setValue(currencyType.getValue());
        dto.setDescription(currencyType.getDescription());
        dto.setUnit(currencyType.getUnit());
        dto.setUnitDescription(currencyType.getUnitDescription());
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getUnitDescription() {
        return unitDescription;
    }

    public void setUnitDescription(String unitDescription) {
        this.unitDescription = unitDescription;
    }
}
