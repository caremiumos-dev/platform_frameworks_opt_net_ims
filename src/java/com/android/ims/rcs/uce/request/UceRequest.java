/*
 * Copyright (c) 2020 The Android Open Source Project
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

package com.android.ims.rcs.uce.request;

import android.annotation.IntDef;
import android.net.Uri;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.IRcsUceControllerCallback;
import android.telephony.ims.RcsContactUceCapability;
import android.util.Log;

import com.android.ims.rcs.uce.UceRequestManager.RequestManagerCallback;
import com.android.ims.rcs.uce.eab.EabCapabilityResult;
import com.android.ims.rcs.uce.util.UceUtils;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The base class of the UCE request to request the capabilities from the carrier network.
 */
public abstract class UceRequest {

    private static final String LOG_TAG = "UceRequest";

    /** The request type: CAPABILITY */
    public static final int REQUEST_TYPE_CAPABILITY = 1;

    /** The request type: AVAILABILITY */
    public static final int REQUEST_TYPE_AVAILABILITY = 2;

    /**@hide*/
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "REQUEST_TYPE_", value = {
            REQUEST_TYPE_CAPABILITY,
            REQUEST_TYPE_AVAILABILITY
    })
    public @interface UceRequestType {}

    protected final int mSubId;
    protected final long mTaskId;
    protected final @UceRequestType int mRequestType;
    protected final CapabilityRequestResponse mRequestResponse;
    protected final RequestManagerCallback mRequestManagerCallback;

    protected List<Uri> mUriList;
    protected volatile boolean mIsFinished;

    public UceRequest(int subId, @UceRequestType int requestType,
            RequestManagerCallback taskMgrCallback) {
        mSubId = subId;
        mRequestType = requestType;
        mRequestManagerCallback = taskMgrCallback;
        mRequestResponse = new CapabilityRequestResponse();

        mTaskId = UceUtils.generateTaskId();
    }

    /**
     * Get the task ID of this request.
     */
    public long getTaskId() {
        return mTaskId;
    }

    /**
     * Set this request is finish.
     */
    public void onFinish() {
        mIsFinished = true;
    }

    /**
     * Set the contacts of the capabilities are being requested for.
     */
    public void setContactUri(List<Uri> uris) {
        mUriList = uris;
    }

    /**
     * Set the callback to receive the contacts capabilities update.
     */
    public void setCapabilitiesCallback(IRcsUceControllerCallback callback) {
        mRequestResponse.setCapabilitiesCallback(callback);
    }

    /**
     * Start to execute this request.
     */
    public void executeRequest() {
        // Check whether this request is allowed to execute.
        if (!isRequestAllowed()) {
            logd("executeRequest: The request is not allowed");
            handleRequestFailed(true);
            return;
        }

        // Get the capabilities from the cache.
        final List<RcsContactUceCapability> cachedCapabilityList =
                getCapabilitiesFromCache(mRequestType);

        logd("executeRequest: cached capabilities=" + cachedCapabilityList.size());

        // Trigger the callback for those capabilities from the cache.
        if (!handleCachedCapabilities(cachedCapabilityList)) {
            // Terminate this request if triggering the capabilities received callback fails.
            handleRequestFailed(true);
            return;
        }

        // Get the rest contacts which need to request capabilities from the network.
        final List<Uri> requestCapUris = getUrisNeedRequestCapabilities(cachedCapabilityList);

        logd("executeRequest: requestCapUris size=" + requestCapUris.size());

        // Finish this request if all the requested capabilities can be retrieved from cache.
        // Otherwise, request capabilities for those contacts which cannot retrieve capabilities
        // from cache.
        if (requestCapUris.isEmpty()) {
            handleRequestCompleted(true);
        } else {
            requestCapabilities(requestCapUris);
        }
    }

    // Check whether the request is allowed to execute.
    private boolean isRequestAllowed() {
        if (mUriList == null || mUriList.isEmpty()) {
            logw("isRequestAllowed: uri is empty");
            mRequestResponse.setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return false;
        }

        if (mIsFinished) {
            logw("isRequestAllowed: This request is finished");
            mRequestResponse.setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return false;
        }
        return true;
    }

    // Get the cached capabilities by the given request type.
    private List<RcsContactUceCapability> getCapabilitiesFromCache(
            @UceRequestType int requestType) {
        EabCapabilityResult eabResult = null;
        if (requestType == REQUEST_TYPE_CAPABILITY) {
            eabResult = mRequestManagerCallback.getCapabilitiesFromCache(mUriList);
        } else if (requestType == REQUEST_TYPE_AVAILABILITY) {
            Uri uri = mUriList.get(0);
            eabResult = mRequestManagerCallback.getAvailabilityFromCache(uri);
        }
        return (eabResult != null) ? eabResult.getContactCapabilities() : Collections.emptyList();
    }

    @VisibleForTesting
    public List<Uri> getUrisNeedRequestCapabilities(List<RcsContactUceCapability> capabilityList) {
        return mUriList.stream()
                .filter(uri -> capabilityList.stream()
                        .noneMatch(cap -> cap.getContactUri().equals(uri)))
                        .collect(Collectors.toList());
    }

    /*
     * Trigger the capabilities received callback for those cached capabilities. This is called
     * when the contact capabilities are retrieved from cache.
     * @return true if triggering the callback is success.
     */
    private boolean handleCachedCapabilities(List<RcsContactUceCapability> cachedCapabilities) {
        return mRequestResponse.triggerCachedCapabilitiesCallback(cachedCapabilities);
    }

    /**
     * Save the updated capabilities to cache and trigger the capabilities received callback. This
     * is called when the capabilities updated from network.
     * @return true if saving to cache and triggering the callback are success.
     */
    public boolean handleCapabilitiesUpdated() {
        List<RcsContactUceCapability> updatedCapabilities =
                mRequestResponse.getUpdatedContactCapability();
        if (!updatedCapabilities.isEmpty()) {
            // Save the updated capabilities to cache
            mRequestManagerCallback.saveCapabilities(updatedCapabilities);
            // Trigger the capabilities received callback
            return mRequestResponse.triggerCapabilitiesCallback(updatedCapabilities);
        }
        return true;
    }

    /**
     * Send the onError callback to end this request.
     */
    public void handleRequestFailed(boolean notifyRequestManager) {
        logd("handleRequestFailed: error=" + mRequestResponse.getErrorCode()
                + ", notify RequestManager=" + notifyRequestManager);
        // Trigger the onError callback
        mRequestResponse.triggerErrorCallback();

        // Check if notify RequestManager that the request is finished.
        if (notifyRequestManager) {
            mRequestManagerCallback.onRequestFinished(mTaskId);
        }
    }

    /**
     * Send the onComplete callback to end this request.
     */
    public void handleRequestCompleted(boolean notifyRequestManager) {
        logd("handleRequestCompleted: notify RequestManager=" + notifyRequestManager);
        // Trigger the onComplete callback
        mRequestResponse.triggerCompletedCallback();

        // Check if notify RequestManager that the request is finished.
        if (notifyRequestManager) {
            mRequestManagerCallback.onRequestFinished(mTaskId);
        }
    }

    protected abstract void requestCapabilities(List<Uri> requestCapUris);

    protected void logd(String log) {
        Log.d(LOG_TAG, getLogPrefix().append(log).toString());
    }

    protected void logw(String log) {
        Log.w(LOG_TAG, getLogPrefix().append(log).toString());
    }

    private StringBuilder getLogPrefix() {
        StringBuilder builder = new StringBuilder("[");
        builder.append(mSubId).append("][taskId=").append(mTaskId).append("] ");
        return builder;
    }
}
