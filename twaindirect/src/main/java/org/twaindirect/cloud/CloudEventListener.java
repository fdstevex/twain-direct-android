package org.twaindirect.cloud;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.HashMap;
import java.util.Map;

/**
 * Subscribe to the scanner's MQTT response topic and broker messages.
 */
public class CloudEventListener {
    String authToken;
    CloudEventBrokerInfo eventBrokerInfo;
    MqttClient client;
    Map<String, MqttMessage> messagesReceived = new HashMap<>();

    public CloudEventListener(String authToken, CloudEventBrokerInfo eventBrokerInfo) throws MqttException {
        client = new MqttClient(eventBrokerInfo.url, MqttClient.generateClientId(), new MemoryPersistence());
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("connectionLost");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                System.out.println("messageArrived");
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                System.out.println("deliveryComplete");
            }
        });
    }

    public void connect() throws MqttException {
        client.connect();
    }
}
