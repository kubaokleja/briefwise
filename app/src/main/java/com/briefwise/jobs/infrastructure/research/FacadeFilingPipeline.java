package com.briefwise.jobs.infrastructure.research;

import com.briefwise.jobs.domain.port.FilingPipeline;
import com.briefwise.research.ResearchFacade;
import com.briefwise.research.ResearchFacade.FailedFilingView;
import com.briefwise.research.ResearchFacade.FilingIngestSummaryView;
import com.briefwise.shared.domain.model.Ticker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class FacadeFilingPipeline implements FilingPipeline {

    private final ResearchFacade research;

    FacadeFilingPipeline(ResearchFacade research) {
        this.research = research;
    }

    @Override
    public Map<String, Object> ingestHeld() {
        FilingIngestSummaryView s = research.ingestHeld();
        long failedListings = s.failed().stream().filter(f -> f.accessionNo().isEmpty()).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companies", s.companies().size());
        result.put("skippedTickers", s.skipped().stream().map(Ticker::symbol).toList());
        result.put("selected", s.selected());
        result.put("inserted", s.inserted());
        result.put("replaced", s.replaced());
        result.put("unchanged", s.unchanged());
        result.put("sections", s.sections());
        result.put("suspectedSections", s.suspectedSections());
        result.put("attempted", s.selected() + (int) failedListings);
        result.put("failed", s.failed().size());
        result.put("failedFilings", failures(s.failed()));
        return result;
    }

    /** {@code TICKER accession: reason}, or {@code TICKER listing: reason} when the listing itself failed. */
    private static List<String> failures(List<FailedFilingView> failed) {
        return failed.stream()
                .map(f -> f.ticker().symbol() + " " + f.accessionNo().orElse("listing") + ": " + f.reason())
                .toList();
    }
}
