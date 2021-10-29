package io.lotsandlots.etrade.rest;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public class EtradeRestTemplate extends RestTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(EtradeRestTemplate.class);

    public EtradeRestTemplate(ClientHttpRequestFactory factory) {
        super(factory);
    }

    public <T> ResponseEntity<T> doGet(Message message, Class<T> responseType) {
        HttpHeaders httpHeaders = httpHeadersFromMessage(message);
        String url = urlStringFromMessage(message);
        LOG.debug("Executing GET Message, url={} headers={}", url, httpHeaders);
        return doGetExchange(url, new HttpEntity<>(httpHeaders), responseType);
    }

    @VisibleForTesting
    <T> ResponseEntity<T> doGetExchange(String url, HttpEntity<String> httpEntity, Class<T> responseType) {
        return super.exchange(url, HttpMethod.GET, httpEntity, responseType);
    }

    public <T> ResponseEntity<T> doPost(Message message, Object payload, Class<T> responseType) {
        HttpHeaders httpHeaders = httpHeadersFromMessage(message);
        String url = urlStringFromMessage(message);
        LOG.debug("Executing POST Message, url={} headers={} payload={}", url, httpHeaders, payload);
        return doPost(url, new HttpEntity<>(payload, httpHeaders), responseType);
    }

    @VisibleForTesting
    <T> ResponseEntity<T> doPost(String url, HttpEntity<Object> httpEntity, Class<T> responseType) {
        return super.exchange(url, HttpMethod.POST, httpEntity, responseType);
    }

    HttpHeaders httpHeadersFromMessage(Message message) {
        HttpHeaders httpHeaders = new HttpHeaders();
        if (StringUtils.isNotBlank(message.getOauthHeader())) {
            httpHeaders.add("Authorization", message.getOauthHeader());
        }
        return httpHeaders;
    }

    String urlStringFromMessage(Message message) {
        if ( StringUtils.isNotBlank(message.getQueryString())) {
            return String.format("%s?%s", message.getUrl(), message.getQueryString());
        } else {
            return message.getUrl();
        }
    }
}