package com.paipi.common.channel;

import com.paipi.transformer.TransformerExecution;

public interface Channel<T> {
    void push(T record) throws Exception;

    void pushAll(java.util.Collection<T> records) throws Exception;

    T pull() throws Exception;

    java.util.List<T> pullAll(int maxElements) throws Exception;

    boolean isEmpty();

    void close();

    boolean isClosed();

    int getWriteCount();

    int getReadCount();

    /**
     * 获取transformer链（可选实现）
     */
    default TransformerExecution getTransformerExecution() {
        return null;
    }

    /**
     * 注入transformer链（可选实现，便于扩展）
     */
    default void setTransformerExecution(TransformerExecution transformerExecution) {
    }

    /**
     * 资源彻底释放（如删除临时文件），主流程在所有数据消费完后调用
     */
    default void cleanup() {
        // 默认无操作，具体实现类可覆盖
    }
}