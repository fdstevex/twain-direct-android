package org.twaindirect.cloud;

import org.json.JSONObject;

import java.net.URI;

/**
 * Information from TWAIN Cloud about a specific scanner.
 * Clients receive a list of these back from CloudConnection.getScannerList
 */
public class CloudScannerInfo {
    // TWAIN Direct URL for this scanner
    private URI cloudUrl;

    // The original JSON we received
    private JSONObject cloudScannerJSON;

    public CloudScannerInfo(URI baseUrl, CloudEventBrokerInfo eventBrokerInfo, JSONObject cloudScannerJSON) {
        this.cloudScannerJSON = cloudScannerJSON;

        this.cloudUrl = baseUrl.resolve(baseUrl.getPath() + "/scanners/" + getScannerId());
    }

    public String getScannerId() {
        return cloudScannerJSON.getString("id");
    }

    public URI getCloudUrl() {
        return cloudUrl;
    }

    public String getName() {
        return cloudScannerJSON.getString("name");
    }

    public String getDescription() {
        return cloudScannerJSON.getString("description");
    }
}
