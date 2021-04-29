/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.ims.rcs.uce;

import android.annotation.IntDef;
import android.content.Context;
import android.telephony.ims.RcsUceAdapter;
import android.util.Log;

import com.android.ims.rcs.uce.UceController.UceControllerCallback;
import com.android.ims.rcs.uce.util.NetworkSipCode;
import com.android.ims.rcs.uce.util.UceUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.util.Optional;

/**
 * Represents the server state.
 */
public class DeviceState {

    private static final String TAG = UceUtils.getLogPrefix() + "DeviceState";

    /**
     * The result of the current device state.
     */
    public static class DeviceStateResult {
        final int mDeviceState;
        final Optional<Integer> mErrorCode;

        public DeviceStateResult(int deviceState, Optional<Integer> errorCode) {
            mDeviceState = deviceState;
            mErrorCode = errorCode;
        }

        public boolean isDisallowed() {
            return isRequestDisallowed(mDeviceState);
        }

        public int getDeviceState() {
            return mDeviceState;
        }

        public int getErrorCode() {
            // The default error code is forbidden.
            return mErrorCode.orElse(RcsUceAdapter.ERROR_FORBIDDEN);
        }
    }

    private static final int DEVICE_STATE_OK = 0;
    private static final int DEVICE_STATE_FORBIDDEN = 1;
    private static final int DEVICE_STATE_BAD_EVENT = 2;

    @IntDef(value = {
            DEVICE_STATE_OK,
            DEVICE_STATE_FORBIDDEN,
            DEVICE_STATE_BAD_EVENT,
    }, prefix="DEVICE_STATE_")
    @Retention(RetentionPolicy.SOURCE)
    public @interface DeviceStateType {}

    /**
     * Check the given device state whether is disallowed or not.
     */
    private static boolean isRequestDisallowed(@DeviceStateType int deviceState) {
        switch(deviceState) {
            case DEVICE_STATE_FORBIDDEN:
            case DEVICE_STATE_BAD_EVENT:
                return true;
            default:
                return false;
        }
    }

    private final int mSubId;
    private final Context mContext;
    private final UceControllerCallback mUceCtrlCallback;

    private @DeviceStateType int mDeviceState;
    private Optional<Integer> mErrorCode;

    public DeviceState(int subId, Context context, UceControllerCallback uceCtrlCallback) {
        mSubId = subId;
        mContext = context;
        mDeviceState = DEVICE_STATE_OK;
        mErrorCode = Optional.empty();
        mUceCtrlCallback = uceCtrlCallback;
    }

    /**
     * @return The current device state.
     */
    public synchronized DeviceStateResult getCurrentState() {
        return new DeviceStateResult(mDeviceState, mErrorCode);
    }

    /**
     * Reset the device state.
     */
    public synchronized void reset() {
        Log.d(TAG, "reset device state");
        mDeviceState = DEVICE_STATE_OK;
        mErrorCode = Optional.empty();
        clearExitStateTimer();
    }

    /**
     * Update the device state to check whether the device is allowed to send the UCE requests.
     *  @param sipCode The SIP CODE of the request result.
     *  @param reason The reason from the network response.
     */
    public synchronized void refreshDeviceState(int sipCode, String reason) {
        mDeviceState = getDeviceState(sipCode);

        Log.d(TAG, "refreshDeviceState: sipCode=" + sipCode + ", reason=" + reason +
                ", deviceState=" + mDeviceState);

        // Set the error code when the device is disallowed.
        if (isRequestDisallowed(mDeviceState)) {
            mErrorCode = Optional.of(NetworkSipCode.getCapabilityErrorFromSipCode(sipCode, reason));
            // Setup the exit disallowed state timer.
            setupExitStateTimer(mDeviceState);
        } else {
            mErrorCode = Optional.empty();
            clearExitStateTimer();
        }
    }

    private int getDeviceState(int sipCode) {
        switch (sipCode) {
            case NetworkSipCode.SIP_CODE_FORBIDDEN:
                return DEVICE_STATE_FORBIDDEN;
            case NetworkSipCode.SIP_CODE_BAD_EVENT:
                return DEVICE_STATE_BAD_EVENT;
            default:
                return DEVICE_STATE_OK;
        }
    }

    /**
     * Set the timer to exit the device disallowed state and then trigger a PUBLISH request.
     */
    private void setupExitStateTimer(@DeviceStateType int deviceState) {
        if (deviceState == DEVICE_STATE_BAD_EVENT) {
            long expirationSec = UceUtils.getNonRcsCapabilitiesCacheExpiration(mContext, mSubId);
            mUceCtrlCallback.setupResetDeviceStateTimer(expirationSec);
        }
    }

    /**
     * Remove the exit device disallowed state timer.
     */
    private void clearExitStateTimer() {
        mUceCtrlCallback.clearResetDeviceStateTimer();
    }
}
