/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Spinner;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.feedback.FeedbackProvider;
import com.google.android.apps.forscience.whistlepunk.intro.AgeVerifier;
import com.google.android.apps.forscience.whistlepunk.intro.TutorialActivity;
import com.google.android.apps.forscience.whistlepunk.project.ProjectTabsFragment;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivity";
    public static final String ARG_SELECTED_NAV_ITEM_ID = "selected_nav_item_id";
    public static final int PERMISSIONS_AUDIO_RECORD_REQUEST = 1;
    protected static final int NO_SELECTED_ITEM = -1;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitleToRestore;

    private FeedbackProvider mFeedbackProvider;
    private NavigationView mNavigationView;
    private MultiTouchDrawerLayout mDrawerLayout;
    private Spinner mSpinner;
    private RecordFragment mRecordFragment;
    private int mSelectedItemId = NO_SELECTED_ITEM;
    private RecorderController.RecordingStateListener mRecordingStateListener;
    private boolean mIsRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (showRequiredScreensIfNeeded()) {
            return;
        }
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        }

        mSpinner = (Spinner) findViewById(R.id.spinner_nav);
        mDrawerLayout = (MultiTouchDrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerLayout.setStatusBarBackgroundColor(getResources().getColor(
                R.color.color_primary_dark));
        mNavigationView = (NavigationView) findViewById(R.id.navigation);
        mNavigationView.setNavigationItemSelectedListener(this);

        // Only show dev testing options for (1) user-debug devices (2) debug APK builds
        if (DevOptionsFragment.shouldHideTestingOptions(this)) {
            mNavigationView.getMenu().removeItem(R.id.dev_testing_options);
        }

        mFeedbackProvider = WhistlePunkApplication.getFeedbackProvider(this);

        Bundle extras = getIntent().getExtras();
        int selectedNavItemId = R.id.navigation_item_observe;

        int savedItemId = getSavedItemId(savedInstanceState);
        if (savedItemId != NO_SELECTED_ITEM) {
            selectedNavItemId = savedItemId;
        } else if (extras != null) {
            selectedNavItemId = extras.getInt(
                    ARG_SELECTED_NAV_ITEM_ID, R.id.navigation_item_observe);
        }
        MenuItem item = mNavigationView.getMenu().findItem(selectedNavItemId);
        if (item == null) {
            selectedNavItemId = R.id.navigation_item_observe;
            item = mNavigationView.getMenu().findItem(selectedNavItemId);
        }
        mNavigationView.setCheckedItem(selectedNavItemId);
        onNavigationItemSelected(item);

        mRecordingStateListener = new RecorderController.RecordingStateListener() {
            @Override
            public void onRecordingStateChanged(RecordingMetadata recording) {
                mNavigationView.getMenu().findItem(R.id.navigation_item_projects).setEnabled(
                        recording == null);
                mIsRecording = recording != null;
                exitMetadataIfNeeded();
            }

            @Override
            public void onRecordingStartFailed(
                    @RecorderController.RecordingStartErrorType int errorType, Exception e) {

            }

            @Override
            public void onRecordingStopFailed(
                    @RecorderController.RecordingStopErrorType int recordingStateErrorType) {

            }
        };

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    private void exitMetadataIfNeeded() {
        if (mIsRecording) {
            if (mSelectedItemId == R.id.navigation_item_projects) {
                finish();
            }
        }
    }

    private int getSavedItemId(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return NO_SELECTED_ITEM;
        } else {
            return savedInstanceState.getInt(ARG_SELECTED_NAV_ITEM_ID, NO_SELECTED_ITEM);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(ARG_SELECTED_NAV_ITEM_ID, mSelectedItemId);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (showRequiredScreensIfNeeded()) {
            return;
        }
        AppSingleton.getInstance(this).withRecorderController(TAG,
                new Consumer<RecorderController>() {
                    @Override
                    public void take(RecorderController rc) {
                        rc.addRecordingStateListener(TAG, mRecordingStateListener);
                        rc.setRecordActivityInForeground(true);
                    }
                });
        mRecordFragment = (RecordFragment) getFragmentManager().findFragmentByTag(
                String.valueOf(R.id.navigation_item_observe));
        // If we get to here, it's safe to log the mode we are in: user has completed age
        // verification.
        WhistlePunkApplication.getUsageTracker(this).trackEvent(TrackerConstants.CATEGORY_APP,
                TrackerConstants.ACTION_SET_MODE,
                AgeVerifier.isOver13(AgeVerifier.getUserAge(this)) ?
                        TrackerConstants.LABEL_MODE_NONCHILD : TrackerConstants.LABEL_MODE_CHILD,
                0);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent != null) {
            setIntent(intent);
        }
        super.onNewIntent(intent);

        if (mNavigationView != null && mNavigationView.getMenu() != null) {
            int desiredItemId = -1;
            if (intent.getExtras() != null) {
                desiredItemId = intent.getExtras().getInt(ARG_SELECTED_NAV_ITEM_ID,
                        NO_SELECTED_ITEM);
            }
            if (desiredItemId != -1 && mSelectedItemId != desiredItemId) {
                onNavigationItemSelected(mNavigationView.getMenu().findItem(desiredItemId));
            }
        }

    }

    @Override
    protected void onPause() {
        AppSingleton singleton = AppSingleton.getInstance(this);
        singleton.withRecorderController(TAG,
                new Consumer<RecorderController>() {
                    @Override
                    public void take(RecorderController rc) {
                        rc.removeRecordingStateListener(TAG);
                        rc.setRecordActivityInForeground(false);
                    }
                });
        singleton.removeListeners(TAG);
        super.onPause();
    }

    /**
     * If we haven't seen all the required screens, opens the next required activity, and finishes
     * this activity
     *
     * @return true iff the activity has been finished
     */
    private boolean showRequiredScreensIfNeeded() {
        if (TutorialActivity.shouldShowTutorial(this)) {
            Intent intent = new Intent(this, TutorialActivity.class);
            startActivity(intent);
            finish();
            return true;
        } else if (AgeVerifier.shouldShowUserAge(this)) {
            Intent intent = new Intent(this, AgeVerifier.class);
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }

    // TODO: need a more principled way of keeping the action bar current

    public void restoreActionBar() {
        if (mTitleToRestore != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle(mTitleToRestore);
            mSpinner.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem menuItem) {
        if (menuItem == null) {
            return false;
        }
        if (menuItem.getGroupId() == R.id.navigation_top) {
            FragmentManager fragmentManager = getFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            Fragment fragment;
            int itemId = menuItem.getItemId();

            final String tag = String.valueOf(itemId);
            fragment = getFragmentManager().findFragmentByTag(tag);
            if (fragment == null) {
                fragment = createNewFragment(itemId);
            }
            adjustActivityForSelectedItem(itemId);

            mTitleToRestore = getTitleToRestore(menuItem);
            transaction.replace(R.id.content_container, fragment, tag).commit();
            if (menuItem.isCheckable()) {
                menuItem.setChecked(true);
            }
            mDrawerLayout.closeDrawers();
            restoreActionBar();
            mSelectedItemId = itemId;
        } else if (menuItem.getGroupId() == R.id.navigation_bottom) {
            mDrawerLayout.closeDrawers();
            // Launch intents
            Intent intent = null;
            int itemId = menuItem.getItemId();

            if (itemId == R.id.navigation_item_website) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                        getString(R.string.website_url)));
            } else if (itemId == R.id.navigation_item_settings) {
                intent = SettingsActivity.getLaunchIntent(this, menuItem.getTitle(),
                        SettingsActivity.TYPE_SETTINGS);
            } else if (itemId == R.id.navigation_item_about) {
                intent = SettingsActivity.getLaunchIntent(this, menuItem.getTitle(),
                        SettingsActivity.TYPE_ABOUT);
            } else if (itemId == R.id.dev_testing_options) {
                intent = SettingsActivity.getLaunchIntent(this, menuItem.getTitle(),
                        SettingsActivity.TYPE_DEV_OPTIONS);
            } else if (itemId == R.id.navigation_item_feedback) {
                mFeedbackProvider.sendFeedback(new LoggingConsumer<Boolean>(TAG,
                        "Send feedback") {
                    @Override
                    public void success(Boolean value) {
                        if (!value) {
                            showFeedbackError();
                        }
                    }

                    @Override
                    public void fail(Exception e) {
                        super.fail(e);
                        showFeedbackError();
                    }
                });
            }
            if (intent != null) {
                startActivity(intent);
            }
        }

        return false;
    }

    private CharSequence getTitleToRestore(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.navigation_item_observe) {
            return null;
        } else {
            return menuItem.getTitle();
        }
    }

    private Fragment createNewFragment(int itemId) {
        if (itemId == R.id.navigation_item_observe) {
            mRecordFragment = RecordFragment.newInstance();
            return mRecordFragment;
        } else if (itemId == R.id.navigation_item_projects) {
            return ProjectTabsFragment.newInstance();
        } else {
            throw new IllegalArgumentException("Unknown menu item " + itemId);
        }
    }

    private void adjustActivityForSelectedItem(int itemId) {
        MenuItem menu = mNavigationView.getMenu().findItem(itemId);
        setTitle(getString(R.string.title_activity_main, menu.getTitle()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Only show items in the action bar relevant to this screen
        // if the drawer is not showing. Otherwise, let the drawer
        // decide what to show in the action bar.
        if (!mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            restoreActionBar();
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == android.R.id.home) {
            if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout.closeDrawer(GravityCompat.START);
            } else {
                mDrawerLayout.openDrawer(GravityCompat.START);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void showFeedbackError() {
        AccessibilityUtils.makeSnackbar(findViewById(R.id.drawer_layout),
                getResources().getString(R.string.feedback_error_message),
                Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults) {
        final boolean granted = grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED;
        switch (requestCode) {
            case PERMISSIONS_AUDIO_RECORD_REQUEST:
                AppSingleton.getInstance(this).withRecorderController(TAG,
                        new Consumer<RecorderController>() {
                            @Override
                            public void take(RecorderController rc) {
                                if (mRecordFragment != null) {
                                    mRecordFragment.audioPermissionGranted(granted);
                                }
                            }
                        });
                return;
            default:
                PictureUtils.onRequestPermissionsResult(requestCode, permissions, grantResults,
                        this);
                return;
        }
    }

    /**
     * Launches the main activity to the selected navigation item.
     *
     * @param id One of the navigation_item constants.
     */
    public static void launch(Context context, int id) {
        context.startActivity(launchIntent(context, id));
    }

    @NonNull
    public static Intent launchIntent(Context context, int id) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(ARG_SELECTED_NAV_ITEM_ID, id);
        return intent;
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO: Do this for all possible IDs in case others have activity results.
        Fragment fragment = getFragmentManager().findFragmentByTag(
                String.valueOf(R.id.navigation_item_observe));
        if (fragment != null) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }
}
