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
