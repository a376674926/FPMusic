/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.ListActivity;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.KeyEvent;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import java.lang.Integer;

public class VideoBrowserActivity extends ListActivity implements MusicUtils.Defs
{
    private int mSelectedPosition; // Position of selected view
    private static final int SHARE = 0; // Menu to share video
    private static final int DELETE = 1; // Menu to delete video
    private String mFilterString = "";

    private static String EXTRA_ALL_VIDEO_FOLDER = "org.codeaurora.intent.extra.ALL_VIDEO_FOLDER";
    private static String EXTRA_ORDERBY = "org.codeaurora.intent.extra.VIDEO_LIST_ORDERBY";
    private static String DELETE_VIDEO_ITEM = "delete.video.file";

    public VideoBrowserActivity()
    {
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            mFilterString = intent.getStringExtra(SearchManager.QUERY);
        }
        init();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        // Get position of selected view
        AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfo;
        mSelectedPosition =  mi.position;

        // Set menu title
        if (null == mCursor) {
            return;
        }
        mCursor.moveToPosition(mSelectedPosition);
        String currentVideoName = mCursor.getString(mCursor.getColumnIndexOrThrow(
                MediaStore.Video.Media.TITLE));
        menu.setHeaderTitle(currentVideoName);

        // Menu item to share video
        menu.add(0, SHARE, 0, R.string.share);
        menu.add(0, DELETE, 0, R.string.delete_item);

        if (getResources().getBoolean(R.bool.def_music_play_selection_enabled)) {
            menu.add(0, PLAY_SELECTION, 0, R.string.play_selection);
        }

    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case SHARE:
                // Send intent to share video
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("video/*");

                if (null == mCursor) {
                    return super.onContextItemSelected(item);
                }
                mCursor.moveToPosition(mSelectedPosition);
                long id = mCursor.getLong(mCursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID));
                Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                startActivity(shareIntent);
                return true;
            case DELETE:
                mCursor.moveToPosition(mSelectedPosition);
                long videoId = mCursor.getLong(mCursor.getColumnIndexOrThrow
                        (MediaStore.Video.Media._ID));
                Uri videoUri = ContentUris.withAppendedId(MediaStore.Video.Media
                        .EXTERNAL_CONTENT_URI, videoId);
                String videoTitle= mCursor.getString(mCursor.getColumnIndexOrThrow
                        (MediaStore.Video.Media.TITLE));
                String videoName = getString(R.string.delete_video_item_message,
                        videoTitle);
                Bundle bundle = new Bundle();
                bundle.putString("description", DELETE_VIDEO_ITEM);
                bundle.putString("videoName", videoName);
                bundle.putParcelable("videoUri", videoUri);
                Intent deleteIntent = new Intent();
                deleteIntent.setClass(this, DeleteItems.class);
                deleteIntent.putExtras(bundle);
                startActivity(deleteIntent);
                return true;

            case PLAY_SELECTION: {
                // play the video
                int position = mSelectedPosition;
                mCursor.moveToPosition(position);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                long selectId = mCursor.getLong(mCursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media._ID));
                String type = mCursor.getString(mCursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media.MIME_TYPE));
                intent.setDataAndType(ContentUris.withAppendedId(MediaStore.Video
                        .Media.EXTERNAL_CONTENT_URI, selectId), type);
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(this, R.string.enable_gallery_app, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (getResources().getBoolean(R.bool.def_music_add_more_video_enabled))
            menu.add(0, MORE_VIDEO, 0, R.string.more_video).setIcon(
                    R.drawable.ic_menu_set_as_ringtone);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MORE_VIDEO:
                Uri MoreUri = Uri
                        .parse(getResources().getString(R.string.def_music_add_more_video));
                Intent MoreIntent = new Intent(Intent.ACTION_VIEW, MoreUri);
                startActivity(MoreIntent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void init() {

        // Set the layout for this activity.  You can find it
        // in assets/res/any/layout/media_picker_activity.xml
        setContentView(R.layout.media_picker_activity);
        View listView = findViewById(R.id.buttonbar);
        listView.setVisibility(View.GONE);
        getListView().setOnCreateContextMenuListener(this); // Set OnCreateContextMenuListener interface

        MakeCursor();

        if (mCursor == null || mCursor.getCount() == 0) {
            MusicUtils.displayDatabaseError(this);
            return;
        }

        if (mCursor.getCount() > 0) {
            setTitle(R.string.videos_title);
        } else {
            setTitle(R.string.no_videos_title);
        }

        // Map Cursor columns to views defined in media_list_item.xml
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                this,
                R.layout.simple_list_item_1,
                mCursor,
                new String[] { MediaStore.Video.Media.TITLE},
                new int[] { android.R.id.text1 });

        setListAdapter(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        mCursor.moveToPosition(position);
        String type = mCursor.getString(mCursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE));
        intent.setDataAndType(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id), type);
        intent.putExtra(EXTRA_ALL_VIDEO_FOLDER, true);
        if (mSortOrder != null) {
            intent.putExtra(EXTRA_ORDERBY, mSortOrder);
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, R.string.enable_gallery_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void MakeCursor() {
        String[] cols = new String[] {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.ARTIST
        };
        ContentResolver resolver = getContentResolver();
        if (resolver == null) {
            System.out.println("resolver = null");
        } else {
            mSortOrder = MediaStore.Video.Media.TITLE + " COLLATE UNICODE";
            if (TextUtils.isEmpty(mFilterString)){
                mWhereClause = MediaStore.Video.Media.TITLE + " != ''";
            }else{
                mWhereClause = MediaStore.Video.Media.TITLE + " like '%"+mFilterString+"%'";
            }
            mCursor = resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                cols, mWhereClause , null, mSortOrder);
        }
    }

    @Override
    public void onResume() {
        if (mCursor != null && mCursor.getCount() > 0) {
            setTitle(R.string.videos_title);
        }
        super.onResume();
    }

    @Override
    public void onDestroy() {
        if (mCursor != null) {
            mCursor.close();
        }
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP &&
                event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private Cursor mCursor;
    private String mWhereClause;
    private String mSortOrder;
}

