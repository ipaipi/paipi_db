package com.paipi.report;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class ReportContext {

    private Object preCheckRes;
    private Object postCheckRes;
    private Object executeReport;

    public <T> T getPreCheckRes(Class<T> cls) {
        return cls.cast(preCheckRes);
    }

    public <T> T getPostCheckRes(Class<T> cls) {
        return cls.cast(postCheckRes);
    }

    public <T> T getExecuteReport(Class<T> cls) {
        return cls.cast(executeReport);
    }
}
