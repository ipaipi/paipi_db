package com.paipi.common.column;


import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ColumnStruct {
    private String columnName;
    private Column.Type type;
    private int precision;
    private int scale;
    private boolean isStr = false;
    private String index = "";

    public ColumnStruct() {
    }

    public ColumnStruct(String columnName, Column.Type type, int precision, int scale, int index) {
        this.columnName = columnName;
        this.type = type;
        this.precision = precision;
        this.scale = scale;
        String temp = index + "";
        for (int i = 0; i < 10 - temp.length(); i++) {
            this.index += "0";
        }
        this.index += temp;
    }

    public ColumnStruct(String columnName, String typeStr, int precision, int scale, int index) {
        // 传入进来的类型是已经转成内部类型的字符串
        this.columnName = columnName;
        this.type = parseType(typeStr.toUpperCase());
        this.precision = precision;
        this.scale = scale;
        String temp = index + "";
        for (int i = 0; i < 10 - temp.length(); i++) {
            this.index += "0";
        }
        this.index += temp;
    }

    public ColumnStruct(String columnName, String typeStr, int index) {
        this(columnName, typeStr, 2, 2, index);
    }

    private Column.Type parseType(String typeStr) {
        try {
            return Column.Type.valueOf(typeStr);
        } catch (Exception e) {
            return Column.Type.BAD;
        }
    }
}
