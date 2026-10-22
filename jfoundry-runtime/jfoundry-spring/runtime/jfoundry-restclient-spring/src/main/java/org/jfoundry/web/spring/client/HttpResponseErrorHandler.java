package org.jfoundry.web.spring.client;

import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;

import java.io.IOException;
import java.net.URI;

/// Translates HTTP error responses and retains the original response body.
public final class HttpResponseErrorHandler extends DefaultResponseErrorHandler {

    @Override
    public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
        throw new HttpResponseException(response.getStatusCode(), response.getBody().readAllBytes(),
                response.getHeaders().getContentType());
    }
}
