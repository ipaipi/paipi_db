package com.paipi.filewriter.parquet;

import com.paipi.common.column.Column;
import org.apache.avro.Schema;


public class SchemaTypeMapper {

    private SchemaTypeMapper() {
    }

    public static Schema.Type getBy(Column.Type type) {
        if (type == null) return Schema.Type.STRING;
        switch (type) {
            case INT:
                return Schema.Type.INT;
            case LONG:
                return Schema.Type.LONG;
            case DOUBLE:
                return Schema.Type.DOUBLE;
            case STRING:
                return Schema.Type.STRING;
            case BOOL:
                return Schema.Type.BOOLEAN;
            case DATE:
                return Schema.Type.STRING; // Parquet无原生DATE，通常用String或Long
            case BYTES:
                return Schema.Type.BYTES;
            default:
                return Schema.Type.STRING;
        }
    }

}
