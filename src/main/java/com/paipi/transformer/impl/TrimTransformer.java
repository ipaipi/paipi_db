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

/**
 * 对所有String类型字段做trim处理的Transformer。
 * 支持DataX风格的columnIndex参数。
 */
public class TrimTransformer implements Transformer {
    private Set<Integer> columnIndexSet = null;

    public TrimTransformer() {
    }

    public TrimTransformer(Map<String, Object> parameter) {
        if (parameter != null && parameter.get(TransformerParameterConstant.COLUMN_INDEX) != null) {
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
    }

    @Override
    public Record transform(Record record) {
        if (record == null) return null;
        for (int i = 0; i < record.getColumnNumber(); i++) {
            if (columnIndexSet != null && !columnIndexSet.contains(i)) continue;
            Column col = record.getColumn(i);
            if (col instanceof StringColumn) {
                String val = col.asString();
                if (val != null) {
                    record.setColumn(i, new StringColumn(val.trim()));
                }
            }
        }
        return record;
    }
} 