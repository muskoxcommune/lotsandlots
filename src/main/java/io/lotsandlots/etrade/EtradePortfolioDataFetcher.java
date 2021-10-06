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
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    private static EtradePortfolioDataFetcher DATA_FETCHER = null;

    private final Map<String, List<PositionLotsResponse.PositionLot>> lotsCache = new HashMap<>();
    private final Map<String, PortfolioResponse.Position> positionCache = new HashMap<>();

    private PortfolioResponse.Totals totals;

    private EtradePortfolioDataFetcher() {
        SCHEDULED_EXECUTOR.scheduleAtFixedRate(this, 0, 1, TimeUnit.MINUTES);
    }

    public int aggregateLotCount() {
        int lotCount = 0;
        for (List<PositionLotsResponse.PositionLot> lots : lotsCache.values()) {
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
                totals = portfolioResponse.getTotals(); // Update portfolio totals
                PortfolioResponse.AccountPortfolio accountPortfolio = portfolioResponse.getAccountPortfolio();
                for (PortfolioResponse.Position position : accountPortfolio.getPositionList()) {
                    String symbolDescription = position.getSymbolDescription();
                    if (positionCache.containsKey(symbolDescription)) {
                        // Compare retrieved data with cached data.
                        // If quantity owned changes, refresh lots.
                        PortfolioResponse.Position cachedPosition = positionCache.get(symbolDescription);
                        if (!cachedPosition.getQuantity().equals(position.getQuantity())) {
                            // Fetch lots.
                            fetchPositionLotsResponse(securityContext, symbolDescription, position.getLotsDetails());
                        }
                    } else {
                        // Fetch lots.
                        fetchPositionLotsResponse(securityContext, symbolDescription, position.getLotsDetails());
                    }
                    // - Update cache
                    positionCache.put(symbolDescription, position);
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
            SecurityContext securityContext, String symbolDescription, String lotsDetailsUrl) {
        Message lotsMessage = new Message();
        lotsMessage.setRequiresOauth(true);
        lotsMessage.setHttpMethod("GET");
        lotsMessage.setUrl(lotsDetailsUrl);
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
                lotsCache.put(symbolDescription, positionLotsResponse.getPositionLots());
            }
        } catch (Exception e) {
            LOG.error("Failed to fetch lots data", e);
        }
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
                System.currentTimeMillis() - timeStartedMillis, positionCache.size(), aggregateLotCount());
    }
}
