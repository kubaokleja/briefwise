from datetime import date

import pandas as pd
import pytest

from briefwise_quant import prices
from briefwise_quant.universe import UNIVERSE


def raw_frame(tickers: list[str], days: int = 3) -> pd.DataFrame:
    """Shape of yf.download(..., group_by="ticker"): (ticker, field) columns, date index."""
    index = pd.bdate_range("2025-01-02", periods=days, name="Date")
    columns = pd.MultiIndex.from_product([tickers, ["Open", "High", "Low", "Close", "Volume"]])
    frame = pd.DataFrame(1.0, index=index, columns=columns)
    for ticker in tickers:
        frame[(ticker, "Close")] = [100.123456789, 101.5, 102.0][:days]
        frame[(ticker, "Volume")] = [1000.0, 2000.0, 3000.0][:days]
    return frame


def test_extract_returns_date_close_volume():
    result = prices.extract(raw_frame(["AAPL"]), "AAPL")

    assert list(result.columns) == ["date", "close", "volume"]
    assert result["date"].tolist() == ["2025-01-02", "2025-01-03", "2025-01-06"]
    assert result["close"].tolist() == [100.123457, 101.5, 102.0]
    assert result["volume"].tolist() == [1000, 2000, 3000]


def test_extract_drops_rows_without_close_and_defaults_missing_volume_to_zero():
    raw = raw_frame(["AAPL"])
    raw.loc[raw.index[0], ("AAPL", "Close")] = float("nan")
    raw.loc[raw.index[1], ("AAPL", "Volume")] = float("nan")

    result = prices.extract(raw, "AAPL")

    assert result["date"].tolist() == ["2025-01-03", "2025-01-06"]
    assert result["volume"].tolist() == [0, 3000]


def test_extract_unknown_ticker_is_empty():
    assert prices.extract(raw_frame(["AAPL"]), "MSFT").empty


def test_write_snapshot_writes_one_csv_per_ticker(tmp_path):
    rows = prices.write_snapshot(raw_frame(["AAPL", "MSFT"]), ["AAPL", "MSFT", "NVDA"], tmp_path)

    assert rows == {"AAPL": 3, "MSFT": 3}
    assert (tmp_path / "AAPL.csv").read_text().splitlines()[0] == "date,close,volume"
    assert not (tmp_path / "NVDA.csv").exists()


def test_refresh_uses_the_universe_and_never_the_network(tmp_path):
    tickers = [instrument.ticker for instrument in UNIVERSE]
    calls = []

    def fake_fetcher(requested, start, end):
        calls.append((requested, start, end))
        return raw_frame(requested)

    rows = prices.refresh(date(2025, 1, 1), date(2025, 2, 1), tmp_path, fetcher=fake_fetcher)

    assert calls == [(tickers, date(2025, 1, 1), date(2025, 2, 1))]
    assert set(rows) == set(tickers)


def test_refresh_fails_loudly_when_a_ticker_has_no_data(tmp_path):
    with pytest.raises(RuntimeError, match="MSFT"):
        prices.refresh(
            date(2025, 1, 1),
            date(2025, 2, 1),
            tmp_path,
            fetcher=lambda tickers, start, end: raw_frame([t for t in tickers if t != "MSFT"]),
        )


def test_universe_has_unique_tickers_and_known_kinds():
    tickers = [instrument.ticker for instrument in UNIVERSE]
    assert len(tickers) == len(set(tickers))
    assert {instrument.kind for instrument in UNIVERSE} == {"STOCK", "ETF"}
    assert sum(i.kind == "STOCK" for i in UNIVERSE) == 30
