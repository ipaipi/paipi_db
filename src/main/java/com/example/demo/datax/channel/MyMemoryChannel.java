package com.example.demo.datax.channel;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 模拟内存通道实现
 * 这是一个线程安全的生产者-消费者模式的内存通道实现
 * 支持单条和批量数据的读写操作
 */
public class MyMemoryChannel {
    // 使用LinkedList作为底层队列实现
    private final Queue<DataRecord> queue;
    // 通道容量限制
    private final int capacity;
    // 使用AtomicLong统计读者等待时间(纳秒)
    private final AtomicLong waitReaderNanos = new AtomicLong(0);
    // 使用AtomicLong统计写者等待时间(纳秒)
    private final AtomicLong waitWriterNanos = new AtomicLong(0);
    // 标记通道是否已关闭
    private volatile boolean closed = false;

    /**
     * 构造函数
     *
     * @param capacity 通道容量
     */
    public MyMemoryChannel(int capacity) {
        this.capacity = capacity;
        this.queue = new LinkedList<>();
    }

    /**
     * 写入单条记录
     *
     * @param record 要写入的数据记录
     * @throws InterruptedException  如果线程在等待时被中断
     * @throws IllegalStateException 如果通道已关闭
     */
    public void push(DataRecord record) throws InterruptedException {
        synchronized (queue) {
            // 当队列满且通道未关闭时，等待
            while (queue.size() >= capacity && !closed) {
                long start = System.nanoTime();
                queue.wait();  // 释放锁并等待
                waitWriterNanos.addAndGet(System.nanoTime() - start);
            }
            if (closed) throw new IllegalStateException("Channel closed");
            queue.add(record);
            queue.notifyAll();  // 通知所有等待线程
        }
    }

    /**
     * 批量写入记录
     *
     * @param records 要写入的记录集合
     * @throws InterruptedException 如果线程在等待时被中断
     */
    public void pushAll(Collection<DataRecord> records) throws InterruptedException {
        for (DataRecord r : records) {
            push(r);  // 循环调用单条写入方法
        }
    }

    /**
     * 读取单条记录
     *
     * @return 读取到的数据记录，如果通道已关闭且队列为空则返回null
     * @throws InterruptedException 如果线程在等待时被中断
     */
    public DataRecord pull() throws InterruptedException {
        synchronized (queue) {
            // 当队列为空且通道未关闭时，等待
            while (queue.isEmpty() && !closed) {
                long start = System.nanoTime();
                queue.wait();  // 释放锁并等待
                waitReaderNanos.addAndGet(System.nanoTime() - start);
            }
            DataRecord record = queue.poll();
            queue.notifyAll();  // 通知所有等待线程
            return record;
        }
    }

    /**
     * 批量读取记录
     *
     * @param collector 用于收集读取到的记录的集合
     * @throws InterruptedException 如果线程在等待时被中断
     */
    public void pullAll(Collection<DataRecord> collector) throws InterruptedException {
        synchronized (queue) {
            // 将队列中所有记录转移到收集器中
            while (!queue.isEmpty()) {
                collector.add(queue.poll());
            }
            queue.notifyAll();  // 通知所有等待线程
        }
    }

    /**
     * 关闭通道
     * 会唤醒所有等待的线程，之后所有操作将抛出异常
     */
    public void close() {
        closed = true;
        synchronized (queue) {
            queue.notifyAll();  // 唤醒所有等待线程
        }
    }

    /**
     * 获取当前队列大小
     *
     * @return 队列中的元素数量
     */
    public int size() {
        synchronized (queue) {
            return queue.size();
        }
    }

    /**
     * 数据记录内部类
     */
    public static class DataRecord {
        // 记录的数据列
        private final Object[] columns;
        // 记录占用的字节大小
        private final long byteSize;

        /**
         * 构造函数
         *
         * @param data     记录数据数组
         * @param byteSize 记录字节大小
         */
        public DataRecord(Object[] data, long byteSize) {
            this.columns = data;
            this.byteSize = byteSize;
        }

        /**
         * 获取指定列的数据
         *
         * @param index 列索引
         * @return 列数据
         */
        public Object getColumn(int index) {
            return columns[index];
        }

        /**
         * 获取记录字节大小
         *
         * @return 字节大小
         */
        public long getByteSize() {
            return byteSize;
        }
    }
}