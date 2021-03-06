package io.lotsandlots.etrade;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.typesafe.config.Config;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.Message;
import io.lotsandlots.util.ConfigWrapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EtradePortfolioDataFetcher extends EtradeDataFetcher {

    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static final Logger LOG = LoggerFactory.getLogger(EtradePortfolioDataFetcher.class);

    private final List<PortfolioDataFetchCompletionHandler> portfolioDataFetchCompletionHandlers = new LinkedList<>();
    private final List<SymbolToLotsIndexPutHandler> symbolToLotsIndexPutHandlers = new LinkedList<>();
    private Long portfolioDataExpirationSeconds = 120L;
    private Long portfolioDataFetchIntervalSeconds = 60L;
    private Double defaultOrderCreationThreshold = 0.03;
    private Cache<String, PortfolioResponse.Position> positionCache;
    private Map<String, List<PositionLotsResponse.PositionLot>> symbolToLotsIndex = new HashMap<>();
    private PortfolioResponse.Totals totals = new PortfolioResponse.Totals();

    public EtradePortfolioDataFetcher() {
        if (CONFIG.hasPath("etrade.defaultOrderCreationThreshold")) {
            defaultOrderCreationThreshold = CONFIG.getDouble("etrade.defaultOrderCreationThreshold");
        }
        if (CONFIG.hasPath("etrade.portfolioDataExpirationSeconds")) {
            portfolioDataExpirationSeconds = CONFIG.getLong("etrade.portfolioDataExpirationSeconds");
        }
        if (CONFIG.hasPath("etrade.portfolioDataFetchInterval")) {
            portfolioDataFetchIntervalSeconds = CONFIG.getLong("etrade.portfolioDataFetchInterval");
        }

        positionCache = newCacheFromCacheBuilder(
                CacheBuilder.newBuilder(), portfolioDataExpirationSeconds);

        LOG.info("Initialized EtradePortfolioDataFetcher, defaultOrderCreationThreshold={} "
                        + "portfolioDataExpirationSeconds={} portfolioDataFetchIntervalSeconds={}",
                defaultOrderCreationThreshold, portfolioDataExpirationSeconds, portfolioDataFetchIntervalSeconds);
    }

    public void addDataFetchCompletionHandler(PortfolioDataFetchCompletionHandler handler) {
        portfolioDataFetchCompletionHandlers.add(handler);
    }

    public void addSymbolToLotsIndexPutHandler(SymbolToLotsIndexPutHandler handler) {
        symbolToLotsIndexPutHandlers.add(handler);
    }

    public int aggregateLotCount() {
        int lotCount = 0;
        for (List<PositionLotsResponse.PositionLot> lots : symbolToLotsIndex.values()) {
            lotCount += lots.size();
        }
        return lotCount;
    }

    /**
     * Call E*Trade's portfolio API and process position data.
     *
     * @param securityContext SecurityContext object from EtradeRestTemplateFactory.
     * @param pageNumber String for paginating results. Null for initial invocation.
     * @throws GeneralSecurityException
     * @throws UnsupportedEncodingException
     */
    void fetchPortfolioResponse(SecurityContext securityContext,
                                String pageNumber)
            throws GeneralSecurityException, UnsupportedEncodingException {
        Message portfolioMessage = new Message();
        portfolioMessage.setRequiresOauth(true);
        portfolioMessage.setHttpMethod("GET");
        portfolioMessage.setUrl(getApiConfig().getPortfolioUrl());
        String portfolioQueryString = getApiConfig().getPortfolioQueryString();
        if (!StringUtils.isBlank(pageNumber)) {
            portfolioQueryString += "&pageNumber=" + pageNumber;
        }
        portfolioMessage.setQueryString(portfolioQueryString);
        setOAuthHeader(securityContext, portfolioMessage);
        ResponseEntity<PortfolioResponse> portfolioResponseResponseEntity = getRestTemplateFactory()
                .newCustomRestTemplate()
                .doGet(portfolioMessage, PortfolioResponse.class);
        PortfolioResponse portfolioResponse = portfolioResponseResponseEntity.getBody();
        if (portfolioResponse == null) {
            throw new RuntimeException("Empty portfolio response");
        } else {
            totals = portfolioResponse.getTotals(); // Update portfolio totals
            LOG.info("Portfolio cash={}, todaysGainLossPct={}, todaysGainLoss={}, "
                            + "totalGainLossPct={}, totalGainLoss={}, totalPaid={}, totalValue={}",
                    totals.getCashBalance(),
                    totals.getTodaysGainLossPct(),
                    totals.getTodaysGainLoss(),
                    totals.getTotalGainLossPct(),
                    totals.getTotalGainLoss(),
                    totals.getTotalPricePaid(),
                    totals.getTotalMarketValue()
            );
            PortfolioResponse.AccountPortfolio accountPortfolio = portfolioResponse.getAccountPortfolio();
            for (PortfolioResponse.Position freshPositionData : accountPortfolio.getPositionList()) {
                String symbol = freshPositionData.getSymbolDescription();
                fetchPositionLotsResponse(securityContext, symbol, freshPositionData);
                positionCache.put(symbol, freshPositionData);
            }
            if (accountPortfolio.hasNextPageNo()) {
                fetchPortfolioResponse(securityContext, accountPortfolio.getNextPageNo());
            }
        }
    }

    void fetchPositionLotsResponse(SecurityContext securityContext,
                                   String symbol,
                                   PortfolioResponse.Position position)
            throws GeneralSecurityException, UnsupportedEncodingException{
        Message lotsMessage = new Message();
        lotsMessage.setRequiresOauth(true);
        lotsMessage.setHttpMethod("GET");
        lotsMessage.setUrl(position.getLotsDetails());
        setOAuthHeader(securityContext, lotsMessage);
        ResponseEntity<PositionLotsResponse> positionLotsResponseResponseEntity = getRestTemplateFactory()
                .newCustomRestTemplate()
                .doGet(lotsMessage, PositionLotsResponse.class);
        PositionLotsResponse positionLotsResponse = positionLotsResponseResponseEntity.getBody();
        if (positionLotsResponse == null) {
            throw new RuntimeException("Empty response");
        } else {
            Double orderCreationThreshold = defaultOrderCreationThreshold;
            String overrideOrderCreationThresholdPath = "etrade.overrideOrderCreationThresholds." + symbol;
            if (CONFIG.hasPath(overrideOrderCreationThresholdPath)) {
                orderCreationThreshold = CONFIG.getDouble(overrideOrderCreationThresholdPath);
            }
            Integer lotCount = positionLotsResponse.getPositionLots().size();
            for (PositionLotsResponse.PositionLot lot : positionLotsResponse.getPositionLots()) {
                lot.setSymbol(symbol);
                lot.setTotalLotCount(lotCount);
                lot.setTotalPositionCost(position.getTotalCost());
                lot.setPositionPctOfPortfolio(position.getPctOfPortfolio());
                lot.setOrderCreationThreshold(orderCreationThreshold);
                lot.setFollowPrice(lot.getPrice() * (1F - orderCreationThreshold.floatValue()));
                lot.setTargetPrice(lot.getPrice() * (1F + orderCreationThreshold.floatValue()));
            }
            symbolToLotsIndex.put(symbol, positionLotsResponse.getPositionLots());
            for (SymbolToLotsIndexPutHandler handler : symbolToLotsIndexPutHandlers) {
                handler.handleSymbolToLotsIndexPut(symbol, positionLotsResponse.getPositionLots(), totals);
            }
        }
    }

    Cache<String, PortfolioResponse.Position> getPositionCache() {
        return positionCache;
    }
    void setPositionCache(Cache<String, PortfolioResponse.Position> positionCache) {
        this.positionCache = positionCache;
    }

    public Long getPortfolioDataExpirationSeconds() {
        return portfolioDataExpirationSeconds;
    }

    public Long getPortfolioDataFetchIntervalSeconds() {
        return portfolioDataFetchIntervalSeconds;
    }

    public Map<String, List<PositionLotsResponse.PositionLot>> getSymbolToLotsIndex() {
        return symbolToLotsIndex;
    }
    void setSymbolToLotsIndex(Map<String, List<PositionLotsResponse.PositionLot>> symbolToLotsIndex) {
        this.symbolToLotsIndex = symbolToLotsIndex;
    }

    Cache<String, PortfolioResponse.Position> newCacheFromCacheBuilder(CacheBuilder<Object, Object> cacheBuilder,
                                                                       Long expirationSeconds) {
        return cacheBuilder
                .expireAfterWrite(expirationSeconds, TimeUnit.SECONDS)
                .removalListener((RemovalListener<String, PortfolioResponse.Position>) notification -> {
                    if (notification.wasEvicted()) {
                        LOG.info("Removing lots for evicted position '{}', cause={}",
                                notification.getKey(), notification.getCause());
                        String symbol = notification.getKey();
                        if (symbolToLotsIndex.containsKey(symbol)) {
                            symbolToLotsIndex.remove(notification.getKey());
                        } else {
                            LOG.error("Expected lots for symbol={}, but cache missed", symbol);
                        }
                    }
                })
                .build();
    }

    @Override
    public void run() {
        SecurityContext securityContext = getRestTemplateFactory().getSecurityContext();
        if (!securityContext.isInitialized()) {
            LOG.warn("SecurityContext not initialized, please go to /etrade/authorize");
            return;
        }
        if (getApiConfig().getPortfolioUrl() == null) {
            LOG.warn("Please configure etrade.accountIdKey");
            return;
        }
        LOG.info("Fetching portfolio data");
        long timeStartedMillis = System.currentTimeMillis();
        try {
            fetchPortfolioResponse(securityContext, null);
            // Expired entries are not guaranteed to be cleaned up immediately, so we do it here explicitly.
            // Reference https://github.com/google/guava/wiki/CachesExplained#when-does-cleanup-happen
            positionCache.cleanUp();
            long timeStoppedMillis = System.currentTimeMillis();
            LOG.info("Fetched portfolio and lots data, duration={}ms, positions={} lots={}",
                    timeStoppedMillis - timeStartedMillis, positionCache.size(), aggregateLotCount());
            setLastSuccessfulFetchTimeMillis(timeStoppedMillis);
            for (PortfolioDataFetchCompletionHandler handler : portfolioDataFetchCompletionHandlers) {
                try {
                    handler.handlePortfolioDataFetchCompletion(timeStartedMillis, timeStoppedMillis, totals);
                } catch (Exception e) {
                    LOG.error("Failed to handle portfolio data fetch completion event");
                }
            }
        } catch (Exception e) {
            long timeFailedMillis = System.currentTimeMillis();
            LOG.info("Failed to fetch portfolio and lots data, duration={}ms", timeFailedMillis - timeStartedMillis, e);
            setLastFailedFetchTimeMillis(timeFailedMillis);
        }
    }

    public interface SymbolToLotsIndexPutHandler {

        void handleSymbolToLotsIndexPut(String symbol, List<PositionLotsResponse.PositionLot> lots, PortfolioResponse.Totals totals);
    }

    public interface PortfolioDataFetchCompletionHandler {

        void handlePortfolioDataFetchCompletion(
                long timeFetchStarted, long timeFetchStopped, PortfolioResponse.Totals totals);
    }
}
