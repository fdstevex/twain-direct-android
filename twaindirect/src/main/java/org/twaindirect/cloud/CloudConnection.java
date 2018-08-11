package org.twaindirect.cloud;

import org.json.JSONArray;
import org.json.JSONObject;
import org.twaindirect.discovery.ScannerInfo;
import org.twaindirect.session.AsyncResult;
import org.twaindirect.session.HttpJsonRequest;

import java.net.MalformedURLException;
import java.net.URL;
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

    private String baseUrl;
    private String authToken;
    private String refreshToken;

    private ExecutorService executor = Executors.newFixedThreadPool(1);

    public CloudConnection(String baseUrl, String authToken, String refreshToken) {
        this.baseUrl = baseUrl;
        this.authToken = authToken;
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

                            CloudScannerInfo csi = new CloudScannerInfo(baseUrl, eventBrokerInfo, scannerCloudInfo);
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

        executor.submit(request);
    }

    /**
     * Fetch the /user endpoint and extract the EventBroker info
     * @param response
     */
    public void getEventBrokerInfo(final AsyncResult<CloudEventBrokerInfo> response) {
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
        try {
            request.url = new URL(baseUrl + "scanners/" + scannerId);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        request.method = "GET";
        request.headers.put("Authorization", authToken);

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
}
