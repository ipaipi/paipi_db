package com.paipi.transformer;

import com.paipi.common.channel.Record;

/**
 * 数据转换器接口，支持链式调用。
 * 可用于字段清洗、类型转换、数据增强等。
 */
public interface Transformer {
    /**
     * 对单条Record进行转换处理。
     *
     * @param record 输入记录
     * @return 转换后的记录（可为null，表示过滤）
     */
    Record transform(Record record);
} 