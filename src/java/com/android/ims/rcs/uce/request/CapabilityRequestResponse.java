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
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.IRcsUceControllerCallback;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase;
import android.text.TextUtils;
import android.util.Log;

import com.android.ims.rcs.uce.util.NetworkSipCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CapabilityRequestResponse {
    private static final String LOG_TAG = "CapabilityRequestResponse";

    // The error code of this request
    private @RcsUceAdapter.ErrorCode int mErrorCode;

    // The list of the updated contact capabilities.
    private final List<RcsContactUceCapability> mCapabilityUpdatedList;

    // The callback to notify the result of this request.
    public IRcsUceControllerCallback mCapabilitiesCallback;

    private final Object mLock = new Object();

    public CapabilityRequestResponse() {
        mCapabilityUpdatedList = new ArrayList<>();
    }

    /**
     * Set the callback to receive the contacts capabilities update.
     */
    public void setCapabilitiesCallback(IRcsUceControllerCallback c) {
        mCapabilitiesCallback = c;
    }

    /**
     * Set the error code if the request is failed.
     */
    public void setErrorCode(@RcsUceAdapter.ErrorCode int errorCode) {
        mErrorCode = errorCode;
    }

    /**
     * Get the error code.
     */
    public int getErrorCode() {
        return mErrorCode;
    }

    /**
     * Trigger the capabilities callback. This capabilities is retrieved from the cache.
     */
    public boolean triggerCachedCapabilitiesCallback(
            List<RcsContactUceCapability> cachedCapabilities) {
        synchronized (mLock) {
            try {
                mCapabilitiesCallback.onCapabilitiesReceived(
                        Collections.unmodifiableList(cachedCapabilities));
            } catch (RemoteException e) {
                Log.w(LOG_TAG, "triggerCachedCapabilitiesCallback exception: " + e);
                setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
                return false;
            }
        }
        return true;
    }

    /**
     * Add the updated contact capabilities which received from ImsService.
     */
    public void addCapabilitiesUpdate(List<RcsContactUceCapability> capabilityList) {
        synchronized (mLock) {
            mCapabilityUpdatedList.addAll(capabilityList);
        }
    }

    public List<RcsContactUceCapability> getUpdatedContactCapability() {
        synchronized (mLock) {
            return Collections.unmodifiableList(mCapabilityUpdatedList);
        }
    }

    /**
     * Trigger the capabilities updated callback. It is called in the handler thread since it may
     * take times.
     */
    public boolean triggerCapabilitiesCallback(List<RcsContactUceCapability> capabilityList) {
        synchronized (mLock) {
            try {
                mCapabilitiesCallback.onCapabilitiesReceived(capabilityList);
            } catch (RemoteException e) {
                Log.w(LOG_TAG, "triggerCapabilitiesCallback exception: " + e);
                setErrorCode(RcsUceAdapter.ERROR_GENERIC_FAILURE);
                return false;
            } finally {
                // Remove those elements that have executed the callback onCapabilitiesReceived.
                mCapabilityUpdatedList.removeAll(capabilityList);
            }
        }
        return true;
    }

    /**
     * Trigger the onError callback to notify the request is failed.
     */
    public void triggerErrorCallback() {
        synchronized (mLock) {
            try {
                mCapabilitiesCallback.onError(mErrorCode);
            } catch (RemoteException e) {
                Log.w(LOG_TAG, "triggerErrorCallback exception: " + e);
            }
        }
    }

    /**
     * Trigger the onComplete callback to notify the request is success.
     */
    public void triggerCompletedCallback() {
        synchronized (mLock) {
            try {
                mCapabilitiesCallback.onComplete();
            } catch (RemoteException e) {
                Log.w(LOG_TAG, "triggerCompletedCallback exception: " + e);
            }
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

    public static int convertSipCodeErrorToCapabilityError(int sipCode, String reason) {
        if (sipCode == NetworkSipCode.SIP_CODE_FORBIDDEN) {
            if (reason == null || TextUtils.isEmpty(reason)) {
                return RcsUceAdapter.ERROR_FORBIDDEN;
            } else if (NetworkSipCode.SIP_NOT_REGISTERED.equalsIgnoreCase(reason)) {
                return RcsUceAdapter.ERROR_NOT_REGISTERED;
            } else if (NetworkSipCode.SIP_NOT_AUTHORIZED_FOR_PRESENCE.equalsIgnoreCase(reason)) {
                return RcsUceAdapter.ERROR_NOT_AUTHORIZED;
            }
        }
        return RcsUceAdapter.ERROR_GENERIC_FAILURE;
    }
}
