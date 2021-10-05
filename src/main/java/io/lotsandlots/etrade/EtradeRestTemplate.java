package io.lotsandlots.etrade;

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
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class EtradeRestTemplate extends RestTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(EtradeRestTemplate.class);

    public EtradeRestTemplate(ClientHttpRequestFactory factory) {
        super(factory);
    }

    public <T> ResponseEntity<T> execute(Message message, Class<T> responseType) {
        String url;
        if ( StringUtils.isNotBlank(message.getQueryString())) {
            url = String.format("%s?%s", message.getUrl(),message.getQueryString());
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
                return super.exchange(url, HttpMethod.GET, entity, responseType);
            default:
                return null;
        }
    }

    @Deprecated
    public String execute(Message message) {
        String url;

        if ( StringUtils.isNotBlank(message.getQueryString())) {
            url = String.format("%s?%s", message.getUrl(),message.getQueryString());
        } else {
            url = message.getUrl();
        }
        UriComponents uriComponents = UriComponentsBuilder
                .fromHttpUrl(url)
                .queryParams(message.getHeaderMap())
                .build();

        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        if (StringUtils.isNotBlank(message.getOauthHeader())) {
            headers.add("Authorization", message.getOauthHeader());
        }
        switch (message.getHttpMethod()) {
            case "POST":
                HttpEntity<String> request = new HttpEntity<>(message.getBody(),headers);
                return super.postForObject(uriComponents.toString(), request, String.class);
            default:
                return "";
        }
    }
}