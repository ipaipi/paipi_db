package com.paipi.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SqlMode {
    /**
     * 拉数将表的数据落成文件（支持csv、parquet、json等多种格式，分块/单文件均可）
     */
    DB_TO_FILE(0),

    /**
     * 执行自定义sql
     */
    EXECUTE_SQL(1),

    /**
     * 异构数据库间高效同步 Reader/Channel/Writer
     */
    DB_TO_DB(2);

    private final int code;
}
