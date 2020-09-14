/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.ims.rcs.uce.options;

import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.telephony.ims.ImsException;
import android.telephony.ims.RcsContactUceCapability;
import android.util.Log;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.UceController.UceControllerCallback;
import com.android.ims.rcs.uce.RcsCapabilityExchangeImplAdapter;
import com.android.ims.rcs.uce.RcsCapabilityExchangeImplAdapter.OptionsResponseCallback;

/**
 * The implementation class of the interface OptionsController.
 */
public class OptionsControllerImpl implements OptionsController {

    private static final String LOG_TAG = "OptionsControllerImpl";

    private final int mSubId;
    private final Context mContext;
    private final Looper mLooper;

    private volatile boolean mIsRcsConnected;
    private volatile boolean mIsDestroyedFlag;

    private RcsFeatureManager mRcsFeatureManager;

    public OptionsControllerImpl(Context context, int subId, Looper looper) {
        mContext = context;
        mSubId = subId;
        mLooper = looper;
        mIsDestroyedFlag = false;
        logi("create");
    }

    @Override
    public void onRcsConnected(RcsFeatureManager manager) {
        logi("onRcsConnected");
        mRcsFeatureManager = manager;
        mIsRcsConnected = true;
    }

    @Override
    public void onRcsDisconnected() {
        logi("onRcsDisconnected");
        mIsRcsConnected = false;
    }

    @Override
    public void onDestroy() {
        logi("onDestroy");
        mIsDestroyedFlag = true;
    }

    /**
     * Request the capabilities for the requested contact.
     */
    @Override
    public void sendCapabilitiesRequest(@NonNull Uri contactUri,
            @NonNull RcsContactUceCapability ownCapabilities, @NonNull OptionsResponseCallback c) {
        if (mIsDestroyedFlag) {
            try {
                c.onCommandError(RcsCapabilityExchangeImplAdapter.COMMAND_CODE_GENERIC_FAILURE);
            } catch (ImsException e) {
                logw("sendCapabilitiesRequest: onCommandError=" + e.getMessage());
            }
        }
        // TODO: Refactor the RcsFeatureManager to deliver the capabilities request to ImsService.
    }

    private void logi(String log) {
        Log.i(LOG_TAG, getLogPrefix().append(log).toString());
    }

    private void logw(String log) {
        Log.w(LOG_TAG, getLogPrefix().append(log).toString());
    }

    private StringBuilder getLogPrefix() {
        StringBuilder builder = new StringBuilder("[");
        builder.append(mSubId);
        builder.append("] ");
        return builder;
    }
}
