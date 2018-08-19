package org.twaindirect.cloud;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.twaindirect.session.AsyncResponse;
import org.twaindirect.session.AsyncResult;
import org.twaindirect.session.Session;

import java.net.URI;

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
    private URI apiRoot;

    /**
     * The scanner we're working with.
     */
    private String scannerId;

    /**
     * Cloud API authorization token.
     */
    private String accessToken;

    /**
     * MQTT event listener
     */
    private CloudEventBroker cloudEventBroker;

    /**
     * Prepare the cloud session.
     * Pass in the authorization token.
     * @param apiRoot
     * @param scannerId
     * @param accessToken
     */
    public CloudSession(URI apiRoot, String scannerId, String accessToken) {
        this.apiRoot = apiRoot;
        this.scannerId = scannerId;
        this.accessToken = accessToken;
    }

    /**
     * Create the session. Makes some cloud calls to learn how to
     * communicate with the scanner, prepares the session, and
     * then calls listener when the session is ready to use.
     * @param listener
     */
    public void createSession(final AsyncResult<Session> listener) {
        CloudConnection connection = new CloudConnection(apiRoot, accessToken, null);
        connection.getEventBrokerInfo(new AsyncResult<CloudEventBrokerInfo>() {
            @Override
            public void onResult(CloudEventBrokerInfo eventBrokerInfo) {
                try {
                    cloudEventBroker = new CloudEventBroker(accessToken, eventBrokerInfo);
                    cloudEventBroker.connect(new AsyncResponse() {
                        @Override
                        public void onSuccess() {
                            URI url = apiRoot.resolve(apiRoot.getPath() + "/scanners/" + scannerId);
                            Session session = new Session(url, cloudEventBroker, accessToken);
                            listener.onResult(session);
                        }

                        @Override
                        public void onError(Exception e) {
                            listener.onError(e);
                        }
                    });
                } catch (MqttException e) {
                    listener.onError(e);
                    return;
                }
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }
}
