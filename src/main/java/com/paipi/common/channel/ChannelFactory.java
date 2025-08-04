package com.paipi.common.channel;

import com.paipi.config.Configuration;
import com.paipi.constant.CoreConstant;

import java.io.IOException;

/**
 * 通道工厂类，根据配置动态创建不同类型的Channel（内存、文件、缓冲）。
 * 支持DataX风格的通道类型切换。
 */
public class ChannelFactory {
    /**
     * 根据配置创建Channel实例
     *
     * @param config 配置对象
     * @return Channel实例
     */
    public static <T extends java.io.Serializable> Channel<T> createChannel(Configuration config) throws IOException {
        String channelType = config.getString(CoreConstant.DB_JOB_SETTING_CHANNEL_CLASS, "memory").toLowerCase();
        switch (channelType) {
            case "buffered":
                int memCap = config.getInt(CoreConstant.DB_JOB_SETTING_CHANNEL_MEMORY_CAPACITY, 10000);
                // 缓冲文件
                String bufFile = config.getString(CoreConstant.DB_JOB_SETTING_CHANNEL_PATH, "./buffered_channel");
                return new BufferedChannel<>(memCap, bufFile);
            case "memory":
            default:
                int cap = config.getInt(CoreConstant.DB_JOB_SETTING_CHANNEL_MEMORY_CAPACITY, 10000);
                return new MemoryChannel<>(cap);
        }
    }
} 