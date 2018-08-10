package org.twaindirect.cloud;

import org.json.JSONObject;
import org.twaindirect.discovery.ScannerInfo;

/**
 * Information from TWAIN Cloud about a specific scanner.
 * This includes the result from the cloud API, and the
 * TWAIN Local /privet/infoex response.
 */
public class CloudScannerInfo {
    // TWAIN Direct URL for this scanner
    private String cloudUrl;

    // MQTT WebSocket URL for request responses and events
    private String mqttUrl;

    // MQTT topic to listen on for responses
    private String responseTopic;

    // Scanner ID
    private String id;

    // TWAIN Direct scanner info
    private ScannerInfo scannerInfo;

    CloudScannerInfo(JSONObject scannerInfoResponse, ScannerInfo scannerInfo) {

    }

    public ScannerInfo getScannerInfo() {
        return scannerInfo;
    }

    public String getCloudUrl() {
        return cloudUrl;
    }
}
