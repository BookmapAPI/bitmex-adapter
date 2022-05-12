package com.bookmap.plugins.layer0.bitmex.adapter;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClients;

import com.bookmap.plugins.layer0.bitmex.Provider;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.GeneralType;
import com.bookmap.plugins.layer0.bitmex.adapter.ConnectorUtils.Method;
import com.bookmap.plugins.layer0.bitmex.messages.ModuleTargetedHttpRequestFeedbackMessage;

import velox.api.layer1.common.Log;

public class HttpClientHolder implements Closeable {
    
    public static class HttpDeleteWithBody extends HttpEntityEnclosingRequestBase {
        public static final String METHOD_NAME = "DELETE";

        @Override
        public String getMethod() {
            return METHOD_NAME;
        }

        public HttpDeleteWithBody(final String url) throws URISyntaxException {
            super();
            URI uri = new URI(url);
            setURI(uri);
        }

        public HttpDeleteWithBody(final URI uri) {
            super();
            setURI(uri);
        }
    }

    private String orderApiKey;
    private String orderApiSecret;
    private Provider provider;
    private final int allowedRequestsPerMinuteMaximum = 60;
    private AtomicInteger allowedRequestsPerMinuteLeft = new AtomicInteger(allowedRequestsPerMinuteMaximum);
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    private CloseableHttpClient client = HttpClients.custom()
            .setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE)
            .build();
    
    public HttpClientHolder(String orderApiKey, String orderApiSecret, Provider provider) {
        super();
        this.orderApiKey = orderApiKey;
        this.orderApiSecret = orderApiSecret;
        this.provider = provider;
        scheduleRateLimitIncrementing();
    }
    
    public Pair<Boolean, String> makeRequest(GeneralType genType, Method method, String data) {
        return makeRequest(genType, method, data, false, null);
    }
    
    public Pair<Boolean, String> makeRequest(GeneralType genType, Method method, String data, boolean isOrderListBeingCanceled) {
        return makeRequest(genType, method, data, isOrderListBeingCanceled, null);
    }

    public Pair<Boolean, String> makeRequest(GeneralType genType, Method method, String data, boolean isOrderListBeingCanceled, String requestParameters) {
        String subPath; 
        
        if (genType != null) {
            subPath = ConnectorUtils.subPaths.get(genType);
        } else {
            subPath = "";
        }
        
        if (data == null) data = "";

        if (genType.equals(GeneralType.POSITION) && data.contains("leverage")) {
            subPath += "/leverage";
        }
        if (requestParameters != null) {
            subPath += requestParameters;
        }

        String path = provider.getConnector().getRestApi() + subPath;
        String response = "";
        boolean isSuccessful = false;

        try {
            HttpRequestBase requestBase = getRequest(method, path);
            
            StringEntity requestEntity = new StringEntity(
                    data,
                    ContentType.APPLICATION_JSON);

            if (requestBase instanceof HttpEntityEnclosingRequestBase) {
                ((HttpEntityEnclosingRequestBase) requestBase).setEntity(requestEntity);
            }

            String contentType = genType.equals(GeneralType.ORDERBULK) || isOrderListBeingCanceled
                    ? "application/x-www-form-urlencoded" : "application/json";

            requestBase.addHeader("Accept", "application/json");
            requestBase.addHeader("User-Agent", Constants.user_agent);
            requestBase.addHeader("Content-Type", contentType);

            if (!StringUtils.isBlank(orderApiSecret) && genType != GeneralType.ACTIVE_INSTRUMENTS) {
                long moment = ConnectorUtils.getMomentAndTimeToLive();
                String messageBody = ConnectorUtils.createMessageBody(ConnectorUtils.methods.get(method), subPath, data,
                        moment);
                String signature = ConnectorUtils.generateSignature(orderApiSecret, messageBody);
                requestBase.addHeader("api-expires", Long.toString(moment));
                requestBase.addHeader("api-key", orderApiKey);
                requestBase.addHeader("api-signature", signature);
            }
            
            CloseableHttpResponse httpResponse = client.execute(requestBase);
            Header[] headers = httpResponse.getAllHeaders();
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            isSuccessful = statusCode == 200;
            
            HttpEntity entity = httpResponse.getEntity();

            BufferedReader br = new BufferedReader(new InputStreamReader((entity.getContent())));
            StringBuilder sb = new StringBuilder("");
            String output = null;

            while ((output = br.readLine()) != null) {
                sb.append(output);
            }
            response = sb.toString();
            br.close();
            httpResponse.close();
            requestBase.releaseConnection();
            
            if (isSuccessful) {
                Header rateLimitRemainingHeader = ConnectorUtils.getHeader(headers,
                        Constants.rateLimitRemainingHeaderName);
                if (rateLimitRemainingHeader != null) {
                    try {
                        int rateLimitRemainingValue = Integer.parseInt(rateLimitRemainingHeader.getValue());
                        allowedRequestsPerMinuteLeft.set(rateLimitRemainingValue);
                    } catch (Exception e) {
                        Log.error("no ratelimit data", e);
                    }
                }
            } else {
                Log.info("Server response " + statusCode + " " + response);
                Integer timeOut = ConnectorUtils.getTimeoutFromErrorMessage(response);
                if (timeOut != null) {
                    allowedRequestsPerMinuteLeft.set(-timeOut);
                }
            }

            try {
                if (Class.forName("velox.api.layer1.messages.Layer1ApiUserInterModuleMessage") != null) {
                    provider.onUserMessage(new ModuleTargetedHttpRequestFeedbackMessage(genType, method, data,
                            isOrderListBeingCanceled, requestParameters, headers, statusCode, response));
                }
            } catch (ClassNotFoundException e) {
                //this is the desired behavior for 7.0 so no warnings needed
            }
        } catch (UnknownHostException | NoRouteToHostException e) {
            Log.info("TradeConnector require: no response from server");
        } catch (java.net.SocketException e) {
            Log.info("TradeConnector require: network is unreachable");
        } catch (IOException e) {
            Log.info("TradeConnector require: buffer reading error", e);
        } catch (URISyntaxException e) {
            Log.info("Wrong uri", e);
        }
        return Pair.of(isSuccessful, response);
    }
    
    private HttpRequestBase getRequest (Method method, String path) throws URISyntaxException {
        HttpRequestBase request = null;
        
        switch (method) {
        case GET:
            request = new HttpGet(path);
            break;
        case POST:
            request = new HttpPost(path);
            break;
        case PUT:
            request = new HttpPut(path);
            break;
        case DELETE:
            request = new HttpDeleteWithBody(path);
            break;
        }
        return request;
    }
    
    public int getAllowedRequestsPerMinuteLeft() {
        return allowedRequestsPerMinuteLeft.get();
    }

    @Override
    public void close() throws IOException {
       scheduler.shutdownNow();
       client.close();
    }
    
    private void scheduleRateLimitIncrementing() {
        final IntUnaryOperator unaryOperator = new IntUnaryOperator() {
            @Override
            public int applyAsInt(int operand) {
                if (operand < allowedRequestsPerMinuteMaximum) {
                    return operand + 1;
                } else {
                    return allowedRequestsPerMinuteMaximum;
                }
            }
        };

        Runnable runnable = () -> {
            allowedRequestsPerMinuteLeft.getAndUpdate(unaryOperator);
        };
        scheduler.scheduleAtFixedRate(runnable, 1, 1, TimeUnit.SECONDS);
    }
}
