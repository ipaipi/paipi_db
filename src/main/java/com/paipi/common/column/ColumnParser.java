package com.paipi.common.column;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ColumnParser {

    public static ColumnParseResult parseColumns(List<?> columnConfig) {
        List<String> columnNames = new ArrayList<>();
        List<String> columnTypes = new ArrayList<>();

        if (columnConfig == null || columnConfig.isEmpty()) {
            return new ColumnParseResult(columnNames, columnTypes);
        }
        for (Object col : columnConfig) {
            if (col instanceof String) {
                columnNames.add((String) col);
                columnTypes.add("STRING"); // 使用String代替null
            } else if (col instanceof Map) {
                Map<?, ?> colMap = (Map<?, ?>) col;
                Object nameObj = colMap.get("name");
                Object typeObj = colMap.get("type");

                String name = nameObj != null ? nameObj.toString() : null;
                String type = typeObj != null ? typeObj.toString().toUpperCase() : "STRING";

                if (name != null) {
                    columnNames.add(name);
                    columnTypes.add(type);
                }
            }
        }

        return new ColumnParseResult(columnNames, columnTypes);
    }

    @Getter
    public static class ColumnParseResult {
        private final List<String> columnNames;
        private final List<String> columnTypes;

        public ColumnParseResult(List<String> columnNames, List<String> columnTypes) {
            this.columnNames = columnNames;
            this.columnTypes = columnTypes;
        }

        public String getColumnNamesStr() {
            return String.join(",", columnNames);
        }

        public String getColumnTypesStr() {
            return String.join(",", columnTypes);
        }

        public List<String> getColumnNamesList() {
            return columnNames;
        }

        public List<String> getColumnTypesList() {
            return columnTypes;
        }

        public String[] getColumnNamesArray() {
            return columnNames.toArray(new String[0]);
        }

        public String[] getColumnTypesArray() {
            return columnTypes.toArray(new String[0]);
        }
    }
}