package com.example.demo.report;

import lombok.Data;

/**
 * 上下文对象，持有 DemoPreCheckRes
 */
@Data
public class DemoReportContext {
    private DemoPreCheckRes preCheckRes = new DemoPreCheckRes();

    /**
     * 获取预检查结果对象
     */
    public <T> T getPreCheckRes(Class<T> clazz) {
        // 这里只做简单返回
        return clazz.cast(preCheckRes);
    }
} 