package com.meekdev.moud.script.api;

import com.meekdev.moud.core.query.Rewind;

public interface HistoryRef {

    Rewind rewind();

    double viewTime(String player);
}
