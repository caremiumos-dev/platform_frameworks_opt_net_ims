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

package com.android.ims.rcs.uce;

import android.annotation.NonNull;
import android.net.Uri;
import android.telephony.ims.RcsUceAdapter;

import com.android.ims.rcs.uce.UceRequestTask.REQUEST_TYPE;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This class is to store the parameter of the UCE request.
 */
public class UceRequestParam {
    private final int mSubId;
    private final long mTaskId;
    private final REQUEST_TYPE mRequestType;
    private final List<Uri> mUriList;
    private final RcsUceAdapter.CapabilitiesCallback mCapabilitiesCallback;

    public UceRequestParam(int subId, long taskId, REQUEST_TYPE requestType,
            @NonNull List<Uri> uriList, @NonNull RcsUceAdapter.CapabilitiesCallback c) {
        Objects.requireNonNull(uriList);
        Objects.requireNonNull(c);
        mSubId = subId;
        mTaskId = taskId;
        mRequestType = requestType;
        mUriList = new ArrayList<>(uriList);
        mCapabilitiesCallback = c;
    }

    public int getSubId() {
        return mSubId;
    }

    public long getTaskId() {
        return mTaskId;
    }

    public REQUEST_TYPE getRequestType() {
        return mRequestType;
    }

    public List<Uri> getContactUris() {
        return mUriList;
    }

    public RcsUceAdapter.CapabilitiesCallback getCapabilitiesCallback() {
        return mCapabilitiesCallback;
    }
}
