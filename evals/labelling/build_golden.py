"""Build evals/sentiment_golden.csv from the golden rows of headlines_to_label.csv (stdlib only; see README.md).

Usage: python build_golden.py <headlines_to_label.csv> <system.md> <sentiment_golden.csv>

- Keeps `split=golden` rows only and the columns the eval reads; label columns must be filled.
- Repairs mojibake from the 2026-10-23 export (UTF-8 bytes decoded as Latin-1, e.g. "weekâ\\x80\\x99s"): only runs
  that decode back to valid UTF-8 are replaced, every other character is left alone. Repaired ids are printed.
- Leakage guard: fails if a golden headline appears in the prompt's few-shot block (system.md). Few-shot rows of the
  labelling file are never read.
"""
import csv
import re
import sys

source_path, system_path, target_path = sys.argv[1], sys.argv[2], sys.argv[3]

COLUMNS = ["id", "sample", "tickers", "source", "headline", "summary", "sentiment", "relevance", "event_type"]
LABELS = {
    "sentiment": {"NEGATIVE", "NEUTRAL", "POSITIVE"},
    "relevance": {"LOW", "MEDIUM", "HIGH"},
    "event_type": {"EARNINGS", "GUIDANCE", "M_AND_A", "REGULATORY", "LITIGATION", "MANAGEMENT", "MACRO", "PRODUCT",
                   "ANALYST", "OTHER"},
}
# A UTF-8 lead byte followed by 1-3 continuation bytes, all read as Latin-1 characters.
MOJIBAKE = re.compile("[Â-ô][\u0080-¿]{1,3}")


def repair(text):
    def decode(match):
        try:
            return match.group().encode("latin-1").decode("utf-8")
        except UnicodeDecodeError:
            return match.group()

    return MOJIBAKE.sub(decode, text)


with open(source_path, encoding="utf-8-sig", newline="") as f:
    rows = [row for row in csv.DictReader(f) if row["split"].strip() == "golden"]
with open(system_path, encoding="utf-8") as f:
    system = f.read().casefold()

golden = []
problems = []
for row in rows:
    out = {column: row[column].strip() for column in COLUMNS}
    for column in ("headline", "summary"):
        fixed = repair(out[column])
        if fixed != out[column]:
            print(f"repaired {column} of {out['id']}")
            out[column] = fixed
    for column, allowed in LABELS.items():
        if out[column] not in allowed:
            problems.append(f"{out['id']}: {column}={out[column]!r}")
    if out["headline"].casefold() in system:
        problems.append(f"{out['id']}: headline is in the few-shot examples (leakage)")
    golden.append(out)

if problems:
    sys.exit("Golden set not written:\n  " + "\n  ".join(problems))

with open(target_path, "w", encoding="utf-8", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=COLUMNS, lineterminator="\n")
    writer.writeheader()
    writer.writerows(golden)
print(f"wrote {len(golden)} golden rows to {target_path}")
