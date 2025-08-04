package com.example.demo;


import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class CallbackRouteDemo {

    // 定义一个回调路由表
    private static final Map<String, Consumer<Object>> CALLBACK_ROUTE = new HashMap<>();
    // 用于保存 PreCheckRes 实例
    private static PreCheckRes preCheckRes = new PreCheckRes();

    // 这个方法模拟 setPreCheckResInfo
    private static void setPreCheckResInfo(Consumer<PreCheckRes> consumer) {
        consumer.accept(preCheckRes);
    }

    public static void main(String[] args) {
        // 注册回调，lambda写法
        CALLBACK_ROUTE.put("TABLE_IS_EXIST", it -> setPreCheckResInfo(r -> r.setTableIsExist((Boolean) it)));
        CALLBACK_ROUTE.put("TABLE_IS_EXIST1", it -> {
            preCheckRes.setTableIsExist((boolean) it);
        });

        // 触发回调，相当于业务代码中 publish 的效果
        CALLBACK_ROUTE.get("TABLE_IS_EXIST1").accept(true);

        // 打印结果，查看是否设置成功
        System.out.println(preCheckRes);

        // 你也可以试试 false
        CALLBACK_ROUTE.get("TABLE_IS_EXIST1").accept(false);
        System.out.println(preCheckRes);
    }

    // 一个简单的数据结构，模拟 PreCheckRes
    public static class PreCheckRes {
        private Boolean tableIsExist;

        public void setTableIsExist(Boolean tableIsExist) {
            this.tableIsExist = tableIsExist;
        }

        @Override
        public String toString() {
            return "PreCheckRes{tableIsExist=" + tableIsExist + '}';
        }
    }
} 