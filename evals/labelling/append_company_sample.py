"""Append the stratified "company" sample to headlines_to_label.csv (stdlib only; see README.md).

Usage: python append_company_sample.py <candidates.csv> <headlines_to_label.csv> [per_ticker]

Rows already in the labelling file are skipped, so labelled rows are never touched or re-split. Existing rows
without a `sample` value are marked `random`. New rows alternate golden/golden/golden/fewshot (3:1, like the
random export) and get empty label columns.
"""
import csv
import sys

candidates_path, target_path = sys.argv[1], sys.argv[2]
per_ticker = int(sys.argv[3]) if len(sys.argv) > 3 else 16

with open(target_path, encoding="utf-8-sig", newline="") as f:
    reader = csv.DictReader(f)
    fields = list(reader.fieldnames)
    rows = list(reader)
if "sample" not in fields:
    fields.insert(1, "sample")
for row in rows:
    row["split"] = row["split"].strip()
    row["sample"] = row.get("sample") or "random"
known = {row["id"] for row in rows}

with open(candidates_path, encoding="utf-8-sig", newline="") as f:
    candidates = [c for c in csv.DictReader(f) if c["id"] not in known]
candidates.sort(key=lambda c: (c["stratum"], int(c["rank"])))

taken = {}
added = []
for c in candidates:
    if taken.get(c["stratum"], 0) >= per_ticker:
        continue
    taken[c["stratum"]] = taken.get(c["stratum"], 0) + 1
    new = {field: "" for field in fields}
    new.update({k: c[k] for k in ("id", "tickers", "published_at", "source", "headline", "summary", "url")})
    new["sample"] = "company"
    new["split"] = "fewshot" if len(added) % 4 == 3 else "golden"
    added.append(new)

with open(target_path, "w", encoding="utf-8", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=fields)
    writer.writeheader()
    writer.writerows(rows + added)
print(f"appended {len(added)} rows: {taken}")
