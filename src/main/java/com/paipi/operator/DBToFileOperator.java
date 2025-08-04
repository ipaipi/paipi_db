package com.paipi.operator;

import com.paipi.common.column.ColumnParser;
import com.paipi.config.Configuration;
import com.paipi.constant.CoreConstant;
import com.paipi.constant.ParameterConstant;
import com.paipi.enums.SqlMode;
import com.paipi.operator.base.BaseOperator;
import com.paipi.report.ReportContext;
import com.paipi.report.ReportContextManager;
import com.paipi.report.ReportResponse;
import lombok.*;

import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static com.paipi.Engine.buildDbAdapter;

public class DBToFileOperator extends BaseOperator {
    private static final Map<String, Consumer<Object>> CALLBACK_ROUTE;

    static {
        CALLBACK_ROUTE = new HashMap<>();
        initPreCheckResRoute();
        initExecuteReportRoute();

        ReportContextManager.add(CALLBACK_ROUTE);
        ReportContext reportContext = ReportContext.builder()
                .preCheckRes(new PreCheckRes())
                .executeReport(new ExecuteReport()).build();
        ReportResponse<ReportContext> reportResponse = ReportResponse.init(reportContext);
        ReportContextManager.add(reportResponse);
    }

    public DBToFileOperator(Configuration params) {
        super(params);
    }

    private static void initPreCheckResRoute() {
        CALLBACK_ROUTE.put(
                RouteName.PreCheckRes.TABLE_IS_EXIST.getName(),
                it -> setPreCheckResInfo(r -> r.setTableIsExist((boolean) it))
        );
    }

    private static void initExecuteReportRoute() {
        CALLBACK_ROUTE.put(
                RouteName.ExecuteReport.COL_FILE_NUM.getName(),
                it -> setExecuteReportInfo(r -> r.setColFileNum(Integer.valueOf(it + "")))
        );
        CALLBACK_ROUTE.put(
                RouteName.ExecuteReport.TABLE_COL_NUM.getName(),
                it -> setExecuteReportInfo(r -> r.setTableColNum(Integer.valueOf(it + "")))
        );
        CALLBACK_ROUTE.put(
                RouteName.ExecuteReport.TABLE_ROW_NUM.getName(),
                it -> setExecuteReportInfo(r -> r.setTableRowNum(Long.valueOf(it + "")))
        );
    }

    private static void setPreCheckResInfo(Consumer<PreCheckRes> consumer) {
        ReportContextManager.get(ReportResponse.class).ifPresent(r -> {
            PreCheckRes res = ((ReportContext) r.getData()).getPreCheckRes(PreCheckRes.class);
            consumer.accept(res);
        });
    }

    private static void setExecuteReportInfo(Consumer<ExecuteReport> consumer) {
        ReportContextManager.get(ReportResponse.class).ifPresent(r -> {
            ExecuteReport res = ((ReportContext) r.getData()).getExecuteReport(ExecuteReport.class);
            consumer.accept(res);
        });
    }

    @Override
    public void pre() throws Exception {
        String dbType = originalConfig.getString(CoreConstant.DB_JOB_CONTENT_COMMON_NAME);
        dbAdapter = buildDbAdapter(dbType, originalConfig, commonConfig);

        ReportContextManager.publish(RouteName.PreCheckRes.TABLE_IS_EXIST.getName(), dbAdapter.tableIsExist(dbAdapter.getTable()));
    }

    @Override
    public void run() throws Exception {
        int channel = originalConfig.getInt(CoreConstant.DB_JOB_SETTING_SPEED_CHANNEL);
        String where = commonConfig.getString(ParameterConstant.WHERE);
        String orderBy = commonConfig.getString(ParameterConstant.ORDERBY);
        String colNames = ColumnParser.parseColumns(commonConfig.getList(ParameterConstant.COLUMN)).getColumnNamesStr();
        String saveUrl = commonConfig.getString(ParameterConstant.SAVE_URL);
        String fileType = commonConfig.getString(ParameterConstant.FILE_TYPE, "csv");
        Boolean isChunk = commonConfig.getBool(ParameterConstant.IS_CHUNK, false);
        Integer chunkSize = commonConfig.getInt(ParameterConstant.CHUNK_SIZE, 1000000);
        String sep = commonConfig.getString(ParameterConstant.SEP, ",");
        String querySql = commonConfig.getString(ParameterConstant.QUERY_SQL, "");

        if (dbAdapter.getLoginUser() == null) {
            dbAdapter.downloadData(
                    dbAdapter.getTable(), colNames, where, orderBy, channel, saveUrl, sep, fileType, isChunk, chunkSize, querySql
            );
        } else {
            Exception e = dbAdapter.getLoginUser().doAs((PrivilegedAction<Exception>) () -> {
                try {
                    dbAdapter.downloadData(
                            dbAdapter.getTable(), colNames, where, orderBy, channel, saveUrl, sep, fileType, isChunk, chunkSize, querySql
                    );
                } catch (Exception ee) {
                    BaseOperator.logger.error("", ee);
                    return ee;
                }
                return null;
            });
            if (e != null) {
                throw e;
            }
        }
    }

    @Override
    public void post() throws Exception {
    }

    public interface RouteName {
        @Getter
        @AllArgsConstructor
        enum PreCheckRes {
            TABLE_IS_EXIST(SqlMode.DB_TO_FILE.name() + ":PreCheckRes:TABLE_IS_EXIST");
            private final String name;
        }

        @Getter
        @AllArgsConstructor
        enum ExecuteReport {
            COL_FILE_NUM(SqlMode.DB_TO_FILE.name() + ":ExecuteReport:COL_FILE_NUM"),
            TABLE_COL_NUM(SqlMode.DB_TO_FILE.name() + ":ExecuteReport:TABLE_COL_NUM"),
            TABLE_ROW_NUM(SqlMode.DB_TO_FILE.name() + ":ExecuteReport:TABLE_ROW_NUM");
            private final String name;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreCheckRes {
        // 表是否存在
        private Boolean tableIsExist;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecuteReport {
        // parquet文件的数量
        private Integer colFileNum;
        // 源表的列数
        private Integer tableColNum;
        // 源表的行数
        private Long tableRowNum;
    }
}
