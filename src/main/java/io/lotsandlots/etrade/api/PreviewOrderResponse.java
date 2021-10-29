package io.lotsandlots.etrade.api;

import java.util.List;

public class PreviewOrderResponse {

    String orderType;
    List<Message> messageList;
    Float totalOrderValue;
    Float totalCommission;

}
