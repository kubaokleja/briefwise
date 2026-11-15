package com.briefwise.research.domain.model;

/**
 * What ingest did with one filing (ADR 0026): stored it for the first time, replaced its sections because the content
 * hash changed (a new extractor version), or left it as stored because the hash is the same.
 */
public enum IngestOutcome { INSERTED, REPLACED, UNCHANGED }
