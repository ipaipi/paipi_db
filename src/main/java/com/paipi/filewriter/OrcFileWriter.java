package com.paipi.filewriter;

import com.paipi.common.column.ColumnStruct;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.DoubleColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.OrcFile;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

public class OrcFileWriter implements FileWriter {
    private final Writer writer;
    private final VectorizedRowBatch batch;
    private final List<String> colNames;
    private final List<ColumnStruct> colStructs;
    private final TypeDescription schema;
    private final int colCount;
    private final Charset encoding;

    public OrcFileWriter(String filePath, List<String> colNames, List<ColumnStruct> colStructs, String compress, String encoding) throws IOException {
        this.colNames = colNames;
        this.colStructs = colStructs;
        this.colCount = colNames.size();
        this.schema = buildOrcSchema(colStructs);
        Configuration conf = new Configuration();

        // 确保目标文件不存在
        Path orcPath = new Path(filePath);
        FileSystem fs = orcPath.getFileSystem(conf);
        if (fs.exists(orcPath)) {
            fs.delete(orcPath, false);
        }

        // 设置 ORC Writer 选项（强制覆盖）
        OrcFile.WriterOptions opts = OrcFile.writerOptions(conf)
                .setSchema(schema);

        if (compress != null && !"NONE".equalsIgnoreCase(compress)) {
            try {
                opts.compress(org.apache.orc.CompressionKind.valueOf(compress.toUpperCase()));
            } catch (Exception e) {
                throw new IOException("ORC compress参数不合法: " + compress, e);
            }
        }

        this.writer = OrcFile.createWriter(orcPath, opts);
        this.batch = schema.createRowBatch();
        this.encoding = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
    }

    private TypeDescription buildOrcSchema(List<ColumnStruct> colStructs) {
        TypeDescription schema = TypeDescription.createStruct();
        for (ColumnStruct col : colStructs) {
            switch (col.getType()) {
                case INT:
                case LONG:
                case BOOL:
                case DATE:
                    schema.addField(col.getColumnName(), TypeDescription.createLong());
                    break;
                case DOUBLE:
                    schema.addField(col.getColumnName(), TypeDescription.createDouble());
                    break;
                case STRING:
                case BYTES:
                default:
                    schema.addField(col.getColumnName(), TypeDescription.createString());
            }
        }
        return schema;
    }

    @Override
    public void writeHeader(List<String> colNames) throws IOException {
        // ORC 不需要表头
    }

    @Override
    public void writeRow(List<Object> row) throws IOException {
        int rowIdx = batch.size++;
        for (int i = 0; i < colCount; i++) {
            Object val = i < row.size() ? row.get(i) : null;
            try {
                switch (colStructs.get(i).getType()) {
                    case DATE: {
                        LongColumnVector datecv = (LongColumnVector) batch.cols[i];
                        if (val == null) {
                            datecv.vector[rowIdx] = 0L;
                        } else if (val instanceof Number) {
                            datecv.vector[rowIdx] = ((Number) val).longValue();
                        } else {
                            String str = val.toString();
                            try {
                                datecv.vector[rowIdx] = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(str).getTime();
                            } catch (ParseException e) {
                                throw new IOException("日期字段解析失败: " + str, e);
                            }
                        }
                        break;
                    }
                    case INT:
                    case LONG: {
                        LongColumnVector intcv = (LongColumnVector) batch.cols[i];
                        intcv.vector[rowIdx] = val == null ? 0L : Long.parseLong(val.toString());
                        break;
                    }
                    case BOOL: {
                        LongColumnVector bcv = (LongColumnVector) batch.cols[i];
                        bcv.vector[rowIdx] = val == null ? 0L : ("true".equalsIgnoreCase(val.toString()) || "1".equals(val.toString()) ? 1L : 0L);
                        break;
                    }
                    case DOUBLE: {
                        DoubleColumnVector dcv = (DoubleColumnVector) batch.cols[i];
                        dcv.vector[rowIdx] = val == null ? Double.NaN : Double.parseDouble(val.toString());
                        break;
                    }
                    case STRING:
                    case BYTES:
                    default: {
                        BytesColumnVector strcv = (BytesColumnVector) batch.cols[i];
                        byte[] bytes = val == null ? new byte[0] : val.toString().getBytes(encoding);
                        strcv.setVal(rowIdx, bytes, 0, bytes.length);
                        break;
                    }
                }
            } catch (Exception e) {
                throw new IOException("ORC写入第" + i + "列类型转换异常，值=" + val + ", 类型=" + colStructs.get(i).getType(), e);
            }
        }
        if (batch.size == VectorizedRowBatch.DEFAULT_SIZE) {
            writer.addRowBatch(batch);
            batch.reset();
        }
    }

    @Override
    public void writeRows(List<List<Object>> rows) throws IOException {
        for (List<Object> row : rows) {
            writeRow(row);
        }
    }

    @Override
    public void close() throws IOException {
        if (batch.size > 0) {
            writer.addRowBatch(batch);
            batch.reset();
        }
        writer.close();
    }
} 