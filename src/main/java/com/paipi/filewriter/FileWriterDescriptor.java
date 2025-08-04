package com.paipi.filewriter;

import com.paipi.common.column.ColumnStruct;

import java.io.IOException;
import java.util.List;

public interface FileWriterDescriptor {
    String getFileSuffix();

    String getChunkFileName(int chunkIndex);

    String getDataFileName(int fileIndex); // 新增：数据文件名（如0.csv、1.csv）

    FileWriter createWriter(String filePath, List<String> colNames, List<ColumnStruct> colStructs, String sep, String compress, String encoding) throws IOException;

    boolean needHeader();
}