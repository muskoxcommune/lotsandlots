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
    private static final Map<String, List<PositionLotsResponse.PositionLot>> SYMBOL_TO_LOTS_INDEX = new HashMap<>();
    private static final Cache<String, PortfolioResponse.Position> POSITION_CACHE = CacheBuilder
            .newBuilder()
            .expireAfterWrite(90, TimeUnit.SECONDS)
            .removalListener((RemovalListener<String, PortfolioResponse.Position>) notification -> {
                if (notification.wasEvicted()) {
                    LOG.info("Removing lots for evicted position '{}', cause={}",
                            notification.getKey(), notification.getCause());
                    SYMBOL_TO_LOTS_INDEX.remove(notification.getKey());
                }
            })
            .build();

    private static EtradePortfolioDataFetcher DATA_FETCHER = null;
    private static PortfolioResponse.Totals TOTALS = new PortfolioResponse.Totals();

    public int aggregateLotCount() {
        int lotCount = 0;
        for (List<PositionLotsResponse.PositionLot> lots : SYMBOL_TO_LOTS_INDEX.values()) {
            lotCount += lots.size();
        }
        return lotCount;
    }

    private void fetchPortfolioResponse(SecurityContext securityContext,
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
        ResponseEntity<PortfolioResponse> portfolioResponseResponseEntity = EtradeRestTemplateFactory
                .getClient()
                .newCustomRestTemplate()
                .execute(portfolioMessage, PortfolioResponse.class);
        PortfolioResponse portfolioResponse = portfolioResponseResponseEntity.getBody();
        if (portfolioResponse == null) {
            throw new RuntimeException("Empty portfolio response");
        } else {
            TOTALS = portfolioResponse.getTotals(); // Update portfolio totals
            PortfolioResponse.AccountPortfolio accountPortfolio = portfolioResponse.getAccountPortfolio();
            for (PortfolioResponse.Position freshPositionData : accountPortfolio.getPositionList()) {
                String symbol = freshPositionData.getSymbolDescription();

                PortfolioResponse.Position cachedPositionData = POSITION_CACHE.getIfPresent(symbol);
                if (cachedPositionData == null
                        || !cachedPositionData.getMarketValue().equals(freshPositionData.getMarketValue())
                        || !cachedPositionData.getPricePaid().equals(freshPositionData.getPricePaid())) {
                    fetchPositionLotsResponse(securityContext, symbol, freshPositionData);
                }
                POSITION_CACHE.put(symbol, freshPositionData);
            }
            if (accountPortfolio.hasNextPageNo()) {
                fetchPortfolioResponse(securityContext, accountPortfolio.getNextPageNo());
            }
        }
    }

    private void fetchPositionLotsResponse(SecurityContext securityContext,
                                           String symbol,
                                           PortfolioResponse.Position position)
            throws GeneralSecurityException, UnsupportedEncodingException{
        Message lotsMessage = new Message();
        lotsMessage.setRequiresOauth(true);
        lotsMessage.setHttpMethod("GET");
        lotsMessage.setUrl(position.getLotsDetails());
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
            SYMBOL_TO_LOTS_INDEX.put(symbol, positionLotsResponse.getPositionLots());
        }
    }

    public static Map<String, List<PositionLotsResponse.PositionLot>> getSymbolToLotsIndex() {
        if (SYMBOL_TO_LOTS_INDEX.isEmpty()) {
            SCHEDULED_EXECUTOR.submit(DATA_FETCHER);
        }
        return SYMBOL_TO_LOTS_INDEX;
    }

    public static void init() {
        if (DATA_FETCHER == null) {
            DATA_FETCHER = new EtradePortfolioDataFetcher();
            SCHEDULED_EXECUTOR.scheduleAtFixedRate(DATA_FETCHER, 0, 60, TimeUnit.SECONDS);
        }
    }

    @Override
    public void run() {
        SecurityContext securityContext = EtradeRestTemplateFactory.getClient().getSecurityContext();
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
                    System.currentTimeMillis() - timeStartedMillis, POSITION_CACHE.size(), aggregateLotCount());
        } catch (Exception e) {
            LOG.info("Failed to fetch portfolio and lots data, duration={}ms",
                    System.currentTimeMillis() - timeStartedMillis, e);
        }
    }
}
