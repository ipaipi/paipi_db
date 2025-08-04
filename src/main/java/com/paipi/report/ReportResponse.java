package com.paipi.report;

import lombok.Data;

@Data
public class ReportResponse<T> {

    private int code;
    private String sqlMode;
    private String commonName;
    private String readerName;
    private String writerName;
    private String msg;
    private long costTime;
    private T data;
    private String stackTrace;

    private ReportResponse(int code, String sqlMode, String msg, long costTime, T data) {
        this.code = code;
        this.msg = msg;
        this.costTime = costTime;
        this.data = data;
    }

    public static <T> ReportResponse<T> init(T data) {
        return new ReportResponse<>(Code.SUCCESS.ordinal(), "-1", "", 0, data);
    }

    public enum Code {
        /**
         * 0: 成功; 1: 失败
         */
        SUCCESS, FAILED
    }
}
