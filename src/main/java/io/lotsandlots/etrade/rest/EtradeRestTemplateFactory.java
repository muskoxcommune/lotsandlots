package io.lotsandlots.etrade.rest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import io.lotsandlots.etrade.api.ApiConfig;
import io.lotsandlots.etrade.oauth.OAuthConfig;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.util.ConfigWrapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.MultiValueMap;

import javax.net.ssl.SSLContext;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class EtradeRestTemplateFactory {

    private static final Logger LOG = LoggerFactory.getLogger(EtradeRestTemplateFactory.class);
    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static EtradeRestTemplateFactory TEMPLATE_FACTORY = null;

    private final ApiConfig apiConfig;
    private final ClientHttpRequestFactory clientHttpRequestFactory;

    private SecurityContext securityContext;

    EtradeRestTemplateFactory() throws GeneralSecurityException {
        try {
            apiConfig = new ApiConfig();
            if (CONFIG.hasPath("etrade.accountIdKey")) {
                apiConfig.setAccountIdKey(CONFIG.getString("etrade.accountIdKey"));
                apiConfig.setOrdersCancelUrl(CONFIG.getString("etrade.ordersCancelUrl")
                        .replace("<etrade.accountIdKey>", apiConfig.getAccountIdKey()));
                apiConfig.setOrdersPlaceUrl(CONFIG.getString("etrade.ordersPlaceUrl")
                        .replace("<etrade.accountIdKey>", apiConfig.getAccountIdKey()));
                apiConfig.setOrdersPreviewUrl(CONFIG.getString("etrade.ordersPreviewUrl")
                        .replace("<etrade.accountIdKey>", apiConfig.getAccountIdKey()));
                apiConfig.setOrdersUrl(CONFIG.getString("etrade.ordersUrl")
                        .replace("<etrade.accountIdKey>", apiConfig.getAccountIdKey()));
                apiConfig.setPortfolioUrl(CONFIG.getString("etrade.portfolioUrl")
                        .replace("<etrade.accountIdKey>", apiConfig.getAccountIdKey()));
            } else {
                apiConfig.setAccountIdKey(null);
                apiConfig.setOrdersUrl(null);
                apiConfig.setPortfolioUrl(null);
            }
            apiConfig.setAccountListUrl(CONFIG.getString("etrade.accountListUrl"));
            apiConfig.setBaseUrl(CONFIG.getString("etrade.apiBaseUrl"));
            apiConfig.setOrdersQueryString(CONFIG.getString("etrade.ordersQueryParams"));
            apiConfig.setPortfolioQueryString(CONFIG.getString("etrade.portfolioQueryParams"));
            apiConfig.setQuoteUrl(CONFIG.getString("etrade.quoteUrl"));

            clientHttpRequestFactory = newClientHttpRequestFactory();

            securityContext = newSecurityContext();
        } catch (Exception e) {
            LOG.error("Failed to initialize EtradeRestTemplateFactory", e);
            throw e;
        }
    }

    public ApiConfig getApiConfig() {
        return apiConfig;
    }

    public static EtradeRestTemplateFactory getTemplateFactory() {
        return TEMPLATE_FACTORY;
    }

    public ClientHttpRequestFactory getClientHttpRequestFactory() {
        return clientHttpRequestFactory;
    }

    public SecurityContext getSecurityContext() {
        return securityContext;
    }
    public SecurityContext newSecurityContext() {
        SecurityContext newSecurityContext = new SecurityContext();
        OAuthConfig oauthOAuthConfig = new OAuthConfig();
        oauthOAuthConfig.setAccessTokenHttpMethod("GET");
        oauthOAuthConfig.setAccessTokenUrl(CONFIG.getString("etrade.accessTokenUrl"));
        oauthOAuthConfig.setAuthorizeUrl(CONFIG.getString("etrade.authorizeUrl"));
        oauthOAuthConfig.setConsumerKey(CONFIG.getString("etrade.consumerKey"));
        oauthOAuthConfig.setRequestTokenHttpMethod("GET");
        oauthOAuthConfig.setRequestTokenUrl(CONFIG.getString("etrade.requestTokenUrl"));
        oauthOAuthConfig.setSharedSecret(CONFIG.getString("etrade.consumerSecret"));
        newSecurityContext.setOAuthConfig(oauthOAuthConfig);
        return newSecurityContext;
    }
    public void setSecurityContext(SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    public static void init() throws GeneralSecurityException {
        if (TEMPLATE_FACTORY == null) {
            TEMPLATE_FACTORY =  new EtradeRestTemplateFactory();
        }
    }

    private ClientHttpRequestFactory newClientHttpRequestFactory() throws GeneralSecurityException {
        int timeout = 30000;
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .setSocketTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .setRedirectsEnabled(true)
                .build();
        SSLContext sslContext = org.apache.http.ssl.SSLContexts
                .custom()
                .loadTrustMaterial(null, (x509Certificates, s) -> true)
                .build();
        SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
        CloseableHttpClient client = HttpClientBuilder
                .create()
                .setDefaultRequestConfig(config)
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setSSLSocketFactory(csf)
                .build();
        return new HttpComponentsClientHttpRequestFactory(client);
    }

    public EtradeRestTemplate newCustomRestTemplate() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>(2);
        converters.add(new FormHttpMessageConverter() {
            public boolean canRead(Class<?> clazz, MediaType mediaType) {
                return MultiValueMap.class.isAssignableFrom(clazz);
            }
        });
        converters.add(new StringHttpMessageConverter());
        converters.add(newMappingJackson2HttpMessageConverter());

        EtradeRestTemplate oauthTemplate = new EtradeRestTemplate(clientHttpRequestFactory);
        oauthTemplate.setMessageConverters(converters);
        return oauthTemplate;
    }

    public MappingJackson2HttpMessageConverter newMappingJackson2HttpMessageConverter() {
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        List<MediaType> mediaTypeList = new ArrayList<>();
        mediaTypeList.add(MediaType.APPLICATION_JSON);
        jsonConverter.setSupportedMediaTypes(mediaTypeList);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
              .configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);
        jsonConverter.setObjectMapper(mapper);
        return jsonConverter;
    }
}
