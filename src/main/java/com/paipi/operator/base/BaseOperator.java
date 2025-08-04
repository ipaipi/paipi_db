package com.paipi.operator.base;

import com.alibaba.fastjson.JSON;
import com.paipi.adapter.base.BaseAdapter;
import com.paipi.config.Configuration;
import com.paipi.constant.CoreConstant;
import com.paipi.report.ReportContextManager;
import com.paipi.report.ReportResponse;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * BaseOperator 是所有 Operator 的抽象基类，定义了 Operator 的基本执行流程和通用方法。
 * 主要职责：
 * 1. 统一 Operator 的生命周期（pre、run、post）
 * 2. 提供参数解密、适配器构建、缓存等通用能力
 * 3. 负责执行结果的收集与报告生成
 */

public abstract class BaseOperator {
    /**
     * 日志记录器
     */
    protected static final Logger logger = LogManager.getLogger(BaseOperator.class);

    /**
     * 数据库适配器
     */
    protected BaseAdapter dbAdapter;
    protected BaseAdapter readerAdapter;
    protected BaseAdapter writerAdapter;

    /**
     * 运行参数
     */
    protected Configuration originalConfig;
    protected Configuration commonConfig;
    protected Configuration readerConfig;
    protected Configuration writerConfig;


    /**
     * 构造方法
     *
     * @param originalConfig 运行参数
     */
    protected BaseOperator(Configuration originalConfig) {
        // 原始配置 备用
        this.originalConfig = originalConfig;
        // sqlMode 1-7 使用
        this.commonConfig = originalConfig.getConfiguration(CoreConstant.DB_JOB_CONTENT_COMMON_PARAMETER);

        // sqlMode 8-9 使用
        this.readerConfig = originalConfig.getConfiguration(CoreConstant.DB_JOB_CONTENT_READER_PARAMETER);
        this.writerConfig = originalConfig.getConfiguration(CoreConstant.DB_JOB_CONTENT_WRITER_PARAMETER);
    }

    /**
     * Operator 执行前的前置处理（如参数校验、资源准备等）
     *
     * @throws Exception 处理异常
     */
    public abstract void pre() throws Exception;

    /**
     * Operator 的核心执行逻辑
     *
     * @throws Exception 执行异常
     */
    public abstract void run() throws Exception;

    /**
     * Operator 执行后的收尾处理（资源清理）
     *
     * @throws Exception 处理异常
     */
    public abstract void post() throws Exception;

    /**
     * Operator 执行入口，包含完整生命周期（pre、run、post）
     *
     * @throws Exception 执行异常
     */
    public void start() throws Exception {
        boolean enablePreCheck = BooleanUtils.isTrue(originalConfig.getBool(CoreConstant.DB_JOB_SETTING_ENABLE_PRE_CHECK, true));
        boolean enablePostCheck = BooleanUtils.isTrue(originalConfig.getBool(CoreConstant.DB_JOB_SETTING_ENABLE_POST_CHECK, true));
        String reportFilePath = originalConfig.getString(CoreConstant.DB_JOB_SETTING_REPORT_FILEPATH, "");
        long startTime = System.currentTimeMillis();

        if (enablePreCheck) {
            try {
                pre();
            } catch (Exception e) {
                logger.error("执行前置校验发生异常，终止执行", e);
                throw e;
            }
        }

        ReportResponse reportResponse = null;
        try {
            run();

            //记录成功
            reportResponse = ReportContextManager.get(ReportResponse.class).orElse(null);
            if (reportResponse != null) {
                reportResponse.setCode(ReportResponse.Code.SUCCESS.ordinal());
                reportResponse.setSqlMode(originalConfig.getString(CoreConstant.DB_JOB_SQL_MODE));
                reportResponse.setCommonName(originalConfig.getString(CoreConstant.DB_JOB_CONTENT_COMMON_NAME, ""));
                reportResponse.setReaderName(originalConfig.getString(CoreConstant.DB_JOB_CONTENT_READER_NAME, ""));
                reportResponse.setWriterName(originalConfig.getString(CoreConstant.DB_JOB_CONTENT_WRITER_NAME, ""));
                reportResponse.setMsg("执行成功");
            }
        } catch (Exception e) {
            //记录失败
            reportResponse = ReportContextManager.get(ReportResponse.class).orElse(null);
            if (reportResponse != null) {
                reportResponse.setStackTrace(ExceptionUtils.getStackTrace(e));
                reportResponse.setSqlMode(originalConfig.getString(CoreConstant.DB_JOB_SQL_MODE));
                reportResponse.setCommonName(originalConfig.getString(CoreConstant.DB_JOB_CONTENT_COMMON_NAME, ""));
                reportResponse.setReaderName(originalConfig.getString(CoreConstant.DB_JOB_CONTENT_READER_NAME, ""));
                reportResponse.setWriterName(originalConfig.getString(CoreConstant.DB_JOB_CONTENT_WRITER_NAME, ""));
                reportResponse.setMsg("执行失败");
                reportResponse.setCode(ReportResponse.Code.FAILED.ordinal());
            }
            throw e;
        } finally {
            if (enablePostCheck) {
                try {
                    post();
                } catch (Exception e) {
                    logger.error("执行 post() 发生异常", e);
                }
            }

            // 写报告
            if (StringUtils.isNotBlank(reportFilePath)) {
                if (reportResponse == null) {
                    logger.warn("收集器返回的结果对象为空，生成报告文件失败");
                } else {
                    reportResponse.setCostTime(System.currentTimeMillis() - startTime);
                    String json = JSON.toJSONString(reportResponse);
                    try {
                        Path reportPath = Paths.get(reportFilePath);
                        Path parentDir = reportPath.getParent();

                        if (parentDir != null) {
                            Files.createDirectories(parentDir);
                        }

                        try (BufferedWriter writer = new BufferedWriter(
                                new OutputStreamWriter(
                                        Files.newOutputStream(reportPath),
                                        StandardCharsets.UTF_8))) {
                            writer.write(json);
                            writer.flush();
                        }
                        logger.info("报告文件生成成功：" + reportFilePath);
                    } catch (IOException e) {
                        logger.error("写入报告文件失败", e);
                    }
                }
            }
            ReportContextManager.remove();
        }
    }
}
