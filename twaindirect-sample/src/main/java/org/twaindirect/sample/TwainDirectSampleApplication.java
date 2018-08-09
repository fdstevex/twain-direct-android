package org.twaindirect.sample;

import android.app.Application;

import org.twaindirect.cloud.CloudConnection;

/**
 * Custom Application instance, so we have a place to store information shared
 * across various parts of the app.
 */
public class TwainDirectSampleApplication extends Application {
    public CloudConnection cloudConnection;
}
