package com.paipi.transformer.impl;

import com.paipi.common.channel.Record;
import com.paipi.common.column.Column;
import com.paipi.constant.TransformerParameterConstant;
import com.paipi.transformer.Transformer;

import java.util.*;

public class FilterTransformer implements Transformer {
    private Set<Integer> columnIndexSet = null;
    private String value;
    private String op = "="; // 支持=, !=, >, <等

    public FilterTransformer() {
    }

    public FilterTransformer(Map<String, Object> parameter) {
        if (parameter != null) {
            if (parameter.get(TransformerParameterConstant.COLUMN_INDEX) != null) {
                columnIndexSet = new HashSet<>();
                Object idxObj = parameter.get(TransformerParameterConstant.COLUMN_INDEX);
                if (idxObj instanceof List) {
                    for (Object o : (List<?>) idxObj) {
                        columnIndexSet.add(Integer.parseInt(o.toString()));
                    }
                } else if (idxObj instanceof Number) {
                    columnIndexSet.add(((Number) idxObj).intValue());
                } else if (idxObj instanceof String) {
                    columnIndexSet.add(Integer.parseInt((String) idxObj));
                }
            }
            value = parameter.get(TransformerParameterConstant.FILTER_VALUE) == null ? null : parameter.get(TransformerParameterConstant.FILTER_VALUE).toString();
            op = parameter.get(TransformerParameterConstant.FILTER_OP) == null ? "=" : parameter.get(TransformerParameterConstant.FILTER_OP).toString();
        }
    }

    @Override
    public Record transform(Record record) {
        if (record == null || value == null) return record;
        if (columnIndexSet == null || columnIndexSet.isEmpty()) return record;
        // 只判断第一个字段
        int idx = columnIndexSet.iterator().next();
        if (idx < 0 || idx >= record.getColumnNumber()) return record;
        Column col = record.getColumn(idx);
        String colVal = col == null ? null : col.asString();
        boolean match = false;
        if ("=".equals(op)) {
            match = Objects.equals(colVal, value);
        } else if ("!=".equals(op)) {
            match = !Objects.equals(colVal, value);
        } else if (">".equals(op)) {
            try {
                match = colVal != null && Double.parseDouble(colVal) > Double.parseDouble(value);
            } catch (Exception e) {
                match = false;
            }
        } else if ("<".equals(op)) {
            try {
                match = colVal != null && Double.parseDouble(colVal) < Double.parseDouble(value);
            } catch (Exception e) {
                match = false;
            }
        }
        return match ? record : null;
    }
} 