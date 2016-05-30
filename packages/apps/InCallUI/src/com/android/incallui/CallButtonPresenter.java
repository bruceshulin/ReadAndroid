/*
 * Copyright (C) 2013 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.incallui;

import android.content.Context;
import android.os.Bundle;
import android.os.Vibrator;
import android.os.SystemProperties;
import android.content.Intent;
import android.content.IntentFilter;
import android.telecom.AudioState;
import android.telecom.InCallService.VideoCall;
import android.telecom.VideoProfile;

import com.android.contacts.common.CallUtil;
import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.InCallPresenter.CanAddCallListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallVideoCallListenerNotifier.CameraEventListener;

import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.widget.Toast;
import java.util.Objects;
import android.telephony.TelephonyManagerSprd;
import android.telephony.SubscriptionManager;

/**
 * Logic for call buttons.
 */
public class CallButtonPresenter extends Presenter<CallButtonPresenter.CallButtonUi>
        implements InCallStateListener, AudioModeListener, IncomingCallListener,
        InCallDetailsListener,CanAddCallListener,CameraEventListener{//SPRD:add CameraEventListener for bug427447
    private static final String KEY_AUTOMATICALLY_MUTED = "incall_key_automatically_muted";
    private static final String KEY_PREVIOUS_MUTE_STATE = "incall_key_previous_mute_state";
	private String TAG = "CALLBUTTONBRUCE";
    private Call mCall;
    private boolean mAutomaticallyMuted = false;
    private boolean mPreviousMuteState = false;

    /* sprd: add for to display many buttons { */
    private boolean isVolteEnable = SystemProperties.getBoolean("persist.sys.volte.enable", false);

    private static final String MULTI_PICK_CONTACTS_ACTION = "com.android.contacts.action.MULTI_TAB_PICK";
    private static final String ADD_MULTI_CALL_AGAIN = "addMultiCallAgain";
    private static final int MAX_GROUP_CALL_NUMBER = 5;
    /* } */
    private int mVibrationDuration = 200;

    public CallButtonPresenter() {
    }

    @Override
    public void onUiReady(CallButtonUi ui) {
        super.onUiReady(ui);
		android.util.Log.d(TAG, "onUiReady");
        AudioModeProvider.getInstance().addListener(this);

        // register for call state changes last
        InCallPresenter.getInstance().addListener(this);
        InCallPresenter.getInstance().addIncomingCallListener(this);
        InCallPresenter.getInstance().addDetailsListener(this);
        InCallPresenter.getInstance().addCanAddCallListener(this);
        InCallVideoCallListenerNotifier.getInstance().addCameraEventListener(this);//SPRD: add for bug427447
    }

    @Override
    public void onUiUnready(CallButtonUi ui) {
        super.onUiUnready(ui);
		android.util.Log.d(TAG, "onUiUnready");
        InCallPresenter.getInstance().removeListener(this);
        AudioModeProvider.getInstance().removeListener(this);
        InCallPresenter.getInstance().removeIncomingCallListener(this);
        InCallPresenter.getInstance().removeDetailsListener(this);
        InCallVideoCallListenerNotifier.getInstance().removeCameraEventListener(this);//SPRD: add for bug427447
    }
    @Override
    public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
        CallButtonUi ui = getUi();
		android.util.Log.d(TAG, "onStateChange");
		android.util.Log.d(TAG, "oldState:"+ oldState);
		android.util.Log.d(TAG, "newState:"+ newState);
		android.util.Log.d(TAG, "callList:"+ callList);
		
	if (newState == InCallState.OUTGOING) {
            mCall = callList.getOutgoingCall();
			
		} 
		else if (newState == InCallState.INCALL) 
		{
            mCall = callList.getActiveOrBackgroundCall();
			if ((newState == InCallState.INCALL) &&
                   (oldState == InCallState.OUTGOING || oldState == InCallState.INCOMING)) {
					
                   Vibrator vibrator = (Vibrator) ui.getContext().getSystemService(Context.VIBRATOR_SERVICE);
                   vibrator.vibrate(mVibrationDuration);
                }
            

		 
            // When connected to voice mail, automatically shows the dialpad.
            // (On previous releases we showed it when in-call shows up, before waiting for
            // OUTGOING.  We may want to do that once we start showing "Voice mail" label on
            // the dialpad too.)
            if (ui != null) {
                if (oldState == InCallState.OUTGOING && mCall != null) {
                    if (CallerInfoUtils.isVoiceMailNumber(ui.getContext(), mCall)) {
                        ui.displayDialpad(true /* show */, true /* animate */);
                    }
                }
            }
        } else if (newState == InCallState.INCOMING) {
            if (ui != null) {
                ui.displayDialpad(false /* show */, true /* animate */);
            }
            mCall = null;
        }else {
            mCall = null;
        }
        updateUi(newState, mCall);
    }

    /**
     * Updates the user interface in response to a change in the details of a call.
     * Currently handles changes to the call buttons in response to a change in the details for a
     * call.  This is important to ensure changes to the active call are reflected in the available
     * buttons.
     *
     * @param call The active call.
     * @param details The call details.
     */
    @Override
    public void onDetailsChanged(Call call, android.telecom.Call.Details details) {
		android.util.Log.d(TAG, "onDetailsChanged");
        if (getUi() != null && Objects.equals(call, mCall)) {
            updateCallButtons(call, getUi().getContext());
        }
    }

    @Override
    public void onIncomingCall(InCallState oldState, InCallState newState, Call call) {
		android.util.Log.d(TAG, "onIncomingCall");
        onStateChange(oldState, newState, CallList.getInstance());
    }

    @Override
    public void onCanAddCallChanged(boolean canAddCall) {
		android.util.Log.d(TAG, "onCanAddCallChanged");
        if (getUi() != null && mCall != null) {
            updateCallButtons(mCall, getUi().getContext());
        }
    }

    @Override
    public void onAudioMode(int mode) {
		android.util.Log.d(TAG, "onAudioMode");
        if (getUi() != null) {
            getUi().setAudio(mode);
        }
    }

    @Override
    public void onSupportedAudioMode(int mask) {
		android.util.Log.d(TAG, "onSupportedAudioMode");
        if (getUi() != null) {
            getUi().setSupportedAudio(mask);
        }
    }

    @Override
    public void onMute(boolean muted) {
		android.util.Log.d(TAG, "onMute");
        if (getUi() != null && !mAutomaticallyMuted) {
            getUi().setMute(muted);
        }
    }

    public int getAudioMode() {
		android.util.Log.d(TAG, "getAudioMode");
        return AudioModeProvider.getInstance().getAudioMode();
    }

    public int getSupportedAudio() {
		android.util.Log.d(TAG, "getSupportedAudio");
        return AudioModeProvider.getInstance().getSupportedModes();
    }

    public void setAudioMode(int mode) {
android.util.Log.d(TAG, "setAudioMode");
        // TODO: Set a intermediate state in this presenter until we get
        // an update for onAudioMode().  This will make UI response immediate
        // if it turns out to be slow

        Log.d(this, "Sending new Audio Mode: " + AudioState.audioRouteToString(mode));
        TelecomAdapter.getInstance().setAudioRoute(mode);
    }

    /**
     * Function assumes that bluetooth is not supported.
     */
    public void toggleSpeakerphone() {
		android.util.Log.d(TAG, "toggleSpeakerphone");
        // this function should not be called if bluetooth is available
        if (0 != (AudioState.ROUTE_BLUETOOTH & getSupportedAudio())) {

            // It's clear the UI is wrong, so update the supported mode once again.
            Log.e(this, "toggling speakerphone not allowed when bluetooth supported.");
            getUi().setSupportedAudio(getSupportedAudio());
            return;
        }

        int newMode = AudioState.ROUTE_SPEAKER;

        // if speakerphone is already on, change to wired/earpiece
        if (getAudioMode() == AudioState.ROUTE_SPEAKER) {
            newMode = AudioState.ROUTE_WIRED_OR_EARPIECE;
        }

        setAudioMode(newMode);
    }

    public void muteClicked(boolean checked) {
		android.util.Log.d(TAG, "muteClicked");
        Log.d(this, "turning on mute: " + checked);
        TelecomAdapter.getInstance().mute(checked);
    }

    public void holdClicked(boolean checked) {
				android.util.Log.d(TAG, "holdClicked");
        if (mCall == null) {
            return;
        }
        /* SPRD: fix bug 432259 @{ */
        if (mCall != null && mCall.getIsVideoStateChange()) {
            Toast.makeText(getUi().getContext(), R.string.call_video_state_change_hold, 0).show();
            return;
            }
        /* @} */
        if (checked) {
            Log.i(this, "Putting the call on hold: " + mCall);
            TelecomAdapter.getInstance().holdCall(mCall.getId());
        } else {
            Log.i(this, "Removing the call from hold: " + mCall);
            TelecomAdapter.getInstance().unholdCall(mCall.getId());
        }
    }

    public void swapClicked() {
						android.util.Log.d(TAG, "swapClicked");
        if (mCall == null) {
            return;
        }

        Log.i(this, "Swapping the call: " + mCall);
        TelecomAdapter.getInstance().swap(mCall.getId());
    }

    public void mergeClicked() {
		android.util.Log.d(TAG, "mergeClicked");
        TelecomAdapter.getInstance().merge(mCall.getId());
    }

    public void addCallClicked() {
		android.util.Log.d(TAG, "addCallClicked");
        // Automatically mute the current call
        /* SPRD: fix bug 432259 @{ */
        if (mCall != null && mCall.getIsVideoStateChange()) {
            Toast.makeText(getUi().getContext(), R.string.call_video_state_change_add, 0).show();
            return;
        }
        /* @} */
        mAutomaticallyMuted = true;
        mPreviousMuteState = AudioModeProvider.getInstance().getMute();
        // Simulate a click on the mute button
        muteClicked(true);
        /* SPRD: modify by fix bug435564 @{ */
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        try {
            subId = Integer.parseInt(mCall.getAccountHandle().getId());
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (SubscriptionManager.isValidSubscriptionId(subId)) {
            TelecomAdapter.getInstance().addCall(subId);
        } else {
            TelecomAdapter.getInstance().addCall();
        }
        /* @} */
    }

    public void changeToVoiceClicked() {
		android.util.Log.d(TAG, "changeToVoiceClicked");
        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        VideoProfile videoProfile = new VideoProfile(
                VideoProfile.VideoState.AUDIO_ONLY, VideoProfile.QUALITY_DEFAULT);
        videoCall.sendSessionModifyRequest(videoProfile);
    }

    public void showDialpadClicked(boolean checked) {
		android.util.Log.d(TAG, "showDialpadClicked");
        Log.v(this, "Show dialpad " + String.valueOf(checked));
        getUi().displayDialpad(checked /* show */, true /* animate */);
    }

    public void changeToVideoClicked() {
		android.util.Log.d(TAG, "changeToVideoClicked");
        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        VideoProfile videoProfile =
                new VideoProfile(VideoProfile.VideoState.BIDIRECTIONAL);
        videoCall.sendSessionModifyRequest(videoProfile);

        /* SPRD: fix bug 432259 @{ */
        if(TelephonyManagerSprd.getVolteEnabled()){
            return;
        }
        /* @} */
        mCall.setSessionModificationState(Call.SessionModificationState.REQUEST_FAILED);
    }

    /**
     * Switches the camera between the front-facing and back-facing camera.
     * @param useFrontFacingCamera True if we should switch to using the front-facing camera, or
     *     false if we should switch to using the back-facing camera.
     */
    public void switchCameraClicked(boolean useFrontFacingCamera) {
		android.util.Log.d(TAG, "switchCameraClicked");
        InCallCameraManager cameraManager = InCallPresenter.getInstance().getInCallCameraManager();
        cameraManager.setUseFrontFacingCamera(useFrontFacingCamera);

        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }

        String cameraId = cameraManager.getActiveCameraId();
        if (cameraId != null) {
            videoCall.setCamera(cameraId);
            videoCall.requestCameraCapabilities();
        }
        getUi().setSwitchCameraButton(!useFrontFacingCamera);
    }

    /**
     * Stop or start client's video transmission.
     * @param pause True if pausing the local user's video, or false if starting the local user's
     *    video.
     */
    public void pauseVideoClicked(boolean pause) {
			android.util.Log.d(TAG, "pauseVideoClicked");
        VideoCall videoCall = mCall.getVideoCall();
        if (videoCall == null) {
            return;
        }
        /*SPRD: Add for bug427471*/
        InCallPresenter.getInstance().getInCallCameraManager().setCameraPaused(pause);
        /* @} */
        if (pause) {
            videoCall.setCamera(null);
            VideoProfile videoProfile = new VideoProfile(
                    mCall.getVideoState() | VideoProfile.VideoState.PAUSED);
            videoCall.sendSessionModifyRequest(videoProfile);
        } else {
            InCallCameraManager cameraManager = InCallPresenter.getInstance().
                    getInCallCameraManager();
            videoCall.setCamera(cameraManager.getActiveCameraId());
            VideoProfile videoProfile = new VideoProfile(
                    mCall.getVideoState() & ~VideoProfile.VideoState.PAUSED);
            videoCall.sendSessionModifyRequest(videoProfile);
        }
        getUi().setPauseVideoButton(pause);
    }

    /* sprd: add for to display many buttons { */
    public void inviteClicked() {
		android.util.Log.d(TAG, "inviteClicked");
        Log.i(this, "inviteClicked");
        final CallButtonUi ui = getUi();
        Intent intentPick = new Intent(MULTI_PICK_CONTACTS_ACTION).
                putExtra("checked_limit_count",MAX_GROUP_CALL_NUMBER - CallList.getInstance().getforgroundCalNumber()).
                putExtra("cascading",new Intent(MULTI_PICK_CONTACTS_ACTION).setType(Phone.CONTENT_ITEM_TYPE)).
                putExtra("multi",ADD_MULTI_CALL_AGAIN);;
        ui.getContext().startActivity(intentPick);
    }
    /* } */

    private void updateUi(InCallState state, Call call) {
		android.util.Log.d(TAG, "updateUi");
        Log.d(this, "Updating call UI for call: ", call);

        final CallButtonUi ui = getUi();
        if (ui == null) {
            return;
        }

        final boolean isEnabled =
                state.isConnectingOrConnected() &&!state.isIncoming() && call != null;
        ui.setEnabled(isEnabled);

        if (!isEnabled) {
            return;
        }

        updateCallButtons(call, ui.getContext());

        ui.enableMute(call.can(android.telecom.Call.Details.CAPABILITY_MUTE));
    }

    /**
     * Updates the buttons applicable for the UI.
     *
     * @param call The active call.
     * @param context The context.
     */
    private void updateCallButtons(Call call, Context context) {
		android.util.Log.d(TAG, "updateCallButtons");
        if (call.isVideoCall(context)) {
            updateVideoCallButtons(call);
        } else {
            updateVoiceCallButtons(call);
        }
    }

    private void updateVideoCallButtons(Call call) {
		android.util.Log.d(TAG, "updateVideoCallButtons");
        Log.v(this, "Showing buttons for video call.");
        final CallButtonUi ui = getUi();

        // Hide all voice-call-related buttons.
        //SPRD:Modify showAudioButton to true for Bug427417
        // ui.showAudioButton(false);
        ui.showAudioButton(true);
        /* SPRD: modify for Video Call don't support hold
         * @Orig:ui.showDialpadButton(false);
         *  */
        ui.showDialpadButton(true);
        /* @} */
        ui.showHoldButton(false);
        ui.showSwapButton(false);
        ui.showChangeToVideoButton(false);
        ui.showAddCallButton(false);
        ui.showMergeButton(false);
        ui.showOverflowButton(false);

        // Show all video-call-related buttons.
        /* SPRD: modify for bug383592
         * @Orig:
         * ui.showChangeToVoiceButton(true);
         * ui.showSwitchCameraButton(true);
         * { */
        if(!isVolteEnable){
            ui.showChangeToVoiceButton(false);
            ui.showSwitchCameraButton(true);
        }else{
            ui.showOverflowButton(true);
            ui.configureOverflowMenu(false, false, false, false,
                    false, false, true, call.getState() == Call.State.ACTIVE);
        }
        /* @} */
        ui.showPauseVideoButton(true);

        /* sprd: add for to display many buttons
         * @Orig
        final boolean supportHold = call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD);
        final boolean enableHoldOption = call.can(android.telecom.Call.Details.CAPABILITY_HOLD);
        /* SPRD: modify for Video Call don't support hold
         * @Orig:
         * ui.showHoldButton(supportHold);
         * ui.enableHold(enableHoldOption);
         * ui.setHold(call.getState() == Call.State.ONHOLD);
         *  */
        /* SPRD: Add for recorder @{ */
        if (call.getState() == Call.State.ACTIVE) {
            ui.enableRecord(true);
            /* SPRD: add for bug428566@{*/
            ui.enablePauseVideoButton(true);
            if(!isVolteEnable){
                ui.enableSwitchCameraButton(true);
            }
            /* @} */
        } else {
           ui.enableRecord(false);
           /* SPRD: add for bug428566@{*/
           ui.enablePauseVideoButton(false);
           if(!isVolteEnable){
               ui.enableSwitchCameraButton(false);
           }
           /* @} */
        }
        /* @} */
        /* } */
    }

    private void updateVoiceCallButtons(Call call) {
		android.util.Log.d(TAG, "updateVoiceCallButtons");
        Log.v(this, "Showing buttons for voice call.");
        final CallButtonUi ui = getUi();

        // Hide all video-call-related buttons.
        ui.showChangeToVoiceButton(false);
        ui.showSwitchCameraButton(false);
        ui.showPauseVideoButton(false);

        // Show all voice-call-related buttons.
        ui.showAudioButton(true);
        ui.showDialpadButton(true);

        Log.v(this, "Show hold ", call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD));
        Log.v(this, "Enable hold", call.can(android.telecom.Call.Details.CAPABILITY_HOLD));
        Log.v(this, "Show merge ", call.can(
                android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE));
        Log.v(this, "Show swap ", call.can(
                android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE));
        Log.v(this, "Show add call ", TelecomAdapter.getInstance().canAddCall());
        Log.v(this, "Show mute ", call.can(android.telecom.Call.Details.CAPABILITY_MUTE));

        final boolean canAdd = TelecomAdapter.getInstance().canAddCall();
        final boolean enableHoldOption = call.can(android.telecom.Call.Details.CAPABILITY_HOLD);
        final boolean supportHold = call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD);
        final boolean isCallOnHold = call.getState() == Call.State.ONHOLD;

        /* SPRD: modify for bug383592
         * @Orig:
         * boolean canVideoCall = call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL)
                && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORTS_VT_REMOTE);
           ui.showChangeToVideoButton(canVideoCall);
           ui.enableChangeToVideoButton(!isCallOnHold);
         * { */
        boolean canVideoCall = CallUtil.isVideoEnabled(ui.getContext()) && isVolteEnable;
        /* } */

        /* SPRD: check conference size whether it's less than 5 before update buttons bug430708 @{ */
        int conferenceSize = 0;
        if (call.isConferenceCall()) {
            conferenceSize = call.getChildCallIds().size();
        } else {
            conferenceSize = call.getTelecommCall().getConferenceableCalls()
                    .size();
        }
        final boolean showMergeOption = (call.can(
                android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE)) && (conferenceSize < 5);
        /* @} */
        final boolean showAddCallOption = canAdd;

        // Show either HOLD or SWAP, but not both. If neither HOLD or SWAP is available:
        //     (1) If the device normally can hold, show HOLD in a disabled state.
        //     (2) If the device doesn't have the concept of hold/swap, remove the button.
        final boolean showSwapOption = call.can(
                android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE);
        final boolean showHoldOption = !showSwapOption && (enableHoldOption || supportHold);

        ui.setHold(isCallOnHold);
        // If we show video upgrade and add/merge and hold/swap, the overflow menu is needed.
        if(!isVolteEnable){
            final boolean isVideoOverflowScenario = canVideoCall
                    && (showAddCallOption || showMergeOption) && (showHoldOption || showSwapOption);
            // If we show hold/swap, add, and merge simultaneously, the overflow menu is needed.
            final boolean isOverflowScenario =
                    (showHoldOption || showSwapOption) && showMergeOption && showAddCallOption;
            if (isVideoOverflowScenario) {
                ui.showHoldButton(false);
                ui.showSwapButton(false);
                ui.showAddCallButton(false);
                ui.showMergeButton(false);

                ui.configureOverflowMenu(
                        showMergeOption,
                        showAddCallOption,//showAddMenuOption
                        showHoldOption && enableHoldOption,//showHoldMenuOption
                        showSwapOption,
                        isVolteEnable,//SPRD:add for VoLTE
                        isVolteEnable,
                        isVolteEnable,
                        isVolteEnable);
                ui.showOverflowButton(true);
            } else {
                if (isOverflowScenario) {
                    ui.showAddCallButton(false);
                    ui.showMergeButton(false);

                    ui.configureOverflowMenu(
                            showMergeOption,
                            showAddCallOption,//showAddMenuOption
                            false, //howHoldMenuOption
                            false, //showSwapMenuOption
                            isVolteEnable,//SPRD:add for VoLTE
                            isVolteEnable,
                            isVolteEnable,
                            isVolteEnable);
                } else {
                    ui.showMergeButton(showMergeOption);
                    ui.showAddCallButton(showAddCallOption);
                }

                ui.showOverflowButton(isOverflowScenario);
                ui.showHoldButton(showHoldOption);
                ui.enableHold(enableHoldOption);
                ui.showSwapButton(showSwapOption);

                // SPRD: Add for recorder
                ui.enableRecord(enableRecorderOrAddCall(call));
                ui.enableAddCall(enableRecorderOrAddCall(call));
            }
        } else {
            final boolean canInvite = call.isConferenceCall() && isVolteEnable;
            final boolean showAddorMerge = showAddCallOption || showMergeOption;
            final boolean showHoldorSwap = showHoldOption || showSwapOption;
            final boolean isVideoOverflowScenario = (canVideoCall && showAddorMerge && showHoldorSwap)
                    || (canInvite && showAddorMerge && showHoldorSwap)
                    || (canVideoCall && canInvite && showAddorMerge)
                    || (canVideoCall && canInvite && showHoldorSwap);
            // If we show hold/swap, add, and merge simultaneously, the overflow menu is needed.
            final boolean isOverflowScenario = (showHoldorSwap && showMergeOption && showAddCallOption)
                    || (showHoldorSwap && showMergeOption && canInvite)
                    || (showHoldorSwap && showMergeOption && canVideoCall)
                    || (showHoldorSwap && canInvite && showAddCallOption)
                    || (showHoldorSwap && canVideoCall && showAddCallOption)
                    || (showHoldorSwap && canInvite && canVideoCall)
                    || (canInvite && showMergeOption && showAddCallOption)
                    || (canInvite && canVideoCall && showAddCallOption)
                    || (canVideoCall && showMergeOption && showAddCallOption)
                    || (canInvite && showMergeOption && canVideoCall);

            if (isVideoOverflowScenario || isOverflowScenario) {
                ui.showHoldButton(false);
                ui.showSwapButton(false);
                ui.showAddCallButton(false);
                ui.showMergeButton(false);
                ui.showChangeToVideoButton(false);
                ui.showInviteButton(false);

                ui.configureOverflowMenu(
                        showMergeOption,
                        showAddCallOption /* showAddMenuOption */,
                        showHoldOption && enableHoldOption /* showHoldMenuOption */,
                        showSwapOption,
                        canVideoCall,
                        canInvite,
                        false,
                        false);
                ui.showOverflowButton(true);
            } else {
                ui.showOverflowButton(false);
                ui.showMergeButton(showMergeOption);
                ui.showAddCallButton(showAddCallOption);
                ui.showHoldButton(showHoldOption);
                ui.enableHold(enableHoldOption);
                ui.showSwapButton(showSwapOption);
                ui.showChangeToVideoButton(canVideoCall);
                ui.enableChangeToVideoButton(!isCallOnHold);
                ui.showInviteButton(canInvite);

                // SPRD: Add for recorder
                ui.enableRecord(enableRecorderOrAddCall(call));
                ui.enableAddCall(enableRecorderOrAddCall(call));
                /* } */
            }
            /* } */
        }
    }

    public void refreshMuteState() {
		android.util.Log.d(TAG, "refreshMuteState");
        // Restore the previous mute state
        if (mAutomaticallyMuted &&
                AudioModeProvider.getInstance().getMute() != mPreviousMuteState) {
            if (getUi() == null) {
                return;
            }
            muteClicked(mPreviousMuteState);
        }
        mAutomaticallyMuted = false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
		android.util.Log.d(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_AUTOMATICALLY_MUTED, mAutomaticallyMuted);
        outState.putBoolean(KEY_PREVIOUS_MUTE_STATE, mPreviousMuteState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
		android.util.Log.d(TAG, "onRestoreInstanceState");
        mAutomaticallyMuted =
                savedInstanceState.getBoolean(KEY_AUTOMATICALLY_MUTED, mAutomaticallyMuted);
        mPreviousMuteState =
                savedInstanceState.getBoolean(KEY_PREVIOUS_MUTE_STATE, mPreviousMuteState);
        super.onRestoreInstanceState(savedInstanceState);
    }
    public interface CallButtonUi extends Ui {
        void setEnabled(boolean on);
        void setMute(boolean on);
        void enableMute(boolean enabled);
        void showAudioButton(boolean show);
        void showChangeToVoiceButton(boolean show);
        void showDialpadButton(boolean show);
        void setHold(boolean on);
        void showHoldButton(boolean show);
        void enableHold(boolean enabled);
        void showSwapButton(boolean show);
        void showChangeToVideoButton(boolean show);
        void enableChangeToVideoButton(boolean enable);
        void showSwitchCameraButton(boolean show);
        void setSwitchCameraButton(boolean isBackFacingCamera);
        void showAddCallButton(boolean show);
        void showMergeButton(boolean show);
        void showPauseVideoButton(boolean show);
        void setPauseVideoButton(boolean isPaused);
        void showOverflowButton(boolean show);
        /* sprd: add for to display many buttons { */
        void showInviteButton(boolean show);
        /* } */
        void displayDialpad(boolean on, boolean animate);
        boolean isDialpadVisible();
        void setAudio(int mode);
        void setSupportedAudio(int mask);
        void configureOverflowMenu(boolean showMergeMenuOption, boolean showAddMenuOption,
                boolean showHoldMenuOption, boolean showSwapMenuOption,
                // sprd: add showChangeCallMenuOption and showInviteMenuOption for to display many buttons
                boolean showChangeToVideoOption, boolean showInviteMenuOption,
                boolean showChangeToVoiceOption, boolean showSwitchCameraOption);
        Context getContext();
        /* SPRD: add for bug427447@{*/
        void enablePauseVideoButton(boolean enabled);
        void enableSwitchCameraButton(boolean enabled);
        /* @} */

        // SPRD: Add for recorder. @{
        void setRecord(boolean on);
        void enableRecord(boolean on);
        void toggleRecord();
        // @}
        void enableAddCall(boolean on); // SPRD: Add for addCall function
    }

//------------------------------ SPRD --------------------------

    /* Add for recorder */
    public void recordClicked(boolean checked) {
		android.util.Log.d(TAG, "recordClicked");
        android.util.Log.d("suzie", "recordClicked............. checked===  " + checked);
        getUi().toggleRecord();
    }

    public boolean enableRecorderOrAddCall(Call call) {
		android.util.Log.d(TAG, "enableRecorderOrAddCall");
        int state = call.getState();
        return (state == Call.State.ACTIVE || state == Call.State.ONHOLD
               || state == Call.State.CONFERENCED);
    }
    /* SPRD: add for bug427447@{*/
    @Override
    public void onCameraSwitchStateChanged(boolean isSwithing){
				android.util.Log.d(TAG, "onCameraSwitchStateChanged");
    final CallButtonUi ui = getUi();
    if (ui == null) {
    return;
    }
    ui.enablePauseVideoButton(!isSwithing);
    ui.enableSwitchCameraButton(!isSwithing);
    }
    /* @} */
}
