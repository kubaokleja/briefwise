package com.briefwise.news.domain.port;

import com.briefwise.news.domain.model.SummaryPoint;
import com.briefwise.news.domain.model.SummaryViolation;
import java.util.List;

/** Outbound port: the compliance check for summary text (implemented over the compliance facade). */
public interface SummaryCompliance {

    /** The fixed disclaimer, appended in code to every summary. */
    String disclaimer();

    /** Rules the points (shown with {@code disclaimer}) break; empty if compliant. */
    List<SummaryViolation> check(List<SummaryPoint> points, String disclaimer);
}
