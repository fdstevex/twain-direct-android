package org.twaindirect.sample.cloud;

import android.content.SharedPreferences;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.twaindirect.cloud.CloudScannerInfo;
import org.twaindirect.discovery.AndroidServiceDiscoverer;
import org.twaindirect.discovery.ScannerDiscoveredListener;
import org.twaindirect.discovery.ScannerInfo;
import org.twaindirect.sample.Preferences;
import org.twaindirect.sample.R;
import org.twaindirect.sample.ScannerInfoArrayAdapter;
import org.twaindirect.sample.ScannerPickerActivity;
import org.twaindirect.sample.TwainDirectSampleApplication;
import org.twaindirect.session.AsyncResult;

import java.util.ArrayList;
import java.util.List;

public class CloudScannerPickerActivity extends AppCompatActivity {
    ListView listView;
    private static final String TAG = "CloudScannerPickerAct";
    private List<CloudScannerInfo> scanners = new ArrayList<>();
    private ArrayAdapter<CloudScannerInfo> scannerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_cloud_scanner_picker);

        listView = (ListView)findViewById(R.id.scanner_list);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                CloudScannerInfo selectedScanner = scanners.get(i);

                // TODO: Remember the selected cloud scanner
//                SharedPreferences prefs = Preferences.getSharedPreferences(ScannerPickerActivity.this);
//                prefs.edit().putString("selectedScanner", selectedScanner.toJSON()).apply();
            }
        });

        scannerAdapter = new CloudScannerInfoArrayAdapter(this, scanners);
        listView.setAdapter(scannerAdapter);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Snackbar.make(listView, R.string.searching_for_scanners, Snackbar.LENGTH_INDEFINITE).show();

        TwainDirectSampleApplication application = (TwainDirectSampleApplication)getApplication();
        application.cloudConnection.getScannerList(new AsyncResult<List<CloudScannerInfo>>() {
            @Override
            public void onResult(final List<CloudScannerInfo> scanners) {
                System.out.println("Got scanner list");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scannerAdapter.clear();
                        scannerAdapter.addAll(scanners);
                        scannerAdapter.notifyDataSetChanged();
                    }
                });

            }

            @Override
            public void onError(Exception e) {
                Log.e(CloudScannerPickerActivity.TAG, e.getMessage());
            }
        });
    }
}
