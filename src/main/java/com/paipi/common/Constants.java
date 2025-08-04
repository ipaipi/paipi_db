package com.paipi.common;

import java.time.format.DateTimeFormatter;


public class Constants {

    /**
     * 重试获取连接的最大次数
     */
    public static final int MAX_RETRY = 5;
    /**
     * 重试的间隔，单位：毫秒
     */
    public static final int RETRY_INTERVAL = 2500;
    /**
     * 列名与列索引映射文件的名称
     */
    public static final String INDEX_MAP_FILE_NAME = "indexMap.json";
    /**
     * 累计数据达到该数量就write一次
     */
    public static final int DEF_BATCH_SIZE = 100000;
    public static final String DEF_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DAY_TIME_PATTERN = "yyyy-MM-dd";
    public static final String SQL_TERM = ";";
    public static final DateTimeFormatter DATA_TIME_FORMATTER = DateTimeFormatter.ofPattern(DEF_TIME_PATTERN);

    private Constants() {
    }
}
