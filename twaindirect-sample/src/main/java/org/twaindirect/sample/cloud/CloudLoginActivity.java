package org.twaindirect.sample.cloud;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.twaindirect.cloud.CloudConnection;
import org.twaindirect.sample.R;
import org.twaindirect.sample.TwainDirectSampleApplication;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

public class CloudLoginActivity extends AppCompatActivity {
    private static final Logger logger = Logger.getLogger(CloudLoginActivity.class.getName());

    EditText urlEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud_login);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        urlEditor = (EditText)findViewById(R.id.cloud_url);

        Button loginGoogleButton = (Button)findViewById(R.id.login_google);
        loginGoogleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Build the Google login URL by appending the endpoint to the API base
                String url = getEnteredUrl() + "authentication/signin/google";

                logger.info("Launching " + url);

                Intent i = new Intent(CloudLoginActivity.this, CloudLoginWebView.class);
                i.putExtra("url", url);
                startActivityForResult(i, 1);
            }
        });

        Button loginFacebookButton = (Button)findViewById(R.id.login_facebook);
        loginFacebookButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Build the Facebook login URL by appending the endpoint to the API base
                String url = getEnteredUrl() + "authentication/signin/facebook";

                logger.info("Launching " + url);

                Intent i = new Intent(CloudLoginActivity.this, CloudLoginWebView.class);
                i.putExtra("url", url);
                startActivityForResult(i, 1);
            }
        });

        // Development support - we don't have the redirect from the authentication endpoints
        // back to the app's URL scheme yet, so for now the user can paste in an auth
        // token and hit the Proceed button, and we'll treat that as the callback.
        Button proceedButton = (Button)findViewById(R.id.proceed_button);
        proceedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String accessToken = ((EditText)findViewById(R.id.auth_token)).getText().toString();
                try {
                    URI url = new URI(getEnteredUrl());

                    // Set the application's cloudConnection
                    CloudConnection cloudConnection = new CloudConnection(url, accessToken, null);
                    ((TwainDirectSampleApplication)getApplication()).cloudConnection = cloudConnection;

                    // Kick off the scanner list activity
                    Intent intent = new Intent(CloudLoginActivity.this, CloudScannerPickerActivity.class);
                    startActivity(intent);
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Helper method that gets the URL the user typed and ensures it ends with a slash.
     * @return
     */
    private String getEnteredUrl() {
        String url = urlEditor.getText().toString();
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        return url;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (data != null) {
                String accessToken = data.getStringExtra(CloudLoginWebView.AUTH_TOKEN_KEY);
                String refreshToken = data.getStringExtra(CloudLoginWebView.REFRESH_TOKEN_KEY);

                // Set the application's cloudConnection
                try {
                    URI url = new URI(getEnteredUrl());
                    CloudConnection cloudConnection = new CloudConnection(url, accessToken, refreshToken);
                    TwainDirectSampleApplication application = (TwainDirectSampleApplication) getApplication();
                    application.cloudConnection = cloudConnection;
                    application.saveCloudConnectionTokens();

                    // Kick off the scanner list activity
                    Intent intent = new Intent(CloudLoginActivity.this, CloudScannerPickerActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
                    startActivity(intent);
                    finish();
                } catch (URISyntaxException e) {
                    logger.warning(e.getMessage());
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}
