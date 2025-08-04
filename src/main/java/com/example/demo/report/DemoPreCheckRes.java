package com.example.demo.report;

import lombok.Data;

/**
 * 预检查结果对象，包含"去向表是否存在"字段
 */
@Data
public class DemoPreCheckRes {
    // 去向表是否存在
    private Boolean toTableIsExist;
} 