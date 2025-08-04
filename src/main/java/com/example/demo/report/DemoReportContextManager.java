package com.example.demo.report;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 管理路由表和上下文响应对象
 */
public class DemoReportContextManager {
    // 路由表，模拟 CALLBACK_ROUTE
    private static final Map<String, Consumer<Object>> CALLBACK_ROUTE = new HashMap<>();
    // 存储 ReportResponse
    private static DemoReportResponse<DemoReportContext> reportResponse;

    /**
     * 注册路由
     */
    public static void addRoute(String routeName, Consumer<Object> consumer) {
        CALLBACK_ROUTE.put(routeName, consumer);
    }

    /**
     * 发布事件
     */
    public static void publish(String routeName, Object value) {
        Consumer<Object> consumer = CALLBACK_ROUTE.get(routeName);
        if (consumer != null) {
            consumer.accept(value);
        }
    }

    /**
     * 获取 ReportResponse
     */
    public static Optional<DemoReportResponse<DemoReportContext>> getReportResponse() {
        return Optional.ofNullable(reportResponse);
    }

    /**
     * 设置 ReportResponse
     */
    public static void setReportResponse(DemoReportResponse<DemoReportContext> response) {
        reportResponse = response;
    }

    /**
     * 设置预检查结果
     */
    public static void setPreCheckResInfo(Consumer<DemoPreCheckRes> consumer) {
        getReportResponse().ifPresent(r -> {
            DemoReportContext context = r.getData();
            DemoPreCheckRes preCheckRes = context.getPreCheckRes(DemoPreCheckRes.class);
            consumer.accept(preCheckRes);
        });
    }
} 