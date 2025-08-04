package com.example.demo;

import com.paipi.config.Configuration;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ConfigLoader {

    /**
     * 从resources目录加载JSON配置文件到DataX Configuration对象
     *
     * @param configPath resources目录下的相对路径，如"datasource-config.json"
     * @return Configuration对象
     * @throws RuntimeException 当文件读取失败或JSON解析失败时抛出
     */
    public static Configuration loadConfigFromResources(String configPath) {
        // 1. 获取资源文件的输入流
        InputStream inputStream = ConfigLoader.class.getClassLoader().getResourceAsStream(configPath);
        if (inputStream == null) {
            throw new RuntimeException("配置文件未找到: " + configPath);
        }

        try {
            // 2. 读取文件内容为字符串
            String configContent = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

            // 4. 创建DataX Configuration对象
            return Configuration.from(configContent);

        } catch (IOException e) {
            throw new RuntimeException("读取配置文件失败: " + configPath, e);
        } catch (Exception e) {
            throw new RuntimeException("解析JSON配置失败: " + configPath, e);
        } finally {
            // 5. 确保关闭输入流
            IOUtils.closeQuietly(inputStream);
        }
    }
}