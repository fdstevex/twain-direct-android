package org.twaindirect.cloud;

import org.json.JSONObject;
import org.twaindirect.discovery.ScannerInfo;

/**
 * Information from TWAIN Cloud about a specific scanner.
 * Clients receive a list of these back from CloudConnection.getScannerList
 */
public class CloudScannerInfo {
    // TWAIN Direct URL for this scanner
    private String cloudUrl;

    // The original JSON we received
    private JSONObject cloudScannerJSON;

    public CloudScannerInfo(String baseUrl, CloudEventBrokerInfo eventBrokerInfo, JSONObject cloudScannerJSON) {
        this.cloudScannerJSON = cloudScannerJSON;

        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }

        this.cloudUrl = baseUrl + "scanners/" + getScannerId();
    }

    public String getScannerId() {
        return cloudScannerJSON.getString("id");
    }

    public String getCloudUrl() {
        return cloudUrl;
    }

    public String getName() {
        return cloudScannerJSON.getString("name");
    }

    public String getDescription() {
        return cloudScannerJSON.getString("description");
    }
}
