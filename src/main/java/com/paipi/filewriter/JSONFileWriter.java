package com.paipi.filewriter;

import com.alibaba.fastjson.JSON;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;

public class JSONFileWriter implements com.paipi.filewriter.FileWriter {
    private final BufferedWriter writer;
    private final List<String> colNames;
    private boolean firstRow = true;

    public JSONFileWriter(String filePath, List<String> colNames) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(filePath));
        this.colNames = colNames;
        writer.write("[");
    }

    @Override
    public void writeHeader(List<String> colNames) throws IOException {
        // JSON文件可不需要表头
    }

    @Override
    public void writeRow(List<Object> row) throws IOException {
        if (!firstRow) {
            writer.write(",");
        } else {
            firstRow = false;
        }
        LinkedHashMap<String, Object> obj = new LinkedHashMap<>();
        for (int i = 0; i < colNames.size(); i++) {
            obj.put(colNames.get(i), i < row.size() ? row.get(i) : null);
        }
        writer.write(JSON.toJSONString(obj));
    }

    @Override
    public void writeRows(List<List<Object>> rows) throws IOException {
        for (List<Object> row : rows) {
            writeRow(row);
        }
    }

    @Override
    public void close() throws IOException {
        writer.write("]");
        writer.close();
    }
} 