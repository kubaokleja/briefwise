package com.briefwise.market.infrastructure.web;

import com.briefwise.market.MarketFacade;
import com.briefwise.market.MarketFacade.DailyMoveView;
import com.briefwise.market.MarketFacade.PricePointView;
import com.briefwise.shared.domain.model.Ticker;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Inbound adapter: calls the facade only, no business logic. */
@RestController
@RequestMapping("/api/market/instruments/{ticker}")
class MarketController {

    private final MarketFacade facade;

    MarketController(MarketFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/prices")
    List<PricePointResponse> prices(
            @PathVariable String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return facade.getPriceHistory(new Ticker(ticker), from, to).stream().map(PricePointResponse::from).toList();
    }

    @GetMapping("/daily-move")
    DailyMoveResponse dailyMove(@PathVariable String ticker) {
        return DailyMoveResponse.from(facade.getDailyMove(new Ticker(ticker)));
    }

    record PricePointResponse(LocalDate date, BigDecimal close, long volume) {
        static PricePointResponse from(PricePointView view) {
            return new PricePointResponse(view.date(), view.close(), view.volume());
        }
    }

    record DailyMoveResponse(String ticker, LocalDate date, BigDecimal close, BigDecimal previousClose,
                             BigDecimal change, BigDecimal changePercent) {
        static DailyMoveResponse from(DailyMoveView view) {
            return new DailyMoveResponse(view.ticker().symbol(), view.date(), view.close(), view.previousClose(),
                    view.change(), view.changePercent());
        }
    }
}
