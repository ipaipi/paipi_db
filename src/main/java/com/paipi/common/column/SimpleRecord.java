package com.paipi.common.column;

import com.paipi.common.channel.Record;

import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleRecord implements Record {
    private final LinkedHashMap<String, Column> columns = new LinkedHashMap<>();
    private Map<String, String> meta;

    @Override
    public void addColumn(Column column) {
        columns.put(String.valueOf(columns.size()), column);
    }

    public void addColumn(String name, Column column) {
        columns.put(name, column);
    }

    @Override
    public void setColumn(int i, Column column) {
        String key = (String) columns.keySet().toArray()[i];
        columns.put(key, column);
    }

    @Override
    public Column getColumn(int i) {
        String key = (String) columns.keySet().toArray()[i];
        return columns.get(key);
    }

    @Override
    public Column getColumnByName(String name) {
        return columns.get(name);
    }

    @Override
    public String toString() {
        return columns.values().toString();
    }

    @Override
    public int getColumnNumber() {
        return columns.size();
    }

    @Override
    public int getByteSize() {
        return columns.values().stream().mapToInt(Column::getByteSize).sum();
    }

    @Override
    public int getMemorySize() {
        return getByteSize();
    }

    @Override
    public Map<String, String> getMeta() {
        return meta;
    }

    @Override
    public void setMeta(Map<String, String> meta) {
        this.meta = meta;
    }
} 