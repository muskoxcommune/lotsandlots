package io.lotsandlots.etrade.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class OrderDetail {

    @JsonProperty("Instrument")
    List<Instrument> instrumentList;

    Float limitPrice;
    String orderTerm;
    Float orderValue;
    Long placedTime;
    String priceType;
    String status;

    public Float getLimitPrice() {
        return limitPrice;
    }
    public void setLimitPrice(Float limitPrice) {
        this.limitPrice = limitPrice;
    }

    public List<Instrument> getInstrumentList() {
        return instrumentList;
    }
    public void setInstrumentList(List<Instrument> instrumentList) {
        this.instrumentList = instrumentList;
    }

    public String getOrderTerm() {
        return orderTerm;
    }
    public void setOrderTerm(String orderTerm) {
        this.orderTerm = orderTerm;
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

    public static class Instrument {

        Float filledQuantity;
        String orderAction;
        Long orderedQuantity;

        @JsonProperty("Product")
        Product product;

        public Float getFilledQuantity() {
            return filledQuantity;
        }
        public void setFilledQuantity(Float filledQuantity) {
            this.filledQuantity = filledQuantity;
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

        public static class Product {

            String symbol;

            public String getSymbol() {
                return symbol;
            }
            public void setSymbol(String symbol) {
                this.symbol = symbol;
            }
        }
    }
}
