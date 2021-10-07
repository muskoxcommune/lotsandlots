package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.ApiConfig;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.oauth.SecurityContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EtradePortfolioDataFetcher implements EtradeApiClient, Runnable {

    private static final ApiConfig API = EtradeRestTemplateFactory.getClient().getApiConfig();
    private static final Logger LOG = LoggerFactory.getLogger(EtradePortfolioDataFetcher.class);
    private static final Map<String, List<PositionLotsResponse.PositionLot>> LOTS_CACHE = new HashMap<>();
    private static final Map<String, PortfolioResponse.Position> POSITION_CACHE = new HashMap<>();
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    private static EtradePortfolioDataFetcher DATA_FETCHER = null;
    private static PortfolioResponse.Totals TOTALS = new PortfolioResponse.Totals();

    private EtradePortfolioDataFetcher() {
        SCHEDULED_EXECUTOR.scheduleAtFixedRate(this, 0, 1, TimeUnit.MINUTES);
    }

    public int aggregateLotCount() {
        int lotCount = 0;
        for (List<PositionLotsResponse.PositionLot> lots : LOTS_CACHE.values()) {
            lotCount += lots.size();
        }
        return lotCount;
    }

    public static void destroy() {
        SCHEDULED_EXECUTOR.shutdown();
    }

    private void fetchPortfolioResponse(SecurityContext securityContext, String pageNumber) {
        Message portfolioMessage = new Message();
        portfolioMessage.setRequiresOauth(true);
        portfolioMessage.setHttpMethod("GET");
        portfolioMessage.setUrl(API.getBaseUrl() + API.getPortfolioUri());
        String portfolioQueryString = API.getPortfolioQueryString();
        if (!StringUtils.isBlank(pageNumber)) {
            portfolioQueryString += "&pageNumber=" + pageNumber;
        }
        portfolioMessage.setQueryString(portfolioQueryString);
        try {
            setOAuthHeader(securityContext, portfolioMessage);
            ResponseEntity<PortfolioResponse> portfolioResponseResponseEntity = EtradeRestTemplateFactory
                    .getClient()
                    .newCustomRestTemplate()
                    .execute(portfolioMessage, PortfolioResponse.class);
            PortfolioResponse portfolioResponse = portfolioResponseResponseEntity.getBody();
            if (portfolioResponse == null) {
                throw new RuntimeException("Empty response");
            } else {
                TOTALS = portfolioResponse.getTotals(); // Update portfolio totals
                PortfolioResponse.AccountPortfolio accountPortfolio = portfolioResponse.getAccountPortfolio();
                for (PortfolioResponse.Position position : accountPortfolio.getPositionList()) {
                    String symbolDescription = position.getSymbolDescription();
                    if (POSITION_CACHE.containsKey(symbolDescription)) {
                        // Compare retrieved data with cached data.
                        // If quantity owned changes, refresh lots.
                        PortfolioResponse.Position cachedPosition = POSITION_CACHE.get(symbolDescription);
                        if (!cachedPosition.getQuantity().equals(position.getQuantity())) {
                            // Fetch lots.
                            fetchPositionLotsResponse(securityContext, symbolDescription, position);
                        }
                    } else {
                        // Fetch lots.
                        fetchPositionLotsResponse(securityContext, symbolDescription, position);
                    }
                    // - Update cache
                    POSITION_CACHE.put(symbolDescription, position);
                }
                if (accountPortfolio.hasNextPageNo()) {
                    fetchPortfolioResponse(securityContext, accountPortfolio.getNextPageNo());
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to fetch portfolio data", e);
        }
    }

    private void fetchPositionLotsResponse(
            SecurityContext securityContext, String symbolDescription, PortfolioResponse.Position position) {
        Message lotsMessage = new Message();
        lotsMessage.setRequiresOauth(true);
        lotsMessage.setHttpMethod("GET");
        lotsMessage.setUrl(position.getLotsDetails());
        try {
            setOAuthHeader(securityContext, lotsMessage);
            ResponseEntity<PositionLotsResponse> positionLotsResponseResponseEntity = EtradeRestTemplateFactory
                    .getClient()
                    .newCustomRestTemplate()
                    .execute(lotsMessage, PositionLotsResponse.class);
            PositionLotsResponse positionLotsResponse = positionLotsResponseResponseEntity.getBody();
            if (positionLotsResponse == null) {
                throw new RuntimeException("Empty response");
            } else {
                Integer lotCount = positionLotsResponse.getPositionLots().size();
                for (PositionLotsResponse.PositionLot lot : positionLotsResponse.getPositionLots()) {
                    lot.setSymbol(symbolDescription);
                    lot.setTotalLotCount(lotCount);
                    lot.setTotalPositionCost(position.getTotalCost());
                    lot.setPositionPctOfPortfolio(position.getPctOfPortfolio());
                    lot.setTargetPrice(lot.getPrice() * 1.03F);
                }
                LOTS_CACHE.put(symbolDescription, positionLotsResponse.getPositionLots());
            }
        } catch (Exception e) {
            LOG.error("Failed to fetch lots data", e);
        }
    }

    public static Map<String, List<PositionLotsResponse.PositionLot>> getLotsCache() {
        if (LOTS_CACHE.isEmpty()) {
            SCHEDULED_EXECUTOR.submit(DATA_FETCHER);
        }
        return LOTS_CACHE;
    }

    public static Map<String, PortfolioResponse.Position> getPositionCache() {
        if (POSITION_CACHE.isEmpty()) {
            SCHEDULED_EXECUTOR.submit(DATA_FETCHER);
        }
        return POSITION_CACHE;
    }

    public static void init() {
        if (DATA_FETCHER == null) {
            DATA_FETCHER = new EtradePortfolioDataFetcher();
        }
    }

    @Override
    public void run() {
        SecurityContext securityContext = EtradeRestTemplateFactory.getClient().getSecurityContext();
        if (!securityContext.isInitialized()) {
            LOG.warn("SecurityContext not initialized, please go to /etrade/authorize");
            return;
        }
        long timeStartedMillis = System.currentTimeMillis();
        fetchPortfolioResponse(securityContext, null);
        LOG.info("Updated caches in {}ms, positions={} lots={}",
                System.currentTimeMillis() - timeStartedMillis, POSITION_CACHE.size(), aggregateLotCount());
    }
}
