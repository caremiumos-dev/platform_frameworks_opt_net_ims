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

package com.android.ims.rcs.uce.eab;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.telephony.ims.RcsContactUceCapability;
import android.util.Log;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.UceController.UceControllerCallback;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The implementation class of the interface EabController
 */
public class EabControllerImpl implements EabController {

    private static final String LOG_TAG = "EabControllerImpl";

    private final int mSubId;
    private final Context mContext;
    private final Looper mLooper;

    public EabControllerImpl(Context context, int subId, Looper looper) {
        mSubId = subId;
        mContext = context;
        mLooper = looper;
    }

    /**
     * The RcsFeature has been connected to the framework.
     */
    @Override
    public void onRcsConnected(RcsFeatureManager manager) {
        // TODO: Implement this method
    }

    /**
     * The framework has lost the binding to the RcsFeature.
     */
    @Override
    public void onRcsDisconnected() {
        // TODO: Implement this method
    }

    @Override
    public void onDestroy() {
        // TODO: Implement this method
    }

    /**
     * Retrieve the contacts' capabilities from the EAB database.
     */
    @Override
    public @NonNull EabCapabilityResult getCapabilities(@NonNull List<Uri> uris) {
        Objects.requireNonNull(uris);
        // TODO: Implement the EAB provider
        return null;
    }

    /**
     * Retrieve the contact's capabilities from the availability cache.
     */
    @Override
    public @NonNull EabCapabilityResult getAvailability(@NonNull Uri contactUri) {
        Objects.requireNonNull(contactUri);
        // Implement this method;
        return null;
    }

    /**
     * Update the availability catch and save the capabilities to the EAB database.
     */
    @Override
    public void saveCapabilities(@NonNull List<RcsContactUceCapability> contactCapabilities) {
        Objects.requireNonNull(contactCapabilities);
        // TODO: Implement the EAB provider to save capabilities into EAB database
    }

    /**
     * Set the callback for sending the request to UceController.
     */
    @Override
    public void setUceRequestCallback(@NonNull UceControllerCallback callback) {
        Objects.requireNonNull(callback);
        // Implement this method
    }
}
