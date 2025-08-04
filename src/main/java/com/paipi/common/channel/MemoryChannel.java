package com.paipi.common.channel;

import com.paipi.transformer.TransformerExecution;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public class MemoryChannel<T> implements Channel<T> {
    private final Queue<T> queue = new LinkedList<>();
    private final int capacity;
    private final AtomicInteger writeCount = new AtomicInteger(0);
    private final AtomicInteger readCount = new AtomicInteger(0);
    private boolean closed = false;
    @Getter
    @Setter
    private TransformerExecution transformerExecution;

    public MemoryChannel() {
        this(10000); // 默认容量
    }

    public MemoryChannel(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public synchronized void push(T record) throws Exception {
        // transformer链处理（仅对Record类型生效）
        if (transformerExecution != null && record instanceof com.paipi.common.channel.Record) {
            record = (T) transformerExecution.execute((com.paipi.common.channel.Record) record);
            if (record == null) return; // 被过滤
        }
        while (queue.size() >= capacity && !closed) {
            wait();
        }
        if (closed) throw new Exception("Channel is closed");
        queue.offer(record);
        writeCount.incrementAndGet();
        notifyAll();
    }

    @Override
    public synchronized void pushAll(java.util.Collection<T> records) throws Exception {
        for (T r : records) {
            push(r);
        }
    }

    @Override
    public synchronized T pull() throws Exception {
        while (queue.isEmpty() && !closed) {
            wait();
        }
        T t = queue.poll();
        if (t != null) readCount.incrementAndGet();
        notifyAll();
        return t;
    }

    @Override
    public synchronized java.util.List<T> pullAll(int maxElements) throws Exception {
        java.util.List<T> list = new ArrayList<>();
        while (list.size() < maxElements) {
            T t = pull();
            if (t == null) break;
            list.add(t);
        }
        return list;
    }

    @Override
    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public synchronized void close() {
        closed = true;
        notifyAll();
    }

    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public int getWriteCount() {
        return writeCount.get();
    }

    @Override
    public int getReadCount() {
        return readCount.get();
    }
} 