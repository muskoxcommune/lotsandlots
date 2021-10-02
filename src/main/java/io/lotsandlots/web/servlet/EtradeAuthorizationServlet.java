package io.lotsandlots.web.servlet;

import io.lotsandlots.etrade.EtradeRestTemplateFactory;
import io.lotsandlots.etrade.oauth.OAuth1Template;
import io.lotsandlots.etrade.Message;
import io.lotsandlots.etrade.oauth.OAuthToken;
import io.lotsandlots.etrade.oauth.SecurityContext;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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

@Api(value = "/etrade")
public class EtradeAuthorizationServlet extends HttpServlet implements EtradeServlet {

    private static final Logger LOG = LoggerFactory.getLogger(EtradeAuthorizationServlet.class);

    private Message tokenMessage;

    private void setTokenMessageOauthHeader(SecurityContext securityContext, Message tokenMessage)
            throws UnsupportedEncodingException, GeneralSecurityException {
        OAuth1Template oAuth1Template = new OAuth1Template(securityContext, tokenMessage);
        oAuth1Template.computeOauthSignature(
                securityContext.getOAuthConfig().getRequestTokenHttpMethod(),
                securityContext.getOAuthConfig().getRequestTokenUrl());
        tokenMessage.setOauthHeader(oAuth1Template.getAuthorizationHeader());
    }

    private OAuthToken getOauthToken(SecurityContext securityContext, Message tokenMessage)
            throws UnsupportedEncodingException, GeneralSecurityException{
        setTokenMessageOauthHeader(securityContext, tokenMessage);

        ResponseEntity<AuthorizationLinkedMultiValueMap> tokenMessageResponse =
                new AuthorizationRestTemplate(EtradeRestTemplateFactory.getClient().getClientHttpRequestFactory())
                        .execute(tokenMessage);
        MultiValueMap<String, String> tokenMessageResponseBody = tokenMessageResponse.getBody();

        OAuthToken token = new OAuthToken(
                tokenMessageResponseBody.getFirst("oauth_token"),
                tokenMessageResponseBody.getFirst("oauth_token_secret"));
        securityContext.put("TOKEN", token);
        return token;
    }

    @ApiOperation(
            httpMethod = "GET",
            value = "Resource for updating OAuth security context",
            nickname = "authorize")
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContext securityContext;

        String verifierCode = request.getParameter("verifier");
        if (verifierCode == null) {
            // If a verifier code is not provided, redirect to etrade to get one.
            LOG.info("Verifier code not provided, will redirect to etrade");
            securityContext = EtradeRestTemplateFactory.getClient().newSecurityContext();
            tokenMessage = new Message();
            tokenMessage.setRequiresOauth(true);
            tokenMessage.setHttpMethod(securityContext.getOAuthConfig().getRequestTokenHttpMethod());
            tokenMessage.setUrl(securityContext.getOAuthConfig().getRequestTokenUrl());
            LOG.info("Initialized tokenMessage: {}", tokenMessage);
            try {
                OAuthToken requestToken = getOauthToken(securityContext, tokenMessage);
                String url = String.format(
                        "%s?key=%s&token=%s",
                        securityContext.getOAuthConfig().getAuthorizeUrl(),
                        securityContext.getOAuthConfig().getConsumerKey(),
                        requestToken.getOauthToken());
                response.sendRedirect(url);
            } catch (Exception e) {
                LOG.error("Failed redirect to etrade", e);
                response.sendError(500, "Unable to redirect to etrade");
            }
        } else {
            // If a verifier code parameter is found, complete authorization flow.
            LOG.info("Verifier code found, verifier={}", verifierCode);
            securityContext = EtradeRestTemplateFactory.getClient().getSecurityContext();
            tokenMessage.setVerifierCode(verifierCode);
            tokenMessage.setHttpMethod(securityContext.getOAuthConfig().getAccessTokenHttpMethod());
            tokenMessage.setUrl(securityContext.getOAuthConfig().getAccessTokenUrl());
            LOG.info("Updated url and verifierCode, tokenMessage={}", tokenMessage);
            try {
                getOauthToken(securityContext, tokenMessage);
                securityContext.setInitialized(true);
                response.getWriter().print("Authorization completed at "
                        + DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").format(LocalDateTime.now()));
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
