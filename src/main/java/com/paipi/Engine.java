package com.paipi;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.paipi.adapter.base.BaseAdapter;
import com.paipi.config.Configuration;
import com.paipi.constant.CoreConstant;
import com.paipi.constant.ParameterConstant;
import com.paipi.enums.SqlMode;
import com.paipi.exception.DbAdapterException;
import com.paipi.operator.DBToDBOperator;
import com.paipi.operator.DBToFileOperator;
import com.paipi.operator.ExecuteSQLOperator;
import com.paipi.operator.base.BaseOperator;
import org.apache.logging.log4j.LogManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class Engine {
    protected static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(Engine.class);

    // 优化后的缓存：key为插件类型+配置摘要，value为Class对象或工厂
    protected static final Cache<String, Class<? extends BaseAdapter>> adapterClassCache = CacheBuilder.newBuilder()
            .maximumSize(50)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    public static void main(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            logger.error("请通过命令行参数指定配置文件路径或文件名");
            System.out.println("用法: java -jar xxx.jar <配置文件路径或文件名>");
            return;
        }
        long startTime = System.currentTimeMillis();
        runDb(args);
        long endTime = System.currentTimeMillis();
        long costTime = endTime - startTime;
        logger.info("任务执行完毕，耗时：{}ms", costTime);
    }

    /**
     * 根据 sqlMode 选择并执行对应的 Operator
     */
    private static void runDb(String[] args) throws Exception {
        Configuration originalConfig;
        try {
            originalConfig = getParamFromArgs(args);
            int sqlMode = originalConfig.getInt(CoreConstant.DB_JOB_SQL_MODE);

            BaseOperator baseOperator;
            if (sqlMode == SqlMode.DB_TO_FILE.getCode()) {
                baseOperator = new DBToFileOperator(originalConfig);
            } else if (sqlMode == SqlMode.EXECUTE_SQL.getCode()) {
                baseOperator = new ExecuteSQLOperator(originalConfig);
            } else if (sqlMode == SqlMode.DB_TO_DB.getCode()) {
                baseOperator = new DBToDBOperator(originalConfig);
            } else {
                throw new Exception("have no sql mode can be matched. sqlMode: " + sqlMode);
            }
            baseOperator.start();
        } catch (Exception e) {
            logger.error(e);
            throw e;
        }
    }

    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    /**
     * 生成唯一且安全的缓存key
     */
    private static String buildAdapterCacheKey(String dbType, Configuration config) {
        // 只拼接非敏感主键信息，或用hash摘要
        String main = dbType + ":" +
                Objects.toString(config.getString(ParameterConstant.CONNECTION_IP), "") + ":" +
                Objects.toString(config.getString(ParameterConstant.CONNECTION_PORT), "") + ":" +
                Objects.toString(config.getString(ParameterConstant.CONNECTION_USERNAME), "") + ":" +
                Objects.toString(config.getString(ParameterConstant.TABLE), "");
        // 可选：加hash防止key过长
        return dbType + ":" + md5(main);
    }

    /**
     * DataX风格插件反射加载与缓存（优化版）
     */
    public static BaseAdapter buildDbAdapter(String dbType, Configuration originalConfig, Configuration initArgs) {
        String className = "com.paipi.adapter." + dbType + "Adapter";
        String cacheKey = buildAdapterCacheKey(dbType, initArgs);

        // 1. 优先从缓存获取Class对象
        Class<? extends BaseAdapter> clazz = adapterClassCache.getIfPresent(cacheKey);
        if (clazz == null) {
            try {
                Class<?> raw = Class.forName(className);
                if (!BaseAdapter.class.isAssignableFrom(raw)) {
                    throw new DbAdapterException(className + " 不是 BaseAdapter 的子类");
                }
                clazz = (Class<? extends BaseAdapter>) raw;
                adapterClassCache.put(cacheKey, clazz);
            } catch (ClassNotFoundException e) {
                logger.error("Adapter class not found: " + className, e);
                throw new DbAdapterException("找不到适配器类: " + className, e);
            }
        }

        // 2. 每次都反射新实例，避免多线程/多任务冲突
        try {
            Constructor<?> constructor = clazz.getConstructor(Configuration.class, Configuration.class);
            return (BaseAdapter) constructor.newInstance(originalConfig, initArgs);
        } catch (Exception e) {
            logger.error("Adapter实例化失败: " + className, e);
            throw new DbAdapterException("适配器实例化失败: " + className, e);
        }
    }

    /**
     * 从命令行参数构建 Configuration
     *
     * @param args 命令行参数
     * @return 参数对象
     * @throws Exception 解析异常
     */
    private static Configuration getParamFromArgs(String[] args) throws Exception {
        String op = args[0];
        InputStream inputStream = null;
        java.io.File file = new java.io.File(op);
        if (file.exists() && file.isFile()) {
            // 优先从本地文件系统读取
            inputStream = Files.newInputStream(file.toPath());
        } else {
            // 回退到 resources 目录下
            inputStream = Engine.class.getClassLoader().getResourceAsStream(op);
            if (inputStream == null) {
                throw new RuntimeException("找不到配置文件: " + op);
            }
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        String configContent = result.toString("UTF-8");
        // 创建DataX Configuration对象
        return Configuration.from(configContent);
    }
}