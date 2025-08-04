package com.paipi.report;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class ReportContextManager {
    private static final Logger logger = LogManager.getLogger(ReportContextManager.class);
    private static final ThreadLocal<Object> DATA_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Consumer<Object>>> CALLBACK_ROUTE = new ThreadLocal<>();

    private ReportContextManager() {
    }

    public static <T> void add(T data) {
        DATA_HOLDER.set(data);
    }

    public static void add(Map<String, Consumer<Object>> callbackRoute) {
        if (CALLBACK_ROUTE.get() == null) {
            CALLBACK_ROUTE.set(callbackRoute);
        } else {
            CALLBACK_ROUTE.get().putAll(callbackRoute);
        }
    }

    /**
     * 获取当前线程上下文中的指定类型对象
     *
     * @param <T> 对象类型
     * @param cls 要获取的对象类型class
     * @return 如果存在指定类型对象则返回Optional包含该对象，否则返回空Optional
     */
    public static <T> Optional<T> get(Class<T> cls) {
        if (DATA_HOLDER.get() == null) {
            return Optional.empty();
        }
        return Optional.of(cls.cast(DATA_HOLDER.get()));
    }

    public static void remove() {
        DATA_HOLDER.remove();
        CALLBACK_ROUTE.remove();
    }

    public static void publish(String routeName, Object data) {
        try {
            if (CALLBACK_ROUTE.get() != null && CALLBACK_ROUTE.get().get(routeName) != null) {
                CALLBACK_ROUTE.get().get(routeName).accept(data);
            } else {
                logger.warn("this routeName: {} is not in CALLBACK_ROUTE", routeName);
            }
        } catch (Exception e) {
            logger.error("publish failed", e);
        }
    }
}