package org.twaindirect.session;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPostHC4;
import org.apache.http.entity.StringEntityHC4;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtilsHC4;
import org.json.JSONObject;
import org.twaindirect.cloud.CloudEventBroker;
import org.twaindirect.cloud.CloudEventBrokerListener;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;


/**
 * Download an image block.  Image blocks are delivered as a MIME body with the
 * metadata as an application/json part, and the image as an application/pdf part.
 */
class HttpBlockRequest implements Runnable, CloudEventBrokerListener {
    private static final Logger logger = Logger.getLogger(HttpBlockRequest.class.getName());

    public URI url;
    public String ipaddr;
    public Map<String, String> headers = new HashMap<String, String>();

    AsyncResult<InputStream> listener;
    public JSONObject requestBody;

    public int readTimeout = 30000;
    public int connectTimeout = 20000;

    CloudEventBroker cloudEventBroker;
    CountDownLatch responseReady = new CountDownLatch(1);
    String authorization;
    String commandId;

    @Override
    public void run() {
        String result = null;
        try {
            logger.finer("Executing Image Block request for " + url + " commandId " + commandId);
            if (requestBody != null) {
                logger.finest("Request body: " + requestBody.toString(2));
            }

            //Create a connection
            CloseableHttpClient httpClient = HttpClientBuilder.createHttpClient(url.getHost(), ipaddr);
            HttpPostHC4 request = new HttpPostHC4(url.toString());

            RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(connectTimeout).setSocketTimeout(readTimeout).build();
            request.setConfig(requestConfig);

            request.addHeader("Content-Type", "application/json; charset=UTF-8");

            if (authorization != null) {
                request.addHeader("Authorization", authorization);
            }

            // Set any custom headers
            for (String key : headers.keySet()) {
                request.addHeader(key, headers.get(key));
            }

            if (cloudEventBroker != null) {
                cloudEventBroker.addListener(this);
            }

            // Set the request body
            if (requestBody != null) {
                byte[] bodyBytes = requestBody.toString().getBytes("UTF-8");
                HttpPostHC4 postRequest = (HttpPostHC4)request;
                postRequest.setEntity(new StringEntityHC4(requestBody.toString()));
            }

            // Connect to our url, get the response
            CloseableHttpResponse response = httpClient.execute(request);
            if (cloudEventBroker == null) {
                // If we're local, we will have the result now
                listener.onResult(response.getEntity().getContent());
            } else {
                if (response.getStatusLine().getStatusCode() != 200) {
                    logger.warning("HTTP response " + response.getStatusLine().toString());
                    String responseBody = EntityUtilsHC4.toString(response.getEntity(), "UTF-8");
                    logger.finest(responseBody);
                    listener.onError(new Exception("Unexpected HTTP response " + response.getStatusLine().toString()));
                }

                // Block waiting for the result
                if (!responseReady.await(readTimeout, TimeUnit.MILLISECONDS)) {
                    listener.onError(new TimeoutException());
                }
            }
        } catch (IOException e) {
            listener.onError(e);
        } catch (InterruptedException e) {
            listener.onError(e);
        }
    }

    @Override
    public String getCommandId() {
        return commandId;
    }

    @Override
    public void deliverJSONResponse(String body) {

    }
}
