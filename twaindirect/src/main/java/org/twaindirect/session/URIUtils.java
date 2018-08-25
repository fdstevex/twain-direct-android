package org.twaindirect.session;

import java.net.URI;

public class URIUtils {

    /**
     * Return a new URI that appends a path to an existing URI.
     * Handles starting/trailing slashes.
     */
    public static URI appendPathToURI(URI uri, String path) {
        String uriPath = uri.getPath();
        if (!uriPath.endsWith("/")) {
            uriPath = uriPath + "/";
        }

        if (path.startsWith("/")) {
            return uri.resolve(uriPath + path.substring(1));
        } else {
            return uri.resolve(uriPath + path);
        }
    }
}
