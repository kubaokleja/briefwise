"""Instruments the platform tracks. The first 11 match the Flyway seed (V4); the rest extend it (ADR 0004)."""

from typing import NamedTuple


class Instrument(NamedTuple):
    ticker: str
    name: str
    kind: str  # "STOCK" or "ETF", as in instruments.instrument_type


UNIVERSE: tuple[Instrument, ...] = (
    # Already seeded by V4
    Instrument("AAPL", "Apple Inc.", "STOCK"),
    Instrument("MSFT", "Microsoft Corporation", "STOCK"),
    Instrument("NVDA", "NVIDIA Corporation", "STOCK"),
    Instrument("AMZN", "Amazon.com, Inc.", "STOCK"),
    Instrument("JPM", "JPMorgan Chase & Co.", "STOCK"),
    Instrument("JNJ", "Johnson & Johnson", "STOCK"),
    Instrument("PG", "The Procter & Gamble Company", "STOCK"),
    Instrument("KO", "The Coca-Cola Company", "STOCK"),
    Instrument("XOM", "Exxon Mobil Corporation", "STOCK"),
    Instrument("SPY", "SPDR S&P 500 ETF Trust", "ETF"),
    Instrument("AGG", "iShares Core U.S. Aggregate Bond ETF", "ETF"),
    # Added for ~30 companies
    Instrument("GOOGL", "Alphabet Inc. Class A", "STOCK"),
    Instrument("META", "Meta Platforms, Inc.", "STOCK"),
    Instrument("TSLA", "Tesla, Inc.", "STOCK"),
    Instrument("BAC", "Bank of America Corporation", "STOCK"),
    Instrument("GS", "The Goldman Sachs Group, Inc.", "STOCK"),
    Instrument("UNH", "UnitedHealth Group Incorporated", "STOCK"),
    Instrument("LLY", "Eli Lilly and Company", "STOCK"),
    Instrument("MRK", "Merck & Co., Inc.", "STOCK"),
    Instrument("PFE", "Pfizer Inc.", "STOCK"),
    Instrument("WMT", "Walmart Inc.", "STOCK"),
    Instrument("COST", "Costco Wholesale Corporation", "STOCK"),
    Instrument("HD", "The Home Depot, Inc.", "STOCK"),
    Instrument("PEP", "PepsiCo, Inc.", "STOCK"),
    Instrument("DIS", "The Walt Disney Company", "STOCK"),
    Instrument("CVX", "Chevron Corporation", "STOCK"),
    Instrument("CAT", "Caterpillar Inc.", "STOCK"),
    Instrument("BA", "The Boeing Company", "STOCK"),
    Instrument("CSCO", "Cisco Systems, Inc.", "STOCK"),
    Instrument("ORCL", "Oracle Corporation", "STOCK"),
    Instrument("INTC", "Intel Corporation", "STOCK"),
    Instrument("V", "Visa Inc.", "STOCK"),
    # Added ETFs
    Instrument("QQQ", "Invesco QQQ Trust", "ETF"),
    Instrument("IWM", "iShares Russell 2000 ETF", "ETF"),
    Instrument("GLD", "SPDR Gold Shares", "ETF"),
    Instrument("TLT", "iShares 20+ Year Treasury Bond ETF", "ETF"),
)
