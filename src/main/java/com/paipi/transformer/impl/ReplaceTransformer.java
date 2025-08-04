package com.paipi.transformer.impl;

import com.paipi.common.channel.Record;
import com.paipi.common.column.Column;
import com.paipi.common.column.StringColumn;
import com.paipi.constant.TransformerParameterConstant;
import com.paipi.transformer.Transformer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReplaceTransformer implements Transformer {
    private Set<Integer> columnIndexSet = null;
    private String oldValue;
    private String newValue;

    public ReplaceTransformer() {
    }

    public ReplaceTransformer(Map<String, Object> parameter) {
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
            oldValue = parameter.get(TransformerParameterConstant.REPLACE_OLD_VALUE) == null ? null : parameter.get(TransformerParameterConstant.REPLACE_OLD_VALUE).toString();
            newValue = parameter.get(TransformerParameterConstant.REPLACE_NEW_VALUE) == null ? null : parameter.get(TransformerParameterConstant.REPLACE_NEW_VALUE).toString();
        }
    }

    @Override
    public Record transform(Record record) {
        if (record == null || oldValue == null || newValue == null) return record;
        for (int i = 0; i < record.getColumnNumber(); i++) {
            if (columnIndexSet != null && !columnIndexSet.contains(i)) continue;
            Column col = record.getColumn(i);
            if (col instanceof StringColumn) {
                String val = ((StringColumn) col).asString();
                if (val != null) {
                    record.setColumn(i, new StringColumn(val.replace(oldValue, newValue)));
                }
            }
        }
        return record;
    }
}