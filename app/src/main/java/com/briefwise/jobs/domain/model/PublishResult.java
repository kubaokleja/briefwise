package com.briefwise.jobs.domain.model;

/** One relay pass as the jobs see it: rows sent, rows still pending, and whether the broker refused. */
public record PublishResult(int published, int pending, boolean brokerUnavailable) {}
