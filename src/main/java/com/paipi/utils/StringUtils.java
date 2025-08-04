package com.paipi.utils;


import lombok.extern.slf4j.Slf4j;


@Slf4j
public class StringUtils {
    public static String quot(String str) {
        if (str == null) {
            return "null";
        }
        // 单引号转为两个单引号，双引号无需转义
        return "'" + str.replace("'", "''") + "'";
    }

    public static String parseSeparator(String sep) {
        if (sep == null) return ",";
        switch (sep) {
            case "\\t":
            case "tab":
                return "\t";
            case "\\n":
                return "\n";
            case "\\r":
                return "\r";
            case "comma":
                return ",";
            case "pipe":
                return "|";
            case "space":
                return " ";
            default:
                return sep;
        }
    }
}
