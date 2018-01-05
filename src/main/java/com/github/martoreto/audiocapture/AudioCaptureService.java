package com.github.martoreto.audiocapture;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.audiopolicy.AudioMix;
import android.media.audiopolicy.AudioMixingRule;
import android.media.audiopolicy.AudioPolicy;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.support.annotation.GuardedBy;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class AudioCaptureService extends Service {
    private static final String TAG = "AudioCaptureService";

    static final String ACTION_PERMISSION_RESULT = "AudioCaptureService.PERMISSION_RESULT";
    static final String EXTRA_RESULT = "result";

    private final Object mLock = new Object();
    @GuardedBy("mLock") private IAudioCaptureCallback mCallback;

    private Handler mHandler = new Handler();
    private AudioMix mMix;
    private AudioPolicy mAudioPolicy;
    private AudioRecord mAudioRecord;
    private AudioThread mAudioThread;
    private boolean mWaitingForPermission;

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter intentFilter = new IntentFilter(ACTION_PERMISSION_RESULT);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IAudioCaptureService.Stub mBinder = new IAudioCaptureService.Stub() {
        @Override
        public void startAudioCapture(IAudioCaptureCallback callback) throws RemoteException {
            synchronized (mLock) {
                if (ContextCompat.checkSelfPermission(AudioCaptureService.this,
                        "android.permission.MODIFY_AUDIO_ROUTING") != PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException("Audio Capture Service does not have the required " +
                            "MODIFY_AUDIO_ROUTING permission.");
                }
                if (mCallback != null) {
                    throw new IllegalStateException("Capture already started");
                }
                mCallback = callback;

                callback.asBinder().linkToDeath(mDeathRecipient, 0);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        start();
                    }
                });
            }
        }

        @Override
        public void stopAudioCapture(IAudioCaptureCallback callback) throws RemoteException {
            synchronized (mLock) {
                if (!callback.asBinder().equals(mCallback.asBinder())) {
                    throw new IllegalStateException("Bad capture");
                }
            }
            mHandler.post(mStopRunnable);
        }
    };

    private final IBinder.DeathRecipient mDeathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            mHandler.post(mStopRunnable);
        }
    };

    private final Runnable mStopRunnable = new Runnable() {
        @Override
        public void run() {
            stop();
        }
    };

    private void start() {
        synchronized (mLock) {
            if (mCallback == null) {
                return;
            }
        }

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED ) {
            mWaitingForPermission = true;
            Intent intent = new Intent(this, SetupActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } else {
            mWaitingForPermission = false;
        }

        mMix = buildAudioMix(true, 48000);
        mAudioPolicy = new AudioPolicy.Builder(this)
                .addMix(mMix)
                .setLooper(Looper.getMainLooper())
                .build();

        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            try {
                int result = (int) AudioManager.class.getMethod("registerAudioPolicy", AudioPolicy.class)
                        .invoke(audioManager, mAudioPolicy);
                if (result != 0) {
                    throw new RuntimeException("registerAudioPolicy failed with error " + result);
                }
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to redirect audio", e);
            return;
        }

        mAudioRecord = mAudioPolicy.createAudioRecordSink(mMix);

        mAudioThread = new AudioThread(mAudioRecord);
        mAudioThread.start();
    }

    private AudioAttributes buildAudioAttributes(int usage) {
        AudioAttributes.Builder builder = new AudioAttributes.Builder()
                .setUsage(usage);
        try {
            builder.getClass().getMethod("addTag", String.class).invoke(builder, "fixedVolume");
        } catch (IllegalAccessException|NoSuchMethodException|InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        return builder.build();
    }

    private AudioMix buildAudioMix(boolean stereo, int sampleRate) {
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(stereo ? AudioFormat.CHANNEL_IN_LEFT | AudioFormat.CHANNEL_IN_RIGHT
                        : AudioFormat.CHANNEL_IN_LEFT)
                .setSampleRate(sampleRate)
                .build();

        return new AudioMix.Builder(new AudioMixingRule.Builder()
                .addRule(buildAudioAttributes(AudioAttributes.USAGE_MEDIA),
                        AudioMixingRule.RULE_MATCH_ATTRIBUTE_USAGE)
                    .build())
                .setFormat(audioFormat)
                .setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK)
                .build();
    }

    private void stop() {
        IAudioCaptureCallback savedCallback;
        synchronized (mLock) {
            if (mCallback == null) {
                return;
            }
            savedCallback = mCallback;
            mCallback = null;
        }

        mWaitingForPermission = false;

        if (mAudioThread != null) {
            mAudioThread.interrupt();
            try {
                mAudioThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Exception stopping audio thread", e);
            }
            mAudioThread = null;
        }

        try {
            savedCallback.onStop();
        } catch (RemoteException e) {
            Log.w(TAG, "Error calling onStop()", e);
        }

        savedCallback.asBinder().unlinkToDeath(mDeathRecipient, 0);

        if (mAudioRecord != null) {
            mAudioRecord.release();
            mAudioRecord = null;

            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            try {
                AudioManager.class.getMethod("unregisterAudioPolicyAsync", AudioPolicy.class)
                        .invoke(audioManager, mAudioPolicy);
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            mAudioPolicy = null;
        }
    }

    class AudioThread extends Thread {
        private AudioRecord mAudioRecord;

        public AudioThread(AudioRecord record) {
            super("Audio");
            mAudioRecord = record;
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

            ParcelFileDescriptor[] pipe = null;
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
            try {
                mAudioRecord.startRecording();
                pipe = ParcelFileDescriptor.createPipe();
                synchronized (mLock) {
                    mCallback.onChannelReady(pipe[0]);
                }
                pipe[0].close();
                pipe[0] = null;
                FileOutputStream outStream = new FileOutputStream(pipe[1].getFileDescriptor());
                WritableByteChannel outChannel = outStream.getChannel();
                while (!Thread.interrupted()) {
                    int read = mAudioRecord.read(buffer, buffer.remaining());
                    if (read <= 0) {
                        throw new RuntimeException("read() returned " + read);
                    }
                    outChannel.write(buffer);
                    buffer.clear();
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception in audio thread", e);
            } finally {
                try {
                    mAudioRecord.stop();
                } catch (Exception e) {
                    Log.w(TAG, "Error stopping AudioRecord", e);
                }
                if (pipe != null) {
                    if (pipe[0] != null) {
                        try {
                            pipe[0].close();
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing pipe[0]", e);
                        }
                    }
                    try {
                        pipe[1].close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing pipe[1]", e);
                    }
                }
                synchronized (mLock) {
                    if (mCallback != null) {
                        mHandler.post(mStopRunnable);
                    }
                }
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_PERMISSION_RESULT.equals(intent.getAction())) {
                if (!mWaitingForPermission) {
                    return;
                }
                mWaitingForPermission = false;
                int result = intent.getIntExtra(EXTRA_RESULT, PackageManager.PERMISSION_DENIED);
                if (result == PackageManager.PERMISSION_GRANTED) {
                    start();
                } else {
                    Toast.makeText(AudioCaptureService.this, R.string.permission_denied,
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    };
}
