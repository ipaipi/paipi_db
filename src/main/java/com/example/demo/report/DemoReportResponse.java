package com.example.demo.report;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 响应对象，持有上下文
 */
@Data
@AllArgsConstructor
public class DemoReportResponse<T> {
    private T data;
} 