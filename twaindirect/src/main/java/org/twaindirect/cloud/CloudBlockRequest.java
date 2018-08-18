package org.twaindirect.cloud;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGetHC4;
import org.apache.http.impl.client.CloseableHttpClient;
import org.json.JSONObject;
import org.twaindirect.session.AsyncResult;
import org.twaindirect.session.HttpClientBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;


/**
 * Download an image block from the cloud endpoint.
 */
public class CloudBlockRequest implements Runnable {
    private static final Logger logger = Logger.getLogger(CloudBlockRequest.class.getName());

    // The block's cloud URL
    public URI url;

    // Headers
    public Map<String, String> headers = new HashMap<String, String>();

    // The downloaded image is delivered to listener
    public AsyncResult<InputStream> listener;

    // Read timeout in milliseconds
    public int readTimeout = 30000;

    // Connect timeout in milliseconds
    public int connectTimeout = 20000;

    @Override
    public void run() {
        String result = null;
        try {
            logger.info("Requesting image block from " + url.toString());

            //Create a connection
            CloseableHttpClient httpClient = HttpClientBuilder.createHttpClient(url.getHost(), null);
            HttpGetHC4 request = new HttpGetHC4(url.toString());

            RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(connectTimeout).setSocketTimeout(readTimeout).build();
            request.setConfig(requestConfig);

            request.addHeader("Content-Type", "application/json; charset=UTF-8");

            // Set any custom headers
            for (String key : headers.keySet()) {
                request.addHeader(key, headers.get(key));
            }

            // Send the request, pass on the response
            CloseableHttpResponse response = httpClient.execute(request);
            listener.onResult(response.getEntity().getContent());
        } catch (IOException e) {
            listener.onError(e);
        }
    }
}
