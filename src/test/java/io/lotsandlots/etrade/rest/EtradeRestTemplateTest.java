package io.lotsandlots.etrade.rest;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.security.GeneralSecurityException;

@Test(groups = {"unit"})
public class EtradeRestTemplateTest {

    @BeforeClass
    public void beforeClass() throws GeneralSecurityException {
        EtradeRestTemplateFactory.init();
    }

    public void testExecute() {
        String testUrl = "https://example.com";
        Message testMessage = new Message();
        testMessage.setHttpMethod("GET");
        testMessage.setUrl(testUrl);
        testMessage.setOAuthHeader("xxx");

        Class<String> responseClass = String.class;
        ClientHttpRequestFactory requestFactory = EtradeRestTemplateFactory
                .getTemplateFactory()
                .getClientHttpRequestFactory();
        EtradeRestTemplate restTemplate = Mockito.spy(new EtradeRestTemplate(requestFactory));
        Mockito.doAnswer((Answer<ResponseEntity<String>>) invocation -> {
            Assert.assertEquals(invocation.getArgument(0, String.class), testUrl);
            Assert.assertTrue(invocation.getArgument(1, HttpEntity.class).getHeaders().containsKey("Authorization"));
            Assert.assertEquals(invocation.getArgument(2, Class.class), responseClass);
            return Mockito.mock(ResponseEntity.class);
        }).when(restTemplate).doGetExchange(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        restTemplate.doGet(testMessage, responseClass);
    }
}
