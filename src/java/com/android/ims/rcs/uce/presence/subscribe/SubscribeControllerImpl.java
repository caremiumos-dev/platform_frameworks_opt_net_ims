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

package com.android.ims.rcs.uce.presence.subscribe;

import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.telephony.ims.RcsUceAdapter;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.UceController.UceControllerCallback;
import com.android.ims.rcs.uce.RcsCapabilityExchangeImplAdapter.SubscribeResponseCallback;

import java.util.List;

/**
 * The implementataion class of the interface SubscribeController.
 */
public class SubscribeControllerImpl implements SubscribeController {

    private static final String LOG_TAG = "SubscribeControllerImpl";

    private final Context mContext;
    private final int mSubId;
    private final Looper mLooper;

    public SubscribeControllerImpl(Context context, int subId, Looper looper) {
        mContext = context;
        mSubId = subId;
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
     * Request the capabilities for the requested contacts.
     */
    @Override
    public void requestCapabilities(@NonNull List<Uri> contactUris,
            @NonNull RcsUceAdapter.CapabilitiesCallback c) {
        // TODO: Implement this method
    }

    /**
     * Request the capabilities for the requested contact.
     */
    @Override
    public void requestAvailability(@NonNull Uri uri,
            @NonNull RcsUceAdapter.CapabilitiesCallback c) {
        // TODO: Implement this method
    }

    /**
     * Set the UceRequestCallback for sending the request back to UceController.
     */
    @Override
    public void setUceRequestCallback(@NonNull UceControllerCallback c) {
        // TODO: Implement this method
    }
}
