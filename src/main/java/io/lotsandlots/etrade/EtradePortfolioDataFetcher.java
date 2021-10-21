package io.lotsandlots.etrade;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.oauth.SecurityContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class EtradePortfolioDataFetcher extends EtradeDataFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(EtradePortfolioDataFetcher.class);

    private static EtradePortfolioDataFetcher DATA_FETCHER = null;

    private final Map<String, List<PositionLotsResponse.PositionLot>> symbolToLotsIndex = new HashMap<>();
    private final Cache<String, PortfolioResponse.Position> positionCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(90, TimeUnit.SECONDS)
            .removalListener((RemovalListener<String, PortfolioResponse.Position>) notification -> {
                if (notification.wasEvicted()) {
                    LOG.info("Removing lots for evicted position '{}', cause={}",
                            notification.getKey(), notification.getCause());
                    symbolToLotsIndex.remove(notification.getKey());
                }
            })
            .build();

    private PortfolioResponse.Totals totals = new PortfolioResponse.Totals();

    public int aggregateLotCount() {
        int lotCount = 0;
        for (List<PositionLotsResponse.PositionLot> lots : symbolToLotsIndex.values()) {
            lotCount += lots.size();
        }
        return lotCount;
    }

    void fetchPortfolioResponse(SecurityContext securityContext,
                                        String pageNumber)
            throws GeneralSecurityException, UnsupportedEncodingException {
        Message portfolioMessage = new Message();
        portfolioMessage.setRequiresOauth(true);
        portfolioMessage.setHttpMethod("GET");
        portfolioMessage.setUrl(API.getPortfolioUrl());
        String portfolioQueryString = API.getPortfolioQueryString();
        if (!StringUtils.isBlank(pageNumber)) {
            portfolioQueryString += "&pageNumber=" + pageNumber;
        }
        portfolioMessage.setQueryString(portfolioQueryString);
        setOAuthHeader(securityContext, portfolioMessage);
        ResponseEntity<PortfolioResponse> portfolioResponseResponseEntity = getRestTemplateFactory()
                .newCustomRestTemplate()
                .execute(portfolioMessage, PortfolioResponse.class);
        PortfolioResponse portfolioResponse = portfolioResponseResponseEntity.getBody();
        if (portfolioResponse == null) {
            throw new RuntimeException("Empty portfolio response");
        } else {
            totals = portfolioResponse.getTotals(); // Update portfolio totals
            PortfolioResponse.AccountPortfolio accountPortfolio = portfolioResponse.getAccountPortfolio();
            for (PortfolioResponse.Position freshPositionData : accountPortfolio.getPositionList()) {
                String symbol = freshPositionData.getSymbolDescription();

                PortfolioResponse.Position cachedPositionData = positionCache.getIfPresent(symbol);
                if (cachedPositionData == null
                        || !cachedPositionData.getMarketValue().equals(freshPositionData.getMarketValue())
                        || !cachedPositionData.getPricePaid().equals(freshPositionData.getPricePaid())) {
                    fetchPositionLotsResponse(securityContext, symbol, freshPositionData);
                }
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
            symbolToLotsIndex.put(symbol, positionLotsResponse.getPositionLots());
        }
    }

    Cache<String, PortfolioResponse.Position> getPositionCache() {
        return positionCache;
    }

    public static Map<String, List<PositionLotsResponse.PositionLot>> getSymbolToLotsIndex() {
        return DATA_FETCHER.getSymbolToLotsIndex(true);
    }

    Map<String, List<PositionLotsResponse.PositionLot>> getSymbolToLotsIndex(boolean runIfEmpty) {
        if (symbolToLotsIndex.isEmpty() && runIfEmpty) {
            getScheduledExecutor().submit(this);
        }
        return symbolToLotsIndex;
    }

    public static void init() {
        if (DATA_FETCHER == null) {
            DATA_FETCHER = new EtradePortfolioDataFetcher();
            DATA_FETCHER.getScheduledExecutor().scheduleAtFixedRate(DATA_FETCHER, 0, 60, TimeUnit.SECONDS);
        }
    }

    @Override
    public void run() {
        SecurityContext securityContext = EtradeRestTemplateFactory.getTemplateFactory().getSecurityContext();
        if (!securityContext.isInitialized()) {
            LOG.warn("SecurityContext not initialized, please go to /etrade/authorize");
            return;
        }
        if (API.getPortfolioUrl() == null) {
            LOG.warn("Please configure etrade.accountIdKey");
            return;
        }
        long timeStartedMillis = System.currentTimeMillis();
        try {
            fetchPortfolioResponse(securityContext, null);
            LOG.info("Fetched portfolio and lots data, duration={}ms, positions={} lots={}",
                    System.currentTimeMillis() - timeStartedMillis, positionCache.size(), aggregateLotCount());
        } catch (Exception e) {
            LOG.info("Failed to fetch portfolio and lots data, duration={}ms",
                    System.currentTimeMillis() - timeStartedMillis, e);
        }
    }

    public static void shutdown() {
        DATA_FETCHER.getScheduledExecutor().shutdown();
    }
}
