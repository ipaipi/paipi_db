package com.paipi.common.channel;

import com.paipi.transformer.TransformerExecution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存+文件混合缓冲通道，兼顾速度和容量。
 * 内存满时自动溢写到磁盘，适合数据量波动大场景。
 * T需实现Serializable接口。
 */
public class BufferedChannel<T extends Serializable> implements Channel<T> {
    // 1. 静态常量
    protected static final Logger logger = LogManager.getLogger(BufferedChannel.class);
    private final Queue<T> memoryQueue;
    private final int memoryCapacity;
    private final File file;
    private final File filePath;
    private final AtomicInteger writeCount = new AtomicInteger(0);
    private final AtomicInteger readCount = new AtomicInteger(0);
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    private boolean closed = false;
    private TransformerExecution transformerExecution;

    public BufferedChannel(int memoryCapacity, String filePath) throws IOException {
        this.memoryCapacity = memoryCapacity;
        this.memoryQueue = new LinkedList<>();
        this.filePath = new File(filePath);
        if (!this.filePath.exists()) {
            this.filePath.mkdirs(); // 创建目录
        }
        this.file = new File(this.filePath, "channel_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 100000) + ".tmp");
        File parent = this.file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs(); // 确保父目录存在
        }
        if (!this.file.exists()) {
            this.file.createNewFile();
        }
        this.oos = new ObjectOutputStream(Files.newOutputStream(file.toPath()));
    }

    @Override
    public synchronized void push(T record) throws Exception {
        if (closed) throw new Exception("Channel is closed");
        if (transformerExecution != null && record instanceof Record) {
            record = (T) transformerExecution.execute((Record) record);
            if (record == null) return;
        }
        while (memoryQueue.size() >= memoryCapacity && !closed) {
            wait(); // 队列满时等待
        }
        if (closed) throw new Exception("Channel is closed");
        if (memoryQueue.size() < memoryCapacity) {
            memoryQueue.offer(record);
        } else {
            oos.writeObject(record);
        }
        writeCount.incrementAndGet();
        notifyAll(); // 唤醒等待pull的线程
    }

    @Override
    public synchronized void pushAll(Collection<T> records) throws Exception {
        for (T r : records) {
            push(r);
        }
    }

    @Override
    public synchronized T pull() throws Exception {
        while (memoryQueue.isEmpty() && !closed && (ois == null || file.length() == 0)) {
            wait(); // 没数据且未关闭时等待
        }
        T t = null;
        if (!memoryQueue.isEmpty()) {
            t = memoryQueue.poll();
        } else if (ois == null && file.exists() && file.length() > 0) {
            ois = new ObjectInputStream(Files.newInputStream(file.toPath()));
        }
        if (t == null && ois != null) {
            try {
                t = (T) ois.readObject();
            } catch (EOFException e) {
                t = null;
            }
        }
        if (t != null) {
            readCount.incrementAndGet();
            notifyAll(); // 唤醒等待push的线程
        }
        return t;
    }

    @Override
    public synchronized List<T> pullAll(int maxElements) throws Exception {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < maxElements; i++) {
            T t = pull();
            if (t == null) break;
            list.add(t);
        }
        return list;
    }

    @Override
    public synchronized boolean isEmpty() {
        return memoryQueue.isEmpty();
    }

    @Override
    public synchronized void close() {
        closed = true;
        try {
            if (oos != null) oos.close();
            if (ois != null) ois.close();
        } catch (IOException ignored) {
        }
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

    @Override
    public TransformerExecution getTransformerExecution() {
        return transformerExecution;
    }

    @Override
    public void setTransformerExecution(TransformerExecution transformerExecution) {
        this.transformerExecution = transformerExecution;
    }

    @Override
    public synchronized void cleanup() {
        if (file != null && file.exists()) {
            boolean deleted = file.delete();
            // 可选：加日志
            logger.info("BufferedChannel cleanup: 文件 {}{}", file.getAbsolutePath(), deleted ? " 已删除" : " 未删除");
        }
    }
} 