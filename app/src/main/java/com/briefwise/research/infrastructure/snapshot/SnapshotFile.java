package com.briefwise.research.infrastructure.snapshot;

import com.briefwise.research.domain.model.AccessionNo;
import com.briefwise.research.domain.model.Cik;
import com.briefwise.research.domain.model.FetchedFiling;
import com.briefwise.research.domain.model.FilingRef;
import com.briefwise.research.domain.model.FilingSection;
import com.briefwise.research.domain.model.FilingType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One file of the snapshot, {@code {TICKER}/{accession}.json} (ADR 0026): the listing, provenance and extracted
 * sections, no raw HTML. Dates are ISO strings and {@code reportDate} may be null; {@code items} are sorted so the
 * same filing always gives the same bytes. {@code contentSha256} is checked on read, so a hand-edited text is caught.
 * Bump {@link #FORMAT_VERSION} on an incompatible change.
 */
record SnapshotFile(int formatVersion, String ticker, String accessionNo, String cik, String form, String filedAt,
                    String reportDate, String primaryDocument, List<String> items, String sourceUrl,
                    String extractorVersion, String contentSha256, List<Section> sections) {

    static final int FORMAT_VERSION = 1;

    record Section(String itemId, String title, String text) {
    }

    static SnapshotFile of(String ticker, FetchedFiling filing) {
        FilingRef ref = filing.ref();
        return new SnapshotFile(FORMAT_VERSION, ticker, ref.accessionNo().value(), ref.cik().value(),
                ref.type().form(), ref.filedAt().toString(), ref.reportDate().map(LocalDate::toString).orElse(null),
                ref.primaryDocument(), ref.items().stream().sorted().toList(), filing.sourceUrl(),
                filing.extractorVersion(), filing.contentSha256(),
                filing.sections().stream().map(s -> new Section(s.itemId(), s.title(), s.text())).toList());
    }

    FilingRef ref() {
        FilingType type = FilingType.fromForm(form)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported form '" + form + "'"));
        return new FilingRef(new AccessionNo(accessionNo), new Cik(cik), type, LocalDate.parse(filedAt),
                Optional.ofNullable(reportDate).filter(d -> !d.isBlank()).map(LocalDate::parse), primaryDocument,
                items == null ? Set.of() : Set.copyOf(items));
    }

    /**
     * @throws IllegalStateException if the format is unknown or the sections do not match {@code contentSha256}
     */
    FetchedFiling filing() {
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalStateException("Snapshot " + accessionNo + " has format " + formatVersion
                    + ", expected " + FORMAT_VERSION);
        }
        List<FilingSection> parsed = sections == null ? List.of()
                : sections.stream().map(s -> new FilingSection(s.itemId(), s.title(), s.text())).toList();
        FetchedFiling filing = new FetchedFiling(ref(), sourceUrl, extractorVersion, parsed);
        if (!filing.contentSha256().equals(contentSha256)) {
            throw new IllegalStateException("Snapshot " + accessionNo + " was changed: content SHA-256 "
                    + filing.contentSha256() + " does not match the stored " + contentSha256);
        }
        return filing;
    }
}
