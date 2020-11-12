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

import android.net.Uri;
import android.os.RemoteException;
import android.telephony.ims.RcsContactTerminatedReason;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.ISubscribeResponseCallback;

import com.android.ims.rcs.uce.UceRequestManager.RequestManagerCallback;
import com.android.ims.rcs.uce.presence.pidfparser.PidfParser;
import com.android.ims.rcs.uce.presence.subscribe.SubscribeController;
import com.android.ims.rcs.uce.util.NetworkSipCode;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Request the capabilities via SubscribeController.
 */
public class SubscribeRequest extends UceRequest {

    private SubscribeController mSubscribeController;

    // The result callback of the subscribe request from IMS service.
    private ISubscribeResponseCallback mSubscribeResponseCallback =
            new ISubscribeResponseCallback.Stub() {
                @Override
                public void onCommandError(int code) {
                    handleCommandError(code);
                }

                @Override
                public void onNetworkResponse(int code, String reason) {
                    handleNetworkResponse(code, reason);
                }

                @Override
                public void onNotifyCapabilitiesUpdate(List<String> pidfXmls) {
                    handleCapabilitiesUpdate(pidfXmls);
                }

                @Override
                public void onResourceTerminated(
                        List<RcsContactTerminatedReason> uriTerminatedReason) {
                    if (mIsFinished) return;
                }

                @Override
                public void onTerminated(String reason, String retryAfter) {
                    handleTerminated(reason, retryAfter);
                }
            };

    public SubscribeRequest(int subId, @UceRequestType int requestType,
            RequestManagerCallback taskMgrCallback, SubscribeController subscribeController) {
        super(subId, requestType, taskMgrCallback);
        mSubscribeController = subscribeController;
    }

    @Override
    public void onFinish() {
        super.onFinish();
        mSubscribeController = null;
    }

    @Override
    protected void requestCapabilities(List<Uri> requestCapUris) {
        SubscribeController subscribeController = mSubscribeController;
        if (subscribeController == null) {
            logd("requestCapabilities: request is finished");
            mRequestResponse.setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            handleRequestFailed(true);
            return;
        }

        logd("requestCapabilities: " + requestCapUris.size());
        try {
            subscribeController.requestCapabilities(requestCapUris, mSubscribeResponseCallback);
        } catch (RemoteException e) {
            logd("requestCapabilities exception: " + e);
            mRequestResponse.setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            handleRequestFailed(true);
        }
    }

    // Handle the command error triggered by ISubscribeResponseCallback
    private void handleCommandError(int errorCode) {
        logd("handleCommandError: " + errorCode);
        // Set the error code and notify RequestManager that this request is failed.
        mRequestResponse.setErrorCode(
                CapabilityRequestResponse.convertImsServiceCommandErrorToCapabilityError(
                        errorCode));
        mRequestManagerCallback.onRequestFailed(mTaskId);
    }

    // Handle the NetworkResponse callback triggered by ISubscribeResponseCallback
    private void handleNetworkResponse(int sipCode, String reason) {
        logd("handleNetworkResponse: " + sipCode + ", reason=" + reason);
        // If the SIP code is not success, set the error code and notify RequestManager. Otherwise,
        // waiting the onNotifyCapabilitiesUpdate callback.
        if (sipCode != NetworkSipCode.SIP_CODE_OK) {
            mRequestResponse.setErrorCode(
                    CapabilityRequestResponse.convertSipCodeErrorToCapabilityError(
                            sipCode, reason));
            mRequestManagerCallback.onRequestFailed(mTaskId);
        }
    }

    // Handle the CapabilitiesUpdate callback triggered by ISubscribeResponseCallback
    private void handleCapabilitiesUpdate(List<String> pidfXml) {
        if (pidfXml == null || pidfXml.isEmpty()) {
            logd("handleCapabilitiesUpdate: parameter is empty");
            return;
        }

        List<RcsContactUceCapability> capabilityList = pidfXml.stream()
                .map(pidf -> PidfParser.convertToRcsContactUceCapability(pidf))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        logd("handleCapabilitiesUpdate: PIDF size=" + pidfXml.size()
                + ", contact capability size=" + capabilityList.size());

        // Add these updated RcsContactUceCapability into RequestResponse and notify RequestManager
        // to process the RcsContactUceCapabilities update.
        mRequestResponse.addCapabilitiesUpdate(capabilityList);
        mRequestManagerCallback.onCapabilityUpdate(mTaskId);
    }

    // Handle the terminated callback triggered by ISubscribeResponseCallback
    private void handleTerminated(String reason, String retryAfter) {
        logd("handleTerminated: reason=" + reason + ", retryAfter=" + retryAfter);
        mRequestManagerCallback.onRequestSuccess(mTaskId);
    }
}
