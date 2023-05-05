package io.lotsandlots.etrade;

import io.lotsandlots.etrade.api.OrderDetail;
import io.lotsandlots.etrade.api.PreviewOrderRequest;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.security.GeneralSecurityException;
import java.util.UUID;

@Test(groups = {"unit"})
public class EtradeOrderCreatorTest {

    static class TestEtradeOrderCreator extends EtradeOrderCreator {

        @Override
        public void run() {
        }
    }

    @BeforeClass
    public void beforeClass() throws GeneralSecurityException {
        EtradeRestTemplateFactory.init();
    }

    public void testNewPreviewOrderRequest() throws Exception {
        String clientOrderId = UUID.randomUUID().toString().substring(0, 8);;
        float limitPrice = (float) Math.random();

        TestEtradeOrderCreator etradeOrderCreator = new TestEtradeOrderCreator();
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setLimitPrice(limitPrice);
        PreviewOrderRequest previewOrderRequest = etradeOrderCreator.newPreviewOrderRequest(clientOrderId, orderDetail);
        Assert.assertEquals(clientOrderId, previewOrderRequest.getClientOrderId());
        Assert.assertEquals(previewOrderRequest.getOrderType(), "EQ");
        Assert.assertEquals(limitPrice, previewOrderRequest.getOrderDetailList().get(0).getLimitPrice());
    }
}
