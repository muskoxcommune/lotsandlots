package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.EtradeBuyOrderController;
import io.lotsandlots.etrade.EtradeOrdersDataFetcher;
import io.lotsandlots.etrade.EtradePortfolioDataFetcher;
import io.lotsandlots.etrade.EtradeSellOrderController;
import io.lotsandlots.etrade.oauth.EtradeOAuthClient;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.rest.Message;
import io.lotsandlots.etrade.oauth.OAuthToken;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.lotsandlots.util.TimeBoxedRunnableRunner;
import io.lotsandlots.web.listener.LifecycleListener;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Api(value = "/etrade")
public class EtradeAuthorizationServlet extends HttpServlet implements EtradeOAuthClient {

    private static final Logger LOG = LoggerFactory.getLogger(EtradeAuthorizationServlet.class);

    private boolean isInitialized = false;
    private Message tokenMessage;

    private OAuthToken getOauthToken(
            SecurityContext securityContext,
            Message tokenMessage,
            OAuthToken.TokenType tokenType)
            throws UnsupportedEncodingException, GeneralSecurityException{
        setOAuthHeader(securityContext, tokenMessage);

        ResponseEntity<AuthorizationLinkedMultiValueMap> tokenMessageResponse =
                new AuthorizationRestTemplate(EtradeRestTemplateFactory.getTemplateFactory().getClientHttpRequestFactory())
                        .execute(tokenMessage);
        MultiValueMap<String, String> tokenMessageResponseBody = tokenMessageResponse.getBody();

        OAuthToken token = new OAuthToken(
                tokenMessageResponseBody.getFirst("oauth_token"),
                tokenMessageResponseBody.getFirst("oauth_token_secret"),
                tokenType);
        securityContext.setToken(token);
        return token;
    }

    @ApiOperation(
            httpMethod = "GET",
            value = "Initialize or refresh E*Trade's OAuth tokens. "
                    + "A GET request without a 'verifier' query parameter will result in a redirect "
                    + "to E*Trade's authorization page. Once authorized, E*Trade will provide a "
                    + "Verifier Code. Use this code to make another GET request to complete "
                    + "authorization. Access tokens expire at midnight US Eastern time.",
            nickname = "authorize")
    @ApiImplicitParams(
            {@ApiImplicitParam(name = "verifier", dataType = "string", paramType = "query",
                               value = "Code provided by E*Trade after authorization.",
                               example = "XXXXX")})
    @ApiResponses({
            @ApiResponse(code = 200, message = "Security context is ready."),
            @ApiResponse(code = 302, message = "Redirect to E*Trade to get Verifier Code."),
            @ApiResponse(code = 500, message = "Unable to complete authorization.")})
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContext securityContext;

        String verifierCode = request.getParameter("verifier");
        if (StringUtils.isBlank(verifierCode)) {
            // If a verifier code is not provided, redirect to E*Trade to get one.
            LOG.info("Verifier code not provided, will redirect to E*Trade");
            securityContext = EtradeRestTemplateFactory.getTemplateFactory().newSecurityContext();
            EtradeRestTemplateFactory.getTemplateFactory().setSecurityContext(securityContext);
            tokenMessage = new Message();
            tokenMessage.setRequiresOauth(true);
            tokenMessage.setHttpMethod(securityContext.getOAuthConfig().getRequestTokenHttpMethod());
            tokenMessage.setUrl(securityContext.getOAuthConfig().getRequestTokenUrl());
            LOG.debug("Initialized tokenMessage: {}", tokenMessage);
            try {
                OAuthToken requestToken = getOauthToken(
                        securityContext, tokenMessage, OAuthToken.TokenType.REQUEST);
                String url = String.format(
                        "%s?key=%s&token=%s",
                        securityContext.getOAuthConfig().getAuthorizeUrl(),
                        securityContext.getOAuthConfig().getConsumerKey(),
                        requestToken.getOauthToken());
                response.sendRedirect(url);
            } catch (Exception e) {
                LOG.error("Failed redirect to E*Trade", e);
                response.sendError(500, "Unable to redirect to E*Trade");
            }
        } else {
            verifierCode = HtmlUtils.htmlEscape(verifierCode, "UTF-8");

            // If a verifier code parameter is found, complete authorization flow.
            LOG.info("Verifier code found, verifier={}", verifierCode);
            securityContext = EtradeRestTemplateFactory.getTemplateFactory().getSecurityContext();
            tokenMessage.setVerifierCode(verifierCode);
            tokenMessage.setHttpMethod(securityContext.getOAuthConfig().getAccessTokenHttpMethod());
            tokenMessage.setUrl(securityContext.getOAuthConfig().getAccessTokenUrl());
            LOG.debug("Updated url and verifierCode, tokenMessage={}", tokenMessage);
            try {
                getOauthToken(securityContext, tokenMessage, OAuthToken.TokenType.ACCESS);
                securityContext.setInitialized(true);
                response.getWriter().print("Authorization completed at "
                        + DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").format(LocalDateTime.now()));
                if (!isInitialized) {
                    LifecycleListener lifecycleListener = LifecycleListener.getListener();

                    // Initialize data fetchers and order controllers.

                    EtradeOrdersDataFetcher ordersDataFetcher = new EtradeOrdersDataFetcher();

                    TimeBoxedRunnableRunner<EtradeOrdersDataFetcher> etradeOrdersDataFetcherRunner =
                            new TimeBoxedRunnableRunner<>(
                                    ordersDataFetcher,
                                    0,
                                    ordersDataFetcher.getOrdersDataFetchIntervalSeconds(),
                                    TimeUnit.SECONDS);
                    lifecycleListener.setEtradeOrdersDataFetcherRunner(etradeOrdersDataFetcherRunner);

                    EtradePortfolioDataFetcher portfolioDataFetcher = new EtradePortfolioDataFetcher();
                    lifecycleListener.setBuyOrderController(
                            new EtradeBuyOrderController(portfolioDataFetcher, ordersDataFetcher));
                    lifecycleListener.setSellOrderController(
                            new EtradeSellOrderController(portfolioDataFetcher, ordersDataFetcher));

                    TimeBoxedRunnableRunner<EtradePortfolioDataFetcher> etradePortfolioDataFetcherRunner =
                            new TimeBoxedRunnableRunner<>(
                                    portfolioDataFetcher,
                                    0,
                                    portfolioDataFetcher.getPortfolioDataFetchIntervalSeconds(),
                                    TimeUnit.SECONDS);
                    lifecycleListener.setEtradePortfolioDataFetcherRunner(etradePortfolioDataFetcherRunner);

                    isInitialized = true;
                }
            } catch (Exception e) {
                LOG.error("Failed to initialize SecurityContext", e);
                response.sendError(500, "Unable to complete authorization");
            }
        }
    }

    static class AuthorizationLinkedMultiValueMap extends LinkedMultiValueMap<String, String> {
    }

    static class AuthorizationRestTemplate extends RestTemplate {

        public AuthorizationRestTemplate(ClientHttpRequestFactory factory) {
            super(factory);
        }

        public ResponseEntity<AuthorizationLinkedMultiValueMap> execute(Message message) {
            MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
            headers.add("Content-type", MediaType.APPLICATION_FORM_URLENCODED.toString());
            headers.add("Authorization", message.getOauthHeader());

            UriComponents uriComponents = UriComponentsBuilder
                    .fromHttpUrl(message.getUrl())
                    .build();
            return super.exchange(
                    uriComponents.toString(),
                    HttpMethod.valueOf(message.getHttpMethod()),
                    new HttpEntity<>(headers),
                    AuthorizationLinkedMultiValueMap.class
            );
        }
    }
}
