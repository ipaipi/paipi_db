package com.example.demo.datax.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MyChannelMainTest {

    public static void main(String[] args) {
        try {
            System.out.println("=== 开始通道测试 ===");

            testSingleThread();
            testBatchOperations();
            testConcurrentAccess();
            testFlowControl();
            testCloseBehavior();

            System.out.println("=== 所有测试通过 ===");
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void testSingleThread() throws Exception {
        System.out.println("\n[测试单线程基础功能]");
        MyMemoryChannel channel = new MyMemoryChannel(5);

        // 基础读写测试
        MyMemoryChannel.DataRecord record = new MyMemoryChannel.DataRecord(
                new Object[]{"test", 123}, 10);

        channel.push(record);
        System.out.println("写入后通道大小: " + channel.size());

        MyMemoryChannel.DataRecord result = channel.pull();
        System.out.println("读取数据: " + result.getColumn(0) + ", " + result.getColumn(1));
        System.out.println("读取后通道大小: " + channel.size());
    }

    static void testBatchOperations() throws Exception {
        System.out.println("\n[测试批量操作]");
        MyMemoryChannel channel = new MyMemoryChannel(100);
        List<MyMemoryChannel.DataRecord> records = new ArrayList<>();

        // 准备测试数据
        for (int i = 0; i < 50; i++) {
            records.add(new MyMemoryChannel.DataRecord(
                    new Object[]{i, "data-" + i}, 20));
        }

        // 批量写入
        channel.pushAll(records);
        System.out.println("批量写入后大小: " + channel.size());

        // 批量读取
        List<MyMemoryChannel.DataRecord> results = new ArrayList<>();
        channel.pullAll(results);
        System.out.println("读取记录数: " + results.size());
        System.out.println("读取示例: " + results.get(0).getColumn(0) + "=" + results.get(0).getColumn(1));
    }

    static void testConcurrentAccess() throws Exception {
        System.out.println("\n[测试并发访问]");
        final MyMemoryChannel channel = new MyMemoryChannel(10);
        final int threadCount = 4;
        final int recordsPerThread = 100;
        final CountDownLatch latch = new CountDownLatch(threadCount * 2);
        final AtomicInteger writeCounter = new AtomicInteger();
        final AtomicInteger readCounter = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount * 2);

        // 生产者线程
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < recordsPerThread; j++) {
                        channel.push(new MyMemoryChannel.DataRecord(
                                new Object[]{threadId, j}, 8));
                        writeCounter.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("写入线程错误: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 消费者线程
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    int count = 0;
                    while (count < recordsPerThread) {
                        MyMemoryChannel.DataRecord record = channel.pull();
                        if (record != null) {
                            count++;
                            readCounter.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("读取线程错误: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        System.out.println("写入总数: " + writeCounter.get());
        System.out.println("读取总数: " + readCounter.get());
        System.out.println("最终通道大小: " + channel.size());
    }

    static void testFlowControl() throws Exception {
        System.out.println("\n[测试流量控制]");
        MyMemoryChannel channel = new MyMemoryChannel(3);

        // 填满通道
        channel.push(new MyMemoryChannel.DataRecord(new Object[0], 1));
        channel.push(new MyMemoryChannel.DataRecord(new Object[0], 1));
        channel.push(new MyMemoryChannel.DataRecord(new Object[0], 1));

        // 异步尝试超额写入
        new Thread(() -> {
            try {
                System.out.println("尝试超额写入...");
                channel.push(new MyMemoryChannel.DataRecord(new Object[0], 1));
                System.out.println("超额写入完成");
            } catch (Exception e) {
                System.err.println("写入错误: " + e.getMessage());
            }
        }).start();

        // 等待写入阻塞
        Thread.sleep(100);
        //System.out.println("写入等待时间: " + channel.waitWriterNanos.get() + "ns");

        // 释放空间
        channel.pull();
        Thread.sleep(100); // 等待写入完成
        System.out.println("最终通道大小: " + channel.size());
    }

    static void testCloseBehavior() throws Exception {
        System.out.println("\n[测试关闭行为]");
        MyMemoryChannel channel = new MyMemoryChannel(1);
        channel.close();

        try {
            channel.push(new MyMemoryChannel.DataRecord(new Object[0], 1));
            System.err.println("错误: 关闭后写入未抛出异常");
        } catch (IllegalStateException e) {
            System.out.println("正确捕获关闭异常: " + e.getMessage());
        }
    }
}