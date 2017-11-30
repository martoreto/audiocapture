package com.github.martoreto.audiocapture;

import android.app.Service;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

public class MediaMetadataService extends Service {
    private static final String TAG = "MediaMetadataService";

    private RemoteCallbackList<IMediaMetadataCallback> mCallbacks = new RemoteCallbackList<>();
    MetadataMonitor mMonitor;
    private boolean mNotAllowed;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            mMonitor = new MetadataMonitor(this, null);
            mMonitor.registerCallback(mMonitorCallback);
        } catch (SecurityException e) {
            Log.w(TAG, "No permission to monitor media metadata", e);
            mNotAllowed = true;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        if (mMonitor != null) {
            mMonitor.release();
        }
        super.onDestroy();
    }

    private final IMediaMetadataService.Stub mBinder = new IMediaMetadataService.Stub() {
        @Override
        public void registerCallback(IMediaMetadataCallback callback) throws RemoteException {
            if (mNotAllowed) {
                throw new SecurityException("Audio Capture Service does not have the required " +
                        "MEDIA_CONTENT_CONTROL permission.");
            }
            mCallbacks.register(callback);
            mMonitor.dispatchState();
        }

        @Override
        public void unregisterCallback(IMediaMetadataCallback callback) throws RemoteException {
            mCallbacks.unregister(callback);
        }
    };

    private final MetadataMonitor.Callback mMonitorCallback = new MetadataMonitor.Callback() {
        @Override
        public void onMetadataChanged(@Nullable String packageName, @Nullable MediaMetadata metadata) {
            int i = mCallbacks.beginBroadcast();
            while (i > 0) {
                i--;
                try {
                    mCallbacks.getBroadcastItem(i).onMetadataChanged(packageName, metadata);
                } catch (RemoteException e) {
                    // do nothing
                }
            }
            mCallbacks.finishBroadcast();
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            int i = mCallbacks.beginBroadcast();
            while (i > 0) {
                i--;
                try {
                    mCallbacks.getBroadcastItem(i).onPlaybackStateChanged(state);
                } catch (RemoteException e) {
                    // do nothing
                }
            }
            mCallbacks.finishBroadcast();
        }
    };
}
