package com.paipi.filewriter;

import com.paipi.common.column.ColumnStruct;
import com.paipi.filewriter.parquet.AllColumnParquetWriter;
import org.apache.avro.generic.GenericRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ParquetFileWriter implements FileWriter {
    private final AllColumnParquetWriter writer;
    private final List<String> colNames;

    public ParquetFileWriter(String filePath, List<String> colNames, List<ColumnStruct> colStructs) throws IOException {
        this.colNames = colNames;
        this.writer = new AllColumnParquetWriter(colStructs, filePath);
    }

    @Override
    public void writeHeader(List<String> colNames) throws IOException {
        // Parquet不需要表头
    }

    @Override
    public void writeRow(List<Object> row) throws IOException {
        GenericRecord record = writer.createGenericRecord();
        for (int i = 0; i < colNames.size(); i++) {
            record.put(colNames.get(i), i < row.size() ? row.get(i) : null);
        }
        writer.writeRecord(record);
    }

    @Override
    public void writeRows(List<List<Object>> rows) throws IOException {
        List<GenericRecord> records = new ArrayList<>();
        for (List<Object> row : rows) {
            GenericRecord record = writer.createGenericRecord();
            for (int i = 0; i < colNames.size(); i++) {
                record.put(colNames.get(i), i < row.size() ? row.get(i) : null);
            }
            records.add(record);
        }
        writer.writeRecords(records);
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}