package org.twaindirect.cloud;

/**
 * Manage a connection to a TWAIN Cloud service.
 * This includes the REST API and the MQTT events listener.
 */
public class CloudConnection {
    private String baseUrl;
    private String authToken;
    private String refreshToken;

    public CloudConnection(String baseUrl, String authToken, String refreshToken) {
        this.baseUrl = baseUrl;
        this.authToken = authToken;
        this.refreshToken = refreshToken;
    }

    public void getScannerList() {

    }
}
