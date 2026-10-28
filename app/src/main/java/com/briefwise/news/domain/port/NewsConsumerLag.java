package com.briefwise.news.domain.port;

/** Outbound port: how many {@code news.raw} messages the news consumer group has not consumed yet. */
public interface NewsConsumerLag {

    /**
     * Sum over partitions of end offset minus committed offset (a partition without a commit counts from 0).
     *
     * @throws IllegalStateException if the broker cannot be asked
     */
    long lag();
}
