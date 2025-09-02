package com.paipi.adapter;

import com.paipi.adapter.base.BaseAdapter;
import com.paipi.common.channel.Channel;
import com.paipi.common.channel.Record;
import com.paipi.common.column.*;
import com.paipi.config.Configuration;
import com.paipi.constant.OdpsParameterConstant;
import com.paipi.constant.ParameterConstant;
import org.apache.commons.lang3.StringUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang.StringUtils.isNotBlank;

public class OdpsAdapter extends BaseAdapter {

    public OdpsAdapter(Configuration originalConfig, Configuration config) {
        super(originalConfig, config);
    }

    @Override
    public String getDriverName() {
        return "com.aliyun.odps.jdbc.OdpsDriver";
    }

    @Override
    public String getJdbcUrl() {
        // DataX 风格：jdbc:odps:<project>?accessId=...;accessKey=...;endpoint=...;tunnelEndpoint=...;...
        String projectFromParam = config.getString(OdpsParameterConstant.PROJECT);
        String finalProject = isNotBlank(projectFromParam) ? projectFromParam : this.database;
        // 兼容 DataX 风格直传参数
        String accessId = config.getString(OdpsParameterConstant.ACCESS_ID);
        String accessKey = config.getString(OdpsParameterConstant.ACCESS_KEY);
        String odpsServer = config.getString(OdpsParameterConstant.ODPS_SERVER);
        String tunnelServer = config.getString(OdpsParameterConstant.TUNNEL_SERVER);

        StringBuilder url = new StringBuilder("odps:");
        url.append(finalProject);

        // 组装核心凭证与端点参数
        StringBuilder paramSb = new StringBuilder();
        paramSb.append("accessId=").append(accessId).append(";");
        paramSb.append("accessKey=").append(accessKey).append(';');
        if (isNotBlank(odpsServer)) {
            paramSb.append("endpoint=").append(odpsServer).append(';');
        }
        if (isNotBlank(tunnelServer)) {
            paramSb.append("tunnelEndpoint=").append(tunnelServer).append(';');
        }

        // 追加通用 JDBC 参数（来自 BaseAdapter 或配置的 jdbcUrlParam）
        String extraParams = assembleJdbcUrlParam();
        if (isNotBlank(extraParams)) {
            paramSb.append(extraParams);
        }

        if (paramSb.length() > 0) {
            url.append('?').append(paramSb);
        }
        return url.toString();
    }

    @Override
    public String dbTypeToInternalType(String colType) {
        if (colType == null) return StringColumn.TYPE_NAME;
        String type = colType.toLowerCase();
        // MaxCompute 常见类型：bigint, double, string, boolean, datetime, decimal, binary
        if (type.contains("int")) return IntColumn.TYPE_NAME; // ODPS 整型通常为 BIGINT，这里统一转内部 INT/LONG 由精度再定
        if (type.contains("bigint") || type.contains("long")) return LongColumn.TYPE_NAME;
        if (type.contains("double") || type.contains("float") || type.contains("decimal") || type.contains("numeric"))
            return DoubleColumn.TYPE_NAME;
        if (type.contains("char") || type.contains("string") || type.contains("varchar") || type.contains("text"))
            return StringColumn.TYPE_NAME;
        if (type.contains("bool")) return BoolColumn.TYPE_NAME;
        if (type.contains("date") || type.contains("time")) return DateColumn.TYPE_NAME; // datetime
        if (type.contains("binary") || type.contains("bytes")) return BytesColumn.TYPE_NAME;
        return StringColumn.TYPE_NAME;
    }

    @Override
    public List<Object> internalTypeToDbType(ColumnStruct column) {
        switch (column.getType()) {
            case STRING:
                return Arrays.asList("string", column.getPrecision() > 0 ? column.getPrecision() : 255);
            case INT:
                // MaxCompute 无原生 32 位 int，通常使用 bigint，这里向上兼容
                return Arrays.asList("bigint", column.getPrecision());
            case LONG:
                return Arrays.asList("bigint", column.getPrecision());
            case DOUBLE:
                return Arrays.asList("double", column.getPrecision(), column.getScale());
            case BOOL:
                return Arrays.asList("boolean");
            case DATE:
                return Arrays.asList("datetime");
            case BYTES:
                // MaxCompute 支持 binary，也可退化为 string
                return Arrays.asList("binary");
            default:
                return Arrays.asList("string", 255);
        }
    }

    @Override
    public String getCreateTableSql(String tableName, String[] colNames, String[] colTypes) {
        StringBuilder cols = new StringBuilder();
        for (int i = 0; i < colNames.length; i++) {
            String colName = colNames[i];
            cols.append(colName).append(" ");
            Column.Type type = Column.Type.valueOf(colTypes[i].toUpperCase());
            ColumnStruct struct = new ColumnStruct();
            struct.setType(type);
            List<Object> typeInfo = internalTypeToDbType(struct);
            cols.append(typeInfo.get(0));
            if (typeInfo.size() > 1 && typeInfo.get(1) != null) {
                cols.append("(").append(typeInfo.get(1));
                if (typeInfo.size() > 2 && typeInfo.get(2) != null) {
                    cols.append(",").append(typeInfo.get(2));
                }
                cols.append(")");
            }
            if (i != (colTypes.length - 1)) cols.append(", ");
        }
        return "create table " + tableName + " (" + cols + ")";
    }

    @Override
    public void writerPlugin(Channel<Record> channel) throws Exception {
        try {
            String tableName = config.getString(ParameterConstant.TABLE, this.table);
            List<String> colNames = ColumnParser.parseColumns(config.getList(ParameterConstant.COLUMN)).getColumnNamesList();
            if (colNames == null || colNames.isEmpty()) throw new IllegalArgumentException("colNames参数不能为空");
            int batchSize = insertBatchSize;
            StringBuilder colStr = new StringBuilder();
            StringBuilder valueStr = new StringBuilder();
            for (int i = 0; i < colNames.size(); i++) {
                colStr.append(colNames.get(i));
                valueStr.append("?");
                if (i != (colNames.size() - 1)) {
                    colStr.append(",");
                    valueStr.append(",");
                }
            }
            String sql = "insert into " + tableName + " (" + colStr + ") values(" + valueStr + ")";
            long totalWrite = 0;
            try (Connection con = getConn(); PreparedStatement pstmt = con.prepareStatement(sql)) {
                ResultSetMetaData metaData = pstmt.getMetaData();
                if (metaData == null) {
                    Statement stmt = con.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT " + colStr + " FROM " + tableName + " WHERE 1=0");
                    metaData = rs.getMetaData();
                    rs.close();
                    stmt.close();
                }
                con.setAutoCommit(false);
                while (true) {
                    List<Record> records = channel.pullAll(batchSize);
                    if (records == null || records.isEmpty()) break;
                    try {
                        for (Record record : records) {
                            for (int i = 0; i < colNames.size(); i++) {
                                Column col = record.getColumn(i);
                                int sqlType = metaData.getColumnType(i + 1);
                                int maxLength = metaData.getPrecision(i + 1);
                                setPreparedStatementValue(pstmt, i + 1, sqlType, maxLength, col, tableName, colNames.get(i));
                            }
                            pstmt.addBatch();
                        }
                        pstmt.executeBatch();
                        con.commit();
                        totalWrite += records.size();
                        if (totalWrite % recordingTotalWrite == 0) {
                            logger.info("[Thread-{}] 已写入{}条数据", Thread.currentThread().getId(), totalWrite);
                        }
                    } catch (Exception e) {
                        channel.close();
                        throw e;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("writerPlugin error", e);
            throw e;
        } finally {
            try {
                channel.close();
                logger.info("Channel closed in writerPlugin for OdpsAdapter");
            } catch (Exception ex) {
                logger.warn("Exception when closing channel in writerPlugin: {}", ex.getMessage(), ex);
            }
        }
    }

    @Override
    public List<com.paipi.config.Configuration> split(int adviceNumber) throws Exception {
        String tableName = config.getString(ParameterConstant.TABLE, this.table);
        String splitPk = config.getString(ParameterConstant.SPLIT_PK);
        if (StringUtils.isBlank(splitPk)) {
            List<com.paipi.config.Configuration> splits = new ArrayList<>();
            splits.add(config.clone());
            return splits;
        }
        try (Connection con = getConn(); Statement stmt = con.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT MIN(" + splitPk + "), MAX(" + splitPk + ") FROM " + tableName);
            if (!rs.next()) {
                List<com.paipi.config.Configuration> splits = new ArrayList<>();
                splits.add(config.clone());
                return splits;
            }
            Object minObj = rs.getObject(1);
            Object maxObj = rs.getObject(2);
            if (!(minObj instanceof Number) || !(maxObj instanceof Number)) {
                List<com.paipi.config.Configuration> splits = new ArrayList<>();
                splits.add(config.clone());
                return splits;
            }
            long min = ((Number) minObj).longValue(), max = ((Number) maxObj).longValue();
            if (min == max) {
                List<com.paipi.config.Configuration> splits = new ArrayList<>();
                com.paipi.config.Configuration conf = config.clone();
                String finalWhere = config.getString(ParameterConstant.WHERE, "");
                String splitWhere = splitPk + " = " + min;
                if (StringUtils.isNotBlank(splitWhere)) {
                    if (StringUtils.isBlank(finalWhere)) finalWhere = splitWhere;
                    else finalWhere = "(" + finalWhere + ") AND (" + splitWhere + ")";
                }
                conf.set(ParameterConstant.WHERE, finalWhere);
                splits.add(conf);
                return splits;
            }
            long step = Math.max(1, (max - min + 1) / Math.max(1, adviceNumber));
            List<com.paipi.config.Configuration> splits = new ArrayList<>();
            for (int i = 0; i < adviceNumber; i++) {
                long start = min + i * step;
                long end = (i == adviceNumber - 1) ? max : (start + step - 1);
                com.paipi.config.Configuration conf = config.clone();
                String finalWhere = config.getString(ParameterConstant.WHERE, "");
                String splitWhere = splitPk + " >= " + start + " AND " + splitPk + " <= " + end;
                if (StringUtils.isNotBlank(splitWhere)) {
                    if (StringUtils.isBlank(finalWhere)) finalWhere = splitWhere;
                    else finalWhere = "(" + finalWhere + ") AND (" + splitWhere + ")";
                }
                conf.set(ParameterConstant.WHERE, finalWhere);
                splits.add(conf);
            }
            return splits;
        }
    }
}


