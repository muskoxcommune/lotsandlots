package io.lotsandlots.etrade;

import com.google.common.cache.Cache;
import io.lotsandlots.etrade.api.PortfolioResponse;
import io.lotsandlots.etrade.api.PositionLotsResponse;
import io.lotsandlots.etrade.oauth.SecurityContext;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
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
        String ticker = "TEST1";
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

        positionList.add(position1);
        accountPortfolio.setPositionList(positionList);
        accountPortfolioList.add(accountPortfolio);
        portfolioResponse.setAccountPortfolioList(accountPortfolioList);

        Mockito.doReturn(portfolioResponse).when(mockResponseEntity).getBody();
        Mockito.doReturn(mockResponseEntity).when(mockRestTemplate).execute(Mockito.any(Message.class), Mockito.any());
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
        String ticker = "TEST1";
        EtradeRestTemplateFactory mockTemplateFactory = Mockito.mock(EtradeRestTemplateFactory.class);
        EtradeRestTemplate mockRestTemplate = Mockito.mock(EtradeRestTemplate.class);
        ResponseEntity<PositionLotsResponse> mockResponseEntity = Mockito.mock(ResponseEntity.class);

        PortfolioResponse.Position position = new PortfolioResponse.Position();
        position.setPctOfPortfolio(1.0F);
        position.setTotalCost(1000.00F);
        List<PositionLotsResponse.PositionLot> positionLots = new LinkedList<>();
        PositionLotsResponse.PositionLot lot1 = new PositionLotsResponse.PositionLot();
        lot1.setPrice(100.00F);
        positionLots.add(lot1);
        PositionLotsResponse.PositionLot lot2 = new PositionLotsResponse.PositionLot();
        lot2.setPrice(100.00F);
        positionLots.add(lot2);
        PositionLotsResponse positionLotsResponse = new PositionLotsResponse();
        positionLotsResponse.setPositionLots(positionLots);

        Mockito.doReturn(positionLotsResponse).when(mockResponseEntity).getBody();
        Mockito.doReturn(mockResponseEntity).when(mockRestTemplate).execute(Mockito.any(Message.class), Mockito.any());
        Mockito.doReturn(mockRestTemplate).when(mockTemplateFactory).newCustomRestTemplate();

        SecurityContext securityContext = EtradeRestTemplateFactory.getTemplateFactory().newSecurityContext();
        EtradePortfolioDataFetcher dataFetcher = new EtradePortfolioDataFetcher();
        dataFetcher.setRestTemplateFactory(mockTemplateFactory);
        dataFetcher.fetchPositionLotsResponse(securityContext, ticker, position);

        Map<String, List<PositionLotsResponse.PositionLot>> symbolToLotsIndex = dataFetcher.getSymbolToLotsIndex(false);
        Assert.assertTrue(symbolToLotsIndex.containsKey(ticker));
        Assert.assertEquals(symbolToLotsIndex.get(ticker).size(), 2);
        PositionLotsResponse.PositionLot indexedLot = symbolToLotsIndex.get(ticker).get(0);
        Assert.assertEquals(indexedLot.getSymbol(), ticker);
        Assert.assertEquals(indexedLot.getTotalLotCount(), Integer.valueOf(2));
        Assert.assertEquals(indexedLot.getTotalPositionCost(), 1000.00F);
        Assert.assertEquals(indexedLot.getPositionPctOfPortfolio(), 1.0F);
        Assert.assertEquals(indexedLot.getTargetPrice(), 103.00F);
    }
}
