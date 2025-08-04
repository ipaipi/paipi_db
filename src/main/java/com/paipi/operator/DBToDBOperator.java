package com.paipi.operator;

import com.paipi.adapter.base.BaseAdapter;
import com.paipi.common.channel.Channel;
import com.paipi.common.channel.ChannelFactory;
import com.paipi.common.channel.Record;
import com.paipi.common.column.ColumnParser;
import com.paipi.config.Configuration;
import com.paipi.constant.CoreConstant;
import com.paipi.constant.ParameterConstant;
import com.paipi.enums.SqlMode;
import com.paipi.exception.DbAdapterException;
import com.paipi.operator.base.BaseOperator;
import com.paipi.report.ReportContext;
import com.paipi.report.ReportContextManager;
import com.paipi.report.ReportResponse;
import com.paipi.transformer.Transformer;
import com.paipi.transformer.TransformerExecution;
import com.paipi.transformer.TransformerFactory;
import lombok.*;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static com.paipi.Engine.buildDbAdapter;

public class DBToDBOperator extends BaseOperator {
    private static final Map<String, Consumer<Object>> CALLBACK_ROUTE = new HashMap<>();

    static {
        initPreCheckResRoute();
        initExecuteReportRoute();
        ReportContextManager.add(CALLBACK_ROUTE);
        ReportContext reportContext = ReportContext.builder()
                .preCheckRes(new PreCheckRes())
                .executeReport(new ExecuteReport())
                .build();

        ReportResponse<ReportContext> reportResponse = ReportResponse.init(reportContext);
        ReportContextManager.add(reportResponse);
    }

    // 新增成员变量
    private java.util.List<Configuration> splitConfigs;

    public DBToDBOperator(Configuration originalConfig) {
        super(originalConfig);
    }

    private static void initPreCheckResRoute() {
        CALLBACK_ROUTE.put(
                RouteName.PreCheckRes.READER_TABLE_NAME.getName(),
                it -> setPreCheckResInfo(r -> r.setReaderTableName((String) it))
        );
        CALLBACK_ROUTE.put(
                RouteName.PreCheckRes.WRITER_TABLE_NAME.getName(),
                it -> setPreCheckResInfo(r -> r.setWriterTableName((String) it))
        );
        CALLBACK_ROUTE.put(
                RouteName.PreCheckRes.READER_TABLE_IS_EXIST.getName(),
                it -> setPreCheckResInfo(r -> r.setReaderTableIsExist((boolean) it))
        );
        CALLBACK_ROUTE.put(
                RouteName.PreCheckRes.WRITER_TABLE_IS_EXIST.getName(),
                it -> setPreCheckResInfo(r -> r.setWriterTableIsExist((boolean) it))
        );
    }

    private static void initExecuteReportRoute() {
        CALLBACK_ROUTE.put(
                RouteName.ExecuteReport.READER_COUNT.getName(),
                it -> setExecuteReportInfo(r -> r.setReaderCount((Integer) it))
        );
        CALLBACK_ROUTE.put(
                RouteName.ExecuteReport.WRITER_COUNT.getName(),
                it -> setExecuteReportInfo(r -> r.setWriterColNum((Integer) it))
        );
    }


    public static void setPreCheckResInfo(Consumer<PreCheckRes> consumer) {
        ReportContextManager.get(ReportResponse.class).ifPresent(r -> {
            // 1. 获取原始数据
            Object data = r.getData();
            // 2. 类型转换
            ReportContext reportContext = (ReportContext) data;
            // 3. 获取预检查结果
            PreCheckRes res = reportContext.getPreCheckRes(PreCheckRes.class);
            // 4. 执行消费者逻辑
            consumer.accept(res); // 等价于 res.setTableIsExist((boolean) it;
        });
    }

    public static void setExecuteReportInfo(Consumer<ExecuteReport> consumer) {
        ReportContextManager.get(ReportResponse.class).ifPresent(r -> {
            // **第一步：获取原始数据（Object 类型）**
            Object data = r.getData();

            // **第二步：强制类型转换成 ReportContext**
            ReportContext reportContext = (ReportContext) data;

            // **第三步：从 ReportContext 获取 ExecuteReport 对象
            ExecuteReport res = reportContext.getExecuteReport(ExecuteReport.class);

            // **第四步：调用 Consumer 处理 ExecuteReport
            consumer.accept(res);
        });
    }

    @Override
    public void pre() throws Exception {
        // todo 对象评估 语法评估
        String readerDbType = originalConfig.getString(CoreConstant.DB_JOB_CONTENT_READER_NAME);
        String writerDbType = originalConfig.getString(CoreConstant.DB_JOB_CONTENT_WRITER_NAME);
        readerAdapter = buildDbAdapter(readerDbType, originalConfig, readerConfig);
        writerAdapter = buildDbAdapter(writerDbType, originalConfig, writerConfig);

        // 执行preSql
        if (!readerConfig.getString(ParameterConstant.PRE_SQL, "").isEmpty()) {
            readerAdapter.executeSQL(readerConfig.getString(ParameterConstant.PRE_SQL));
        }
        if (!writerConfig.getString(ParameterConstant.PRE_SQL, "").isEmpty()) {
            writerAdapter.executeSQL(writerConfig.getString(ParameterConstant.PRE_SQL));
        }

        // 分片数
        int splitCount = originalConfig.getInt("job.setting.speed.channel", 2);
        // 生成分片配置
        this.splitConfigs = readerAdapter.split(splitCount);
        logger.info("==========splitCount：{},splitConfigs:{}==========", splitCount, splitConfigs.size());
        // 如果没有表名称就获取文件名称
        ReportContextManager.publish(RouteName.PreCheckRes.READER_TABLE_NAME.getName(),
                readerAdapter.getTable() != null && !readerAdapter.getTable().isEmpty() ? readerAdapter.getTable() : readerConfig.getString(ParameterConstant.FILE_PATH));
        ReportContextManager.publish(RouteName.PreCheckRes.WRITER_TABLE_NAME.getName(),
                writerAdapter.getTable() != null && !writerAdapter.getTable().isEmpty() ? writerAdapter.getTable() : writerConfig.getString(ParameterConstant.FILE_PATH));
        ReportContextManager.publish(RouteName.PreCheckRes.READER_TABLE_IS_EXIST.getName(), readerAdapter.tableIsExist(readerAdapter.getTable()));
        ReportContextManager.publish(RouteName.PreCheckRes.WRITER_TABLE_IS_EXIST.getName(), writerAdapter.tableIsExist(writerAdapter.getTable()));

        // 自动建表，只用于测试环境使用，不推荐用于生产环境表
        Boolean autoCreateTable = writerConfig.getBool(ParameterConstant.AUTO_CREATE_TABLE, false);
        if (!writerAdapter.tableIsExist(writerAdapter.getTable()) && autoCreateTable) {
            logger.warn("table：{} not exists, auto create table.", writerAdapter.getTable());
            String[] columnNamesArray = ColumnParser.parseColumns(writerConfig.getList(ParameterConstant.COLUMN)).getColumnNamesArray();
            String[] columnTypesArray = ColumnParser.parseColumns(writerConfig.getList(ParameterConstant.COLUMN)).getColumnTypesArray();
            writerAdapter.createTable(writerAdapter.getTable(), columnNamesArray, columnTypesArray);
        }
    }


    @Override
    public void run() throws Exception {
        java.util.List<Thread> threads = new java.util.ArrayList<>();
        java.util.List<Channel<Record>> channels = new java.util.ArrayList<>();
        java.util.List<BaseAdapter> readers = new java.util.ArrayList<>();
        java.util.List<BaseAdapter> writers = new java.util.ArrayList<>();
        String readerDbType = originalConfig.getString(CoreConstant.DB_JOB_CONTENT_READER_NAME);
        String writerDbType = originalConfig.getString(CoreConstant.DB_JOB_CONTENT_WRITER_NAME);
        java.util.concurrent.atomic.AtomicReference<Throwable> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
        for (Configuration splitConfig : splitConfigs) {
            // 通过工厂根据配置动态创建通道（支持memory/file/buffered）
            Channel<Record> channel = ChannelFactory.createChannel(originalConfig);
            // 注入transformer链
            java.util.List<Transformer> transformers = TransformerFactory.createTransformers(originalConfig);
            if (!transformers.isEmpty()) {
                TransformerExecution execution = new TransformerExecution(transformers);
                channel.setTransformerExecution(execution);
            }
            channels.add(channel);
            BaseAdapter reader = buildDbAdapter(readerDbType, originalConfig, splitConfig);
            BaseAdapter writer = buildDbAdapter(writerDbType, originalConfig, writerConfig);
            readers.add(reader);
            writers.add(writer);
            Thread readerThread = new Thread(() -> {
                try {
                    reader.readerPlugin(channel);
                } catch (Throwable e) {
                    errorRef.set(e);
                }
            });
            Thread writerThread = new Thread(() -> {
                try {
                    writer.writerPlugin(channel);
                } catch (Throwable e) {
                    errorRef.set(e);
                }
            });
            threads.add(readerThread);
            threads.add(writerThread);
            readerThread.start();
            writerThread.start();
        }
        for (Thread t : threads) t.join();
        if (errorRef.get() != null) {
            throw new DbAdapterException("DBToDBOperator线程异常", errorRef.get());
        }
        // 汇总所有 channel 的统计信息
        int totalRead = channels.stream().mapToInt(Channel::getWriteCount).sum();
        int totalWrite = channels.stream().mapToInt(Channel::getReadCount).sum();
        ReportContextManager.publish(RouteName.ExecuteReport.READER_COUNT.getName(), totalRead);
        ReportContextManager.publish(RouteName.ExecuteReport.WRITER_COUNT.getName(), totalWrite);
        String checkMsg = "";
        if (totalRead != totalWrite) {
            checkMsg = String.format("[警告] reader数量%d != writer数量%d，数据可能丢失或重复！", totalRead, totalWrite);
            logger.warn(checkMsg);
        }
        logger.info("DBToDBOperator同步完成, reader数量{}, writer数量{}", totalRead, totalWrite);

        // 统一资源清理：所有channel调用cleanup
        for (Channel<Record> channel : channels) {
            try {
                channel.cleanup();
            } catch (Exception e) {
                logger.warn("Channel cleanup异常", e);
            }
        }
    }

    @Override
    public void post() throws Exception {
        // todo 统计校验 、抽样校验、精确校验
        // 执行postSql
        if (!readerConfig.getString(ParameterConstant.POST_SQL, "").isEmpty()) {
            readerAdapter.executeSQL(readerConfig.getString(ParameterConstant.POST_SQL));
        }
        if (!writerConfig.getString(ParameterConstant.POST_SQL, "").isEmpty()) {
            writerAdapter.executeSQL(writerConfig.getString(ParameterConstant.POST_SQL));
        }
    }

    public interface RouteName {
        @Getter
        @AllArgsConstructor
        enum PreCheckRes {
            READER_TABLE_NAME(SqlMode.DB_TO_DB.name() + ":PreCheckRes:READER_TABLE_NAME"),
            WRITER_TABLE_NAME(SqlMode.DB_TO_DB.name() + ":PreCheckRes:WRITER_TABLE_NAME"),
            READER_TABLE_IS_EXIST(SqlMode.DB_TO_DB.name() + ":PreCheckRes:READER_TABLE_IS_EXIST"),
            WRITER_TABLE_IS_EXIST(SqlMode.DB_TO_DB.name() + ":PreCheckRes:WRITER_TABLE_IS_EXIST");
            private final String name;
        }

        @Getter
        @AllArgsConstructor
        enum ExecuteReport {
            READER_COUNT(SqlMode.DB_TO_DB.name() + ":ExecuteReport:READER_COUNT"),
            WRITER_COUNT(SqlMode.DB_TO_DB.name() + ":ExecuteReport:WRITER_COUNT");
            private final String name;
        }
    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreCheckRes {
        private String readerTableName;
        private String writerTableName;
        private Boolean readerTableIsExist;
        private Boolean writerTableIsExist;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecuteReport {
        private Integer readerCount;
        private Integer writerColNum;
    }
} 