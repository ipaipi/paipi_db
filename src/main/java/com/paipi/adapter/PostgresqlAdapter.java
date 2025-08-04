package com.paipi.adapter;

import com.paipi.adapter.base.BaseAdapter;
import com.paipi.common.channel.Record;
import com.paipi.common.column.*;
import com.paipi.config.Configuration;
import com.paipi.constant.ParameterConstant;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

public class PostgresqlAdapter extends BaseAdapter {
    public PostgresqlAdapter(Configuration originalConfig, Configuration config) {
        super(originalConfig, config);
        selectBatchSize = 1000;
        insertBatchSize = 1000;
    }

    @Override
    public String getDriverName() {
        return "org.postgresql.Driver";
    }

    @Override
    public String getJdbcUrl() {
        String url = String.format("postgresql://%s:%s/%s", ip, port, database);
        // schema不为空时，拼接currentSchema参数
        if (StringUtils.isNotBlank(this.schema)) {
            url += "?currentSchema=" + this.schema;
        }
        return url;
    }

    @Override
    public String dbTypeToInternalType(String colType) {
        // 数据库类型转内部类型
        if (colType == null) return StringColumn.TYPE_NAME;
        String type = colType.toLowerCase();
        if (type.contains("int")) return IntColumn.TYPE_NAME;
        if (type.contains("bigint") || type.contains("long")) return LongColumn.TYPE_NAME;
        if (type.contains("double") || type.contains("float") || type.contains("numeric") || type.contains("decimal"))
            return DoubleColumn.TYPE_NAME;
        if (type.contains("char") || type.contains("string") || type.contains("varchar") || type.contains("text"))
            return StringColumn.TYPE_NAME;
        if (type.contains("bool")) return BoolColumn.TYPE_NAME;
        if (type.contains("date") || type.contains("time")) return DateColumn.TYPE_NAME;
        if (type.contains("bytea") || type.contains("binary") || type.contains("bytes")) return BytesColumn.TYPE_NAME;
        return StringColumn.TYPE_NAME;
    }

    @Override
    public List<Object> internalTypeToDbType(ColumnStruct column) {
        // 内部类型转数据库类型
        switch (column.getType()) {
            case STRING:
                return Arrays.asList("varchar", column.getPrecision() > 0 ? column.getPrecision() : 255);
            case INT:
                return Arrays.asList("integer", column.getPrecision());
            case LONG:
                return Arrays.asList("bigint", column.getPrecision());
            case DOUBLE:
                return Arrays.asList("numeric", column.getPrecision(), column.getScale());
            case BOOL:
                return Arrays.asList("boolean");
            case DATE:
                return Arrays.asList("timestamp");
            case BYTES:
                return Arrays.asList("bytea");
            default:
                return Arrays.asList("varchar", 255);
        }
    }

    @Override
    public String getCreateTableSql(String tableName, String[] colNames, String[] colTypes) {
        StringBuilder cols = new StringBuilder();
        for (int i = 0; i < colNames.length; i++) {
            cols.append(colNames[i]).append(" ");
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
            if (i != (colTypes.length - 1)) {
                cols.append(", ");
            }
        }
        String sql;
        if (StringUtils.isEmpty(schema)) {
            sql = String.format("create table %s (%s)", tableName, cols);
        } else {
            sql = String.format("create table %s.%s (%s)", schema, tableName, cols);
        }
        return sql;
    }

    @Override
    public void writerPlugin(com.paipi.common.channel.Channel<com.paipi.common.channel.Record> channel) throws Exception {
        try {
            String tableName = config.getString(ParameterConstant.TABLE, this.table);
            List<String> colNames = com.paipi.common.column.ColumnParser.parseColumns(config.getList(ParameterConstant.COLUMN)).getColumnNamesList();
            if (colNames == null || colNames.isEmpty()) throw new IllegalArgumentException("colNames参数不能为空");
            int batchSize = insertBatchSize;
            String writeMode = config.getString(ParameterConstant.WRITE_MODE, "insert").toLowerCase();
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
            // 获取主键字段
            String pk = null;
            try (java.sql.Connection con = getConn(); java.sql.PreparedStatement ps = con.prepareStatement(
                    "SELECT a.attname FROM pg_index i JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) WHERE i.indrelid = ?::regclass AND i.indisprimary")) {
                ps.setString(1, tableName);
                java.sql.ResultSet rs = ps.executeQuery();
                if (rs.next()) pk = rs.getString(1);
            }
            if (("update".equals(writeMode) || "ignore".equals(writeMode)) && pk == null) {
                throw new IllegalArgumentException("PostgreSQL update/ignore模式必须有主键");
            }
            String sql;
            switch (writeMode) {
                case "update":
                    StringBuilder updateStr = new StringBuilder();
                    for (int i = 0; i < colNames.size(); i++) {
                        String col = colNames.get(i);
                        updateStr.append(col).append("=EXCLUDED.").append(col);
                        if (i != (colNames.size() - 1)) updateStr.append(",");
                    }
                    sql = "insert into " + tableName + " (" + colStr + ") values(" + valueStr + ") ON CONFLICT (" + pk + ") DO UPDATE SET " + updateStr;
                    break;
                case "ignore":
                    sql = "insert into " + tableName + " (" + colStr + ") values(" + valueStr + ") ON CONFLICT (" + pk + ") DO NOTHING";
                    break;
                case "replace":
                    throw new UnsupportedOperationException("PostgreSQL不支持replace模式");
                default:
                    sql = "insert into " + tableName + " (" + colStr + ") values(" + valueStr + ")";
                    break;
            }
            long totalWrite = 0;
            try (java.sql.Connection con = getConn(); java.sql.PreparedStatement pstmt = con.prepareStatement(sql)) {
                java.sql.ResultSetMetaData metaData = pstmt.getMetaData();
                if (metaData == null) {
                    java.sql.Statement stmt = con.createStatement();
                    java.sql.ResultSet rs = stmt.executeQuery("SELECT " + colStr + " FROM " + tableName + " WHERE 1=0");
                    metaData = rs.getMetaData();
                    rs.close();
                    stmt.close();
                }
                con.setAutoCommit(false);
                while (true) {
                    List<Record> records = channel.pullAll(batchSize);
                    if (records == null || records.isEmpty()) break;
                    try {
                        for (com.paipi.common.channel.Record t : records) {
                            com.paipi.common.channel.Record record = t;
                            for (int i = 0; i < colNames.size(); i++) {
                                com.paipi.common.column.Column col = record.getColumn(i);
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
                logger.info("Channel closed in writerPlugin for PostgresqlAdapter");
            } catch (Exception ex) {
                logger.warn("Exception when closing channel in writerPlugin: {}", ex.getMessage(), ex);
            }
        }
    }

    @Override
    public java.util.List<com.paipi.config.Configuration> split(int adviceNumber) throws Exception {
        String tableName = config.getString(ParameterConstant.TABLE, this.table);
        String splitPk = config.getString(ParameterConstant.SPLIT_PK);
        if (splitPk == null) {
            // 自动查主键
            try (java.sql.Connection con = getConn(); java.sql.PreparedStatement ps = con.prepareStatement(
                    "SELECT a.attname FROM pg_index i JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) WHERE i.indrelid = ?::regclass AND i.indisprimary")) {
                ps.setString(1, tableName);
                java.sql.ResultSet rs = ps.executeQuery();
                if (rs.next()) splitPk = rs.getString(1);
            }
        }
        if (splitPk == null) {
            // 没有主键，单分片
            java.util.List<com.paipi.config.Configuration> splits = new java.util.ArrayList<>();
            splits.add(config.clone());
            return splits;
        }
        // 查最小最大值
        try (java.sql.Connection con = getConn(); java.sql.Statement stmt = con.createStatement()) {
            java.sql.ResultSet rs = stmt.executeQuery("SELECT MIN(" + splitPk + "), MAX(" + splitPk + ") FROM " + tableName);
            if (!rs.next()) throw new RuntimeException("无法获取分片列范围");
            long min = rs.getLong(1), max = rs.getLong(2);
            if (min == max) { // 只有一行
                java.util.List<com.paipi.config.Configuration> splits = new java.util.ArrayList<>();
                com.paipi.config.Configuration conf = config.clone();
                String finalWhere = config.getString(ParameterConstant.WHERE, "");
                String splitWhere = splitPk + " = " + min;
                if (org.apache.commons.lang3.StringUtils.isNotBlank(splitWhere)) {
                    if (org.apache.commons.lang3.StringUtils.isBlank(finalWhere)) {
                        finalWhere = splitWhere;
                    } else {
                        finalWhere = "(" + finalWhere + ") AND (" + splitWhere + ")";
                    }
                }
                conf.set(ParameterConstant.WHERE, finalWhere);
                splits.add(conf);
                return splits;
            }
            long step = (max - min + 1) / adviceNumber;
            java.util.List<com.paipi.config.Configuration> splits = new java.util.ArrayList<>();
            for (int i = 0; i < adviceNumber; i++) {
                long start = min + i * step;
                long end = (i == adviceNumber - 1) ? max : (start + step - 1);
                com.paipi.config.Configuration conf = config.clone();
                String finalWhere = config.getString(ParameterConstant.WHERE, "");
                String splitWhere = splitPk + " >= " + start + " AND " + splitPk + " <= " + end;
                if (org.apache.commons.lang3.StringUtils.isNotBlank(splitWhere)) {
                    if (org.apache.commons.lang3.StringUtils.isBlank(finalWhere)) {
                        finalWhere = splitWhere;
                    } else {
                        finalWhere = "(" + finalWhere + ") AND (" + splitWhere + ")";
                    }
                }
                conf.set(ParameterConstant.WHERE, finalWhere);
                splits.add(conf);
            }
            return splits;
        }
    }
}
