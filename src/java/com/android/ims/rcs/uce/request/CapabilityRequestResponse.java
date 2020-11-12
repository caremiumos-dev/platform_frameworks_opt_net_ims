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

package com.android.ims.rcs.uce.request;

import android.os.RemoteException;
import android.telephony.ims.RcsContactTerminatedReason;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.IRcsUceControllerCallback;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase;
import android.text.TextUtils;
import android.util.Log;

import com.android.ims.rcs.uce.presence.pidfparser.PidfParserUtils;
import com.android.ims.rcs.uce.util.NetworkSipCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handle the result of of the capability request.
 */
public class CapabilityRequestResponse {

    private static final String LOG_TAG = "CapabilityRequestResponse";

    // The SIP code of the network response.
    private int mNetworkResponseCode;

    // The reason of the network response.
    private String mNetworkResponseReason;

    // The reason why the this request was terminated.
    private String mTerminatedReason;

    // How long after this request can be retried.
    private long mRetryAfterMilliseconds = 0L;

    // The error code of this request.
    private @RcsUceAdapter.ErrorCode Integer mErrorCode;

    // The list of the updated capabilities from the network.
    private final List<RcsContactUceCapability> mUpdatedCapabilityList;

    // The list of the terminated resource
    private final List<RcsContactUceCapability> mTerminatedResource;

    // The callback to notify the result of this request.
    public IRcsUceControllerCallback mCapabilitiesCallback;

    private final Object mCallbackLock = new Object();
    private final Object mCapabilityListLock = new Object();

    public CapabilityRequestResponse() {
        mTerminatedResource = new ArrayList<>();
        mUpdatedCapabilityList = new ArrayList<>();
    }

    /**
     * Set the callback to receive the contacts capabilities update.
     */
    public void setCapabilitiesCallback(IRcsUceControllerCallback c) {
        mCapabilitiesCallback = c;
    }

    /**
     * Set the error code when the request is failed.
     */
    public void setErrorCode(@RcsUceAdapter.ErrorCode int errorCode) {
        mErrorCode = errorCode;
    }

    /**
     * Get the error code of this request.
     */
    public int getErrorCode() {
        return mErrorCode;
    }

    /**
     * Set the network response of this request which is sent by the network.
     */
    public void setNetworkResponseCode(int sipCode, String reason) {
        mNetworkResponseCode = sipCode;
        mNetworkResponseReason = reason;
    }

    /**
     * Get the sip code of the network response.
     */
    public int getNetworkResponseCode() {
        return mNetworkResponseCode;
    }

    /**
     * Get the reason of the network response.
     */
    public String getNetworkResponseReason() {
        return mNetworkResponseReason;
    }

    /**
     * Check if the network response is success.
     * @return true if the network response code is OK.
     */
    public boolean isNetworkResponseOK() {
        return (mNetworkResponseCode == NetworkSipCode.SIP_CODE_OK) ? true : false;
    }

    /**
     * Set the reason and retry-after info when the callback onTerminated is called.
     * @param reason The reason why this request is terminated.
     * @param retryAfterMilliseconds How long to wait before retry this request.
     */
    public void setRequestTerminated(String reason, long retryAfterMilliseconds) {
        mTerminatedReason = reason;
        mRetryAfterMilliseconds = retryAfterMilliseconds;
    }

    /**
     * Add the updated contact capabilities which sent from ImsService.
     */
    public void addUpdatedCapabilities(List<RcsContactUceCapability> capabilityList) {
        synchronized (mCapabilityListLock) {
            mUpdatedCapabilityList.addAll(capabilityList);
        }
    }

    /**
     * Get all the updated capabilities to trigger the capability receive callback.
     */
    public List<RcsContactUceCapability> getUpdatedContactCapability() {
        synchronized (mCapabilityListLock) {
            return Collections.unmodifiableList(mUpdatedCapabilityList);
        }
    }

    // Remove the given capabilities from the UpdatedCapabilityList when these capabilities have
    // updated to the requester.
    private void removeCapabilities(List<RcsContactUceCapability> capabilityList) {
        synchronized (mCapabilityListLock) {
            mUpdatedCapabilityList.removeAll(capabilityList);
        }
    }

    /**
     * Add the terminated resources which sent from ImsService.
     */
    public void addTerminatedResource(List<RcsContactTerminatedReason> terminatedResource) {
        // Convert the RcsContactTerminatedReason to RcsContactUceCapability
        List<RcsContactUceCapability> capabilityList = terminatedResource.stream()
                .map(reason -> PidfParserUtils.getTerminatedCapability(
                        reason.getContactUri(), reason.getReason())).collect(Collectors.toList());

        synchronized (mCapabilityListLock) {
            mTerminatedResource.addAll(capabilityList);
        }
    }

    /**
     * Get the terminated resources which sent from ImsService.
     */
    public List<RcsContactUceCapability> getTerminatedResources() {
        synchronized (mCapabilityListLock) {
            return Collections.unmodifiableList(mTerminatedResource);
        }
    }

    // Remove the given capabilities from the mTerminatedResource when these capabilities have
    // updated to the requester.
    private void removeTerminatedResources(List<RcsContactUceCapability> terminatedResourceList) {
        synchronized (mCapabilityListLock) {
            mTerminatedResource.removeAll(terminatedResourceList);
        }
    }

    /**
     * Trigger the capabilities callback. This capabilities is retrieved from the cache.
     */
    public boolean triggerCachedCapabilitiesCallback(
            List<RcsContactUceCapability> cachedCapabilities) {
        try {
            synchronized (mCallbackLock) {
                mCapabilitiesCallback.onCapabilitiesReceived(
                        Collections.unmodifiableList(cachedCapabilities));
            }
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "triggerCachedCapabilitiesCallback exception: " + e);
            setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return false;
        }
        return true;
    }

    /**
     * Trigger the capabilities updated callback and remove the given capability from the
     * capability updated list.
     */
    public boolean triggerCapabilitiesCallback(List<RcsContactUceCapability> capabilityList) {
        try {
            synchronized (mCallbackLock) {
                mCapabilitiesCallback.onCapabilitiesReceived(capabilityList);
            }
            // Remove the elements that have executed the callback onCapabilitiesReceived.
            removeCapabilities(capabilityList);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "triggerCapabilitiesCallback exception: " + e);
            setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return false;
        }
        return true;
    }

    /**
     * Trigger the capabilities updated callback and remove the given capability from the resource
     * terminated list.
     */
    public boolean triggerResourceTerminatedCallback(List<RcsContactUceCapability> capList) {
        try {
            synchronized (mCallbackLock) {
                mCapabilitiesCallback.onCapabilitiesReceived(capList);
            }
            // Remove the elements that have executed the callback onCapabilitiesReceived.
            removeTerminatedResources(capList);
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "triggerTerminatedResourceCallback exception: " + e);
            setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return false;
        }
        return true;
    }

    /**
     * Trigger the onComplete callback to notify the request is success.
     */
    public void triggerCompletedCallback() {
        try {
            synchronized (mCallbackLock) {
                mCapabilitiesCallback.onComplete();
            }
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "triggerCompletedCallback exception: " + e);
        }
    }

    /**
     * Trigger the onError callback to notify the request is failed.
     */
    public void triggerErrorCallback() {
        try {
            synchronized (mCallbackLock) {
                mCapabilitiesCallback.onError(mErrorCode, mRetryAfterMilliseconds);
            }
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "triggerErrorCallback exception: " + e);
        }
    }

    /**
     * This method convert from the command error code which are defined in the
     * RcsCapabilityExchangeImplBase to the Capabilities error code which are defined in the
     * RcsUceAdapter.
     */
    public static int convertImsServiceCommandErrorToCapabilityError(int commandError) {
        int uceError;
        switch (commandError) {
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_SERVICE_UNKNOWN:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_GENERIC_FAILURE:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_INVALID_PARAM:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_FETCH_ERROR:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_NOT_SUPPORTED:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_NOT_FOUND:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_SERVICE_UNAVAILABLE:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_NO_CHANGE:
                uceError = RcsUceAdapter.ERROR_GENERIC_FAILURE;
                break;
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_REQUEST_TIMEOUT:
                uceError = RcsUceAdapter.ERROR_REQUEST_TIMEOUT;
                break;
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_INSUFFICIENT_MEMORY:
                uceError = RcsUceAdapter.ERROR_INSUFFICIENT_MEMORY;
                break;
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_LOST_NETWORK_CONNECTION:
                uceError = RcsUceAdapter.ERROR_LOST_NETWORK;
                break;
            default:
                uceError = RcsUceAdapter.ERROR_GENERIC_FAILURE;
                break;
        }
        return uceError;
    }

    public static int convertSipCodeErrorToCapabilityError(int sipCode, String sipReason) {
        if (sipCode == NetworkSipCode.SIP_CODE_FORBIDDEN) {
            if (TextUtils.isEmpty(sipReason)) {
                return RcsUceAdapter.ERROR_FORBIDDEN;
            } else if (NetworkSipCode.SIP_NOT_REGISTERED.equals(sipReason)) {
                return RcsUceAdapter.ERROR_NOT_REGISTERED;
            } else if (NetworkSipCode.SIP_NOT_AUTHORIZED_FOR_PRESENCE.equals(sipReason)) {
                return RcsUceAdapter.ERROR_NOT_AUTHORIZED;
            }
        }
        return RcsUceAdapter.ERROR_GENERIC_FAILURE;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        return builder.append("ErrorCode=").append(mErrorCode)
                .append(", NetworkResponseCode=").append(mNetworkResponseCode)
                .append(", NetworkResponseReason=").append(mNetworkResponseReason)
                .append(", RetryAfter=").append(mRetryAfterMilliseconds)
                .append(", TerminatedReason=").append(mTerminatedReason)
                .toString();
    }
}
