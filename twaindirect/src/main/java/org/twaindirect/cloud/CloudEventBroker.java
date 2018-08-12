package org.twaindirect.cloud;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;
import org.twaindirect.session.BlockDownloader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Subscribe to the scanner's MQTT response topic and broker messages.
 */
public class CloudEventBroker {
    private static final Logger logger = Logger.getLogger(CloudEventBroker.class.getName());

    String authToken;
    CloudEventBrokerInfo eventBrokerInfo;
    MqttAsyncClient client;
    Map<String, MqttMessage> messagesReceived = new HashMap<>();

    /**
     * When we receive a message, we walk the listeners list looking for someone to deliver it to
     */
    List<CloudEventBrokerListener> listeners = new ArrayList<>();

    public CloudEventBroker(String authToken, CloudEventBrokerInfo eventBrokerInfo) throws MqttException {
        this.eventBrokerInfo = eventBrokerInfo;

        logger.setLevel(Level.ALL);

        client = new MqttAsyncClient(eventBrokerInfo.url, MqttClient.generateClientId(), new MemoryPersistence());
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("connectionLost");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                logger.info("MQTT message arrived");
                String payloadJSON = new String(message.getPayload());
                JSONObject payload = new JSONObject(payloadJSON);
                logger.fine(payload.toString(2));

                // Message typically looks like this:
                // {
                //  "headers": {"content-Type": "application/json; charset=UTF-8"},
                //  "statusDescription": null,
                //  "requestId": null,
                //  "body": "{\"version\":\"1.0\",\"name\":\"TWAIN2 FreeImage Software Scanner\",\"description\":\"Sample DS\",\"url\":\"\",\"type\":\"twaindirect\",\"id\":\"\",\"device_state\":\"idle\",\"connection_state\":\"offline\",\"manufacturer\":\"TWAIN Working Group\",\"model\":\"TWAIN2 FreeImage Software Scanner\",\"serial_number\":\"X\",\"firmware\":\"2.1:1.2\",\"uptime\":\"1436\",\"setup_url\":\"\",\"support_url\":\"\",\"update_url\":\"\",\"x-privet-token\":\"50gbKrsF235rSr6RI58PSGghbpA=:636696641228998209\",\"api\":[\"/privet/twaindirect/session\"],\"semantic_state\":\"\",\"clouds\":[{\"url\":\"https://api-twain.hazybits.com/dev\",\"id\":\"3c807fab-07c2-4710-be56-5c6b40bedcaa\",\"connection_state\":\"online\",\"setup_url\":\"\",\"support_url\":\"\",\"update_url\":\"\"}]}",
                //  "statusCode": 200
                // }

                // TODO: pick the right listener
                String bodyJSON = payload.getString("body");
                CloudEventBrokerListener foundListener = null;
                for (CloudEventBrokerListener listener : listeners) {
                    foundListener = listener;
                    break;
                }

                removeListener(foundListener);
                foundListener.deliverJSONResponse(bodyJSON);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                System.out.println("deliveryComplete");
            }
        });
    }

    public void addListener(CloudEventBrokerListener listener) {
        synchronized(this) {
            listeners.add(listener);
        }
    }

    public void removeListener(CloudEventBrokerListener listener) {
        synchronized(this) {
            listeners.remove(listener);
        }
    }

    public void connect() throws MqttException {
        client.connect(null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                try {
                    client.subscribe(eventBrokerInfo.topic, 0);
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                exception.printStackTrace();
            }
        });
    }
}
