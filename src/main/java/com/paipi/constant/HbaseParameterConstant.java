package com.paipi.constant;

public class HbaseParameterConstant {
    // DataX HBase 相关配置常量
    public static final String HBASE_CONFIG = "hbaseConfig"; // HBase连接参数map
    public static final String TABLE = "table"; // HBase表名
    public static final String ROW_KEY = "rowkey"; // rowkey字段名
    public static final String COLUMN = "column"; // column数组
    public static final String SPLIT_MODE = "splitMode"; // 分片模式(region/range)
    public static final String RANGE_START_ROWKEY = "range.startRowkey"; // rowkey范围分片起始
    public static final String RANGE_END_ROWKEY = "range.endRowkey"; // rowkey范围分片结束
    public static final String SPLIT_START_ROW = "split.startRow"; // 分片起始rowkey
    public static final String SPLIT_END_ROW = "split.endRow";   // 分片结束rowkey
}