package com.paipi.transformer;

import java.util.HashMap;
import java.util.Map;

/**
 * transformer注册表，支持内置和自定义transformer。
 * 参考DataX风格。
 */
public class TransformerRegistry {
    private static final Map<String, Class<? extends Transformer>> registry = new HashMap<>();

    static {
        // 注册内置transformer
        register("trim", com.paipi.transformer.impl.TrimTransformer.class);
        register("replace", com.paipi.transformer.impl.ReplaceTransformer.class);
        register("filter", com.paipi.transformer.impl.FilterTransformer.class);
        register("dateformat", com.paipi.transformer.impl.DateFormatTransformer.class);
        register("md5encrypt", com.paipi.transformer.impl.Md5EncryptTransformer.class);
        register("zeroonetoboolean", com.paipi.transformer.impl.ZeroOneToBooleanTransformer.class);
    }

    /**
     * 注册transformer
     */
    public static void register(String name, Class<? extends Transformer> clazz) {
        registry.put(name.toLowerCase(), clazz);
    }

    /**
     * 查找transformer类
     */
    public static Class<? extends Transformer> get(String name) {
        return registry.get(name.toLowerCase());
    }

    /**
     * 通过名称和参数实例化transformer，支持反射加载自定义类
     */
    public static Transformer create(String name, Map<String, Object> parameter) {
        Class<? extends Transformer> clazz = get(name);
        if (clazz != null) {
            try {
                // 优先找带参数的构造方法
                try {
                    return clazz.getConstructor(Map.class).newInstance(parameter);
                } catch (NoSuchMethodException e) {
                    // 回退无参构造
                    return clazz.getConstructor().newInstance();
                }
            } catch (Exception e) {
                throw new RuntimeException("实例化transformer失败: " + name, e);
            }
        }
        // 支持自定义类名（全限定名）
        try {
            Class<?> customClazz = Class.forName(name);
            if (Transformer.class.isAssignableFrom(customClazz)) {
                return (Transformer) customClazz.getConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException("找不到transformer: " + name, e);
        }
        throw new RuntimeException("找不到transformer: " + name);
    }
} 