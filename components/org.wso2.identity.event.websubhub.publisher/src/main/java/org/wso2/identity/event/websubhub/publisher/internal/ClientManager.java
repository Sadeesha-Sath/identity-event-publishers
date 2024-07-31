/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.identity.event.websubhub.publisher.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.ssl.SSLContexts;
import org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants;
import org.wso2.identity.event.websubhub.publisher.exception.WebSubAdapterException;
import org.wso2.identity.event.websubhub.publisher.util.WebSubHubAdapterUtil;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

import static java.util.Objects.isNull;

/**
 * Class to retrieve the HTTP Clients.
 */
public class ClientManager {

    private static final Log LOG = LogFactory.getLog(ClientManager.class);
    private final CloseableHttpAsyncClient httpAsyncClient;

    /**
     * Creates a client manager.
     *
     * @throws WebSubAdapterException on errors while creating the http client.
     */
    public ClientManager() throws WebSubAdapterException {

        LOG.info("Initializing ClientManager");
        PoolingNHttpClientConnectionManager connectionManager;
        try {
            connectionManager = createPoolingConnectionManager();
            LOG.info("Successfully created PoolingNHttpClientConnectionManager");
        } catch (IOException e) {
            throw WebSubHubAdapterUtil.handleServerException
                    (WebSubHubAdapterConstants.ErrorMessages.ERROR_CREATING_ASYNC_HTTP_CLIENT, e);
        }

        RequestConfig config = createRequestConfig();
        HttpAsyncClientBuilder httpClientBuilder = HttpAsyncClients.custom().setDefaultRequestConfig(config);
        addSslContext(httpClientBuilder);
        httpClientBuilder.setConnectionManager(connectionManager);
        httpAsyncClient = httpClientBuilder.build();
        httpAsyncClient.start();
        LOG.info("HttpAsyncClient started");
    }

    /**
     * Get HTTP client properly configured with tenant configurations.
     *
     * @return CloseableHttpAsyncClient instance.
     */
    public CloseableHttpAsyncClient getClient() throws WebSubAdapterException {

        if (isNull(httpAsyncClient)) {
            LOG.error("HttpAsyncClient is null");
            throw WebSubHubAdapterUtil.handleServerException
                    (WebSubHubAdapterConstants.ErrorMessages.ERROR_GETTING_ASYNC_CLIENT, null);
        } else if (!httpAsyncClient.isRunning()) {
            LOG.warn("HttpAsyncClient is not running, starting client");
            httpAsyncClient.start();
        }
        return httpAsyncClient;
    }

    private RequestConfig createRequestConfig() {

        return RequestConfig.custom()
                .setConnectTimeout(WebSubHubAdapterDataHolder.getInstance().getAdapterConfiguration()
                        .getHTTPConnectionTimeout())
                .setConnectionRequestTimeout(WebSubHubAdapterDataHolder.getInstance().getAdapterConfiguration()
                        .getHttpConnectionRequestTimeout())
                .setSocketTimeout(
                        WebSubHubAdapterDataHolder.getInstance().getAdapterConfiguration().getHttpReadTimeout())
                .setRedirectsEnabled(false)
                .setRelativeRedirectsAllowed(false)
                .build();
    }

    private PoolingNHttpClientConnectionManager createPoolingConnectionManager() throws IOException {

        int maxConnections = WebSubHubAdapterDataHolder.getInstance().getAdapterConfiguration()
                .getDefaultMaxConnections();
        int maxConnectionsPerRoute = WebSubHubAdapterDataHolder.getInstance().getAdapterConfiguration()
                .getDefaultMaxConnectionsPerRoute();

        ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor();
        PoolingNHttpClientConnectionManager poolingHttpClientConnectionMgr = new
                PoolingNHttpClientConnectionManager(ioReactor);
        // Increase max total connection to 20.
        poolingHttpClientConnectionMgr.setMaxTotal(maxConnections);
        // Increase default max connection per route to 20.
        poolingHttpClientConnectionMgr.setDefaultMaxPerRoute(maxConnectionsPerRoute);
        LOG.debug("PoolingNHttpClientConnectionManager created with maxConnections: " + maxConnections +
                " and maxConnectionsPerRoute: " + maxConnectionsPerRoute);
        return poolingHttpClientConnectionMgr;
    }

    private void addSslContext(HttpAsyncClientBuilder builder) throws WebSubAdapterException {

        try {
            SSLContext sslContext = SSLContexts.custom()
            //default trust strategy is used (trusting all certificates in the provided trust store).
                    .loadTrustMaterial(WebSubHubAdapterDataHolder.getInstance().getTrustStore(), null)
                    .build();
            builder.setSSLContext(sslContext);
            builder.setSSLHostnameVerifier(new DefaultHostnameVerifier());
            LOG.debug("SSL context and hostname verifier added");
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw WebSubHubAdapterUtil.handleServerException
                    (WebSubHubAdapterConstants.ErrorMessages.ERROR_CREATING_SSL_CONTEXT, e);
        }
    }
}
