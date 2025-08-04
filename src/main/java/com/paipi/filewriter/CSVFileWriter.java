package com.paipi.filewriter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CSVFileWriter implements com.paipi.filewriter.FileWriter {
    private final BufferedWriter writer;
    private final String sep;

    public CSVFileWriter(String filePath, String sep) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(filePath));
        this.sep = sep == null ? "," : sep;
    }

    @Override
    public void writeHeader(List<String> colNames) throws IOException {
        writer.write(String.join(sep, colNames));
        writer.newLine();
    }

    @Override
    public void writeRow(List<Object> row) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) sb.append(sep);
            Object val = row.get(i);
            sb.append(val == null ? "" : val.toString().replaceAll("[\n" + ",]", " "));
        }
        writer.write(sb.toString());
        writer.newLine();
    }

    @Override
    public void writeRows(List<List<Object>> rows) throws IOException {
        for (List<Object> row : rows) {
            writeRow(row);
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
} 