package com.paipi.transformer.impl;

import com.paipi.common.channel.Record;
import com.paipi.common.column.Column;
import com.paipi.common.column.IntColumn;
import com.paipi.common.column.LongColumn;
import com.paipi.common.column.StringColumn;
import com.paipi.constant.TransformerParameterConstant;
import com.paipi.transformer.Transformer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将0/1或"0"/"1"转为"false"/"true"字符串的transformer
 */
public class ZeroOneToBooleanTransformer implements Transformer {
    private Set<Integer> columnIndexSet = null;

    public ZeroOneToBooleanTransformer() {
    }

    public ZeroOneToBooleanTransformer(Map<String, Object> parameter) {
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
        }
    }

    @Override
    public Record transform(Record record) {
        if (record == null) return record;
        for (int i = 0; i < record.getColumnNumber(); i++) {
            if (columnIndexSet != null && !columnIndexSet.contains(i)) continue;
            Column col = record.getColumn(i);
            if (col instanceof StringColumn) {
                String val = ((StringColumn) col).asString();
                if ("0".equals(val)) {
                    record.setColumn(i, new StringColumn("false"));
                } else if ("1".equals(val)) {
                    record.setColumn(i, new StringColumn("true"));
                }
            } else if (col instanceof IntColumn || col instanceof LongColumn) {
                Long val = col.asLong();
                if (val != null) {
                    if (val == 0) {
                        record.setColumn(i, new StringColumn("false"));
                    } else if (val == 1) {
                        record.setColumn(i, new StringColumn("true"));
                    }
                }
            }
        }
        return record;
    }
} 