package org.twaindirect.cloud;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.twaindirect.session.AsyncResponse;
import org.twaindirect.session.AsyncResult;
import org.twaindirect.session.Session;

/**
 * A TWAIN Cloud client uses the CloudSession object to establish a connection to a
 * cloud scanner, and then uses the associated Session to scan images.
 *
 * To use:
 *  - Instantiate the CloudSession
 *  - call createSession
 *
 * This will request the scanner info and event broker info from the cloud,
 * and then prepare a Session. This process is async and the session will be
 * passed to the callback when it is ready.
 *
 * Once connected, the Session provides the same API as a local scanner.
 */
public class CloudSession {
    /**
     * The TWAIN Direct session for communicating with the scanner.
     */
    private Session session;

    /**
     * The URL for the TWAIN Cloud service
     */
    private String apiRoot;

    /**
     * The scanner we're working with.
     */
    private String scannerId;

    /**
     * Cloud API authorization token.
     */
    private String authToken;

    /**
     * MQTT event listener
     */
    private CloudEventListener cloudEventListener;

    /**
     * Prepare the cloud session.
     * Pass in the authorization token.
     * @param apiRoot
     * @param scannerId
     * @param authToken
     */
    public CloudSession(String apiRoot, String scannerId, String authToken) {
        if (!apiRoot.endsWith("/")) {
            apiRoot = apiRoot + "/";
        }
        this.apiRoot = apiRoot;
        this.scannerId = scannerId;
        this.authToken = authToken;
    }

    /**
     * Create the session. Makes some cloud calls to learn how to
     * communicate with the scanner, prepares the session, and
     * then calls listener when the session is ready to use.
     * @param listener
     */
    public void createSession(final AsyncResult<Session> listener) {
        CloudConnection connection = new CloudConnection(apiRoot, authToken, null);
        connection.getEventBrokerInfo(new AsyncResult<CloudEventBrokerInfo>() {
            @Override
            public void onResult(CloudEventBrokerInfo eventBrokerInfo) {
                try {
                    cloudEventListener = new CloudEventListener(authToken, eventBrokerInfo);
                    cloudEventListener.connect();
                } catch (MqttException e) {
                    listener.onError(e);
                    return;
                }

                Session session = new Session(apiRoot, eventBrokerInfo, authToken);
                listener.onResult(session);
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }
}
