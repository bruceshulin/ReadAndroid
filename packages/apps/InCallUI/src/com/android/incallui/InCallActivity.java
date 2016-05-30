/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.incallui;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.MenuItem;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import com.android.phone.common.animation.AnimUtils;
import com.android.phone.common.animation.AnimationListenerAdapter;
import com.android.contacts.common.interactions.TouchPointManager;
import com.android.contacts.common.util.MaterialColorMapUtils;
import com.android.contacts.common.util.MaterialColorMapUtils.MaterialPalette;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment.SelectPhoneAccountListener;
import com.android.incallui.Call.State;
import com.sprd.android.config.OptConfig;
import com.sprd.incallui.PhoneRecorderHelper;
import com.sprd.incallui.PhoneRecorderHelper.OnStateChangedListener;
import com.sprd.incallui.ConferenceListFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//dujunchao add@20150821 for incall
import android.os.PowerManager.WakeLock;
import android.os.PowerManager;
//dujunchao add@20150821 for incall
/**
 * Phone app "in call" screen.
 */
public class InCallActivity extends Activity {

    public static final String SHOW_DIALPAD_EXTRA = "InCallActivity.show_dialpad";
    public static final String DIALPAD_TEXT_EXTRA = "InCallActivity.dialpad_text";
    public static final String NEW_OUTGOING_CALL_EXTRA = "InCallActivity.new_outgoing_call";
    public static final String SHOW_CIRCULAR_REVEAL_EXTRA = "InCallActivity.show_circular_reveal";
    // SPRD: Add for dealing with SS notification.
    private static final String ACTION_SUPP_SERVICE_FAILURE =
            "org.codeaurora.ACTION_SUPP_SERVICE_FAILURE";
    private CallButtonFragment mCallButtonFragment;
    private CallCardFragment mCallCardFragment;
    private AnswerFragment mAnswerFragment;
    private DialpadFragment mDialpadFragment;
    private ConferenceManagerFragment mConferenceManagerFragment;
    private FragmentManager mChildFragmentManager;

    private boolean mIsForegroundActivity;
    private AlertDialog mDialog;
    // sprd: add for conferencecall list
    private ConferenceListFragment mConferenceListFragment;
    // SPRD: Add for dealing with SS notification.
    private SuppServFailureNotificationReceiver mReceiver;

    /** Use to pass 'showDialpad' from {@link #onNewIntent} to {@link #onResume} */
    private boolean mShowDialpadRequested;
    /* SPRD: Add for recorder @{ */
    private PhoneRecorderHelper mRecorderHelper;
    private String mRecordingLabelStr;
    private BroadcastReceiver mSDCardMountEventReceiver;
    private PowerManager mPowerManager = null;//dujunchao add @20150821  for incall
    private WakeLock wakeLock = null;//dujunchao add @20150821  for incall  
    /* @} */

    /** Use to determine if the dialpad should be animated on show. */
    private boolean mAnimateDialpadOnShow;

    /** Use to determine the DTMF Text which should be pre-populated in the dialpad. */
    private String mDtmfText;

    /** Use to pass parameters for showing the PostCharDialog to {@link #onResume} */
    private boolean mShowPostCharWaitDialogOnResume;
    private String mShowPostCharWaitDialogCallId;
    private String mShowPostCharWaitDialogChars;

    private boolean mIsLandscape;
    private Animation mSlideIn;
    private Animation mSlideOut;
    private boolean mDismissKeyguard = false;
    // SPRD: fix bug 432115
    private boolean isRejectCallBySms = false;

    AnimationListenerAdapter mSlideOutListener = new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
            showDialpad(false);
        }
    };

    /**
     * Stores the current orientation of the activity.  Used to determine if a change in orientation
     * has occurred.
     */
    private int mCurrentOrientation;

    @Override
    protected void onCreate(Bundle icicle) {
        Log.d(this, "onCreate()...  this = " + this);

        super.onCreate(icicle);

        // set this flag so this activity will stay in front of the keyguard
        // Have the WindowManager filter out touch events that are "too fat".
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES;

        getWindow().addFlags(flags);

        // Setup action bar for the conference call manager.
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.hide();
        }

        // TODO(klp): Do we need to add this back when prox sensor is not available?
        // lp.inputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_DISABLE_USER_ACTIVITY;

        // Inflate everything in incall_screen.xml and add it to the screen.
        setContentView(R.layout.incall_screen);

        initializeInCall();

        internalResolveIntent(getIntent());

        mCurrentOrientation = getResources().getConfiguration().orientation;
        mIsLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        final boolean isRtl = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) ==
                View.LAYOUT_DIRECTION_RTL;

        if (mIsLandscape) {
            mSlideIn = AnimationUtils.loadAnimation(this,
                    isRtl ? R.anim.dialpad_slide_in_left : R.anim.dialpad_slide_in_right);
            mSlideOut = AnimationUtils.loadAnimation(this,
                    isRtl ? R.anim.dialpad_slide_out_left : R.anim.dialpad_slide_out_right);
        } else {
            mSlideIn = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_in_bottom);
            mSlideOut = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_out_bottom);
        }

        mSlideIn.setInterpolator(AnimUtils.EASE_IN);
        mSlideOut.setInterpolator(AnimUtils.EASE_OUT);

        mSlideOut.setAnimationListener(mSlideOutListener);

        if (icicle != null) {
            // If the dialpad was shown before, set variables indicating it should be shown and
            // populated with the previous DTMF text.  The dialpad is actually shown and populated
            // in onResume() to ensure the hosting CallCardFragment has been inflated and is ready
            // to receive it.
            mShowDialpadRequested = icicle.getBoolean(SHOW_DIALPAD_EXTRA);
            mAnimateDialpadOnShow = false;
            mDtmfText = icicle.getString(DIALPAD_TEXT_EXTRA);
        }
        /* SPRD: Add for recorder @{ */
        mRecorderHelper = PhoneRecorderHelper.getInstance(getApplicationContext());
        mRecorderHelper.setOnStateChangedListener(mRecorderStateChangedListener);
        registerExternalStorageStateListener();
        /* @} */
        /* SPRD: Add for dealing with SS notification. @{ */
        // Register for supplementary service failure  broadcasts.
        mReceiver = new SuppServFailureNotificationReceiver();
        IntentFilter intentFilter =
                new IntentFilter(ACTION_SUPP_SERVICE_FAILURE);
        intentFilter.addAction(ACTION_SUPP_SERVICE_FAILURE);
        registerReceiver(mReceiver, intentFilter);
        //dujunchao add@20150821 for incall
        if(getIntent().getBooleanExtra(NEW_OUTGOING_CALL_EXTRA, false)){
            mPowerManager = (PowerManager) this.getSystemService(this.POWER_SERVICE);
            wakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "InCallActivity");
        }
        //dujunchao add@20150821 for incall
        /* @} */
        Log.d(this, "onCreate(): exit");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putBoolean(SHOW_DIALPAD_EXTRA, mCallButtonFragment.isDialpadVisible());
        if (mDialpadFragment != null) {
            out.putString(DIALPAD_TEXT_EXTRA, mDialpadFragment.getDtmfText());
        }
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onStart() {
        Log.d(this, "onStart()...");
        super.onStart();

        // setting activity should be last thing in setup process
        InCallPresenter.getInstance().setActivity(this);
    }

    @Override
    protected void onResume() {
        Log.i(this, "onResume()...");
        super.onResume();

        mIsForegroundActivity = true;
        isRejectCallBySms = false;
        InCallPresenter.getInstance().setThemeColors();
        InCallPresenter.getInstance().onUiShowing(true);

        if (mShowDialpadRequested) {
            mCallButtonFragment.displayDialpad(true /* show */,
                    mAnimateDialpadOnShow /* animate */);
            mShowDialpadRequested = false;
            mAnimateDialpadOnShow = false;

            if (mDialpadFragment != null) {
                mDialpadFragment.setDtmfText(mDtmfText);
                mDtmfText = null;
            }
        }

        if (mShowPostCharWaitDialogOnResume) {
            showPostCharWaitDialog(mShowPostCharWaitDialogCallId, mShowPostCharWaitDialogChars);
        }
        /* SPRD: Add for recorder @{ */
        mRecordingLabelStr = getResources().getString(R.string.recording);
        mRecorderHelper.notifyCurrentState();
        if(getIntent().getBooleanExtra(NEW_OUTGOING_CALL_EXTRA, false))     wakeLock.acquire();//dujunchao add@20150821 for incall
        /* @} */
    }

    // onPause is guaranteed to be called when the InCallActivity goes
    // in the background.
    @Override
    protected void onPause() {
        Log.d(this, "onPause()...");
        super.onPause();

        mIsForegroundActivity = false;

        if (mDialpadFragment != null ) {
            mDialpadFragment.onDialerKeyUp(null);
        }

        InCallPresenter.getInstance().onUiShowing(false);

        if(wakeLock!=null){
            wakeLock.release();//dujunchao add@20150821 for incall
            wakeLock=null;
        }

        if (isFinishing()) {
            InCallPresenter.getInstance().unsetActivity(this);
        }
    }

    @Override
    protected void onStop() {
        Log.d(this, "onStop()...");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(this, "onDestroy()...  this = " + this);
        InCallPresenter.getInstance().unsetActivity(this);
        // SPRD: Add for recorder
        unRegisterExternalStorageStateListener();
        // SPRD: Add for dealing with SS notification.
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    /**
     * Returns true when theActivity is in foreground (between onResume and onPause).
     */
    /* package */ boolean isForegroundActivity() {
        return mIsForegroundActivity;
    }

    private boolean hasPendingErrorDialog() {
        return mDialog != null;
    }

    /**
     * Dismisses the in-call screen.
     *
     * We never *really* finish() the InCallActivity, since we don't want to get destroyed and then
     * have to be re-created from scratch for the next call.  Instead, we just move ourselves to the
     * back of the activity stack.
     *
     * This also means that we'll no longer be reachable via the BACK button (since moveTaskToBack()
     * puts us behind the Home app, but the home app doesn't allow the BACK key to move you any
     * farther down in the history stack.)
     *
     * (Since the Phone app itself is never killed, this basically means that we'll keep a single
     * InCallActivity instance around for the entire uptime of the device.  This noticeably improves
     * the UI responsiveness for incoming calls.)
     */
    @Override
    public void finish() {
        Log.i(this, "finish().  Dialog showing: " + (mDialog != null));

        // skip finish if we are still showing a dialog.
        if (!hasPendingErrorDialog() && !mAnswerFragment.hasPendingDialogs()) {
            super.finish();
            // SPRD: fix Bug426985
            // Suppress animation if call card is invisible
            if (!mCallCardFragment.isVisible()) {
                overridePendingTransition(0, 0);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(this, "onNewIntent: intent = " + intent);

        // We're being re-launched with a new Intent.  Since it's possible for a
        // single InCallActivity instance to persist indefinitely (even if we
        // finish() ourselves), this sequence can potentially happen any time
        // the InCallActivity needs to be displayed.

        // Stash away the new intent so that we can get it in the future
        // by calling getIntent().  (Otherwise getIntent() will return the
        // original Intent from when we first got created!)
        setIntent(intent);

        // Activities are always paused before receiving a new intent, so
        // we can count on our onResume() method being called next.

        // Just like in onCreate(), handle the intent.
        internalResolveIntent(intent);
    }

    @Override
    public void onBackPressed() {
        Log.i(this, "onBackPressed");

        // BACK is also used to exit out of any "special modes" of the
        // in-call UI:

        if (!mConferenceManagerFragment.isVisible() && !mCallCardFragment.isVisible()) {
            return;
        }

        if (mDialpadFragment != null && mDialpadFragment.isVisible()) {
            mCallButtonFragment.displayDialpad(false /* show */, true /* animate */);
            return;
        } else if (mConferenceManagerFragment.isVisible()) {
            showConferenceCallManager(false);
            return;
        }

        // Always disable the Back key while an incoming call is ringing
        final Call call = CallList.getInstance().getIncomingCall();
        if (call != null) {
            Log.d(this, "Consume Back press for an incoming call");
            return;
        }

        // Nothing special to do.  Fall back to the default behavior.
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // push input to the dialer.
        if (mDialpadFragment != null && (mDialpadFragment.isVisible()) &&
                (mDialpadFragment.onDialerKeyUp(event))){
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_CALL) {
            // Always consume CALL to be sure the PhoneWindow won't do anything with it
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL:
                boolean handled = InCallPresenter.getInstance().handleCallKey();
                if (!handled) {
                    Log.w(this, "InCallActivity should always handle KEYCODE_CALL in onKeyDown");
                }
                // Always consume CALL to be sure the PhoneWindow won't do anything with it
                return true;

            // Note there's no KeyEvent.KEYCODE_ENDCALL case here.
            // The standard system-wide handling of the ENDCALL key
            // (see PhoneWindowManager's handling of KEYCODE_ENDCALL)
            // already implements exactly what the UI spec wants,
            // namely (1) "hang up" if there's a current active call,
            // or (2) "don't answer" if there's a current ringing call.

            case KeyEvent.KEYCODE_CAMERA:
                // Disable the CAMERA button while in-call since it's too
                // easy to press accidentally.
                return true;

            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                // Ringer silencing handled by PhoneWindowManager.
                break;

            case KeyEvent.KEYCODE_MUTE:
                // toggle mute
                TelecomAdapter.getInstance().mute(!AudioModeProvider.getInstance().getMute());
                return true;

            // Various testing/debugging features, enabled ONLY when VERBOSE == true.
            case KeyEvent.KEYCODE_SLASH:
                if (Log.VERBOSE) {
                    Log.v(this, "----------- InCallActivity View dump --------------");
                    // Dump starting from the top-level view of the entire activity:
                    Window w = this.getWindow();
                    View decorView = w.getDecorView();
                    Log.d(this, "View dump:" + decorView);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_EQUALS:
                // TODO: Dump phone state?
                break;
        }

        if (event.getRepeatCount() == 0 && handleDialerKeyDown(keyCode, event)) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private boolean handleDialerKeyDown(int keyCode, KeyEvent event) {
        Log.v(this, "handleDialerKeyDown: keyCode " + keyCode + ", event " + event + "...");

        // As soon as the user starts typing valid dialable keys on the
        // keyboard (presumably to type DTMF tones) we start passing the
        // key events to the DTMFDialer's onDialerKeyDown.
        if (mDialpadFragment != null && mDialpadFragment.isVisible()) {
            return mDialpadFragment.onDialerKeyDown(event);

            // TODO: If the dialpad isn't currently visible, maybe
            // consider automatically bringing it up right now?
            // (Just to make sure the user sees the digits widget...)
            // But this probably isn't too critical since it's awkward to
            // use the hard keyboard while in-call in the first place,
            // especially now that the in-call UI is portrait-only...
        }

        return false;
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        InCallPresenter.getInstance().getProximitySensor().onConfigurationChanged(config);
        Log.d(this, "onConfigurationChanged "+config.orientation);

        // Check to see if the orientation changed to prevent triggering orientation change events
        // for other configuration changes.
        if (config.orientation != mCurrentOrientation) {
            mCurrentOrientation = config.orientation;
            InCallPresenter.getInstance().onDeviceRotationChange(
                    getWindowManager().getDefaultDisplay().getRotation());
            InCallPresenter.getInstance().onDeviceOrientationChange(mCurrentOrientation);
        }
        super.onConfigurationChanged(config);
    }

    public CallButtonFragment getCallButtonFragment() {
        return mCallButtonFragment;
    }

    public CallCardFragment getCallCardFragment() {
        return mCallCardFragment;
    }

    private void internalResolveIntent(Intent intent) {
        final String action = intent.getAction();

        if (action.equals(intent.ACTION_MAIN)) {
            // This action is the normal way to bring up the in-call UI.
            //
            // But we do check here for one extra that can come along with the
            // ACTION_MAIN intent:

            if (intent.hasExtra(SHOW_DIALPAD_EXTRA)) {
                // SHOW_DIALPAD_EXTRA can be used here to specify whether the DTMF
                // dialpad should be initially visible.  If the extra isn't
                // present at all, we just leave the dialpad in its previous state.

                final boolean showDialpad = intent.getBooleanExtra(SHOW_DIALPAD_EXTRA, false);
                Log.d(this, "- internalResolveIntent: SHOW_DIALPAD_EXTRA: " + showDialpad);

                relaunchedFromDialer(showDialpad);
            }

            if (intent.getBooleanExtra(NEW_OUTGOING_CALL_EXTRA, false)) {
                intent.removeExtra(NEW_OUTGOING_CALL_EXTRA);
                Call call = CallList.getInstance().getOutgoingCall();
                if (call == null) {
                    call = CallList.getInstance().getPendingOutgoingCall();
                }

                Bundle extras = null;
                if (call != null) {
                    extras = call.getTelecommCall().getDetails().getExtras();
                }
                if (extras == null) {
                    // Initialize the extras bundle to avoid NPE
                    extras = new Bundle();
                }

                Point touchPoint = null;
                if (TouchPointManager.getInstance().hasValidPoint()) {
                    // Use the most immediate touch point in the InCallUi if available
                    touchPoint = TouchPointManager.getInstance().getPoint();
                } else {
                    // Otherwise retrieve the touch point from the call intent
                    if (call != null) {
                        touchPoint = (Point) extras.getParcelable(TouchPointManager.TOUCH_POINT);
                    }
                }

                // This is only true in the case where an outgoing call is initiated by tapping
                // on the "Select account dialog", in which case we skip the initial animation. In
                // most other cases the circular reveal is done by OutgoingCallAnimationActivity.
                final boolean showCircularReveal =
                        intent.getBooleanExtra(SHOW_CIRCULAR_REVEAL_EXTRA, false);
                mCallCardFragment.animateForNewOutgoingCall(touchPoint, showCircularReveal);

                // InCallActivity is responsible for disconnecting a new outgoing call if there
                // is no way of making it (i.e. no valid call capable accounts)
                if (InCallPresenter.isCallWithNoValidAccounts(call)) {
                    TelecomAdapter.getInstance().disconnectCall(call.getId());
                }

                dismissKeyguard(true);
            }

            Call pendingAccountSelectionCall = CallList.getInstance().getWaitingForAccountCall();
            if (pendingAccountSelectionCall != null) {
                mCallCardFragment.setVisible(false);
                Bundle extras = pendingAccountSelectionCall
                        .getTelecommCall().getDetails().getExtras();

                final List<PhoneAccountHandle> phoneAccountHandles;
                if (extras != null) {
                    phoneAccountHandles = extras.getParcelableArrayList(
                            android.telecom.Call.AVAILABLE_PHONE_ACCOUNTS);
                } else {
                    phoneAccountHandles = new ArrayList<>();
                }

                SelectPhoneAccountListener listener = new SelectPhoneAccountListener() {
                    @Override
                    public void onPhoneAccountSelected(PhoneAccountHandle selectedAccountHandle,
                            boolean setDefault) {
                        InCallPresenter.getInstance().handleAccountSelection(selectedAccountHandle,
                                setDefault);
                    }
                    @Override
                    public void onDialogDismissed() {
                        InCallPresenter.getInstance().cancelAccountSelection();
                    }
                };

                SelectPhoneAccountDialogFragment.showAccountDialog(getFragmentManager(),
                        R.string.select_phone_account_for_calls, true, phoneAccountHandles,
                        listener);
            } else {
                mCallCardFragment.setVisible(true);
            }

            return;
        }
    }

    private void relaunchedFromDialer(boolean showDialpad) {
        mShowDialpadRequested = showDialpad;
        mAnimateDialpadOnShow = true;

        if (mShowDialpadRequested) {
            // If there's only one line in use, AND it's on hold, then we're sure the user
            // wants to use the dialpad toward the exact line, so un-hold the holding line.
            final Call call = CallList.getInstance().getActiveOrBackgroundCall();
            if (call != null && call.getState() == State.ONHOLD) {
                TelecomAdapter.getInstance().unholdCall(call.getId());
            }
        }
    }

    private void initializeInCall() {
        if (mCallCardFragment == null) {
            mCallCardFragment = (CallCardFragment) getFragmentManager()
                    .findFragmentById(R.id.callCardFragment);
        }

        mChildFragmentManager = mCallCardFragment.getChildFragmentManager();

        if (mCallButtonFragment == null) {
            mCallButtonFragment = (CallButtonFragment) mChildFragmentManager
                    .findFragmentById(R.id.callButtonFragment);
            mCallButtonFragment.getView().setVisibility(View.INVISIBLE);
        }

        if (mAnswerFragment == null) {
            mAnswerFragment = (AnswerFragment) mChildFragmentManager
                    .findFragmentById(R.id.answerFragment);
        }

        if (mConferenceManagerFragment == null) {
            mConferenceManagerFragment = (ConferenceManagerFragment) getFragmentManager()
                    .findFragmentById(R.id.conferenceManagerFragment);
            mConferenceManagerFragment.getView().setVisibility(View.INVISIBLE);
        }

        /* sprd: add for conferencecall list { */
        if(mConferenceListFragment == null){
            mConferenceListFragment = (ConferenceListFragment) getFragmentManager()
            .findFragmentById(R.id.conferenceListFragment);
            }
        /* } */
    }

    /**
     * Simulates a user click to hide the dialpad. This will update the UI to show the call card,
     * update the checked state of the dialpad button, and update the proximity sensor state.
     */
    public void hideDialpadForDisconnect() {
        mCallButtonFragment.displayDialpad(false /* show */, true /* animate */);
    }

    public void dismissKeyguard(boolean dismiss) {
        if (mDismissKeyguard == dismiss) {
            return;
        }
        mDismissKeyguard = dismiss;
        if (dismiss) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
    }

    private void showDialpad(boolean showDialpad) {
        // If the dialpad is being shown and it has not already been loaded, replace the dialpad
        // placeholder with the actual fragment before continuing.
        if (mDialpadFragment == null && showDialpad) {
            final FragmentTransaction loadTransaction = mChildFragmentManager.beginTransaction();
            View fragmentContainer = findViewById(R.id.dialpadFragmentContainer);
            mDialpadFragment = new DialpadFragment();
            loadTransaction.replace(fragmentContainer.getId(), mDialpadFragment,
                    DialpadFragment.class.getName());
            loadTransaction.commitAllowingStateLoss();
            mChildFragmentManager.executePendingTransactions();
        }

        final FragmentTransaction ft = mChildFragmentManager.beginTransaction();
        if (showDialpad) {
            ft.show(mDialpadFragment);
        } else {
            ft.hide(mDialpadFragment);
        }
        ft.commitAllowingStateLoss();
    }

    public void displayDialpad(boolean showDialpad, boolean animate) {
        // If the dialpad is already visible, don't animate in. If it's gone, don't animate out.
        if ((showDialpad && isDialpadVisible()) || (!showDialpad && !isDialpadVisible())) {
            return;
        }
        // We don't do a FragmentTransaction on the hide case because it will be dealt with when
        // the listener is fired after an animation finishes.
        if (!animate) {
            showDialpad(showDialpad);
        } else {
            if (showDialpad) {
                showDialpad(true);
                mDialpadFragment.animateShowDialpad();
            }
            mCallCardFragment.onDialpadVisiblityChange(showDialpad);
            mDialpadFragment.getView().startAnimation(showDialpad ? mSlideIn : mSlideOut);
        }

        InCallPresenter.getInstance().getProximitySensor().onDialpadVisible(showDialpad);
    }

    public boolean isDialpadVisible() {
        return mDialpadFragment != null && mDialpadFragment.isVisible();
    }

    /**
     * Hides or shows the conference manager fragment.
     *
     * @param show {@code true} if the conference manager should be shown, {@code false} if it
     *                         should be hidden.
     */
    public void showConferenceCallManager(boolean show) {
        mConferenceManagerFragment.setVisible(show);

        // Need to hide the call card fragment to ensure that accessibility service does not try to
        // give focus to the call card when the conference manager is visible.
        mCallCardFragment.getView().setVisibility(show ? View.GONE : View.VISIBLE);
        /* sprd: add for conferencecall list { */
        if(mConferenceListFragment != null&& mConferenceListFragment.getPresenter().getVolteEnabled()){
            mConferenceListFragment.setVisible(!show);
        }
        /* } */
    }

    public void showPostCharWaitDialog(String callId, String chars) {
        if (isForegroundActivity()) {
            final PostCharDialogFragment fragment = new PostCharDialogFragment(callId,  chars);
            fragment.show(getFragmentManager(), "postCharWait");

            mShowPostCharWaitDialogOnResume = false;
            mShowPostCharWaitDialogCallId = null;
            mShowPostCharWaitDialogChars = null;
        } else {
            mShowPostCharWaitDialogOnResume = true;
            mShowPostCharWaitDialogCallId = callId;
            mShowPostCharWaitDialogChars = chars;
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (mCallCardFragment != null) {
            mCallCardFragment.dispatchPopulateAccessibilityEvent(event);
        }
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    public void maybeShowErrorDialogOnDisconnect(DisconnectCause disconnectCause) {
        Log.d(this, "maybeShowErrorDialogOnDisconnect");

        if (!isFinishing() && !TextUtils.isEmpty(disconnectCause.getDescription())
                && (disconnectCause.getCode() == DisconnectCause.ERROR ||
                        disconnectCause.getCode() == DisconnectCause.RESTRICTED)) {
            showErrorDialog(disconnectCause.getDescription());
        }
    }

    public void dismissPendingDialogs() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        mAnswerFragment.dismissPendingDialogues();
    }

    /**
     * Utility function to bring up a generic "error" dialog.
     */
    private void showErrorDialog(CharSequence msg) {
        Log.i(this, "Show Dialog: " + msg);

        dismissPendingDialogs();

        mDialog = new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onDialogDismissed();
                    }})
                .setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        onDialogDismissed();
                    }})
                .create();

        mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        mDialog.show();
    }

    private void onDialogDismissed() {
        mDialog = null;
        InCallPresenter.getInstance().onDismissDialog();
    }

// ------------------- SPRD -------------------

    /** Add for recorder @{ */
    private OnStateChangedListener mRecorderStateChangedListener = new OnStateChangedListener() {
        public void onTimeChanged(long time) {
            mCallCardFragment.setRecordText(mRecordingLabelStr
                    + DateUtils.formatElapsedTime(time / 1000));
		   Log.v("TimeBruce", "onTimeChanged:"+DateUtils.formatElapsedTime(time / 1000));

        }

        public void onStateChanged(PhoneRecorderHelper.State state) {
            if (mCallButtonFragment != null) {
                mCallButtonFragment.setRecord(state.isActive());
            }
            if (mCallCardFragment.getRecordingLabel() != null) {
                mCallCardFragment
                        .setRecordingVisibility(state.isActive() ? View.VISIBLE
                                : View.GONE);
            }
        }

        @Override
        public void onShowMessage(int type, String msg) {
            String res = null;
            boolean resetIcon = false;
            switch (type) {
                case PhoneRecorderHelper.TYPE_ERROR_SD_NOT_EXIST:
                    res = getString(R.string.no_sd_card);
                    resetIcon = true;
                    break;
                case PhoneRecorderHelper.TYPE_ERROR_SD_FULL:
                    res = getString(R.string.storage_is_full);
                    resetIcon = true;
                    break;
                case PhoneRecorderHelper.TYPE_ERROR_SD_ACCESS:
                    res = getString(R.string.sdcard_access_error);
                    resetIcon = true;
                    break;
                case PhoneRecorderHelper.TYPE_ERROR_IN_RECORD:
                    res = getString(R.string.used_by_other_applications);
                    resetIcon = true;
                    break;
                case PhoneRecorderHelper.TYPE_ERROR_INTERNAL:
                    resetIcon = true;
                    break;
                case PhoneRecorderHelper.TYPE_MSG_PATH:
                case PhoneRecorderHelper.TYPE_SAVE_FAIL:
                    res = msg;
                    resetIcon = true;
                    break;
            }
            if (mCallButtonFragment != null && resetIcon) {
                mCallButtonFragment.setRecord(false);
            }
            Log.d(this, " toast message: " + res);
            if(!TextUtils.isEmpty(res)){
                Toast.makeText(InCallActivity.this, res, Toast.LENGTH_SHORT).show();
            }
        }
    };

    private void registerExternalStorageStateListener() {
        if (mSDCardMountEventReceiver == null) {
            mSDCardMountEventReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    /* SPRD: add for bug436502 @{ */
                    if(intent == null){
                        return;
                    }
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        if (mRecorderHelper != null) {
                            mRecorderHelper.stop();
                            return;
                        }
                    }
                    /* @} */
                    boolean hasSdcard = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
                    if (mRecorderHelper != null && !hasSdcard) {
                        mRecorderHelper.stop();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            /* SPRD: add for bug436502 @{ */
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            /* @} */
            iFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mSDCardMountEventReceiver, iFilter);
        }
    }

    private void unRegisterExternalStorageStateListener() {
        if (mSDCardMountEventReceiver != null) {
            unregisterReceiver(mSDCardMountEventReceiver);
        }
    }

    public void toggleRecord() {
        InCallPresenter.getInstance().toggleRecorder();
    }
    /** @} */

    /** Only be used to call killStopFrontApp */
    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (OptConfig.LC_RAM_SUPPORT) {
            try {
                ActivityManagerNative.getDefault().killStopFrontApp(
                                     ActivityManager.CANCEL_KILL_STOP_TIMEOUT);
            } catch (Exception e) {
                Log.e(this, "killStopFrontApp : CANCEL_KILL_STOP_TIMEOUT");
                e.printStackTrace();
            }
        }
    }
    /** @} */

    /* SPRD: fix bug 432115 @{ */
    public void setIsRejectCallBySms(boolean isReject) {
        isRejectCallBySms = isReject;
    }

    public boolean getIsRejectCallBySms() {
        return isRejectCallBySms;
    }
    /* @} */

    /**
     * Add for dealing with SS notification. @{
     * This enum maps to Phone.SuppService defined in telephony
     */
    private enum SuppService {
        UNKNOWN, SWITCH, SEPARATE, TRANSFER, CONFERENCE, REJECT, HANGUP;
    }

    public class SuppServFailureNotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(this, "Action: " + action);

            if (action.equals(ACTION_SUPP_SERVICE_FAILURE)) {
                int service = intent.getIntExtra("supp_serv_failure", 0);
                Log.d(this, "SuppServFailureNotificationReceiver: " + service);
                onSuppServiceFailed(service);
            }
        }
    }

    /**
      * Handle a failure notification for a supplementary service
      * (i.e. conference, switch, separate, transfer, etc.).
      */
    void onSuppServiceFailed(int service) {
        Log.d(this, "onSuppServiceFailed: " + service);
        SuppService  result = SuppService.values()[service];
        int errorMessageResId;

        switch (result) {
            case SWITCH:
                // Attempt to switch foreground and background/incoming calls failed
                // ("Failed to switch calls")
                errorMessageResId = R.string.incall_error_supp_service_switch;
                final Call call = CallList.getInstance().getActiveOrBackgroundCall();
                if (call != null && call.getState() != State.ONHOLD) {
                    mCallButtonFragment.setHold(false);
                }
                if (null != mAnswerFragment) {
                    mAnswerFragment.getPresenter().onSuppServiceFailed();
                }
                break;

            case SEPARATE:
                // Attempt to separate a call from a conference call
                // failed ("Failed to separate out call")
                errorMessageResId = R.string.incall_error_supp_service_separate;
                break;

            case TRANSFER:
                // Attempt to connect foreground and background calls to
                // each other (and hanging up user's line) failed ("Call
                // transfer failed")
                errorMessageResId = R.string.incall_error_supp_service_transfer;
                break;

            case CONFERENCE:
                // Attempt to add a call to conference call failed
                // ("Conference call failed")
                errorMessageResId = R.string.incall_error_supp_service_conference;
                break;

            case REJECT:
                // Attempt to reject an incoming call failed
                // ("Call rejection failed")
                errorMessageResId = R.string.incall_error_supp_service_reject;
                break;

            case HANGUP:
                // Attempt to release a call failed ("Failed to release call(s)")
                errorMessageResId = R.string.incall_error_supp_service_hangup;
                break;

            case UNKNOWN:
            default:
                // Attempt to use a service we don't recognize or support
                // ("Unsupported service" or "Selected service failed")
                errorMessageResId = R.string.incall_error_supp_service_unknown;
                break;
        }
        final CharSequence msg = getResources().getText(errorMessageResId);
        showErrorDialog(msg);
    }
    /** @} */
}
