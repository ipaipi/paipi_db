package com.paipi.common.column;

import com.paipi.exception.DbAdapterException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;

public class IntColumn extends Column {
    public static final String TYPE_NAME = Type.INT.name();

    public IntColumn(Integer data) {
        super(data, Column.Type.INT, data == null ? 0 : 4);
    }

    public IntColumn(String data) {
        super(null, Column.Type.INT, 0);
        if (data == null) {
            return;
        }
        try {
            Integer value = Integer.valueOf(data);
            super.setRawData(value);
            super.setByteSize(4);
        } catch (Exception e) {
            throw new DbAdapterException(String.format("String[%s]不能转为Int .", data));
        }
    }

    public IntColumn() {
        this((Integer) null);
    }

    @Override
    public Long asLong() {
        Integer value = (Integer) this.getRawData();
        return value == null ? null : value.longValue();
    }

    @Override
    public Double asDouble() {
        Integer value = (Integer) this.getRawData();
        return value == null ? null : value.doubleValue();
    }

    @Override
    public String asString() {
        Integer value = (Integer) this.getRawData();
        return value == null ? null : value.toString();
    }

    @Override
    public Date asDate() {
        throw new DbAdapterException("Int类型不能转为Date .");
    }

    @Override
    public Date asDate(String dateFormat) {
        throw new DbAdapterException("Int类型不能转为Date .");
    }

    @Override
    public byte[] asBytes() {
        throw new DbAdapterException("Int类型不能转为Bytes .");
    }

    @Override
    public Boolean asBoolean() {
        Integer value = (Integer) this.getRawData();
        return value == null ? null : value != 0;
    }

    @Override
    public BigDecimal asBigDecimal() {
        Integer value = (Integer) this.getRawData();
        return value == null ? null : new BigDecimal(value);
    }

    @Override
    public BigInteger asBigInteger() {
        Integer value = (Integer) this.getRawData();
        return value == null ? null : BigInteger.valueOf(value);
    }
} 