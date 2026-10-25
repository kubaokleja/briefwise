package com.briefwise.compliance.application;

import com.briefwise.compliance.domain.event.InjectionSuspected;
import com.briefwise.compliance.domain.model.InjectionRules;
import com.briefwise.compliance.domain.model.ScreeningResult;
import com.briefwise.compliance.domain.port.ContentScreening;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class ContentScreeningService implements ContentScreening {

    private static final Logger log = LoggerFactory.getLogger(ContentScreeningService.class);

    private final ApplicationEventPublisher events;
    private final Clock clock;

    ContentScreeningService(ApplicationEventPublisher events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Override
    public ScreeningResult screen(UUID contentId, String source, String text) {
        ScreeningResult result = new ScreeningResult(InjectionRules.matching(InjectionRules.M1, text));
        if (result.suspected()) {
            log.warn("Possible prompt injection in {} {}: rules {}", source, contentId, result.matchedRules());
            events.publishEvent(new InjectionSuspected(contentId, source, result.matchedRules(), clock.instant()));
        }
        return result;
    }
}
