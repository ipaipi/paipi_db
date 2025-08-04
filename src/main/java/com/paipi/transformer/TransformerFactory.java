package com.paipi.transformer;

import com.paipi.config.Configuration;
import com.paipi.constant.CoreConstant;
import com.paipi.constant.TransformerParameterConstant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 根据配置动态创建transformer链的工厂类。
 * 兼容DataX风格。
 */
public class TransformerFactory {
    /**
     * 根据配置创建transformer链。
     * 支持DataX的transformer/transformers两种写法。
     *
     * @param config 配置对象（readerConfig）
     * @return transformer链
     */
    public static List<Transformer> createTransformers(Configuration config) {
        List<Transformer> transformers = new ArrayList<>();
        if (config == null) return transformers;
        List<Object> transformerDefs = config.getList(CoreConstant.DB_JOB_CONTENT_TRANSFORMER);
        if (transformerDefs == null) {
            transformerDefs = config.getList(CoreConstant.DB_JOB_CONTENT_TRANSFORMER);
        }
        if (transformerDefs == null) return transformers;
        for (Object def : transformerDefs) {
            if (def instanceof String) {
                // 简单字符串，内置transformer
                Transformer t = TransformerRegistry.create((String) def, null);
                transformers.add(t);
            } else if (def instanceof Map) {
                // 复杂对象，支持参数
                Map<?, ?> map = (Map<?, ?>) def;
                String name = (String) map.get(TransformerParameterConstant.NAME);
                Map<String, Object> params = (Map<String, Object>) map.get(TransformerParameterConstant.PARAMETER);
                Transformer t = TransformerRegistry.create(name, params);
                transformers.add(t);
            }
        }
        return transformers;
    }
} 