package org.twaindirect.cloud;

import org.json.JSONObject;
import org.twaindirect.discovery.ScannerInfo;

/**
 * Information from TWAIN Cloud about a specific scanner.
 * This includes the result from the cloud API and the event broker info needed
 * to get responses from the scanner.
 */
public class CloudScannerInfo {
    // TWAIN Direct URL for this scanner
    private String cloudUrl;

    CloudEventBrokerInfo eventBrokerInfo;

    // The original JSON we received
    private JSONObject cloudScannerJSON;

    public CloudScannerInfo(String baseUrl, CloudEventBrokerInfo eventBrokerInfo, JSONObject cloudScannerJSON) {
        this.cloudScannerJSON = cloudScannerJSON;
        this.eventBrokerInfo = eventBrokerInfo;

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
