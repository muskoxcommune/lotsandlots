package io.lotsandlots.etrade;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import io.lotsandlots.etrade.api.ApiResource;
import io.lotsandlots.etrade.oauth.EtradeRestTemplate;
import io.lotsandlots.etrade.oauth.model.Resource;
import io.lotsandlots.etrade.oauth.model.SecurityContext;
import io.lotsandlots.etrade.oauth.model.Signer;
import io.lotsandlots.util.ConfigWrapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
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

    private static final Config CONFIG = ConfigWrapper.getConfig();
    private static EtradeRestTemplateFactory TEMPLATE_FACTORY = null;

    private final ApiResource apiResource;
    private final ClientHttpRequestFactory clientHttpRequestFactory;
    private final SecurityContext securityContext;

    private EtradeRestTemplateFactory() throws GeneralSecurityException {

        apiResource = new ApiResource();
        apiResource.setAcctListUri(CONFIG.getString("etrade.accountListUri"));
        apiResource.setApiBaseUrl(CONFIG.getString("etrade.apiBaseUrl"));
        apiResource.setBalanceUri(CONFIG.getString("etrade.balanceUri"));
        apiResource.setPortfolioUri(CONFIG.getString("etrade.portfolioUri"));
        apiResource.setQuoteUri(CONFIG.getString("etrade.quoteUri"));

        clientHttpRequestFactory = newClientHttpRequestFactory();

        securityContext = new SecurityContext();
        Resource oauthResource = new Resource();
        oauthResource.setAccessTokenHttpMethod("GET");
        oauthResource.setAccessTokenUrl(CONFIG.getString("etrade.oauthBaseUrl") + CONFIG.getString("etrade.accessTokenUrl"));
        oauthResource.setAuthorizeUrl(CONFIG.getString("etrade.authorizeUrl"));
        oauthResource.setConsumerKey(CONFIG.getString("etrade.consumerKey"));
        oauthResource.setRequestTokenHttpMethod("GET");
        oauthResource.setRequestTokenUrl(CONFIG.getString("etrade.oauthBaseUrl") + CONFIG.getString("etrade.requestTokenUrl"));
        oauthResource.setSharedSecret(CONFIG.getString("etrade.consumerSecret"));
        oauthResource.setSignatureMethod(Signer.getSigner("HMAC-SHA1"));
        securityContext.setResource(oauthResource);
    }

    public ApiResource getApiResource() {
        return apiResource;
    }

    public static EtradeRestTemplateFactory getClient() {
        return TEMPLATE_FACTORY;
    }

    public ClientHttpRequestFactory getClientHttpRequestFactory() {
        return clientHttpRequestFactory;
    }

    public SecurityContext getSecurityContext() {
        return securityContext;
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

    public EtradeRestTemplate newCustomRestTemplate(){
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
        mapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);
        jsonConverter.setObjectMapper(mapper);
        return jsonConverter;
    }
}
