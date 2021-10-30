package io.lotsandlots.etrade.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference https://apisb.etrade.com/docs/api/order/api-order-v1.html#/definitions/OrderDetail
 */
public class OrderDetail {

    @JsonSerialize(using = ToStringSerializer.class)
    Boolean allOrNone;
    @JsonProperty("Instrument")
    List<Instrument> instrumentList;
    @JsonSerialize(using = ToStringSerializer.class)
    Float limitPrice;
    String marketSession;
    String orderTerm;
    String orderType;
    Float orderValue;
    Long placedTime;
    String priceType;
    String status;

    public Boolean getAllOrNone() {
        return allOrNone;
    }
    public void setAllOrNone(Boolean allOrNone) {
        this.allOrNone = allOrNone;
    }

    public List<Instrument> getInstrumentList() {
        return instrumentList;
    }
    public void newInstrumentList(Instrument instrument) {
        this.instrumentList = new ArrayList<>();
        this.instrumentList.add(instrument);
    }
    public void setInstrumentList(List<Instrument> instrumentList) {
        this.instrumentList = instrumentList;
    }

    public Float getLimitPrice() {
        return limitPrice;
    }
    public void setLimitPrice(Float limitPrice) {
        this.limitPrice = limitPrice;
    }

    public String getMarketSession() {
        return marketSession;
    }
    public void setMarketSession(String marketSession) {
        this.marketSession = marketSession;
    }

    public String getOrderTerm() {
        return orderTerm;
    }
    public void setOrderTerm(String orderTerm) {
        this.orderTerm = orderTerm;
    }

    public String getOrderType() {
        return orderType;
    }
    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public Float getOrderValue() {
        return orderValue;
    }
    public void setOrderValue(Float orderValue) {
        this.orderValue = orderValue;
    }

    public Long getPlacedTime() {
        return placedTime;
    }
    public void setPlacedTime(Long placedTime) {
        this.placedTime = placedTime;
    }

    public String getPriceType() {
        return priceType;
    }
    public void setPriceType(String priceType) {
        this.priceType = priceType;
    }

    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * https://apisb.etrade.com/docs/api/order/api-order-v1.html#/definitions/Instrument
     */
    public static class Instrument {

        Float filledQuantity;
        @JsonProperty("Lots")
        Lots lots;
        String orderAction;
        Long orderedQuantity;
        @JsonProperty("Product")
        Product product;
        @JsonSerialize(using = ToStringSerializer.class)
        Long quantity;
        String quantityType;

        public Float getFilledQuantity() {
            return filledQuantity;
        }
        public void setFilledQuantity(Float filledQuantity) {
            this.filledQuantity = filledQuantity;
        }

        public Lots getLots() {
            return lots;
        }
        public void setLots(Lots lots) {
            this.lots = lots;
        }

        public String getOrderAction() {
            return orderAction;
        }
        public void setOrderAction(String orderAction) {
            this.orderAction = orderAction;
        }

        public Long getOrderedQuantity() {
            return orderedQuantity;
        }
        public void setOrderedQuantity(Long orderedQuantity) {
            this.orderedQuantity = orderedQuantity;
        }

        public Product getProduct() {
            return product;
        }
        public void setProduct(Product product) {
            this.product = product;
        }

        public Long getQuantity() {
            return quantity;
        }
        public void setQuantity(Long quantity) {
            this.quantity = quantity;
        }

        public String getQuantityType() {
            return quantityType;
        }
        public void setQuantityType(String quantityType) {
            this.quantityType = quantityType;
        }
    }

    /**
     * https://apisb.etrade.com/docs/api/order/api-order-v1.html#/definitions/Lot
     */
    public static class Lot {

        Long id;
        Long size;

        public Long getId() {
            return id;
        }
        public void setId(Long id) {
            this.id = id;
        }

        public Long getSize() {
            return size;
        }
        public void setSize(Long size) {
            this.size = size;
        }
    }

    /**
     * Reference: https://apisb.etrade.com/docs/api/order/api-order-v1.html#/definitions/Lots
     */
    public static class Lots {

        @JsonProperty("Lot")
        private List<Lot> lotList;

        public List<Lot> getLotList() {
            return lotList;
        }
        public void newLotList(Long id, Long size) {
            Lot lot = new Lot();
            lot.setId(id);
            lot.setSize(size);
            this.lotList = new ArrayList<>();
            this.lotList.add(lot);
        }
        public void setLotList(List<Lot> lotList) {
            this.lotList = lotList;
        }
    }

    /**
     * https://apisb.etrade.com/docs/api/order/api-order-v1.html#/definitions/Product
     */
    public static class Product {

        String securityType;
        String symbol;

        public String getSecurityType() {
            return securityType;
        }
        public void setSecurityType(String securityType) {
            this.securityType = securityType;
        }

        public String getSymbol() {
            return symbol;
        }
        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }
    }
}
