package com.paipi.transformer.impl;

import com.paipi.common.channel.Record;
import com.paipi.common.column.Column;
import com.paipi.common.column.DateColumn;
import com.paipi.common.column.StringColumn;
import com.paipi.constant.TransformerParameterConstant;
import com.paipi.transformer.Transformer;

import java.text.SimpleDateFormat;
import java.util.*;

public class DateFormatTransformer implements Transformer {
    private Set<Integer> columnIndexSet = null;
    private String fromFormat;
    private String toFormat;

    public DateFormatTransformer() {
    }

    public DateFormatTransformer(Map<String, Object> parameter) {
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
            fromFormat = parameter.get(TransformerParameterConstant.DATEFORMAT_FROM_FORMAT) == null ? null : parameter.get(TransformerParameterConstant.DATEFORMAT_FROM_FORMAT).toString();
            toFormat = parameter.get(TransformerParameterConstant.DATEFORMAT_TO_FORMAT) == null ? null : parameter.get(TransformerParameterConstant.DATEFORMAT_TO_FORMAT).toString();
        }
    }

    @Override
    public Record transform(Record record) {
        if (record == null || fromFormat == null || toFormat == null) return record;
        SimpleDateFormat fromSdf = new SimpleDateFormat(fromFormat);
        SimpleDateFormat toSdf = new SimpleDateFormat(toFormat);
        for (int i = 0; i < record.getColumnNumber(); i++) {
            if (columnIndexSet != null && !columnIndexSet.contains(i)) continue;
            Column col = record.getColumn(i);
            if (col instanceof StringColumn) {
                String val = ((StringColumn) col).asString();
                if (val != null) {
                    try {
                        Date date = fromSdf.parse(val);
                        record.setColumn(i, new StringColumn(toSdf.format(date)));
                    } catch (Exception e) {
                        System.err.println("[DateFormatTransformer] parse error: '" + val + "' with format '" + fromFormat + "', error: " + e.getMessage());
                    }
                }
            } else if (col instanceof DateColumn) {
                Date date = ((DateColumn) col).asDate();
                if (date != null) {
                    try {
                        record.setColumn(i, new StringColumn(toSdf.format(date)));
                    } catch (Exception e) {
                        System.err.println("[DateFormatTransformer] format error: '" + date + "' to format '" + toFormat + "', error: " + e.getMessage());
                    }
                }
            }
        }
        return record;
    }
} 