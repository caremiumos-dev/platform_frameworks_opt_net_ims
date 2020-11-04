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

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.telephony.ims.RcsUceAdapter.CapabilitiesCallback;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.UceController.UceControllerCallback;

import java.util.List;

public class SubscribeControllerImpl implements SubscribeController {

    private final Context mContext;
    private final int mSubId;
    private final UceControllerCallback mCallback;
    private final Looper mLooper;

    public SubscribeControllerImpl(Context context, int subId, UceControllerCallback c,
            Looper looper) {
        mContext = context;
        mSubId = subId;
        mCallback = c;
        mLooper = looper;
    }

    @Override
    public void requestCapabilities(List<Uri> contactUris, CapabilitiesCallback c) {

    }

    @Override
    public void requestAvailability(Uri uri, CapabilitiesCallback c) {

    }

    @Override
    public void setUceRequestCallback(UceControllerCallback c) {

    }

    @Override
    public void onRcsConnected(RcsFeatureManager manager) {

    }

    @Override
    public void onRcsDisconnected() {

    }

    @Override
    public void onDestroy() {

    }
}
