package com.paipi.filewriter.parquet;

import com.paipi.common.column.ColumnStruct;
import lombok.Getter;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.reflect.ReflectData;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Getter
public class AllColumnParquetWriter implements Closeable {

    private final Path path;
    private final Schema schema;
    private final List<ColumnStruct> columnMetas;
    private final ParquetWriter<Object> parquetWriter;

    public AllColumnParquetWriter(List<ColumnStruct> columnMetas, Path path, String recordName) throws IOException {
        this.columnMetas = columnMetas;
        this.path = path;
        this.schema = parseSchema(recordName);
        this.parquetWriter = buildParquetWriter();
    }

    public AllColumnParquetWriter(List<ColumnStruct> columnMetas, String path) throws IOException {
        this(columnMetas, new Path(path), RandomStringUtils.randomAlphabetic(8));
    }

    private Schema getColTypeSchema(ColumnStruct columnMeta) {
        Schema.Type targetType = SchemaTypeMapper.getBy(columnMeta.getType());
        return Schema.createUnion(Arrays.asList(
                Schema.create(targetType),
                Schema.create(Schema.Type.NULL)
        ));
    }

    private Schema parseSchema(String recordName) {
        List<Schema.Field> fields = new ArrayList<>();
        for (ColumnStruct columnMeta : columnMetas) {
            Schema typeSchema = getColTypeSchema(columnMeta);
            Schema.Field field = null;
            try {
                field = new Schema.Field(
                        columnMeta.getColumnName(), typeSchema, null, null
                );
            } catch (SchemaParseException parseException) {
                throw new SchemaParseException("列名" + columnMeta.getColumnName() + "不符合写parquet的Schema规范");
            }
            fields.add(field);
        }

        Schema recordSchema = Schema.createRecord(
                recordName, null, "", false
        );
        recordSchema.setFields(fields);

        return recordSchema;
    }

    private ParquetWriter<Object> buildParquetWriter() throws IOException {
        return AvroParquetWriter.builder(path)
                .withSchema(schema)
                .withDataModel(ReflectData.get())
                .withConf(new Configuration())
                .withCompressionCodec(CompressionCodecName.UNCOMPRESSED)
                .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                .build();
    }

    public GenericRecord createGenericRecord() {
        return new GenericData.Record(schema);
    }

    public void writeRecord(GenericRecord genericRecord) throws IOException {
        parquetWriter.write(genericRecord);
    }

    public void writeRecords(List<GenericRecord> genericRecords) throws IOException {
        for (GenericRecord genericRecord : genericRecords) {
            parquetWriter.write(genericRecord);
        }
    }

    @Override
    public void close() throws IOException {
        if (parquetWriter != null) {
            parquetWriter.close();
        }
    }
}
