package com.paipi.transformer.impl;

import com.paipi.common.channel.Record;
import com.paipi.common.column.Column;
import com.paipi.common.column.IntColumn;
import com.paipi.common.column.LongColumn;
import com.paipi.common.column.StringColumn;
import com.paipi.constant.TransformerParameterConstant;
import com.paipi.transformer.Transformer;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Md5EncryptTransformer implements Transformer {
    private Set<Integer> columnIndexSet = null;

    public Md5EncryptTransformer() {
    }

    public Md5EncryptTransformer(Map<String, Object> parameter) {
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
        if (record == null) return record;
        for (int i = 0; i < record.getColumnNumber(); i++) {
            if (columnIndexSet != null && !columnIndexSet.contains(i)) continue;
            Column col = record.getColumn(i);
            if (col instanceof StringColumn || col instanceof IntColumn || col instanceof LongColumn) {
                String val = null;
                if (col instanceof StringColumn) {
                    val = ((StringColumn) col).asString();
                }
                if (val != null) {
                    record.setColumn(i, new StringColumn(md5(val)));
                }
            }
        }
        return record;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
} 