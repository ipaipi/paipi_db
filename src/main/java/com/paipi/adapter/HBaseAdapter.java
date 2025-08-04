package com.paipi.adapter;

import com.paipi.adapter.base.BaseAdapter;
import com.paipi.common.channel.Channel;
import com.paipi.common.channel.Record;
import com.paipi.common.column.*;
import com.paipi.config.Configuration;
import com.paipi.constant.HbaseParameterConstant;
import com.paipi.constant.ParameterConstant;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HBaseAdapter extends BaseAdapter {
    private Connection hbaseConn;
    private String rowKey;
    private List<Map<String, Object>> columns;
    private Map<String, Object> hbaseConfig;

    public HBaseAdapter(Configuration originalConfig, Configuration config) throws IOException {
        super(originalConfig, config);
        this.table = config.getString(HbaseParameterConstant.TABLE);
        this.rowKey = config.getString(HbaseParameterConstant.ROW_KEY, "id");
        this.columns = new ArrayList<>();
        List<Object> cols = config.getList(HbaseParameterConstant.COLUMN);
        if (cols != null) {
            for (Object obj : cols) {
                if (obj instanceof Map) {
                    this.columns.add((Map<String, Object>) obj);
                }
            }
        }
        this.hbaseConfig = config.getMap(HbaseParameterConstant.HBASE_CONFIG);
        this.hbaseConn = getHBaseConn();
    }

    private Connection getHBaseConn() throws IOException {
        if (hbaseConn != null && !hbaseConn.isClosed()) return hbaseConn;
        org.apache.hadoop.conf.Configuration hConf = org.apache.hadoop.hbase.HBaseConfiguration.create();
        for (Map.Entry<String, Object> entry : hbaseConfig.entrySet()) {
            hConf.set(entry.getKey(), String.valueOf(entry.getValue()));
        }
        hbaseConn = ConnectionFactory.createConnection(hConf);
        return hbaseConn;
    }

    @Override
    public String getDriverName() {
        return "org.apache.hadoop.hbase.client.Connection";
    }

    @Override
    public String getJdbcUrl() {
        return String.format("hbase://%s", table);
    }

    @Override
    public String dbTypeToInternalType(String colType) {
        if (colType == null) return StringColumn.TYPE_NAME;
        String type = colType.toLowerCase();
        if (type.contains("int")) return IntColumn.TYPE_NAME;
        if (type.contains("long")) return LongColumn.TYPE_NAME;
        if (type.contains("double") || type.contains("float")) return DoubleColumn.TYPE_NAME;
        if (type.contains("bool")) return BoolColumn.TYPE_NAME;
        if (type.contains("date") || type.contains("time")) return DateColumn.TYPE_NAME;
        if (type.contains("bytes") || type.contains("binary")) return BytesColumn.TYPE_NAME;
        return StringColumn.TYPE_NAME;
    }

    @Override
    public List<Object> internalTypeToDbType(ColumnStruct column) {
        return Collections.singletonList("bytes");
    }

    @Override
    public String getCreateTableSql(String tableName, String[] colNames, String[] colTypes) {
        return "-- HBase不支持SQL建表，请用HBase shell或Admin API建表";
    }

    @Override
    public java.sql.Connection getConn() throws java.sql.SQLException {
        throw new UnsupportedOperationException("HBaseAdapter 不支持 JDBC 连接，请勿调用 BaseAdapter.getConn()");
    }

    @Override
    public boolean tableIsExist(String tableName) {
        try {
            try (Admin admin = getHBaseConn().getAdmin()) {
                return admin.tableExists(TableName.valueOf(tableName));
            }
        } catch (IOException e) {
            return false;
        }
    }

    // 支持数字rowkey的均匀切分，非数字rowkey简单切分
    private List<String> splitRowkeyRange(String start, String end, int numSplits) {
        List<String> splits = new ArrayList<>();
        splits.add(start);
        try {
            long s = Long.parseLong(start);
            long e = Long.parseLong(end);
            for (int i = 1; i < numSplits; i++) {
                long split = s + (e - s) * i / numSplits;
                splits.add(String.valueOf(split));
            }
        } catch (Exception ex) {
            // 非数字rowkey，简单用start+"_split"+i
            for (int i = 1; i < numSplits; i++) {
                splits.add(start + "_split" + i);
            }
        }
        splits.add(end);
        return splits;
    }

    private Column dbTypeToInternalType(byte[] value, String type) {
        if (value == null || value.length == 0) return new StringColumn("");
        try {
            String strVal = Bytes.toString(value);
            switch (type.toLowerCase()) {
                case "int":
                    if (value.length == 4) return new IntColumn(Bytes.toInt(value));
                    // 兼容字符串数字
                    if (strVal.matches("-?\\d+")) return new IntColumn(strVal);
                    return new IntColumn((Integer) null);
                case "long":
                    if (value.length == 8) return new LongColumn(Bytes.toLong(value));
                    if (strVal.matches("-?\\d+")) return new LongColumn(strVal);
                    return new LongColumn((Long) null);
                case "double":
                    if (value.length == 8) return new DoubleColumn(Bytes.toDouble(value));
                    if (strVal.matches("-?\\d+(\\.\\d+)?")) return new DoubleColumn(strVal);
                    return new DoubleColumn((Double) null);
                case "bool":
                    if (value.length == 1) return new BoolColumn(Bytes.toBoolean(value));
                    if ("true".equalsIgnoreCase(strVal) || "false".equalsIgnoreCase(strVal))
                        return new BoolColumn(strVal);
                    return new BoolColumn((Boolean) null);
                case "string":
                    return new StringColumn(strVal);
                case "bytes":
                    return new BytesColumn(value);
                default:
                    return new StringColumn(strVal);
            }
        } catch (Exception e) {
            return new StringColumn(Bytes.toString(value));
        }
    }

    private int getColumnIndex(String colName) {
        for (int i = 0; i < columns.size(); i++) {
            String name = (String) columns.get(i).get("name");
            if (name.equals(colName) || name.endsWith(":" + colName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("rowkey字段未在column配置中找到: " + colName);
    }

    private byte[] internalTypeToDbType(Column col, String type) {
        if (col == null) return null;
        switch (type.toLowerCase()) {
            case "int":
                return Bytes.toBytes(col.asLong());
            case "long":
                return Bytes.toBytes(col.asLong());
            case "double":
                return Bytes.toBytes(col.asDouble());
            case "bool":
                return Bytes.toBytes(col.asBoolean());
            case "string":
                return Bytes.toBytes(col.asString());
            case "bytes":
                return col.asBytes();
            default:
                return Bytes.toBytes(col.asString());
        }
    }


    @Override
    public void readerPlugin(Channel<Record> channel) throws Exception {
        try (Table table = getHBaseConn().getTable(TableName.valueOf(this.table))) {
            Scan scan = new Scan();
            String startRow = config.getString(HbaseParameterConstant.SPLIT_START_ROW, "");
            String endRow = config.getString(HbaseParameterConstant.SPLIT_END_ROW, "");
            if (StringUtils.isNotBlank(startRow)) {
                // 判断是否为16进制（region分片），否则按普通字符串处理
                if (startRow.matches("^[0-9a-fA-F]+$") && startRow.length() % 2 == 0) {
                    scan.withStartRow(Bytes.fromHex(startRow));
                } else {
                    scan.withStartRow(Bytes.toBytes(startRow));
                }
            }
            if (StringUtils.isNotBlank(endRow)) {
                if (endRow.matches("^[0-9a-fA-F]+$") && endRow.length() % 2 == 0) {
                    scan.withStopRow(Bytes.fromHex(endRow));
                } else {
                    scan.withStopRow(Bytes.toBytes(endRow));
                }
            }
            ResultScanner scanner = table.getScanner(scan);
            for (Result result : scanner) {
                Record record = new SimpleRecord();
                String rowkey = Bytes.toString(result.getRow());
                int rowkeyIdx = getColumnIndex(rowKey);
                for (int i = 0; i < columns.size(); i++) {
                    Map<String, Object> colConf = columns.get(i);
                    String colName = (String) colConf.get("name");
                    String[] parts = colName.split(":", 2);
                    String family = parts[0];
                    String qualifier = parts[1];
                    String type = (String) colConf.get("type");
                    if (i == rowkeyIdx) {
                        record.addColumn(new StringColumn(rowkey));
                    } else {
                        byte[] value = result.getValue(Bytes.toBytes(family), Bytes.toBytes(qualifier));
                        record.addColumn(dbTypeToInternalType(value, type));
                    }
                }
                channel.push(record);
            }
        } finally {
            channel.close();
        }
    }

    @Override
    public void writerPlugin(Channel<Record> channel) throws Exception {
        try {
            String writeMode = config.getString(ParameterConstant.WRITE_MODE, "insert").toLowerCase();
            if (!"insert".equals(writeMode)) {
                throw new UnsupportedOperationException("HBase只支持insert写入模式，当前writeMode=" + writeMode);
            }
            try (Table table = getHBaseConn().getTable(TableName.valueOf(this.table))) {
                List<Put> batch = new ArrayList<>();
                int batchSize = insertBatchSize;
                long totalWrite = 0;
                while (true) {
                    List<Record> records = channel.pullAll(batchSize);
                    if (records == null || records.isEmpty()) break;
                    for (Record record : records) {
                        // 1. 提取rowkey
                        int rowkeyIdx = getColumnIndex(rowKey);
                        String rowkey = record.getColumn(rowkeyIdx).asString();
                        Put put = new Put(Bytes.toBytes(rowkey));
                        // 2. 依次写入column
                        for (int i = 0; i < columns.size(); i++) {
                            Map<String, Object> colConf = columns.get(i);
                            String colName = (String) colConf.get("name"); // cf1:name
                            String[] parts = colName.split(":", 2);
                            String family = parts[0];
                            String qualifier = parts[1];
                            String type = (String) colConf.get("type");
                            if (colName.equals(rowKey) || colName.endsWith(":" + rowKey)) continue; // 跳过rowkey字段
                            Column col = record.getColumn(i);
                            byte[] value = internalTypeToDbType(col, type);
                            put.addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier), value);
                        }
                        batch.add(put);
                        totalWrite++;
                        if (totalWrite % recordingTotalWrite == 0) {
                            logger.info("[Thread-{}] 已写入{}条数据", Thread.currentThread().getId(), totalWrite);
                        }
                    }
                    table.put(batch);
                    batch.clear();
                }
            }
        } catch (Exception e) {
            logger.error("writerPlugin error", e);
            throw e;
        } finally {
            try {
                channel.close();
                logger.info("Channel closed in writerPlugin for HBaseAdapter");
            } catch (Exception ex) {
                logger.warn("Exception when closing channel in writerPlugin: {}", ex.getMessage(), ex);
            }
        }
    }

    @Override
    public List<com.paipi.config.Configuration> split(int adviceNumber) throws Exception {
        String splitMode = config.getString(HbaseParameterConstant.SPLIT_MODE, "region");
        List<com.paipi.config.Configuration> splits = new ArrayList<>();
        if ("range".equalsIgnoreCase(splitMode)) {
            // DataX风格rowkey范围分片
            String startRowkey = config.getString(HbaseParameterConstant.RANGE_START_ROWKEY, "");
            String endRowkey = config.getString(HbaseParameterConstant.RANGE_END_ROWKEY, "");
            if (StringUtils.isBlank(startRowkey) || StringUtils.isBlank(endRowkey)) {
                throw new IllegalArgumentException("range分片模式下，必须指定range.startRowkey和range.endRowkey");
            }
            List<String> rowkeySplits = splitRowkeyRange(startRowkey, endRowkey, adviceNumber);
            for (int i = 0; i < rowkeySplits.size() - 1; i++) {
                com.paipi.config.Configuration conf = config.clone();
                conf.set(HbaseParameterConstant.SPLIT_START_ROW, rowkeySplits.get(i));
                conf.set(HbaseParameterConstant.SPLIT_END_ROW, rowkeySplits.get(i + 1));
                splits.add(conf);
            }
            return splits;
        } else {
            // 默认region分片
            try (Table table = getHBaseConn().getTable(TableName.valueOf(this.table))) {
                RegionLocator locator = getHBaseConn().getRegionLocator(TableName.valueOf(this.table));
                List<HRegionLocation> regions = locator.getAllRegionLocations();
                int total = regions.size();
                int step = Math.max(1, total / adviceNumber);
                for (int i = 0; i < total; i += step) {
                    com.paipi.config.Configuration conf = config.clone();
                    HRegionLocation region = regions.get(i);
                    byte[] startKey = region.getRegion().getStartKey();
                    byte[] endKey = region.getRegion().getEndKey();
                    conf.set(HbaseParameterConstant.SPLIT_START_ROW, Bytes.toHex(startKey));
                    conf.set(HbaseParameterConstant.SPLIT_END_ROW, Bytes.toHex(endKey));
                    splits.add(conf);
                }
            }
            return splits;
        }
    }

} 