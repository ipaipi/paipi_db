package com.paipi.filewriter;

import com.paipi.common.column.ColumnStruct;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileWriterFactory {


    private static final Map<String, FileWriterDescriptor> registry = new HashMap<>();

    static {
        // 注册内置类型
        register("csv", new FileWriterDescriptor() {
            @Override
            public String getFileSuffix() {
                return ".csv";
            }

            @Override
            public String getChunkFileName(int chunkIndex) {
                return chunkIndex + ".csv";
            }

            @Override
            public String getDataFileName(int fileIndex) {
                return fileIndex + ".csv";
            }

            @Override
            public FileWriter createWriter(String filePath, List<String> colNames, List<ColumnStruct> colStructs, String sep, String compress, String encoding) throws IOException {
                if (!filePath.endsWith(getFileSuffix())) {
                    filePath = filePath + getFileSuffix();
                }
                return new CSVFileWriter(filePath, sep);
            }

            @Override
            public boolean needHeader() {
                return true;
            }
        });
        register("parquet", new FileWriterDescriptor() {
            @Override
            public String getFileSuffix() {
                return ".parquet";
            }

            @Override
            public String getChunkFileName(int chunkIndex) {
                return chunkIndex + ".parquet";
            }

            @Override
            public String getDataFileName(int fileIndex) {
                return fileIndex + ".parquet";
            }

            @Override
            public FileWriter createWriter(String filePath, List<String> colNames, List<ColumnStruct> colStructs, String sep, String compress, String encoding) throws IOException {
                if (!filePath.endsWith(getFileSuffix())) {
                    filePath = filePath + getFileSuffix();
                }
                return new ParquetFileWriter(filePath, colNames, colStructs);
            }

            @Override
            public boolean needHeader() {
                return false;
            }
        });
        register("json", new FileWriterDescriptor() {
            @Override
            public String getFileSuffix() {
                return ".json";
            }

            @Override
            public String getChunkFileName(int chunkIndex) {
                return chunkIndex + ".json";
            }

            @Override
            public String getDataFileName(int fileIndex) {
                return fileIndex + ".json";
            }

            @Override
            public FileWriter createWriter(String filePath, List<String> colNames, List<ColumnStruct> colStructs, String sep, String compress, String encoding) throws IOException {
                if (!filePath.endsWith(getFileSuffix())) {
                    filePath = filePath + getFileSuffix();
                }
                return new JSONFileWriter(filePath, colNames);
            }

            @Override
            public boolean needHeader() {
                return false;
            }
        });
        register("orc", new FileWriterDescriptor() {
            @Override
            public String getFileSuffix() {
                return ".orc";
            }

            @Override
            public String getChunkFileName(int chunkIndex) {
                return chunkIndex + ".orc";
            }

            @Override
            public String getDataFileName(int fileIndex) {
                return fileIndex + ".orc";
            }

            @Override
            public FileWriter createWriter(String filePath, List<String> colNames, List<ColumnStruct> colStructs, String sep, String compress, String encoding) throws IOException {
                if (!filePath.endsWith(getFileSuffix())) {
                    filePath = filePath + getFileSuffix();
                }
                return new OrcFileWriter(filePath, colNames, colStructs, compress, encoding);
            }

            @Override
            public boolean needHeader() {
                return false;
            }
        });
    }

    public static void register(String type, FileWriterDescriptor descriptor) {
        registry.put(type.toLowerCase(), descriptor);
    }

    public static FileWriterDescriptor getDescriptor(String type) {
        return registry.get(type.toLowerCase());
    }

    // 新增统一工厂方法
    public static FileWriter createWriter(String type, String filePath, List<String> colNames, List<ColumnStruct> colStructs, String sep, String compress, String encoding) throws IOException {
        FileWriterDescriptor desc = getDescriptor(type);
        if (desc == null) throw new IllegalArgumentException("不支持的文件类型: " + type);
        return desc.createWriter(filePath, colNames, colStructs, sep, compress, encoding);
    }
} 