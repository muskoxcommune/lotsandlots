package io.lotsandlots.etrade.rest;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

public class EtradeRestTemplate extends RestTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(EtradeRestTemplate.class);

    public EtradeRestTemplate(ClientHttpRequestFactory factory) {
        super(factory);
    }

    public <T> ResponseEntity<T> execute(Message message, Class<T> responseType) {
        String url;
        if ( StringUtils.isNotBlank(message.getQueryString())) {
            url = String.format("%s?%s", message.getUrl(), message.getQueryString());
        } else {
            url = message.getUrl();
        }
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        if (StringUtils.isNotBlank(message.getOauthHeader())) {
            headers.add("Authorization", message.getOauthHeader());
        }
        LOG.debug("Executing Message, method={} url={} headers={}", message.getHttpMethod(), url, headers);
        switch (message.getHttpMethod()) {
            case "GET":
                HttpEntity<String> entity = new HttpEntity<>(headers);
                return doExchange(url, HttpMethod.GET, entity, responseType);
            default:
                return null;
        }
    }

   <T> ResponseEntity<T>  doExchange(String url, HttpMethod httpMethod, HttpEntity<String> headers, Class<T> responseType) {
        // Wrapper for easier testing
        return super.exchange(url, httpMethod, headers, responseType);
   }
}