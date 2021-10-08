package io.lotsandlots.etrade;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
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
    private static final Map<String, List<PositionLotsResponse.PositionLot>> LOTS = new HashMap<>();
    private static final Cache<String, PortfolioResponse.Position> POSITIONS = CacheBuilder
            .newBuilder()
            .expireAfterWrite(90, TimeUnit.SECONDS)
            .removalListener((RemovalListener<String, PortfolioResponse.Position>) notification -> {
                if (notification.wasEvicted()) {
                    LOG.info("Removing lots for evicted position '{}', cause={}",
                            notification.getKey(), notification.getCause());
                    LOTS.remove(notification.getKey());
                }
            })
            .build();
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    private static EtradePortfolioDataFetcher DATA_FETCHER = null;
    private static PortfolioResponse.Totals TOTALS = new PortfolioResponse.Totals();

    private EtradePortfolioDataFetcher() {
        SCHEDULED_EXECUTOR.scheduleAtFixedRate(this, 0, 60, TimeUnit.SECONDS);
    }

    public int aggregateLotCount() {
        int lotCount = 0;
        for (List<PositionLotsResponse.PositionLot> lots : LOTS.values()) {
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
                for (PortfolioResponse.Position freshPositionData : accountPortfolio.getPositionList()) {
                    String symbol = freshPositionData.getSymbolDescription();

                    PortfolioResponse.Position cachedPositionData = POSITIONS.getIfPresent(symbol);
                    if (cachedPositionData == null || !cachedPositionData.getQuantity().equals(freshPositionData.getQuantity())) {
                        fetchPositionLotsResponse(securityContext, symbol, freshPositionData);
                    }
                    POSITIONS.put(symbol, freshPositionData);
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
            SecurityContext securityContext, String symbol, PortfolioResponse.Position position) {
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
                    lot.setSymbol(symbol);
                    lot.setTotalLotCount(lotCount);
                    lot.setTotalPositionCost(position.getTotalCost());
                    lot.setPositionPctOfPortfolio(position.getPctOfPortfolio());
                    lot.setTargetPrice(lot.getPrice() * 1.03F);
                }
                LOTS.put(symbol, positionLotsResponse.getPositionLots());
            }
        } catch (Exception e) {
            LOG.error("Failed to fetch lots data", e);
        }
    }

    public static Map<String, List<PositionLotsResponse.PositionLot>> getLots() {
        if (LOTS.isEmpty()) {
            SCHEDULED_EXECUTOR.submit(DATA_FETCHER);
        }
        return LOTS;
    }

    public static Cache<String, PortfolioResponse.Position> getPositions() {
        if (POSITIONS.size() == 0) {
            SCHEDULED_EXECUTOR.submit(DATA_FETCHER);
        }
        return POSITIONS;
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
                System.currentTimeMillis() - timeStartedMillis, POSITIONS.size(), aggregateLotCount());
    }
}
