package org.twaindirect.session;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.twaindirect.cloud.CloudBlockRequest;
import org.twaindirect.cloud.CloudConnection;
import org.twaindirect.cloud.CloudEventBroker;

import java.io.File;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A TWAIN Direct client uses the Session object to scan images.
 *
 * The general process is:
 *  - Instantiate a Session
 *  - call setSessionListener to supply a listener to be notified when asynchronous
 *  operations, like receiving an image, have occurred.
 *  - call open() to start a session
 *  - call sendTask() to configure the scanner
 *  - call startCapturing() to start the capture operation
 *
 * Your callback will be called, possibly repeatedly, with scanned images. When the
 * process is complete, the listener's onDoneCapturing will be called.
 *
 * Downloaded blocks are stored in tempdir and once all the parts of an image
 * are readyToDownload, they are combined into a final file and delivered to
 * sessionListener.onImageReceived.
 *
 * Many functions accept an AsyncResult or AsyncResponse. The success or failure
 * of the operation is delivered to the listener, but asynchronous operations that
 * aren't the direct result of a specific command are delivered through SessionListener.
 *
 * The client can stop and restart scanning any number of times.
 */

public class Session {

    private static final Logger logger = Logger.getLogger(Session.class.getName());

    // Thread that manages the waitForEvents long-poll
    private Thread eventListenerThread;

    private boolean stopping;
    private File tempDir;

    // Set when the client calls stopCapturing to indicate we should keep the
    // session open even when the scanner stops capturing.
    private boolean paused;

    // Map of block number to the associated ImageBlockInfo for files we've received.
    // The file data is in tempDir at the filename generated by ImageBlockInfo.partFileName()
    private Map<Integer, ImageBlockInfo> files = new HashMap<>();

    private static final String TAG = "Session";

    /**
     * This is the URL for the root of the scanner host,
     * ie, http://192.168.1.4:55555/
     * or, if cloud, the cloud API url + "/scanners/{scanner_id}"
     */
    private URI url;

    /**
     * This is provided by getinfoex, and is typically url + "/privet/twaindirect/session"
     */
    private URI endpoint;

    /**
     * This is the IP address of the scanner - it's used to set up the mDNS name resolution.
     */
    private String scannerIp;

    /**
     * Number of successive failures that have occurred attempting to wait for events.
     * When we hit numWaitForEventsRetriesAllowed retries, the session will be ended.
     */
    private int waitForEventsRetryCount;

    /**
     * Number of retries before we give up.
     */
    private static final int numWaitForEventsRetriesAllowed = 3;

    private ExecutorService executor = Executors.newFixedThreadPool(1);

    public enum State {
        noSession,
        ready,
        capturing,
        closed,
        draining
    }

    public enum StatusDetected {
        nominal,
        coverOpen,
        foldedCorner,
        imageError,
        misfeed,
        doubleFeed,
        multifeed,
        paperJam,
        noMedia,
        staple
    }

    private State state;

    // The last value for results.session.status.success
    private boolean sessionStatusSuccess;

    // The last session value for session.status.detected
    private StatusDetected sessionStatusDetected;

    private String privetToken;
    private String sessionId;
    private int sessionRevision;
    private JSONObject infoExResult;
    private SessionListener sessionListener;
    private boolean doneCapturing;
    private boolean imageBlocksDrained;
    private BlockDownloader blockDownloader;

    private final CloudEventBroker cloudEventBroker;
    private final CloudConnection cloudConnection;

    /**
     * Prepare a TWAIN Local session
     * @param url For example, https://myscanner.local:34034
     * @param scannerIp The IP address that "myscanner.local" resolves to, for mDNS name resolution.
     */
    public Session(URI url, String scannerIp) {
        this.url = url;
        this.scannerIp = scannerIp;
        this.cloudEventBroker = null;
        this.cloudConnection = null;
        reset();

        logger.info("Local session startup");
    }

    /**
     * Prepare for a TWAIN Cloud session
     */
    public Session(URI scannerUrl, CloudEventBroker cloudEventBroker, CloudConnection cloudConnection) {
        this.url = scannerUrl;
        this.cloudEventBroker = cloudEventBroker;
        this.cloudConnection = cloudConnection;
        reset();

        logger.info("Cloud session startup");
    }

    /**
     * Set the session listener.
     * @param listener
     */
    public void setSessionListener(SessionListener listener) {
        this.sessionListener = listener;
    }

    @Override
    public String toString() {
        String result = "";

        if (state != null) {
            result = "state=" + state.toString() + ", revision=" + sessionRevision;
        } else {
            result = "state=null";
        }

        if (privetToken != null) {
            result += "\n privetToken=" + privetToken;
        }
        if (sessionId != null) {
            result += "\n sessionId=" + sessionId;
        }
        return result;
    }

    public void setTempDir(File tempDir) {
        this.tempDir = tempDir;
    }

    /**
     * Get the scanner's state, as reported by the scanner when we last heard from it,
     * either asynchronously or in the response to a request.
     * @return
     */
    public State getState() {
        return state;
    }

    /**
     * Get the scanner's status. See the TWAIN Direct REST API specification for a
     * description of the status values.
     */
    public StatusDetected getStatus() {
        return sessionStatusDetected;
    }

    /**
     * Get the session ID.
     * @return
     */
    String getSessionId() {
        return sessionId;
    }

    /**
     * Returns whether we're currently in the process of stopping (which may
     * mean waiting for the scanner to drain or release images).
     * @return
     */
    public boolean getStopping() {
        return stopping;
    }

    /**
     * Send the privet infoex request to get a token.
     *
     * @param listener
     */
    public void getInfoEx(AsyncResult<JSONObject> listener) {
        URI infoUrl = URIUtils.appendPathToURI(url, "/privet/infoex");

        HttpJsonRequest request =  new HttpJsonRequest();
        request.url = infoUrl;
        request.ipaddr = scannerIp;
        request.listener = listener;
        request.cloudEventBroker = cloudEventBroker;
        request.cloudConnection = cloudConnection;

        // Must be included, but empty
        request.headers.put("X-Privet-Token", "");

        executor.submit(request);
    }

    /**
     * Open a session - requests a privet token if we don't already have one.
     *
     * @param listener
     */
    public void open(final AsyncResponse listener) {
        if (state != State.noSession) {
            listener.onError(new InvalidStateException());
            return;
        }

        reset();

        AsyncResult<JSONObject> privetTokenListener = new AsyncResult<JSONObject>() {
            @Override
            public void onResult(JSONObject result) {
                if (!result.has("x-privet-token")) {
                    listener.onError(new SessionException("getInfoEx response missing x-privet-token"));
                    return;
                }
                try {
                    logger.info("Received privet token");
                    infoExResult = result;
                    privetToken = result.getString("x-privet-token");
                    String apiPath = result.getJSONArray("api").getString(0);
                    endpoint = URIUtils.appendPathToURI(url, apiPath);

                    // try again now that we have the privet token
                    open(listener);
                } catch (JSONException e) {
                    listener.onError(e);
                    return;
                }
            }

            @Override
            public void onError(Exception e) {
                // Propagate to our listener
                listener.onError(e);
            }
        };

        if (privetToken == null) {
            // Chain through getting the privetToken
            logger.info("Requesting privet token");
            getInfoEx(privetTokenListener);
        } else {
            try {
                // Create and send the createSession request
                logger.info("Sending createSession");
                String commandId = UUID.randomUUID().toString();
                JSONObject body = new JSONObject();
                body.put("kind", "twainlocalscanner");
                body.put("method", "createSession");
                body.put("commandId", commandId);

                HttpJsonRequest request = new HttpJsonRequest();
                request.url = endpoint;
                request.method = "POST";
                request.commandId = commandId;
                request.requestBody = body;
                request.headers.put("X-Privet-Token", privetToken);
                request.ipaddr = scannerIp;
                request.cloudConnection = cloudConnection;
                request.cloudEventBroker = cloudEventBroker;

                request.listener = new AsyncResult<JSONObject>() {
                    @Override
                    public void onResult(JSONObject result) {
                        // We need the session ID
                        try {
                            JSONObject results = result.getJSONObject("results");
                            if (!results.getBoolean("success")) {
                                listener.onError(new SessionException("createSession failed: " + results.toString()));
                                return;
                            }

                            logger.fine("Created session");

                            updateSession(results.getJSONObject("session"));

                            if (state != State.ready) {
                                String message = String.format("createSession expected state readyToDownload, got %s", state);
                                listener.onError(new SessionException(message));
                                return;
                            }

                            startEventListener();
                        } catch (JSONException | SessionException e) {
                            listener.onError(e);
                            return;
                        }

                        listener.onSuccess();
                    }

                    @Override
                    public void onError(Exception e) {
                        listener.onError(e);
                    }
                };

                executor.submit(request);
            } catch (JSONException e) {
                listener.onError(e);
            }
        }
    }

    /**
     * Ask the scanner to stop scanning.  You can call stop and then start to resume.
     */
    public void stop(final AsyncResponse listener) {

        paused = true;

        try {
            // Create and send the createSession request
            JSONObject params = new JSONObject();
            params.put("sessionId", sessionId);
            HttpJsonRequest request = createJsonRequest("stopCapturing", params);

            request.listener = new AsyncResult<JSONObject>() {
                @Override
                public void onResult(JSONObject result) {
                    try {
                        JSONObject results = result.getJSONObject("results");
                        if (!results.getBoolean("success")) {
                            listener.onError(new SessionException("stopCapturing failed: " + results.toString()));
                            return;
                        }

                        updateSession(results.getJSONObject("session"));
                    } catch (JSONException | SessionException e) {
                        listener.onError(e);
                        return;
                    }

                    listener.onSuccess();
                }

                @Override
                public void onError(Exception e) {
                    listener.onError(e);
                }
            };

            executor.submit(request);
        } catch (JSONException e) {
            listener.onError(e);
        }
    }


    /**
     * Close the sesssion.
     *
     * @param listener
     */
    public void close(final AsyncResponse listener) {
        try {
            stopping = true;

            // Create and send the createSession request
            JSONObject params = new JSONObject();
            params.put("sessionId", sessionId);
            HttpJsonRequest request = createJsonRequest("closeSession", params);

            request.listener = new AsyncResult<JSONObject>() {
                @Override
                public void onResult(JSONObject result) {
                    // We need the session ID
                    try {
                        JSONObject results = result.getJSONObject("results");
                        if (!results.getBoolean("success")) {
                            listener.onError(new SessionException("createSession failed: " + results.toString()));
                            return;
                        }

                        updateSession(results.getJSONObject("session"));

                        if (state != State.closed && state != State.noSession) {
                            String message = String.format("closeSession expected state noSession, got %s", state);
                            listener.onError(new SessionException(message));
                            return;
                        }
                    } catch (JSONException | SessionException e) {
                        listener.onError(e);
                        return;
                    }

                    listener.onSuccess();
                }

                @Override
                public void onError(Exception e) {
                    listener.onError(e);
                }
            };

            executor.submit(request);
        } catch (JSONException e) {
            listener.onError(e);
        }
    }

    /**
     * Send a task to the scanner.  Success or failure is delivered through the listener.
     *
     * @param task
     * @param listener
     */
    public void sendTask(JSONObject task, final AsyncResult<JSONObject> listener) {
        // Create and send the sendTask request
        JSONObject params = new JSONObject();
        try {
            params.put("sessionId", sessionId);
            params.put("task", task);
        } catch (JSONException e) {
            logger.severe(e.toString());
            return;
        }

        HttpJsonRequest request = createJsonRequest("sendTask", params);

        request.listener = new AsyncResult<JSONObject>() {
            @Override
            public void onResult(JSONObject result) {
                try {
                    JSONObject results = result.getJSONObject("results");
                    if (!results.getBoolean("success")) {
                        listener.onError(new SessionException("createSession failed: " + results.toString()));
                        return;
                    }

                    JSONObject session = results.getJSONObject("session");
                    updateSession(session);

                    JSONObject acceptedTask = session.getJSONObject("task");
                    listener.onResult(acceptedTask);
                } catch (JSONException e) {
                    listener.onError(e);
                    return;
                } catch (SessionException e) {
                    listener.onError(e);
                    return;
                }
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        };

        executor.submit(request);
    }

    /**
     * Start capturing.
     *
     * @param listener
     */
    public void startCapturing(final AsyncResponse listener) {
        // Create and send the sendTask request
        JSONObject params = new JSONObject();
        try {
            params.put("sessionId", sessionId);
        } catch (JSONException e) {
            // Should not happen
            listener.onError(e);
            return;
        }

        blockDownloader = new BlockDownloader(this, tempDir, sessionListener, cloudEventBroker);

        files.clear();

        HttpJsonRequest request = createJsonRequest("startCapturing", params);

        request.listener = new AsyncResult<JSONObject>() {
            @Override
            public void onResult(JSONObject result) {
                try {
                    JSONObject results = result.getJSONObject("results");
                    if (!results.getBoolean("success")) {
                        listener.onError(new SessionException("createSession failed: " + results.toString()));
                        return;
                    }

                    JSONObject session = results.getJSONObject("session");
                    updateSession(session);

                    listener.onSuccess();
                } catch (JSONException | SessionException e) {
                    listener.onError(e);
                    return;
                }
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        };

        paused = false;
        executor.submit(request);
    }

    /**
     * Listen for events, synchronously. Intended to be called from the event listener thread,
     * and called again on timeout or error as long as we are supposed to be listening.
     * Returns false if the listener should not be called again for this session.
     */
    private boolean syncListen() throws JSONException {
        JSONObject params = new JSONObject();
        params.put("sessionId", sessionId);
        params.put("sessionRevision", sessionRevision);
        HttpJsonRequest request = createJsonRequest("waitForEvents", params);

        if (waitForEventsRetryCount >= numWaitForEventsRetriesAllowed) {
            // Don't schedule another wait
            return false;
        }

        // 30 seconds is the recommended poll timeout
        request.readTimeout = 30000;

        final boolean shouldContinue[] = new boolean[1];
        shouldContinue[0] = true;

        request.listener = new AsyncResult<JSONObject>() {
            @Override
            public void onResult(JSONObject result) {
                logger.log(Level.FINE, "Event received: " + result.toString());

                try {
                    JSONObject results = result.getJSONObject("results");
                    if (!results.getBoolean("success")) {
                        logger.log(Level.SEVERE, "waitForEvents reported error");
                        shouldContinue[0] = false;
                        return;
                    }

                    // We received a successful response, so reset the retry count.
                    waitForEventsRetryCount = 0;

                    // Process the events we just received
                    JSONArray events = results.getJSONArray("events");
                    for (int i=0; i<events.length(); i++) {
                        JSONObject event = events.getJSONObject(i);
                        JSONObject session = event.getJSONObject("session");
                        int revision = session.getInt("revision");
                        if (revision < sessionRevision) {
                            // Ignore as we've already seen this revision
                            continue;
                        }

                        updateSession(session);
                    }

                } catch (SessionException e) {
                    logger.severe(e.toString());
                    waitForEventsRetryCount++;
                } catch (JSONException e) {
                    logger.severe(e.toString());
                    waitForEventsRetryCount++;
                }
            }

            @Override
            public void onError(Exception e) {
                if (e instanceof SocketTimeoutException || e instanceof TimeoutException) {
                    // This is expected to fail, timeout, etc., and our
                    // response is to retry.  Don't up the retry count.
                } else {
                    // Unexpected error
                    waitForEventsRetryCount++;
                    logger.warning(e.getLocalizedMessage());
                }
            }
        };

        request.run();

        if (waitForEventsRetryCount == numWaitForEventsRetriesAllowed) {
            // Call this when the retryCount goes over the limit
            sessionListener.onConnectionError(this, new SessionException("waitForSession retries exceeded"));
        }

        return shouldContinue[0];
    }

    /**
     * Notify the device that we're done with this block.
     * @param fromBlock
     * @param toBlock
     */
    void releaseBlock(final int fromBlock, final int toBlock) {
        // Create and send the sendTask request
        JSONObject params = new JSONObject();
        try {
            params.put("sessionId", sessionId);
            params.put("imageBlockNum", fromBlock);
            params.put("lastImageBlockNum", toBlock);
        } catch (JSONException e) {
            // Unexpected error
            logger.severe(e.toString());
            return;
        }

        logger.info(String.format("Releasing blocks from %s to %s", fromBlock, toBlock));

        HttpJsonRequest request = createJsonRequest("releaseImageBlocks", params);
        request.listener = new AsyncResult<JSONObject>() {
            @Override
            public void onResult(JSONObject result) {
                try {
                    JSONObject results = result.getJSONObject("results");
                    if (!results.getBoolean("success")) {
                        logger.warning("Delete failed: " + results.toString());
                        return;
                    }

                    logger.info(String.format("Released blocks from %s to %s", fromBlock, toBlock));

                    logger.fine(String.format("releaseImageBlocks response: %s", results));

                    updateSession(results.getJSONObject("session"));

                    if (doneCapturing && imageBlocksDrained && !paused) {
                        close(new AsyncResponse() {
                            @Override
                            public void onSuccess() {
                                // Nothing to do here
                            }

                            @Override
                            public void onError(Exception e) {
                                // Error occurred closing the session - either way
                                // we're done capturing
                                logger.severe(e.toString());
                                sessionListener.onDoneCapturing(Session.this);
                            }
                        });
                    }
                } catch (SessionException e) {
                    logger.severe(e.toString());
                } catch (JSONException e) {
                    logger.severe(e.toString());
                }
            }

            @Override
            public void onError(Exception e) {
                logger.severe(e.toString());
            }
        };

        executor.submit(request);
    }


    /**
     * Helper to create a configured HttpJsonRequest
     * @param method
     * @param params
     * @return
     */
    HttpJsonRequest createJsonRequest(String method, JSONObject params) {
        // Create and send the createSession request
        String commandId = UUID.randomUUID().toString();
        JSONObject body = new JSONObject();
        try {
            body.put("kind", "twainlocalscanner");
            body.put("method", method);
            body.put("commandId", commandId);
            if (params != null) {
                body.put("params", params);
            }
        } catch (JSONException e) {
            logger.severe(e.toString());
            return null;
        }

        HttpJsonRequest request = new HttpJsonRequest();
        request.cloudConnection = cloudConnection;
        request.cloudEventBroker = cloudEventBroker;
        request.url = endpoint;
        request.commandId = commandId;
        request.ipaddr = scannerIp;
        request.method = "POST";
        request.requestBody = body;
        request.headers.put("X-Privet-Token", privetToken);
        return request;
    }

    /**
     * Helper to create a configured HttpBlockRequest (for BlockDownloader)
     * @param params
     * @return
     */
    HttpBlockRequest createBlockRequest(JSONObject params) {
        // Create and send the createSession request
        JSONObject body = new JSONObject();
        String commandId = UUID.randomUUID().toString();
        try {
            body.put("kind", "twainlocalscanner");
            body.put("method", "readImageBlock");
            body.put("commandId", commandId);
            if (params != null) {
                body.put("params", params);
            }
        } catch (JSONException e) {
            logger.severe(e.toString());
            return null;
        }

        HttpBlockRequest request = new HttpBlockRequest();
        request.url = endpoint;
        request.commandId = commandId;
        request.ipaddr = scannerIp;
        request.requestBody = body;
        request.headers.put("X-Privet-Token", privetToken);
        return request;
    }

    /**
     * Helper to create a configured CloudBlockRequest (for BlockDownloader)
     * @blockId block ID returned in the readImageBlock response when using a cloud source
     * @return
     */
    public CloudBlockRequest createCloudBlockRequest(String blockId) {
        // Create and send the createSession request
        CloudBlockRequest request = new CloudBlockRequest(cloudConnection);
        request.url = URIUtils.appendPathToURI(url, "/blocks/" + blockId);
        request.headers.put("X-Privet-Token", privetToken);
        return request;
    }

    /**
     * Reset internal state
     */
    private void reset() {
        doneCapturing = false;
        imageBlocksDrained = false;
        state = State.noSession;
        files.clear();
        sessionStatusSuccess = true;
        sessionStatusDetected = null;
        waitForEventsRetryCount = 0;
        paused = false;
        blockDownloader = null;
    }

    /**
     * Update local state given a JSONObject representing the session object in a response.
     * @param session
     */
    private void updateSession(JSONObject session) throws JSONException, SessionException {

        logger.fine(String.format("updateSession: %s", session.toString()));

        // typical: { state=readyToDownload, status={detected:nominal,success:true}, revision=1, sessionId=guid}
        if (sessionId == null) {
            sessionId = session.getString("sessionId");
        }

        if (session.has("doneCapturing")) {
            doneCapturing = session.getBoolean("doneCapturing");
        }

        if (session.has("imageBlocksDrained")) {
            imageBlocksDrained = session.getBoolean("imageBlocksDrained");
        }

        // Update the revision
        sessionRevision = Math.max(sessionRevision, session.getInt("revision"));

        State newState = null;
        switch (session.getString("state")) {
            case "ready":
                newState = State.ready;
                break;
            case "capturing":
                newState = State.capturing;
                break;
            case "closed":
                newState = State.closed;
                break;
            case "draining":
                newState = State.draining;
                break;
            case "noSession":
                newState = State.noSession;
                break;
            default:
                throw new SessionException("Unknown state in session " + session.toString());
        }

        if (session.has("imageBlocks")) {
            synchronized(this) {
                JSONArray ibready = session.getJSONArray("imageBlocks");
                if (ibready != null && ibready.length() > 0) {
                    List<Integer> imageBlocks = new ArrayList<Integer>();
                    for (int ibidx = 0; ibidx < ibready.length(); ibidx++) {
                        int blockNum = ibready.getInt(ibidx);
                        imageBlocks.add(blockNum);
                    }

                    blockDownloader.enqueueBlocks(imageBlocks);
                }
            }
        }

        State oldState = state;
        if (newState != state) {
            state = newState;
            sessionListener.onStateChanged(this, oldState, newState);

            if (newState == State.noSession) {
                sessionListener.onDoneCapturing(this);
            }
        }

        if (oldState != State.closed && newState == State.closed && stopping) {
            // Release all the image blocks
            releaseBlock(1, Integer.MAX_VALUE);
        }

        boolean newSuccess = sessionStatusSuccess;
        StatusDetected newDetected = sessionStatusDetected;

        if (session.has("status")) {
            // Update the success status
            JSONObject status = session.getJSONObject("status");
            if (status.has("success")) {
                newSuccess = status.getBoolean("success");
            }

            if (status.has("detected")) {
                String value = status.getString("detected");
                newDetected = StatusDetected.valueOf(value);
                if (newDetected == null) {
                    logger.severe("Unexpected status detected: " + value);
                }
            }
        }

        if (newSuccess != sessionStatusSuccess || newDetected != sessionStatusDetected) {
            sessionStatusSuccess = newSuccess;
            sessionStatusDetected = newDetected;
            sessionListener.onStatusChanged(this, sessionStatusSuccess, sessionStatusDetected);
        }
    }

    private void startEventListener() {
        Runnable listener = new Runnable() {
            @Override
            public void run() {
                logger.info("Starting event listener");
                while (!stopping) {
                    try {
                        if (!syncListen()) {
                            break;
                        }
                    } catch (JSONException e) {
                        // JSON exceptions are unexpected - should not happen at runtime
                        logger.severe(e.toString());
                    }

                    // Wait one second before retrying
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        };

        eventListenerThread = new Thread(listener);
        eventListenerThread.start();
    }
}

