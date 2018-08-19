package org.twaindirect.cloud;

import org.json.JSONArray;
import org.json.JSONObject;
import org.twaindirect.session.AsyncResult;
import org.twaindirect.session.HttpJsonRequest;

import java.net.URI;
import java.util.ArrayList;
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

    private URI apiUrl;
    private String accessToken;
    private String refreshToken;

    private ExecutorService executor = Executors.newFixedThreadPool(1);

    public CloudConnection(URI apiUrl, String accessToken, String refreshToken) {
        this.apiUrl = apiUrl;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public void getScannerList(final AsyncResult<List<CloudScannerInfo>> response) {
        getEventBrokerInfo(new AsyncResult<CloudEventBrokerInfo>() {
            @Override
            public void onResult(final CloudEventBrokerInfo eventBrokerInfo) {
                getScannerListJSON(new AsyncResult<JSONObject>() {
                    @Override
                    public void onResult(JSONObject result) {
                        ArrayList<CloudScannerInfo> cloudScanners = new ArrayList<>();
                        if (!result.has("array")) {
                            // Didn't get any scanners in the response
                            response.onResult(cloudScanners);
                            return;
                        }

                        // Turn the JSON array into an array of CloudScannerInfo to return
                        JSONArray scanners = result.getJSONArray("array");
                        for (int idx=0; idx<scanners.length(); idx++) {
                            JSONObject scannerCloudInfo = scanners.getJSONObject(idx);

                            CloudScannerInfo csi = new CloudScannerInfo(apiUrl, eventBrokerInfo, scannerCloudInfo);
                            cloudScanners.add(csi);
                        }

                        response.onResult(cloudScanners);
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
        request.url = apiUrl.resolve(apiUrl.getPath() + "/scanners");
        request.method = "GET";
        request.headers.put("Authorization", accessToken);

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

        executor.submit(request);
    }

    /**
     * Fetch the /user endpoint and extract the EventBroker info
     * @param response
     */
    public void getEventBrokerInfo(final AsyncResult<CloudEventBrokerInfo> response) {
        // First request the user endpoint, so we know the MQTT response topic to subscribe to
        HttpJsonRequest request = new HttpJsonRequest();
        request.url = apiUrl.resolve(apiUrl.getPath() + "/user");
        request.method = "GET";
        request.headers.put("Authorization", accessToken);

        request.listener = new AsyncResult<JSONObject>() {
            @Override
            public void onResult(JSONObject result) {
                if (!result.has("eventBroker")) {
                    if (result.has("message")) {
                        String message = result.getString("message");
                        response.onError(new Exception(message));
                        return;
                    }

                    response.onError(new Exception("eventBroker key missing from user JSON response"));
                    return;
                }

                CloudEventBrokerInfo eventBrokerInfo = new CloudEventBrokerInfo();

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

    /**
     * Fetch the cloud JSON info for the specified scanner.
     * @param response
     */
    public void getScannerInfoJSON(String scannerId, final AsyncResult<JSONObject> response) {
        HttpJsonRequest request = new HttpJsonRequest();
        request.url = apiUrl.resolve(apiUrl.getPath() + "/scanners/" + scannerId);
        request.method = "GET";
        request.headers.put("Authorization", accessToken);

        request.listener = new AsyncResult<JSONObject>() {
            @Override
            public void onResult(JSONObject result) {
                if (!result.has("eventBroker")) {
                    if (result.has("message")) {
                        String message = result.getString("message");
                        response.onError(new Exception(message));
                        return;
                    }

                    response.onError(new Exception("eventBroker key missing from user JSON response"));
                    return;
                }

                response.onResult(result);
            }

            @Override
            public void onError(Exception e) {
                response.onError(e);
            }
        };

        executor.submit(request);
    }

    public URI getApiUrl() {
        return apiUrl;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
