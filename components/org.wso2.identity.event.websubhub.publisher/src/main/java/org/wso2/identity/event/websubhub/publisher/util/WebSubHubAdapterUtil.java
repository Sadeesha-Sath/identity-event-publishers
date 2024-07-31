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

package org.wso2.identity.event.websubhub.publisher.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.MDC;
import org.wso2.carbon.identity.central.log.mgt.utils.LoggerUtils;
import org.wso2.carbon.utils.DiagnosticLog;
import org.wso2.identity.event.common.publisher.model.EventContext;
import org.wso2.identity.event.common.publisher.model.SecurityEventTokenPayload;
import org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants;
import org.wso2.identity.event.websubhub.publisher.exception.WebSubAdapterClientException;
import org.wso2.identity.event.websubhub.publisher.exception.WebSubAdapterException;
import org.wso2.identity.event.websubhub.publisher.exception.WebSubAdapterServerException;
import org.wso2.identity.event.websubhub.publisher.internal.WebSubHubAdapterDataHolder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils.CORRELATION_ID_MDC;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.ErrorMessages.ERROR_BACKEND_ERROR_FROM_WEBSUB_HUB;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.ErrorMessages.ERROR_EMPTY_RESPONSE_FROM_WEBSUB_HUB;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.ErrorMessages.ERROR_INVALID_RESPONSE_FROM_WEBSUB_HUB;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.ErrorMessages.ERROR_INVALID_WEB_SUB_HUB_BASE_URL;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.ErrorMessages.ERROR_PUBLISHING_EVENT_INVALID_PAYLOAD;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.ErrorMessages.TOPIC_DEREGISTRATION_FAILURE_ACTIVE_SUBS;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.CORRELATION_ID_REQUEST_HEADER;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.DEREGISTER;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.ERROR_TOPIC_DEREG_FAILURE_ACTIVE_SUBS;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.HUB_ACTIVE_SUBS;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.HUB_MODE;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.HUB_REASON;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.HUB_TOPIC;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.PUBLISH;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.REGISTER;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.RESPONSE_FOR_SUCCESSFUL_OPERATION;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.URL_KEY_VALUE_SEPARATOR;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.URL_PARAM_SEPARATOR;

/**
 * This class contains the utility method implementations required by WebSub Hub outbound adapter.
 */
public class WebSubHubAdapterUtil {

    private static final Log log = LogFactory.getLog(WebSubHubAdapterUtil.class);

    private WebSubHubAdapterUtil() {}

    /**
     * Make an asynchronous API call to the WebSub Hub.
     *
     * @param eventPayload      Security event token payload.
     * @param eventContext      Event context.
     * @param topic             Topic.
     * @param webSubHubBaseUrl  WebSub Hub base URL.
     * @throws WebSubAdapterException If an error occurs while making the API call.
     */
    public static void makeAsyncAPICall(SecurityEventTokenPayload eventPayload, EventContext eventContext,
                                        String topic, String webSubHubBaseUrl) throws WebSubAdapterException {

        String url = buildURL(topic, webSubHubBaseUrl, PUBLISH);
        HttpPost request = createHttpPost(url, eventPayload);

        logPublishingEvent(url, eventContext, topic);

        CloseableHttpAsyncClient client = WebSubHubAdapterDataHolder.getInstance().getClientManager().getClient();
        final long requestStartTime = System.currentTimeMillis();

        client.execute(request, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(final HttpResponse response) {

                handleAsyncResponse(response, request, requestStartTime, eventContext, url, topic);
            }

            @Override
            public void failed(final Exception ex) {

                handleResponseCorrelationLog(request, requestStartTime,
                        WebSubHubCorrelationLogUtils.RequestStatus.FAILED.getStatus(),
                        ex.getMessage());
                log.error("Publishing event data to WebSubHub failed. ", ex);
            }

            @Override
            public void cancelled() {

                handleResponseCorrelationLog(request, requestStartTime,
                        WebSubHubCorrelationLogUtils.RequestStatus.CANCELLED.getStatus());
                log.error("Publishing event data to WebSubHub cancelled.");
            }
        });
    }

    /**
     * Make a topic management API call to the WebSub Hub.
     *
     * @param topic             Topic.
     * @param webSubHubBaseUrl  WebSub Hub base URL.
     * @param operation         Operation.
     * @throws IOException If an error occurs while making the API call.
     * @throws WebSubAdapterException If an error occurs while making the API call.
     */
    public static void makeTopicMgtAPICall(String topic, String webSubHubBaseUrl, String operation)
            throws IOException, WebSubAdapterException {

        String topicMgtUrl = buildURL(topic, webSubHubBaseUrl, operation);

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().useSystemProperties().build()) {
            HttpPost httpPost = new HttpPost(topicMgtUrl);
            httpPost.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());

            WebSubHubCorrelationLogUtils.triggerCorrelationLogForRequest(httpPost);
            final long requestStartTime = System.currentTimeMillis();

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                handleTopicMgtResponse(response, httpPost, topic, operation, requestStartTime);
            }
        }
    }

    private static HttpPost createHttpPost(String url, SecurityEventTokenPayload eventPayload)
            throws WebSubAdapterException {

        HttpPost request = new HttpPost(url);
        request.setHeader(ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
        request.setHeader(CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        request.setHeader(CORRELATION_ID_REQUEST_HEADER, getCorrelationID());

        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        try {
            String jsonString = mapper.writeValueAsString(eventPayload);
            request.setEntity(new StringEntity(jsonString));
        } catch (IOException e) {
            throw handleClientException(ERROR_PUBLISHING_EVENT_INVALID_PAYLOAD);
        }

        return request;
    }

    private static void logPublishingEvent(String url, EventContext eventContext, String topic) {

        log.debug("Publishing event data to WebSubHub. URL: " + url + " tenant domain: " +
                eventContext.getTenantDomain());
        if (LoggerUtils.isDiagnosticLogsEnabled()) {
            DiagnosticLog.DiagnosticLogBuilder diagnosticLogBuilder = new DiagnosticLog.DiagnosticLogBuilder(
                    WebSubHubAdapterConstants.LogConstants.WEB_SUB_HUB_ADAPTER,
                    WebSubHubAdapterConstants.LogConstants.ActionIDs.PUBLISH_EVENT);
            diagnosticLogBuilder
                    .inputParam(WebSubHubAdapterConstants.LogConstants.InputKeys.URL, url)
                    .inputParam(WebSubHubAdapterConstants.LogConstants.InputKeys.TENANT_DOMAIN,
                            eventContext.getTenantDomain())
                    .inputParam(WebSubHubAdapterConstants.LogConstants.InputKeys.TOPIC, topic)
                    .resultMessage("Publishing event data to WebSubHub.")
                    .resultStatus(DiagnosticLog.ResultStatus.SUCCESS)
                    .logDetailLevel(DiagnosticLog.LogDetailLevel.INTERNAL_SYSTEM);
            LoggerUtils.triggerDiagnosticLogEvent(diagnosticLogBuilder);
        }
    }

    private static void handleAsyncResponse(HttpResponse response, HttpPost request, long requestStartTime,
                                            EventContext eventContext, String url, String topic) {

        int responseCode = response.getStatusLine().getStatusCode();
        String responsePhrase = response.getStatusLine().getReasonPhrase();
        log.debug("WebSubHub request completed. Response code: " + responseCode);
        handleResponseCorrelationLog(request, requestStartTime,
                WebSubHubCorrelationLogUtils.RequestStatus.COMPLETED.getStatus(),
                String.valueOf(responseCode), responsePhrase);

        if (responseCode == HttpStatus.SC_OK || responseCode == HttpStatus.SC_CREATED ||
                responseCode == HttpStatus.SC_ACCEPTED || responseCode == HttpStatus.SC_NO_CONTENT) {
            logDiagnosticSuccess(eventContext, url, topic);
            try {
                log.debug("Response data: " + EntityUtils.toString(response.getEntity()));
            } catch (IOException e) {
                log.debug("Error while reading WebSubHub event publisher response. ", e);
            }
        } else {
            logDiagnosticFailure(eventContext, url, topic);
            try {
                String errorResponseBody = EntityUtils.toString(response.getEntity());
                log.error("WebHubSub event publisher received " + responseCode + " code. Response data: " +
                        errorResponseBody);
            } catch (IOException e) {
                log.error("Error while reading WebSubHub event publisher response. ", e);
            }
        }
    }

    private static void handleTopicMgtResponse(CloseableHttpResponse response, HttpPost httpPost,
                                               String topic, String operation, long requestStartTime)
            throws IOException, WebSubAdapterException {

        StatusLine statusLine = response.getStatusLine();
        int responseCode = statusLine.getStatusCode();
        String responsePhrase = statusLine.getReasonPhrase();

        if (responseCode == HttpStatus.SC_OK) {
            handleSuccessfulTopicMgt(response, httpPost, topic, operation, requestStartTime,
                    responseCode, responsePhrase);
        } else if ((responseCode == HttpStatus.SC_CONFLICT && operation.equals(REGISTER)) ||
                (responseCode == HttpStatus.SC_NOT_FOUND && operation.equals(DEREGISTER))) {
            handleConflictOrNotFound(response, httpPost, topic, operation, requestStartTime,
                    responseCode, responsePhrase);
        } else {
            handleFailedTopicMgt(response, httpPost, topic, operation, requestStartTime,
                    responseCode, responsePhrase);
        }
    }

    private static void logDiagnosticSuccess(EventContext eventContext, String url, String topic) {

        if (LoggerUtils.isDiagnosticLogsEnabled()) {
            DiagnosticLog.DiagnosticLogBuilder diagnosticLogBuilder = new DiagnosticLog
                    .DiagnosticLogBuilder(WebSubHubAdapterConstants.LogConstants.WEB_SUB_HUB_ADAPTER,
                    WebSubHubAdapterConstants.LogConstants.ActionIDs.PUBLISH_EVENT);
            diagnosticLogBuilder
                    .inputParam(WebSubHubAdapterConstants.LogConstants.InputKeys.URL, url)
                    .inputParam(WebSubHubAdapterConstants.LogConstants.InputKeys.TENANT_DOMAIN,
                            eventContext.getTenantDomain())
                    .inputParam(WebSubHubAdapterConstants.LogConstants.InputKeys.TOPIC, topic)
                    .resultMessage("Event data published to WebSubHub.")
                    .resultStatus(DiagnosticLog.ResultStatus.SUCCESS)
                    .logDetailLevel(DiagnosticLog.LogDetailLevel.INTERNAL_SYSTEM);
            LoggerUtils.triggerDiagnosticLogEvent(diagnosticLogBuilder);
        }
    }

    private static void logDiagnosticFailure(EventContext eventContext, String url, String topic) {

        if (LoggerUtils.isDiagnosticLogsEnabled()) {
            DiagnosticLog.DiagnosticLogBuilder diagnosticLogBuilder = new DiagnosticLog
                    .DiagnosticLogBuilder(WebSubHubAdapterConstants.LogConstants.WEB_SUB_HUB_ADAPTER,
                    WebSubHubAdapterConstants.LogConstants.ActionIDs.PUBLISH_EVENT);
            diagnosticLogBuilder
                    .inputParam(WebSubHubAdapterConstants.LogConstants.InputKeys.URL, url)
                    .inputParam(WebSubHubAdapterConstants.LogConstants.InputKeys.TENANT_DOMAIN,
                            eventContext.getTenantDomain())
                    .inputParam(WebSubHubAdapterConstants.LogConstants.InputKeys.TOPIC, topic)
                    .resultMessage("Failed to publish event data to WebSubHub.")
                    .resultStatus(DiagnosticLog.ResultStatus.FAILED)
                    .logDetailLevel(DiagnosticLog.LogDetailLevel.INTERNAL_SYSTEM);
            LoggerUtils.triggerDiagnosticLogEvent(diagnosticLogBuilder);
        }
    }

    private static void handleSuccessfulTopicMgt(CloseableHttpResponse response, HttpPost httpPost, String topic,
                                                 String operation, long requestStartTime, int responseCode,
                                                 String responsePhrase) throws IOException, WebSubAdapterException {

        HttpEntity entity = response.getEntity();
        WebSubHubCorrelationLogUtils.triggerCorrelationLogForResponse(httpPost, requestStartTime,
                WebSubHubCorrelationLogUtils.RequestStatus.COMPLETED.getStatus(),
                String.valueOf(responseCode), responsePhrase);

        if (entity != null) {
            String responseString = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            if (RESPONSE_FOR_SUCCESSFUL_OPERATION.equals(responseString)) {
                log.debug("Success WebSub Hub operation: " + operation + ", topic: " + topic);
            } else {
                throw handleServerException(ERROR_INVALID_RESPONSE_FROM_WEBSUB_HUB, null,
                        topic, operation, responseString);
            }
        } else {
            String message = String.format(ERROR_EMPTY_RESPONSE_FROM_WEBSUB_HUB.getDescription(), topic, operation);
            throw new WebSubAdapterServerException(message, ERROR_EMPTY_RESPONSE_FROM_WEBSUB_HUB.getCode());
        }
    }

    private static void handleConflictOrNotFound(CloseableHttpResponse response, HttpPost httpPost, String topic,
                                                 String operation, long requestStartTime,
                                                 int responseCode, String responsePhrase) throws IOException {

        HttpEntity entity = response.getEntity();
        String responseString = "";
        WebSubHubCorrelationLogUtils.triggerCorrelationLogForResponse(httpPost, requestStartTime,
                WebSubHubCorrelationLogUtils.RequestStatus.FAILED.getStatus(),
                String.valueOf(responseCode), responsePhrase);
        if (entity != null) {
            responseString = EntityUtils.toString(entity, StandardCharsets.UTF_8);
        }
        log.warn(String.format(ERROR_INVALID_RESPONSE_FROM_WEBSUB_HUB.getDescription(),
                topic, operation, responseString));
    }

    private static void handleFailedTopicMgt(CloseableHttpResponse response, HttpPost httpPost, String topic,
                                             String operation, long requestStartTime, int responseCode,
                                             String responsePhrase) throws IOException, WebSubAdapterException {

        WebSubHubCorrelationLogUtils.triggerCorrelationLogForResponse(httpPost, requestStartTime,
                WebSubHubCorrelationLogUtils.RequestStatus.CANCELLED.getStatus(),
                String.valueOf(responseCode), responsePhrase);

        if (responseCode == HttpStatus.SC_FORBIDDEN) {
            handleForbiddenResponse(response, topic);
        }

        HttpEntity entity = response.getEntity();
        String responseString = "";
        if (entity != null) {
            responseString = EntityUtils.toString(entity, StandardCharsets.UTF_8);
        }
        String message = String.format(ERROR_BACKEND_ERROR_FROM_WEBSUB_HUB.getDescription(),
                topic, operation, responseString);
        log.error(message + ", Response code:" + responseCode);
        throw new WebSubAdapterServerException(message, ERROR_BACKEND_ERROR_FROM_WEBSUB_HUB.getCode());
    }

    private static void handleForbiddenResponse(CloseableHttpResponse response, String topic) throws IOException,
            WebSubAdapterException {

        Map<String, String> hubResponse = parseEventHubResponse(response);
        if (!hubResponse.isEmpty() && hubResponse.containsKey(HUB_REASON)) {
            String errorMsg = String.format(ERROR_TOPIC_DEREG_FAILURE_ACTIVE_SUBS, topic);
            if (errorMsg.equals(hubResponse.get(HUB_REASON))) {
                log.info(String.format(TOPIC_DEREGISTRATION_FAILURE_ACTIVE_SUBS.getDescription(),
                        topic, hubResponse.get(HUB_ACTIVE_SUBS)));
                throw handleClientException(TOPIC_DEREGISTRATION_FAILURE_ACTIVE_SUBS, topic,
                        hubResponse.get(HUB_ACTIVE_SUBS));
            }
        }
    }

    private static String buildURL(String topic, String webSubHubBaseUrl, String operation)
            throws WebSubAdapterServerException {

        try {
            URIBuilder uriBuilder = new URIBuilder(webSubHubBaseUrl);
            uriBuilder.addParameter(HUB_MODE, operation);
            uriBuilder.addParameter(HUB_TOPIC, topic);
            return uriBuilder.build().toString();
        } catch (URISyntaxException e) {
            log.error("Error building URL", e);
            throw handleServerException(ERROR_INVALID_WEB_SUB_HUB_BASE_URL, e);
        }
    }

    private static String getCorrelationID() {

        String correlationID = MDC.get(CORRELATION_ID_MDC);
        if (StringUtils.isBlank(correlationID)) {
            correlationID = UUID.randomUUID().toString();
            MDC.put(CORRELATION_ID_MDC, correlationID);
        }
        return correlationID;
    }

    /**
     * Handle client exceptions.
     *
     * @param error Error message.
     * @param data  Data.
     * @return WebSubAdapterClientException.
     */
    public static WebSubAdapterClientException handleClientException(
            WebSubHubAdapterConstants.ErrorMessages error, String... data) {

        String description = error.getDescription();
        if (ArrayUtils.isNotEmpty(data)) {
            description = String.format(description, data);
        }
        return new WebSubAdapterClientException(error.getMessage(), description, error.getCode());
    }

    /**
     * Handle server exceptions.
     *
     * @param error     Error message.
     * @param throwable Throwable.
     * @param data      Data.
     * @return WebSubAdapterServerException.
     */
    public static WebSubAdapterServerException handleServerException(
            WebSubHubAdapterConstants.ErrorMessages error, Throwable throwable, String... data) {

        String description = error.getDescription();
        if (ArrayUtils.isNotEmpty(data)) {
            description = String.format(description, data);
        }
        return new WebSubAdapterServerException(error.getMessage(), description, error.getCode(), throwable);
    }

    private static Map<String, String> parseEventHubResponse(CloseableHttpResponse response) throws IOException {

        Map<String, String> map = new HashMap<>();
        HttpEntity entity = response.getEntity();

        if (entity != null) {
            String responseContent = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            log.debug("Parsing response content from event hub: " + responseContent);
            String[] responseParams = responseContent.split(URL_PARAM_SEPARATOR);
            for (String param : responseParams) {
                String[] keyValuePair = param.split(URL_KEY_VALUE_SEPARATOR);
                if (keyValuePair.length == 2) {
                    map.put(keyValuePair[0], keyValuePair[1]);
                }
            }
        }
        return map;
    }

    private static void handleResponseCorrelationLog(HttpPost request, long requestStartTime, String... otherParams) {

        try {
            MDC.put(CORRELATION_ID_MDC, request.getFirstHeader(CORRELATION_ID_REQUEST_HEADER).getValue());
            WebSubHubCorrelationLogUtils.triggerCorrelationLogForResponse(request, requestStartTime, otherParams);
        } finally {
            MDC.remove(CORRELATION_ID_MDC);
        }
    }
}
