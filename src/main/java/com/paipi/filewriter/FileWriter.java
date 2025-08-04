package com.paipi.filewriter;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public interface FileWriter extends Closeable {
    void writeHeader(List<String> colNames) throws IOException;

    void writeRow(List<Object> row) throws IOException;

    void writeRows(List<List<Object>> rows) throws IOException;
} 