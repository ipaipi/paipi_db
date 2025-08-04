package com.paipi.adapter;

import com.paipi.adapter.base.BaseAdapter;
import com.paipi.common.channel.Channel;
import com.paipi.common.channel.Record;
import com.paipi.common.column.*;
import com.paipi.config.Configuration;
import com.paipi.constant.ParameterConstant;
import com.paipi.constant.TxtFileParameterConstant;
import com.paipi.filewriter.FileWriterDescriptor;
import com.paipi.filewriter.FileWriterFactory;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.paipi.utils.StringUtils.parseSeparator;

public class TxtFileAdapter extends BaseAdapter {
    protected List<String> filePath;

    public TxtFileAdapter(Configuration originalConfig, Configuration config) {
        super(originalConfig, config);

        try {
            filePath = config.getList(TxtFileParameterConstant.FILE_PATH, String.class);
        } catch (Exception e) {
            String singleFile = config.getString(TxtFileParameterConstant.FILE_PATH, null);
            if (singleFile != null) filePath = new ArrayList<>();
            if (singleFile != null) filePath.add(singleFile);
        }
    }

    @Override
    public String getDriverName() {
        return null; // 文件无驱动
    }

    @Override
    public String getJdbcUrl() {
        return null; // 文件无驱动
    }

    @Override
    public String getCreateTableSql(String tableName, String[] colNames, String[] colTypes) {
        return null;
    }

    @Override
    public String dbTypeToInternalType(String colType) {
        // 数据库的类型转成内部类型
        return null;
    }

    /**
     * 判断指定文件是否存在（txtfile场景下，表即文件）
     *
     * @param tableName 文件路径
     * @return 存在返回true，不存在返回false
     */
    public boolean tableIsExist(String tableName) {
        return true;
        //if (StringUtils.isBlank(tableName)) return false;
        //File file = new File(tableName);
        //return file.exists() && file.isFile();
    }

    @Override
    public List<Object> internalTypeToDbType(ColumnStruct origin_type) {
        return null;
    }

    /**
     * 读取一行，支持UTF-8，兼容多字节字符。
     */
    private String readLineByCharset(RandomAccessFile raf, Charset charset) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = raf.read()) != -1) {
            if (b == '\n') break;
            if (b == '\r') {
                break;
            }
            baos.write(b);
        }
        if (b == -1 && baos.size() == 0) return null;
        // 这里不需要mark/seek，直接跳过即可
        return baos.toString(charset.name());
    }

    /**
     * 支持高效字节切分+行首对齐分片读取。
     * 单大文件时用RandomAccessFile按offset读取，skipHeader仅第一个分片生效。
     * 多文件时仍遍历所有文件。
     */
    @Override
    public void readerPlugin(Channel<Record> channel) {
        try {
            List<String> files = new ArrayList<>();
            try {
                files = config.getList(TxtFileParameterConstant.FILE_PATH, String.class);
            } catch (Exception e) {
                String singleFile = config.getString(TxtFileParameterConstant.FILE_PATH, null);
                if (singleFile != null) files = new ArrayList<>();
                if (singleFile != null) files.add(singleFile);
            }

            if (files == null || files.isEmpty()) {
                throw new IllegalArgumentException("filePath不能为空");
            }
            String sep = config.getString(TxtFileParameterConstant.SEP, ",");
            boolean skipHeader = config.getBool(TxtFileParameterConstant.SKIP_HEADER, false);
            Long startOffset = null, endOffset = null;
            Object startObj = config.get(TxtFileParameterConstant.START_OFFSET);
            Object endObj = config.get(TxtFileParameterConstant.END_OFFSET);
            Object splitIdxObj = config.get(TxtFileParameterConstant.SPLIT_INDEX);
            if (startObj instanceof Number) startOffset = ((Number) startObj).longValue();
            if (endObj instanceof Number) endOffset = ((Number) endObj).longValue();
            if (files.size() == 1 && startOffset != null && endOffset != null) {
                // 单大文件分片，按offset读取
                String file = files.get(0);
                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    raf.seek(startOffset);
                    // 如果不是第一个分片，跳到下一个换行符
                    if (startOffset != 0) {
                        readLineByCharset(raf, StandardCharsets.UTF_8); // 跳过残缺行
                    } else if (skipHeader) {
                        readLineByCharset(raf, StandardCharsets.UTF_8); // 跳过表头
                    }
                    long curPos = raf.getFilePointer();
                    while (curPos < endOffset) {
                        String line = readLineByCharset(raf, StandardCharsets.UTF_8);
                        if (line == null) break;
                        curPos = raf.getFilePointer();
                        if (line.isEmpty()) continue;
                        String[] fields = line.split(sep, -1);
                        Record record = new SimpleRecord();
                        for (String field : fields) {
                            record.addColumn(new StringColumn(field));
                        }
                        channel.push(record);
                    }
                }
            } else {
                // 多文件分片，遍历所有文件
                for (String file : files) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(file)), StandardCharsets.UTF_8))) {
                        String line;
                        boolean firstLine = true;
                        while ((line = reader.readLine()) != null) {
                            if (firstLine && skipHeader) {
                                firstLine = false;
                                continue;
                            }
                            firstLine = false;
                            if (StringUtils.isBlank(line)) continue;
                            String[] fields = line.split(sep, -1);
                            Record record = new SimpleRecord();
                            for (String field : fields) {
                                record.addColumn(new StringColumn(field));
                            }
                            channel.push(record);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("readerPlugin error", e);
            throw new RuntimeException(e);
        } finally {
            try {
                channel.close();
                logger.info("Channel closed in readerPlugin for TxtFileAdapter");
            } catch (Exception ex) {
                logger.warn("Exception when closing channel in readerPlugin: {}", ex.getMessage(), ex);
            }
        }
    }


    @Override
    public void writerPlugin(Channel<Record> channel) throws Exception {
        try {
            // 1. 解析参数
            String filePath = config.getString(TxtFileParameterConstant.FILE_PATH);
            String fileType = config.getString(ParameterConstant.FILE_TYPE, "csv").toLowerCase();
            String sepRaw = config.getString(TxtFileParameterConstant.SEP, ",");
            String sep = parseSeparator(sepRaw);
            boolean skipHeader = config.getBool(TxtFileParameterConstant.SKIP_HEADER, true);
            int batchSize = config.getInt(TxtFileParameterConstant.BATCH_SIZE, 1000);
            int chunkSize = config.getInt(TxtFileParameterConstant.CHUNK_SIZE, -1);

            // 字段名和类型
            List<?> columnConfig = config.getList(ParameterConstant.COLUMN);
            List<String> colNames = ColumnParser.parseColumns(columnConfig).getColumnNames();
            List<String> colTypes = ColumnParser.parseColumns(columnConfig).getColumnTypes();
            if (!skipHeader && (colNames == null || colNames.isEmpty())) {
                throw new IllegalArgumentException("column参数不能为空");
            }
            List<ColumnStruct> colStructs = new ArrayList<>();
            for (int i = 0; i < colNames.size(); i++) {
                String typeStr = (colTypes != null && colTypes.size() > i) ? colTypes.get(i) : "STRING";
                colStructs.add(new ColumnStruct(colNames.get(i), typeStr, i));
            }

            // 2. 生成分片文件名基础
            Path basePath = Paths.get(filePath);
            String parentDir = basePath.getParent() != null ? basePath.getParent().toString() : ".";
            String fileName = basePath.getFileName().toString();
            int dotIdx = fileName.lastIndexOf('.');
            String namePart = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
            String extPart = dotIdx > 0 ? fileName.substring(dotIdx) : "";

            // 3. 创建父目录
            Files.createDirectories(Paths.get(parentDir));

            // 4. 用FileWriterFactory静态方法统一写出，支持chunkSize分片+批量缓冲写
            List<String> writerColNames = colNames;
            List<ColumnStruct> writerColStructs = colStructs;
            if ("csv".equals(fileType)) {
                writerColNames = null;
                writerColStructs = null;
            }
            int fileIndex = 0;
            long totalWrite = 0;
            com.paipi.filewriter.FileWriter writer = null;
            FileWriterDescriptor descriptor = FileWriterFactory.getDescriptor(fileType);
            String outFile = parentDir + "/" + namePart + "_split_" + fileIndex + "_" + System.currentTimeMillis() + "." + fileType.toLowerCase();
            List<List<Object>> batchRows = new ArrayList<>();
            int fileRowCount = 0;
            try {
                writer = FileWriterFactory.createWriter(fileType, outFile, writerColNames, writerColStructs, sep, null, null);
                if (!skipHeader && descriptor != null && descriptor.needHeader() && !colNames.isEmpty()) {
                    writer.writeHeader(colNames);
                }
                while (true) {
                    List<Record> records = channel.pullAll(batchSize);
                    if (records == null || records.isEmpty()) break;
                    for (Record record : records) {
                        List<Object> fields = new ArrayList<>();
                        for (int i = 0; i < record.getColumnNumber(); i++) {
                            Column col = record.getColumn(i);
                            fields.add(col == null ? "" : col.asString());
                        }
                        batchRows.add(fields);
                        totalWrite++;
                        fileRowCount++;
                        if (totalWrite % recordingTotalWrite == 0) {
                            logger.info("[Thread-{}] 已写入{}条数据", Thread.currentThread().getId(), totalWrite);
                        }
                        // chunkSize分片逻辑
                        if (chunkSize > 0 && fileRowCount >= chunkSize) {
                            if (!batchRows.isEmpty()) {
                                writer.writeRows(batchRows);
                                batchRows.clear();
                            }
                            writer.close();
                            fileIndex++;
                            fileRowCount = 0;
                            outFile = parentDir + "/" + namePart + "_split_" + fileIndex + "_" + System.currentTimeMillis() + "." + fileType.toLowerCase();
                            writer = FileWriterFactory.createWriter(fileType, outFile, writerColNames, writerColStructs, sep, null, null);
                            if (!skipHeader && descriptor != null && descriptor.needHeader() && !colNames.isEmpty()) {
                                writer.writeHeader(colNames);
                            }
                        }
                    }
                    // 每批写入一次，防止内存过大
                    if (!batchRows.isEmpty()) {
                        writer.writeRows(batchRows);
                        batchRows.clear();
                    }
                }
            } finally {
                if (writer != null) try {
                    writer.close();
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            logger.error("writerPlugin error", e);
            throw e;
        } finally {
            try {
                channel.close();
                logger.info("Channel closed in writerPlugin for TxtFileAdapter");
            } catch (Exception ex) {
                logger.warn("Exception when closing channel in writerPlugin: {}", ex.getMessage(), ex);
            }
        }
    }

    /**
     * 高效字节切分+行首对齐分片方案。
     * 单大文件时，按字节offset均分，分片参数为startOffset/endOffset。
     * 多文件时，仍按文件均分。
     */
    @Override
    public List<Configuration> split(int adviceNumber) throws Exception {
        List<String> allFiles = Collections.emptyList();
        try {
            allFiles = config.getList(TxtFileParameterConstant.FILE_PATH, String.class);
        } catch (Exception e) {
            String singleFile = config.getString(TxtFileParameterConstant.FILE_PATH, null);
            if (singleFile != null) allFiles = new ArrayList<>();
            if (singleFile != null) allFiles.add(singleFile);
        }
        if (allFiles == null || allFiles.isEmpty()) {
            throw new IllegalArgumentException("filePath不能为空");
        }
        List<Configuration> splits = new ArrayList<>();
        if (allFiles.size() == 1 && adviceNumber > 1) {
            // 单大文件，按字节offset均分
            String file = allFiles.get(0);
            File f = new File(file);
            long fileLength = f.length();
            long avgSize = fileLength / adviceNumber;
            for (int i = 0; i < adviceNumber; i++) {
                long startOffset = i * avgSize;
                long endOffset = (i == adviceNumber - 1) ? fileLength : (i + 1) * avgSize;
                Configuration conf = config.clone();
                conf.set(TxtFileParameterConstant.FILE_PATH, file);
                conf.set(TxtFileParameterConstant.START_OFFSET, startOffset);
                conf.set(TxtFileParameterConstant.END_OFFSET, endOffset);
                conf.set(TxtFileParameterConstant.SPLIT_INDEX, i);
                splits.add(conf);
            }
        } else {
            // 多文件，按文件均分
            List<List<String>> fileGroups = new ArrayList<>();
            int total = allFiles.size();
            int groupSize = (int) Math.ceil((double) total / adviceNumber);
            for (int i = 0; i < adviceNumber; i++) {
                int from = i * groupSize;
                int to = Math.min(from + groupSize, total);
                if (from >= to) break;
                fileGroups.add(new ArrayList<>(allFiles.subList(from, to)));
            }
            for (List<String> group : fileGroups) {
                Configuration conf = config.clone();
                conf.set(TxtFileParameterConstant.FILE_PATH, group);
                splits.add(conf);
            }
        }
        return splits;
    }
} 