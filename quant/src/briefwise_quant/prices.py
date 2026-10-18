"""Refresh the price snapshot (ADR 0004).

Fetches daily closes from Yahoo Finance via yfinance and writes one CSV per ticker (``date,close,volume``) to
``data/prices/``. Only this refresh step touches the network; tests, CI and the demo read the snapshot.
``close`` is the adjusted close (splits and dividends), which is what return and risk calculations need.
"""

import argparse
import sys
from collections.abc import Callable, Iterable
from datetime import date
from pathlib import Path

import pandas as pd

from briefwise_quant.universe import UNIVERSE

DEFAULT_OUT = Path(__file__).resolve().parents[3] / "data" / "prices"
Fetcher = Callable[[list[str], date, date], pd.DataFrame]


def fetch_yahoo(tickers: list[str], start: date, end: date) -> pd.DataFrame:
    """Download adjusted daily bars. Returns a frame with (ticker, field) columns."""
    import yfinance as yf

    return yf.download(
        tickers,
        start=start.isoformat(),
        end=end.isoformat(),
        auto_adjust=True,
        group_by="ticker",
        progress=False,
        threads=True,
    )


def extract(raw: pd.DataFrame, ticker: str) -> pd.DataFrame:
    """Reduce a downloaded frame to ``date, close, volume`` for one ticker (empty if there is no data)."""
    if ticker not in raw.columns.get_level_values(0):
        return pd.DataFrame(columns=["date", "close", "volume"])
    bars = raw[ticker][["Close", "Volume"]].dropna(subset=["Close"])
    frame = pd.DataFrame(
        {
            "date": pd.to_datetime(bars.index).strftime("%Y-%m-%d"),
            "close": bars["Close"].round(6).to_numpy(),
            "volume": bars["Volume"].fillna(0).astype("int64").to_numpy(),
        }
    )
    return frame[frame["close"] > 0].reset_index(drop=True)


def write_snapshot(raw: pd.DataFrame, tickers: Iterable[str], out_dir: Path) -> dict[str, int]:
    """Write one CSV per ticker. Returns rows written per ticker; tickers without data are not written."""
    out_dir.mkdir(parents=True, exist_ok=True)
    rows: dict[str, int] = {}
    for ticker in tickers:
        frame = extract(raw, ticker)
        if frame.empty:
            continue
        frame.to_csv(out_dir / f"{ticker}.csv", index=False, lineterminator="\n")
        rows[ticker] = len(frame)
    return rows


def refresh(start: date, end: date, out_dir: Path, fetcher: Fetcher = fetch_yahoo) -> dict[str, int]:
    tickers = [instrument.ticker for instrument in UNIVERSE]
    raw = fetcher(tickers, start, end)
    rows = write_snapshot(raw, tickers, out_dir)
    missing = sorted(set(tickers) - set(rows))
    if missing:
        raise RuntimeError(f"No data returned for: {', '.join(missing)}")
    return rows


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--start", type=date.fromisoformat, required=True)
    parser.add_argument("--end", type=date.fromisoformat, required=True,
                        help="exclusive; fix it for reproducible snapshots")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args(argv)
    rows = refresh(args.start, args.end, args.out)
    print(f"Wrote {len(rows)} files, {sum(rows.values())} rows to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
