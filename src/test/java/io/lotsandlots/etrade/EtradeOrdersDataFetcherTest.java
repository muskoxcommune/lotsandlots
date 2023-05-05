package io.lotsandlots.etrade;

import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.rest.Message;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.security.GeneralSecurityException;

@Test(groups = {"unit"})
public class EtradeOrdersDataFetcherTest {

    @BeforeClass
    public void beforeClass() throws GeneralSecurityException {
        EtradeRestTemplateFactory.init();
    }

    public void testNewOrdersMessageWithMarker() {
        Message ordersMessage = new EtradeOrdersDataFetcher().newOrdersMessage("test");
        Assert.assertTrue(ordersMessage.getQueryString().contains("fromDate"));
        Assert.assertTrue(ordersMessage.getQueryString().contains("toDate"));
        Assert.assertTrue(ordersMessage.getQueryString().contains("marker=test"));
    }

    public void testNewOrdersMessageWithoutMarker() {
        Message ordersMessage = new EtradeOrdersDataFetcher().newOrdersMessage(null);
        Assert.assertTrue(ordersMessage.getQueryString().contains("fromDate"));
        Assert.assertTrue(ordersMessage.getQueryString().contains("toDate"));
        Assert.assertFalse(ordersMessage.getQueryString().contains("marker"));
    }

    public void testRun() {
    }
}
