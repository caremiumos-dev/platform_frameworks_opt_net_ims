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
import android.annotation.Nullable;
import android.net.Uri;
import android.telephony.ims.ImsException;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;

import com.android.ims.rcs.uce.RcsCapabilityExchangeImplAdapter.OptionsResponseCallback;
import com.android.ims.rcs.uce.UceController.UceControllerCallback;
import com.android.ims.rcs.uce.UceRequestTaskManager.TaskManagerCallback;
import com.android.ims.rcs.uce.eab.EabCapabilityResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The request task to execute the SIP OPTIONS request.
 */
public class OptionsRequestTask extends UceRequestTask {

    private static final String LOG_TAG = "OptionsRequestTask";

    // The callback to receive the response from the Service
    private OptionsResponseCallback mOptionsResponseCallback;

    public OptionsRequestTask(UceControllerCallback controller, UceRequestParam param,
            TaskManagerCallback taskManager) {
        super(controller, param, taskManager);
        logi("create");
    }

    @Override
    protected void defineTaskBehavior() {
        mRequestBehavior = () -> {
            // Check whether the task is allowed to execute.
            if (!isRequestAllowed()) {
                logd("executeBehavior: request is not allowed");
                handleRequestError();
                return;
            }

            // Get the cached capabilities
            RcsContactUceCapability capabilityCache = getCachedCapabilities();

            // Check whether the capability is in the cache without expired.
            boolean isInCache = true;
            if (!isInCacheWithoutExpired(capabilityCache)) {
                isInCache = false;
            }
            logd("executeBehavior: cache=" + isInCache);

            // Trigger callback if the contact's capability is valid or request it from the remote.
            if (isInCache) {
                handleCapabilitiesInCache(capabilityCache);
            } else {
                requestCapabilitiesByOptions();
            }
        };
    }

    /*
     * Check if the request is allowed to execute.
     */
    protected boolean isRequestAllowed() {
        if (mIsReleasedFlagSet) {
            logd("isRequestAllowed: released flag is set");
            mRequestResponse.setRequestError(UceRequestResponse.RequestError.TASK_RELEASED);
            return false;
        }
        return true;
    }

    // Get the cached capability based on the request type.
    private RcsContactUceCapability getCachedCapabilities() {
        REQUEST_TYPE requestType = mRequestParam.getRequestType();
        if (requestType == REQUEST_TYPE.CAPABILITY) {
            return getCapabilitiesFromCache();
        } else if (requestType == REQUEST_TYPE.AVAILABILITY) {
            return getAvailabilityFromCache();
        } else {
            logd("getCachedCapabilities: invalid request type " + requestType);
            return null;
        }
    }

    protected RcsContactUceCapability getCapabilitiesFromCache() {
        EabCapabilityResult eabResult = mUceControllerProxy.getCapabilitiesFromCache(
                mRequestParam.getContactUris());
        if (eabResult == null) {
            return null;
        }

        List<RcsContactUceCapability> list = eabResult.getContactCapabilities();
        if (list == null || list.isEmpty()) {
            return null;
        }

        // Get the first element of the capability list
        return list.get(0);
    }

    protected RcsContactUceCapability getAvailabilityFromCache() {
        List<Uri> uriList = mRequestParam.getContactUris();
        if (uriList == null || uriList.isEmpty()) {
            logw("getAvailabilityFromCache: uri list is null");
            return null;
        }

        Uri contactUri = uriList.get(0);
        if (contactUri == null) {
            logw("getAvailabilityFromCache: contact uri is null");
            return null;
        }

        EabCapabilityResult eabResult = mUceControllerProxy.getAvailabilityFromCache(contactUri);
        if (eabResult == null) {
            logw("getAvailabilityFromCache: eab result is null");
            return null;
        }

        List<RcsContactUceCapability> list = eabResult.getContactCapabilities();
        if (list == null || list.isEmpty()) {
            return null;
        }

        // Get the first element of the capability list
        return list.get(0);
    }

    protected boolean isInCacheWithoutExpired(RcsContactUceCapability cache) {
        if (cache == null) {
            return false;
        }

        Uri contactUri = getContactUri();
        if (contactUri == null) {
            logw("isInCacheWithoutExpired: Uri from param is null");
            return false;
        }

        Uri uriFromCache = cache.getContactUri();
        if (uriFromCache == null) {
            logw("isInCacheWithoutExpired: Uri from cache is null");
            return false;
        }

        // Compare if the two uris are equal
        if (contactUri.compareTo(uriFromCache) == 0) {
            return true;
        }
        return false;
    }

    // Get the uri from the first element of the uri list
    private Uri getContactUri() {
        List<Uri> uriList = mRequestParam.getContactUris();
        if (uriList == null || uriList.isEmpty()) {
            return null;
        }
        return uriList.get(0);
    }

    // Handle the required capabilities are in the cache and not expired.
    protected void handleCapabilitiesInCache(RcsContactUceCapability cacheCapability) {
        RcsUceAdapter.CapabilitiesCallback callback = mRequestParam.getCapabilitiesCallback();

        List<RcsContactUceCapability> contactCapabilities = new ArrayList<>();
        contactCapabilities.add(cacheCapability);

        logd("handleCapabilitiesInCache: trigger callback");
        callback.onCapabilitiesReceived(contactCapabilities);

        // TODO: Should call the method RcsUceAdapter.CapabilitiesCallback#onComplete
        //callback.onComplete();

        setTaskFinish();
    }

    private void requestCapabilitiesByOptions() {
        if (mIsReleasedFlagSet) {
            logd("requestCapabilitiesByOptions: The released flag is set");
            mRequestResponse.setRequestError(UceRequestResponse.RequestError.TASK_RELEASED);
            handleRequestError();
            return;
        }

        RcsContactUceCapability ownCapability = mUceControllerProxy.getDeviceCapabilities();
        if (ownCapability == null) {
            logd("requestCapabilitiesByOptions: device capability is null");
            mRequestResponse.setRequestError(
                    UceRequestResponse.RequestError.DEVICE_CAPABILITY_EMPTY);
            handleRequestError();
            return;
        }

        logd("requestCapabilitiesByOptions");
        Uri uri = getContactUri();
        mUceControllerProxy.requestCapabilitiesByOptions(uri, ownCapability,
                mOptionsResponseCallback);
    }

    @Override
    protected void initResponseCallback() {
        mOptionsResponseCallback = new OptionsResponseCallback() {
            @Override
            public void onCommandError(int code) throws ImsException {
                logd("onCommandError=" + code);
                if (mIsReleasedFlagSet) return;
                mRequestResponse.setCommandError(code);
                handleCommandError();
            }

            @Override
            public void onNetworkResponse(int code, @NonNull String reason,
                    @Nullable List<String> theirCaps) throws ImsException {
                logd("onNetworkResponse: code=" + code + ", reason=" + reason);
                if (mIsReleasedFlagSet) return;
                mRequestResponse.setNetworkResponse(code, reason, theirCaps);
                handleNetworkResponse();
            }
        };
    }

    protected void handleRequestError() {
        int uceError = mRequestResponse.getCapabilityErrorFromRequestError();
        RcsUceAdapter.CapabilitiesCallback callback = mRequestParam.getCapabilitiesCallback();
        callback.onError(uceError);
        setTaskFinish();
    }

    protected void handleCommandError() {
        int errorCode = mRequestResponse.getCommandError();
        logd("handleCommandError: " + errorCode);

        boolean needRetry = false;
        switch (errorCode) {
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_REQUEST_TIMEOUT:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_INSUFFICIENT_MEMORY:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_LOST_NETWORK_CONNECTION:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_SERVICE_UNAVAILABLE:
                needRetry = true;
                break;
        }

        if (!needRetry || !retryWithSendingMessage()) {
            logd("handleCommandError: do not retry");
            int uceError = mRequestResponse.convertCmdErrorToCapabilityError();
            RcsUceAdapter.CapabilitiesCallback callback = mRequestParam.getCapabilitiesCallback();
            callback.onError(uceError);
            setTaskFinish();
        }
    }

    protected void handleNetworkResponse() {
        int code = mRequestResponse.getNetworkResponseCode();
        String reason = mRequestResponse.getNetworkResponseReason();
        RcsUceAdapter.CapabilitiesCallback callback = mRequestParam.getCapabilitiesCallback();
        logd("handleNetworkResponse: " + code + ", reason: " + reason);

        // Trigger callback if the Network response is success.
        if (code == UceRequestResponse.SIP_CODE_OK) {
            List<RcsContactUceCapability> capabilityList = mRequestResponse.getRemoteCaps();
            // Save the remote's capabilities
            mUceControllerProxy.saveCapabilities(capabilityList);
            // Trigger callback
            callback.onCapabilitiesReceived(capabilityList);
            // TODO: Should call the method RcsUceAdapter.CapabilitiesCallback#onComplete
            //capabilitiesCallback.onComplete();

            // Finish this task
            setTaskFinish();
            return;
        }

        boolean needRetry = mRequestResponse.isSipCodeNeedRetry(code);
        if (!needRetry || !retryWithSendingMessage()) {
            logd("handleNetworkResponse: do not retry");
            int uceError = mRequestResponse.convertSipCodeErrorToCapabilityError();
            callback = mRequestParam.getCapabilitiesCallback();
            callback.onError(uceError);
            setTaskFinish();
        }
    }

    @Override
    protected String getLogTag() {
        return LOG_TAG;
    }
}
