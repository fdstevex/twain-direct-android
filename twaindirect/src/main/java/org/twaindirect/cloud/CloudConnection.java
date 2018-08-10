package org.twaindirect.cloud;

import org.json.JSONObject;
import org.twaindirect.discovery.ScannerInfo;
import org.twaindirect.session.AsyncResult;
import org.twaindirect.session.HttpJsonRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Manage a connection to a TWAIN Cloud service.
 * This includes the REST API and the MQTT events listener.
 */
public class CloudConnection {
    private static final Logger logger = Logger.getLogger(CloudConnection.class.getName());

    private String baseUrl;
    private String authToken;
    private String refreshToken;

    class EventBrokerInfo {
        String type;
        String url;
        String topic;
    }

    private ExecutorService executor = Executors.newFixedThreadPool(1);

    public CloudConnection(String baseUrl, String authToken, String refreshToken) {
        this.baseUrl = baseUrl;
        this.authToken = authToken;
        this.refreshToken = refreshToken;
    }

    public void getScannerList(final AsyncResult<List<CloudScannerInfo>> response) {
        getEventBrokerInfo(new AsyncResult<EventBrokerInfo>() {
            @Override
            public void onResult(EventBrokerInfo result) {
                getScannerListJSON(new AsyncResult<JSONObject>() {
                    @Override
                    public void onResult(JSONObject result) {
                        logger.info("Scanner list JSON: " + result.toString(2));
                    }

                    @Override
                    public void onError(Exception e) {
                        response.onError(e);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                response.onError(e);
            }
        });
    }

    /**
     * Fetch the scanner list JSON
     * @param response
     */
    private void getScannerListJSON(final AsyncResult<JSONObject> response) {
        // First request the user endpoint, so we know the MQTT response topic to subscribe to
        HttpJsonRequest request = new HttpJsonRequest();
        try {
            request.url = new URL(baseUrl + "scanners");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        request.method = "GET";
        request.headers.put("Authorization", authToken);

        request.listener = new AsyncResult<JSONObject>() {
            @Override
            public void onResult(JSONObject result) {
                response.onResult(result);
            }

            @Override
            public void onError(Exception e) {
                response.onError(e);
            }
        };

        executor.submit(request);    }

    /**
     * Fetch the /user endpoint and extract the EventBroker info
     * @param response
     */
    private void getEventBrokerInfo(final AsyncResult<EventBrokerInfo> response) {
        // First request the user endpoint, so we know the MQTT response topic to subscribe to
        HttpJsonRequest request = new HttpJsonRequest();
        try {
            request.url = new URL(baseUrl + "user");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        request.method = "GET";
        request.headers.put("Authorization", authToken);

        request.listener = new AsyncResult<JSONObject>() {
            @Override
            public void onResult(JSONObject result) {
                if (!result.has("eventBroker")) {
                    logger.severe("eventBroker key missing from user JSON response");
                    response.onError(null);
                    return;
                }

                EventBrokerInfo eventBrokerInfo = new EventBrokerInfo();

                JSONObject eventBroker = result.getJSONObject("eventBroker");
                eventBrokerInfo.topic = eventBroker.getString("topic");
                eventBrokerInfo.type = eventBroker.getString("type");
                eventBrokerInfo.url = eventBroker.getString("url");

                response.onResult(eventBrokerInfo);
            }

            @Override
            public void onError(Exception e) {
                response.onError(e);
            }
        };

        executor.submit(request);
    }
}
