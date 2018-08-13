package org.twaindirect.cloud;

public interface CloudEventBrokerListener {
    // Return the outstanding command ID
    String getCommandId();

    // Received a JSON response
    void deliverJSONResponse(String body);

    boolean keepAlive();
}

