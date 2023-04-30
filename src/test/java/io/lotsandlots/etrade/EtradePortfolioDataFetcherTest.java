package io.lotsandlots.etrade;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.lotsandlots.etrade.api.ApiConfig;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.etrade.rest.EtradeRestTemplate;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.rest.Message;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Test(groups = {"unit"})
public class EtradePortfolioDataFetcherTest {

    @BeforeClass
    public void beforeClass() throws GeneralSecurityException {
        EtradeRestTemplateFactory.init();
    }

    public void testFetchPortfolioResponse() throws GeneralSecurityException, UnsupportedEncodingException {
        String ticker = "FETCH_PORTFOLIO_RESPONSE";
        EtradeRestTemplateFactory mockTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        EtradeRestTemplate mockRestTemplate = Mockito.mock(EtradeRestTemplate.class);
        ResponseEntity<PortfolioResponse> mockResponseEntity = Mockito.mock(ResponseEntity.class);

        PortfolioResponse portfolioResponse = new PortfolioResponse();
        PortfolioResponse.Totals portfolioTotals = new PortfolioResponse.Totals();
        portfolioResponse.setTotals(portfolioTotals);
        List<PortfolioResponse.AccountPortfolio> accountPortfolioList = new LinkedList<>();
        PortfolioResponse.AccountPortfolio accountPortfolio = new PortfolioResponse.AccountPortfolio();
        List<PortfolioResponse.Position> positionList = new LinkedList<>();
        PortfolioResponse.Position position1 = new PortfolioResponse.Position();
        position1.setSymbolDescription(ticker);
        position1.setPositionId(1L);
        position1.setTotalCost(1000F);

        positionList.add(position1);
        accountPortfolio.setPositionList(positionList);
        accountPortfolioList.add(accountPortfolio);
        portfolioResponse.setAccountPortfolioList(accountPortfolioList);

        Mockito.doReturn(portfolioResponse).when(mockResponseEntity).getBody();
        Mockito.doReturn(mockResponseEntity).when(mockRestTemplate).doGet(Mockito.any(Message.class), Mockito.any());
        Mockito.doReturn(mockRestTemplate).when(mockTemplateFactory).newCustomRestTemplate();

        SecurityContext securityContext = EtradeRestTemplateFactory.getTemplateFactory().newSecurityContext();
        EtradePortfolioDataFetcher dataFetcher = Mockito.spy(new EtradePortfolioDataFetcher());
        dataFetcher.setRestTemplateFactory(mockTemplateFactory);
        Mockito.doAnswer(invocation -> null).when(dataFetcher).fetchPositionLotsResponse(securityContext, ticker, position1);
        dataFetcher.fetchPortfolioResponse(securityContext, null);
        Cache<String, PortfolioResponse.Position> positionCache = dataFetcher.getPositionCache();
        PortfolioResponse.Position cachedPosition = positionCache.getIfPresent(ticker);
        Assert.assertNotNull(cachedPosition);
    }

    public void testFetchPositionLotsResponse() throws GeneralSecurityException, UnsupportedEncodingException {
        String ticker = "FETCH_POSITION_LOTS_RESPONSE";
        EtradeRestTemplateFactory mockTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        EtradeRestTemplate mockRestTemplate = Mockito.mock(EtradeRestTemplate.class);
        ResponseEntity<PositionLotsResponse> mockResponseEntity = Mockito.mock(ResponseEntity.class);

        PortfolioResponse.Position position = new PortfolioResponse.Position();
        position.setPctOfPortfolio(1.0F);
        position.setTotalCost(1000.00F);
        List<PositionLotsResponse.PositionLot> positionLots = new LinkedList<>();
        PositionLotsResponse.PositionLot lot1 = new PositionLotsResponse.PositionLot();
        lot1.setPrice(100.00F);
        lot1.setPositionLotId(1L);
        lot1.setAcquiredDate(0L);
        positionLots.add(lot1);
        PositionLotsResponse.PositionLot lot2 = new PositionLotsResponse.PositionLot();
        lot2.setPrice(100.00F);
        lot2.setPositionLotId(2L);
        lot2.setAcquiredDate(0L);
        positionLots.add(lot2);
        PositionLotsResponse positionLotsResponse = new PositionLotsResponse();
        positionLotsResponse.setPositionLots(positionLots);

        Mockito.doReturn(positionLotsResponse).when(mockResponseEntity).getBody();
        Mockito.doReturn(mockResponseEntity).when(mockRestTemplate).doGet(Mockito.any(Message.class), Mockito.any());
        Mockito.doReturn(mockRestTemplate).when(mockTemplateFactory).newCustomRestTemplate();

        SecurityContext securityContext = EtradeRestTemplateFactory.getTemplateFactory().newSecurityContext();
        EtradePortfolioDataFetcher dataFetcher = new EtradePortfolioDataFetcher();
        dataFetcher.setRestTemplateFactory(mockTemplateFactory);
        dataFetcher.fetchPositionLotsResponse(securityContext, ticker, position);

        Map<String, List<PositionLotsResponse.PositionLot>> symbolToLotsIndex = dataFetcher.getSymbolToLotsIndex();
        Assert.assertTrue(symbolToLotsIndex.containsKey(ticker));
        Assert.assertEquals(symbolToLotsIndex.get(ticker).size(), 2);
        PositionLotsResponse.PositionLot indexedLot = symbolToLotsIndex.get(ticker).get(0);
        Assert.assertEquals(indexedLot.getSymbol(), ticker);
        Assert.assertEquals(indexedLot.getTotalLotCount(), Integer.valueOf(2));
        Assert.assertEquals(indexedLot.getTotalPositionCost(), 1000.00F);
        Assert.assertEquals(indexedLot.getPositionPctOfPortfolio(), 1.0F);
        Assert.assertEquals(indexedLot.getTargetPrice(), 103.00F);
    }

    public void testPositionExpiration() throws InterruptedException {
        String ticker = "POSITION_EXPIRATION";
        Map<String, List<PositionLotsResponse.PositionLot>> symbolToLotsIndex = Mockito.spy(new HashMap<>());
        EtradePortfolioDataFetcher dataFetcher = new EtradePortfolioDataFetcher();
        dataFetcher.setSymbolToLotsIndex(symbolToLotsIndex);
        Cache<String, PortfolioResponse.Position> positionCache = dataFetcher.newCacheFromCacheBuilder(
                CacheBuilder.newBuilder(), 1L);
        dataFetcher.setPositionCache(positionCache);

        symbolToLotsIndex.put(ticker, new LinkedList<>());
        positionCache.put(ticker, new PortfolioResponse.Position());
        Thread.sleep(1500);
        positionCache.cleanUp();
        Mockito.verify(symbolToLotsIndex).remove(Mockito.any(String.class));
    }

    public void testRun() throws GeneralSecurityException, UnsupportedEncodingException {
        SecurityContext mockSecurityContext = Mockito.mock(SecurityContext.class);
        Mockito.doReturn(true).when(mockSecurityContext).isInitialized();
        EtradeRestTemplateFactory mockTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        Mockito.doReturn(mockSecurityContext).when(mockTemplateFactory).getSecurityContext();

        EtradePortfolioDataFetcher dataFetcher = Mockito.spy(new EtradePortfolioDataFetcher());
        dataFetcher.setRestTemplateFactory(mockTemplateFactory);
        Mockito.doAnswer(invocation -> null).when(dataFetcher)
                .fetchPortfolioResponse(Mockito.any(), Mockito.any());
        dataFetcher.run();
        Mockito.verify(dataFetcher)
                .fetchPortfolioResponse(Mockito.any(), Mockito.any());
    }

    public void testRunWithException() throws GeneralSecurityException, UnsupportedEncodingException {
        SecurityContext mockSecurityContext = Mockito.mock(SecurityContext.class);
        Mockito.doReturn(true).when(mockSecurityContext).isInitialized();
        EtradeRestTemplateFactory mockTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        Mockito.doReturn(mockSecurityContext).when(mockTemplateFactory).getSecurityContext();

        EtradePortfolioDataFetcher dataFetcher = Mockito.spy(new EtradePortfolioDataFetcher());
        dataFetcher.setRestTemplateFactory(mockTemplateFactory);
        Mockito.doThrow(new RuntimeException("Thrown for test")).when(dataFetcher)
                .fetchPortfolioResponse(Mockito.any(), Mockito.any());
        dataFetcher.run();
    }

    public void testRunWithoutPortfolioUrl() throws GeneralSecurityException, UnsupportedEncodingException {
        SecurityContext mockSecurityContext = Mockito.mock(SecurityContext.class);
        Mockito.doReturn(true).when(mockSecurityContext).isInitialized();
        EtradeRestTemplateFactory mockTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        Mockito.doReturn(mockSecurityContext).when(mockTemplateFactory).getSecurityContext();

        ApiConfig mockApiConfig = Mockito.mock(ApiConfig.class);
        EtradePortfolioDataFetcher dataFetcher = Mockito.spy(new EtradePortfolioDataFetcher());
        dataFetcher.setApiConfig(mockApiConfig);
        dataFetcher.setRestTemplateFactory(mockTemplateFactory);
        Mockito.doAnswer(invocation -> null).when(dataFetcher)
                .fetchPortfolioResponse(Mockito.any(), Mockito.any());
        dataFetcher.run();
        Mockito.verify(dataFetcher).getApiConfig();
        Mockito.verify(mockApiConfig).getPortfolioUrl();
        Mockito.verify(dataFetcher, Mockito.times(0))
                .fetchPortfolioResponse(Mockito.any(), Mockito.any());
    }

    public void testRunWithUninitializedSecurityContext()
            throws GeneralSecurityException, UnsupportedEncodingException {
        SecurityContext mockSecurityContext = Mockito.mock(SecurityContext.class);
        Mockito.doReturn(false).when(mockSecurityContext).isInitialized();
        EtradeRestTemplateFactory mockTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        Mockito.doReturn(mockSecurityContext).when(mockTemplateFactory).getSecurityContext();

        EtradePortfolioDataFetcher dataFetcher = Mockito.spy(new EtradePortfolioDataFetcher());
        dataFetcher.setRestTemplateFactory(mockTemplateFactory);
        Mockito.doAnswer(invocation -> null).when(dataFetcher)
                .fetchPortfolioResponse(Mockito.any(), Mockito.any());
        dataFetcher.run();
        Mockito.verify(dataFetcher, Mockito.times(0))
                .fetchPortfolioResponse(Mockito.any(), Mockito.any());
    }
}
