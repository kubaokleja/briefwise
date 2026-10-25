package com.briefwise.news.infrastructure.compliance;

import com.briefwise.compliance.ComplianceFacade;
import com.briefwise.news.domain.model.NewsItem;
import com.briefwise.news.domain.port.UntrustedContentScreen;
import org.springframework.stereotype.Component;

/** Screens exactly the third-party fields the classification prompt contains: headline, summary and source. */
@Component
class FacadeContentScreen implements UntrustedContentScreen {

    static final String SOURCE = "news";

    private final ComplianceFacade compliance;

    FacadeContentScreen(ComplianceFacade compliance) {
        this.compliance = compliance;
    }

    @Override
    public boolean isSuspected(NewsItem item) {
        String text = item.headline() + "\n" + item.summary() + "\n" + item.source();
        return compliance.screenUntrustedContent(item.id(), SOURCE, text).suspected();
    }
}
