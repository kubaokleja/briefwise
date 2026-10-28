package com.briefwise.news.domain.model;

/**
 * Precision, recall and F1 of one class; {@code support} is how many cases truly have it. A class that is never
 * predicted has precision 0 (not undefined), so it pulls the macro average down instead of disappearing.
 */
public record ClassMetrics(double precision, double recall, double f1, int support, int predicted) {

    static ClassMetrics of(int truePositives, int support, int predicted) {
        double precision = predicted == 0 ? 0 : (double) truePositives / predicted;
        double recall = support == 0 ? 0 : (double) truePositives / support;
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        return new ClassMetrics(precision, recall, f1, support, predicted);
    }
}
