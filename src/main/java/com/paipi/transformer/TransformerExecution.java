package com.paipi.transformer;

import com.paipi.common.channel.Record;

import java.util.List;

/**
 * Transformer链执行器，依次执行所有transformer。
 */
public class TransformerExecution {
    private final List<Transformer> transformers;

    public TransformerExecution(List<Transformer> transformers) {
        this.transformers = transformers;
    }

    public Record execute(Record record) {
        Record current = record;
        for (Transformer t : transformers) {
            if (current == null) break;
            current = t.transform(current);
        }
        return current;
    }
}