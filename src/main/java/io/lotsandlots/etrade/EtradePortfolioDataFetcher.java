package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.ApiConfig;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.oauth.SecurityContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EtradePortfolioDataFetcher implements EtradeApiClient, Runnable {

    private static final ApiConfig API = EtradeRestTemplateFactory.getClient().getApiConfig();
    private static final Logger LOG = LoggerFactory.getLogger(EtradePortfolioDataFetcher.class);
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    private static EtradePortfolioDataFetcher DATA_FETCHER = null;

    private final Map<String, PortfolioResponse.Position> positionMap = new HashMap<>();
    private PortfolioResponse.Totals totals;

    private EtradePortfolioDataFetcher() {
        SCHEDULED_EXECUTOR.scheduleAtFixedRate(this, 0, 1, TimeUnit.MINUTES);
    }

    public static void destroy() {
        SCHEDULED_EXECUTOR.shutdown();
    }

    private void fetchPositionLotsResponse(SecurityContext securityContext) {
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
            setOauthHeader(securityContext, portfolioMessage);
            ResponseEntity<PortfolioResponse> accountListResponse = EtradeRestTemplateFactory
                    .getClient()
                    .newCustomRestTemplate()
                    .execute(portfolioMessage, PortfolioResponse.class);
            PortfolioResponse portfolioResponse = accountListResponse.getBody();
            if (portfolioResponse == null) {
                throw new RuntimeException("Empty response");
            } else {
                totals = portfolioResponse.getTotals(); // Update portfolio totals
                PortfolioResponse.AccountPortfolio accountPortfolio = portfolioResponse.getAccountPortfolio();
                for (PortfolioResponse.Position position : accountPortfolio.getPositionList()) {
                    if (positionMap.containsKey(position.getSymbolDescription())) {
                        // Compare retrieved data with previously stored data.
                        // If quantity owned changes, refresh lots.
                    } else {
                        // Fetch lots.
                    }
                    // - Store position data
                    positionMap.put(position.getSymbolDescription(), position);
                }
                if (accountPortfolio.hasNextPageNo()) {
                    fetchPortfolioResponse(securityContext, accountPortfolio.getNextPageNo());
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to return portfolio data", e);
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
        fetchPortfolioResponse(securityContext, null);
    }
}
