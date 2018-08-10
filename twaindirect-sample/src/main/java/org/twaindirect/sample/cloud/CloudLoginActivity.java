package org.twaindirect.sample.cloud;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.twaindirect.cloud.CloudConnection;
import org.twaindirect.sample.R;
import org.twaindirect.sample.TwainDirectSampleApplication;

public class CloudLoginActivity extends AppCompatActivity {

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
                // Build the Facebook login URL by appending the endpoint to the API base
                String url = getEnteredUrl() + "authentication/signin/facebook";

                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });

        Button loginFacebookButton = (Button)findViewById(R.id.login_facebook);
        loginFacebookButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Build the Google login URL by appending the endpoint to the API base
                String url = getEnteredUrl() + "authentication/signin/google";

                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });

        // Development support - we don't have the redirect from the authentication endpoints
        // back to the app's URL scheme yet, so for now the user can paste in an auth
        // token and hit the Proceed button, and we'll treat that as the callback.
        Button proceedButton = (Button)findViewById(R.id.proceed_button);
        proceedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String authToken = ((EditText)findViewById(R.id.auth_token)).getText().toString();
                String url = getEnteredUrl();
                if (!url.endsWith("/")) {
                    url = url + "/";
                }

                // Set the application's cloudConnection
                CloudConnection cloudConnection = new CloudConnection(url, authToken, null);
                ((TwainDirectSampleApplication)getApplication()).cloudConnection = cloudConnection;

                // Kick off the scanner list activity
                Intent intent = new Intent(CloudLoginActivity.this, CloudScannerPickerActivity.class);
                startActivity(intent);
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
}
