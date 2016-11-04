/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.music;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Simple widget to show currently playing album art along
 * with play/pause and next track buttons.  
 */
public class MediaAppWidgetProvider extends AppWidgetProvider {
    static final String TAG = "MusicAppWidgetProvider";
    
    public static final String CMDAPPWIDGETUPDATE = "appwidgetupdate";
    private static final String UPDATE_WIDGET_ACTION = "com.android.music.updatewidget";

    private static MediaAppWidgetProvider sInstance;
    
    private static boolean mPauseState = false;
    private static boolean mNoneedperformUpdate = false;

    private final int REFRESH_PROGRESS = 0;
    private MediaPlaybackService mService = null;
    private final int DELAY_REFRESH_TIME = 500;
    private RemoteViews mRemoteViews;

    private Handler mHandler = new Handler(){

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH_PROGRESS:
                    try {
                        doRefresh();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    queueNextRefresh(DELAY_REFRESH_TIME);
                    break;

                default:
                    break;
            }
            super.handleMessage(msg);
        }

    };

    private void doRefresh() throws RemoteException {
        if (mService == null || mRemoteViews == null) {
            return;
        }

        long pos = mService.position();
        long duration = mService.duration();
        if (pos > 0 && duration > 0) {
            int progress = (int) (pos * 1000 / duration);
            mRemoteViews.setProgressBar(R.id.progress_bar, 1000, progress, false);
        }

        if (mService.isComplete()) {
            mRemoteViews.setProgressBar(R.id.progress_bar, 1000, 1000, false);
        }
        pushUpdate(mService.getApplicationContext(), null, mRemoteViews);
    }

    private void queueNextRefresh(long delay) {
        mHandler.removeMessages(REFRESH_PROGRESS);
        if (mService != null && mService.isPlaying()) {
            Message msg = mHandler.obtainMessage(REFRESH_PROGRESS);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }

    static synchronized MediaAppWidgetProvider getInstance() {
        if (sInstance == null) {
            sInstance = new MediaAppWidgetProvider();
        }
        return sInstance;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(UPDATE_WIDGET_ACTION) && hasInstances(context)) {
            //receive from MediaPlaybackService onDestroy
            final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.album_appwidget);
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, this.getClass()));
            /* everytime pushUpdate(updateAppWidget) should do linkButtons, otherwise the buttons will not work */
            linkButtons(context, views, false /* not playing */);
            pushUpdate(context, appWidgetIds, views);
            //this time the service is died,when service reactivate no need call performUpdate
            //otherwise title will be falsh
            mNoneedperformUpdate = true;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        mHandler.removeMessages(REFRESH_PROGRESS, null);
        super.onDeleted(context, appWidgetIds);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        mNoneedperformUpdate = false;
        defaultAppWidget(context, appWidgetIds);
        
        // Send broadcast intent to any running MediaPlaybackService so it can
        // wrap around with an immediate update.
        Intent updateIntent = new Intent(MediaPlaybackService.SERVICECMD);
        updateIntent.putExtra(MediaPlaybackService.CMDNAME,
                MediaAppWidgetProvider.CMDAPPWIDGETUPDATE);
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        updateIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        context.sendBroadcast(updateIntent);
    }
    
    /**
     * Initialize given widgets to default state, where we launch Music on default click
     * and hide actions if service not running.
     */
    private void defaultAppWidget(Context context, int[] appWidgetIds) {
        final Resources res = context.getResources();
        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.album_appwidget);
        views.setViewVisibility(R.id.title, View.INVISIBLE);
        views.setImageViewResource(R.id.control_play, R.drawable.ic_widget_mp_play_selector);
        views.setProgressBar(R.id.progress_bar, 1000, 0, false);
        views.setTextViewText(R.id.title, res.getText(R.string.widget_initial_text));

        linkButtons(context, views, false /* not playing */);
        pushUpdate(context, appWidgetIds, views);
    }
    
    private void pushUpdate(Context context, int[] appWidgetIds, RemoteViews views) {
        // Update specific list of appWidgetIds if given, otherwise default to all
        final AppWidgetManager gm = AppWidgetManager.getInstance(context);
        if (appWidgetIds != null) {
            gm.updateAppWidget(appWidgetIds, views);
        } else {
            gm.updateAppWidget(new ComponentName(context, this.getClass()), views);
        }
    }

    /**
     * Check against {@link AppWidgetManager} if there are any instances of this widget.
     */
    private boolean hasInstances(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new ComponentName(context, this.getClass()));
        return (appWidgetIds.length > 0);
    }

    void setPauseState(boolean mPaused) {
        mPauseState = mPaused;
    }

    /**
     * Handle a change notification coming over from {@link MediaPlaybackService}
     */
    void notifyChange(MediaPlaybackService service, String what) {
        if (hasInstances(service)) {
            if (MediaPlaybackService.META_CHANGED.equals(what) ||
                    MediaPlaybackService.PLAYSTATE_CHANGED.equals(what)) {
                performUpdate(service, null);
            }
        }
    }
    
    /**
     * Update all active widget instances by pushing changes 
     */
    void performUpdate(MediaPlaybackService service, int[] appWidgetIds) {
        mService = service;
        if (mNoneedperformUpdate) {
            //this time no need performUpdate,otherwise title will be flash
            mNoneedperformUpdate = false;
            return;
        }
        final Resources res = service.getResources();
        mRemoteViews = new RemoteViews(service.getPackageName(), R.layout.album_appwidget);
        
        CharSequence titleName = service.getTrackName();
        CharSequence artistName = service.getArtistName();
        CharSequence errorState = null;
        
        // Format title string with track number, or show SD card message
        String status = Environment.getExternalStorageState();
        if (status.equals(Environment.MEDIA_SHARED) ||
                status.equals(Environment.MEDIA_UNMOUNTED)) {
            if (android.os.Environment.isExternalStorageRemovable()) {
                errorState = res.getText(R.string.sdcard_busy_title);
            } else {
                errorState = res.getText(R.string.sdcard_busy_title_nosdcard);
            }
        } else if (status.equals(Environment.MEDIA_REMOVED)) {
            if (android.os.Environment.isExternalStorageRemovable()) {
                errorState = res.getText(R.string.sdcard_missing_title);
            } else {
                errorState = res.getText(R.string.sdcard_missing_title_nosdcard);
            }
        }
        
        if (errorState != null) {
            // Show error state to user
            mRemoteViews.setTextViewText(R.id.title, errorState);
            //views.setTextViewText(R.id.artist, errorState);
            
        } else {
            // No error, so show normal titles
            mRemoteViews.setViewVisibility(R.id.title, View.VISIBLE);
            mRemoteViews.setTextViewText(R.id.title, titleName);
            //views.setTextViewText(R.id.artist, artistName);
        }
        
        // Set correct drawable for pause state
        final boolean playing = service.isPlaying();
        if (playing) {
            mRemoteViews.setImageViewResource(R.id.control_play, R.drawable.ic_widget_mp_pause_selector);
        } else {
            if (titleName == null && !mPauseState && (errorState == null)) {
                mRemoteViews.setViewVisibility(R.id.title, View.INVISIBLE);
                //views.setTextViewText(R.id.artist, res.getText(R.string.emptyplaylist));
            }
            mRemoteViews.setImageViewResource(R.id.control_play, R.drawable.ic_widget_mp_play_selector);
        }

        mHandler.removeMessages(REFRESH_PROGRESS);
        if (playing) {
            mHandler.sendEmptyMessage(REFRESH_PROGRESS);
        }

        // Link actions buttons to intents
        linkButtons(service, mRemoteViews, playing);
        
        pushUpdate(service, appWidgetIds, mRemoteViews);
    }

    /**
     * Link up various button actions using {@link PendingIntents}.
     * 
     * @param playerActive True if player is active in background, which means
     *            widget click will launch {@link MediaPlaybackActivity},
     *            otherwise we launch {@link MusicBrowserActivity}.
     */
    private void linkButtons(Context context, RemoteViews views, boolean playerActive) {
        // Connect up various buttons and touch events
        Intent intent;
        PendingIntent pendingIntent;
        
        final ComponentName serviceName = new ComponentName(context, MediaPlaybackService.class);
        
        if (playerActive) {
            intent = new Intent(context, MediaPlaybackActivity.class);
            pendingIntent = PendingIntent.getActivity(context,
                    0 /* no requestCode */, intent, 0 /* no flags */);
            views.setOnClickPendingIntent(R.id.album_img, pendingIntent);
        } else {
            intent = new Intent(context, MusicBrowserActivity.class);
            pendingIntent = PendingIntent.getActivity(context,
                    0 /* no requestCode */, intent, 0 /* no flags */);
            views.setOnClickPendingIntent(R.id.album_img, pendingIntent);
        }

        intent = new Intent(MediaPlaybackService.PREVIOUS_ACTION);
        intent.setComponent(serviceName);
        pendingIntent = PendingIntent.getService(context,
                0 /* no requestCode */, intent, 0 /* no flags */);
        views.setOnClickPendingIntent(R.id.control_prev, pendingIntent);
        
        intent = new Intent(MediaPlaybackService.TOGGLEPAUSE_ACTION);
        intent.setComponent(serviceName);
        pendingIntent = PendingIntent.getService(context,
                0 /* no requestCode */, intent, 0 /* no flags */);
        views.setOnClickPendingIntent(R.id.control_play, pendingIntent);
        
        intent = new Intent(MediaPlaybackService.NEXT_ACTION);
        intent.setComponent(serviceName);
        pendingIntent = PendingIntent.getService(context,
                0 /* no requestCode */, intent, 0 /* no flags */);
        views.setOnClickPendingIntent(R.id.control_next, pendingIntent);
    }
}
