package io.lotsandlots.etrade.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class OrdersResponse {

    Long marker;
    String next;

    @JsonProperty("Order")
    List<Order> orderList;

    public Long getMarker() {
        return marker;
    }
    public boolean hasMarker() {
        return marker != null;
    }
    public void setMarker(Long marker) {
        this.marker = marker;
    }

    public String getNext() {
        return next;
    }
    public void setNext(String next) {
        this.next = next;
    }

    public List<Order> getOrderList() {
        return orderList;
    }
    public void setOrderList(List<Order> orderList) {
        this.orderList = orderList;
    }

    public static class Order {

        String details;

        @JsonProperty("OrderDetail")
        List<OrderDetail> orderDetailList;

        Long orderId;
        String orderType;

        public String getDetails() {
            return details;
        }
        public void setDetails(String details) {
            this.details = details;
        }

        public List<OrderDetail> getOrderDetailList() {
            return orderDetailList;
        }
        public void setOrderDetailList(List<OrderDetail> orderDetailList) {
            this.orderDetailList = orderDetailList;
        }

        public Long getOrderId() {
            return orderId;
        }
        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public String getOrderType() {
            return orderType;
        }
        public void setOrderType(String orderType) {
            this.orderType = orderType;
        }
    }

    public static class OrderDetail {

        @JsonProperty("Instrument")
        List<Instrument> instrumentList;

        String orderTerm;
        Float orderValue;
        Long placedTime;
        String priceType;
        String status;

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

            String orderAction;
            Long orderedQuantity;

            @JsonProperty("Product")
            Product product;

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
}
