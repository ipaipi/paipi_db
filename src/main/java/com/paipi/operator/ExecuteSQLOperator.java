package com.paipi.operator;

import com.paipi.config.Configuration;
import com.paipi.constant.CoreConstant;
import com.paipi.constant.ParameterConstant;
import com.paipi.operator.base.BaseOperator;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.SqlNodeList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;

import static com.paipi.Engine.buildDbAdapter;


@Slf4j
public class ExecuteSQLOperator extends BaseOperator {
    public ExecuteSQLOperator(Configuration originalConfig) {
        super(originalConfig);
    }

    @Override
    public void pre() throws Exception {
        String dbType = originalConfig.getString(CoreConstant.DB_JOB_CONTENT_COMMON_NAME);
        dbAdapter = buildDbAdapter(dbType, originalConfig, commonConfig);
    }

    @Override
    public void run() throws Exception {
        String sqlFilePath = commonConfig.getString(ParameterConstant.SQL_FILE, null);
        String sqlString = commonConfig.getString(ParameterConstant.SQL, null);
        String saveUrl = commonConfig.getString(ParameterConstant.SAVE_URL, null);
        String fileType = commonConfig.getString(ParameterConstant.FILE_TYPE, "csv");
        Boolean isChunk = commonConfig.getBool(ParameterConstant.IS_CHUNK, false);
        Integer chunkSize = commonConfig.getInt(ParameterConstant.CHUNK_SIZE, 1000000);
        String sep = commonConfig.getString(ParameterConstant.SEP, ",");

        final List<String> sqlList;
        String dbType = originalConfig.getString(CoreConstant.DB_JOB_CONTENT_COMMON_NAME);
        if (sqlFilePath != null && !sqlFilePath.trim().isEmpty()) {
            String sqlScript = new String(Files.readAllBytes(Paths.get(sqlFilePath)), StandardCharsets.UTF_8);
            sqlList = parseSqlStatements(sqlScript, dbType);
        } else if (sqlString != null && !sqlString.trim().isEmpty()) {
            sqlList = parseSqlStatements(sqlString, dbType);
        } else {
            sqlList = new ArrayList<>();
        }
        if (sqlList.isEmpty()) throw new IllegalArgumentException("SQL不能为空");
        if (dbAdapter.getLoginUser() == null) {
            dbAdapter.executeSQLBatch(sqlList, saveUrl, fileType, isChunk, chunkSize, sep);
        } else {
            Exception e = dbAdapter.getLoginUser().doAs((PrivilegedAction<Exception>) () -> {
                try {
                    dbAdapter.executeSQLBatch(sqlList, saveUrl, fileType, isChunk, chunkSize, sep);
                } catch (Exception ee) {
                    logger.error("", ee);
                    return ee;
                }
                return null;
            });
            if (e != null) throw e;
        }
    }

    /**
     * 使用JSqlParser安全分割SQL脚本，支持注释、复杂结构。
     */
    private List<String> parseSqlStatements(String sqlScript, String dbType) throws Exception {
        List<String> sqlList = new ArrayList<>();
        boolean useJSqlParser = false;
        Lex lex = Lex.MYSQL; // 默认
        if (dbType != null) {
            String type = dbType.trim().toLowerCase();
            if (type.contains("mysql")) {
                lex = Lex.MYSQL;
            } else if (type.contains("oracle")) {
                lex = Lex.ORACLE;
            } else if (type.contains("postgresql")) {
                lex = Lex.MYSQL;
            } else if (type.contains("sqlserver")) {
                lex = Lex.SQL_SERVER;
            } else if (type.contains("hive")) {
                // Hive直接用JSqlParser更稳妥
                useJSqlParser = true;
            }
        }
        if (!useJSqlParser) {
            org.apache.calcite.sql.parser.SqlParser.Config parserConfig = org.apache.calcite.sql.parser.SqlParser.config()
                    .withConformance(org.apache.calcite.sql.validate.SqlConformanceEnum.DEFAULT)
                    .withLex(lex);
            org.apache.calcite.sql.parser.SqlParser parser = org.apache.calcite.sql.parser.SqlParser.create(sqlScript, parserConfig);
            try {
                SqlNodeList sqlNode = parser.parseStmtList();
                if (sqlNode != null) {
                    org.apache.calcite.sql.SqlNodeList nodeList = sqlNode;
                    for (org.apache.calcite.sql.SqlNode node : nodeList) {
                        String sql = node.toString();
                        if (sql != null && !sql.trim().isEmpty()) {
                            sqlList.add(sql.trim());
                        }
                    }
                }
                return sqlList;
            } catch (org.apache.calcite.sql.parser.SqlParseException e) {
                logger.warn("Calcite解析失败，降级为JSqlParser");
                // 失败后自动降级
            }
        }
        // JSqlParser分割（兼容Hive/复杂SQL）
        try {
            net.sf.jsqlparser.statement.Statements statements = net.sf.jsqlparser.parser.CCJSqlParserUtil.parseStatements(sqlScript);
            statements.getStatements().forEach(stmt -> {
                String sql = stmt.toString().trim();
                if (!sql.isEmpty()) sqlList.add(sql);
            });
        } catch (Exception e) {
            logger.warn("JSqlParser解析失败，降级为分号分割");
            // 最后兜底
            String[] arr = sqlScript.split(";");
            for (String s : arr) {
                String line = s.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("--") || line.startsWith("#")) continue;
                sqlList.add(line);
            }
        }
        return sqlList;
    }

    @Override
    public void post() {
    }
}