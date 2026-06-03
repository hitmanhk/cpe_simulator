package com.cpe.simulator.model;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;


public class ParameterValue {

    public static final String XSD_STRING = "xsd:string";
    public static final String XSD_INT = "xsd:int";
    public static final String XSD_UNSIGNED_INT = "xsd:unsignedInt";
    public static final String XSD_BOOLEAN = "xsd:boolean";
    public static final String XSD_DATE_TIME = "xsd:dateTime";
    public static final String XSD_BASE64 = "xsd:base64";

    private String value;
    private String type;

    public ParameterValue() {
        this.value = "";
        this.type = XSD_STRING;
    }

    public ParameterValue(String value, String type) {
        this.value = value != null ? value : "";
        this.type = type != null ? type : XSD_STRING;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value != null ? value : "";
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public static boolean validateType(String value, String type) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        if (type == null) {
            return true;
        }
        try {
            switch (type) {
                case XSD_STRING -> {
                    return true;
                }
                case XSD_INT -> {
                    Integer.parseInt(value);
                    return true;
                }
                case XSD_UNSIGNED_INT -> {
                    long v = Long.parseLong(value);
                    return v >= 0 && v <= 4294967295L;
                }
                case XSD_BOOLEAN -> {
                    return "true".equalsIgnoreCase(value)
                        || "false".equalsIgnoreCase(value)
                        || "1".equals(value)
                        || "0".equals(value);
                }
                case XSD_DATE_TIME -> {
                    if ("0001-01-01T00:00:00Z".equals(value)) {
                        return true;
                    }
                    OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    return true;
                }
                case XSD_BASE64 -> {
                    Base64.getDecoder().decode(value);
                    return true;
                }
                default -> {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            return false;
        }
    }

    public ParameterValue deepCopy() {
        return new ParameterValue(this.value, this.type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParameterValue that = (ParameterValue) o;
        return Objects.equals(value, that.value) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, type);
    }

    @Override
    public String toString() {
        return "ParameterValue{value='" + value + "', type='" + type + "'}";
    }
}
