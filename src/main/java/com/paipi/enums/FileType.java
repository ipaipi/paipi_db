package com.paipi.enums;

import lombok.Getter;


@Getter
public enum FileType {
    CSV("csv"),
    PARQUET("parquet"),
    JSON("json"),
    ORC("ORC");
    private final String name;

    FileType(String name) {
        this.name = name;
    }
}
