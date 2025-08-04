package com.example.demo.report;

/**
 * Demo 流程主类，演示如何通过事件驱动方式给 PreCheckRes 赋值
 */
public class DemoPreCheckResFlow {
    public static void main(String[] args) {
        // 1. 初始化上下文和响应对象
        DemoReportContext context = new DemoReportContext();
        DemoReportResponse<DemoReportContext> response = new DemoReportResponse<>(context);
        DemoReportContextManager.setReportResponse(response);

        // 2. 注册路由（模拟 CALLBACK_ROUTE）
        DemoReportContextManager.addRoute("TO_TABLE_IS_EXIST", it ->
                DemoReportContextManager.setPreCheckResInfo(r -> r.setToTableIsExist((Boolean) it))
        );

        // 3. 发布事件（模拟 publish）
        Boolean toTableIsExist = false; // 假设去向表不存在
        DemoReportContextManager.publish("TO_TABLE_IS_EXIST", toTableIsExist);

        // 4. 检查结果
        System.out.println("最终 DemoPreCheckRes.toTableIsExist = " +
                context.getPreCheckRes(DemoPreCheckRes.class).getToTableIsExist());
    }
}