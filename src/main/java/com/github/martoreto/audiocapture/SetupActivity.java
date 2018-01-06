package com.github.martoreto.audiocapture;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.common.io.CharStreams;
import com.stericson.RootShell.RootShell;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class SetupActivity extends AppCompatActivity {
    private static final String TAG = "SetupActivity";

    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!AudioCaptureUtils.isAudioCapturePrivSystemApp(getPackageManager())) {
            new Thread() {
                @Override
                public void run() {
                    if (RootShell.isRootAvailable()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                AlertDialog.Builder builder = new AlertDialog.Builder(SetupActivity.this);
                                builder.setMessage(R.string.install_alert_message);
                                builder.setPositiveButton(R.string.button_systemize, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        new Thread() {
                                            @Override
                                            public void run() {
                                                String error = null;
                                                try {
                                                    InputStream shellStream = getResources().openRawResource(R.raw.systemize);
                                                    List<String> cmd = CharStreams.readLines(new BufferedReader(new InputStreamReader(shellStream)));
                                                    List<String> result = Shell.SU.run(cmd);
                                                    if (result == null) {
                                                        throw new RuntimeException("Script failed");
                                                    }
                                                } catch (Exception e) {
                                                    Log.e(TAG, "Error systemizing", e);
                                                    error = e.toString();
                                                }
                                                final String error2 = error;
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        if (error2 != null) {
                                                            Toast.makeText(SetupActivity.this,
                                                                    getString(R.string.error_systemizing, error2),
                                                                    Toast.LENGTH_LONG).show();
                                                            setResult(RESULT_CANCELED);
                                                        } else {
                                                            setResult(RESULT_OK);
                                                        }
                                                        finish();
                                                    }
                                                });
                                            }
                                        }.start();
                                    }
                                });
                                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        setResult(RESULT_CANCELED);
                                        finish();
                                    }
                                });
                                builder.show();
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(SetupActivity.this, R.string.error_no_su, Toast.LENGTH_LONG).show();
                                setResult(RESULT_CANCELED);
                                finish();
                            }
                        });
                    }
                }
            }.start();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { android.Manifest.permission.RECORD_AUDIO },
                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }

        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                Intent intent = new Intent(AudioCaptureService.ACTION_PERMISSION_RESULT);
                boolean ok = false;
                if (grantResults.length > 0) {
                    intent.putExtra(AudioCaptureService.EXTRA_RESULT, grantResults[0]);
                    ok = (grantResults[0] == PackageManager.PERMISSION_GRANTED);
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                setResult(ok ? RESULT_OK : RESULT_CANCELED);
                finish();
            }
        }
    }
}
