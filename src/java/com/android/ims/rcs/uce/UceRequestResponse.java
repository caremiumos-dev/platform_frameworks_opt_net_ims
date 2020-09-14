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

import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;

import java.util.List;

/**
 * The response class of the UCE request.
 */
public class UceRequestResponse {

    public static final int SIP_CODE_OK = 200;
    public static final int SIP_CODE_REQUEST_TIMEOUT = 408;
    public static final int SIP_CODE_INTERVAL_TOO_BRIEF = 423;
    public static final int SIP_CODE_TEMPORARILY_UNAVAILABLE = 480;
    public static final int SIP_CODE_BUSY_HERE = 486;
    public static final int SIP_CODE_SERVER_INTERVAL_ERROR = 500;
    public static final int SIP_CODE_SERVICE_UNAVAILABLE = 503;
    public static final String SIP_SERVICE_UNAVAILABLE = "Service Unavailable";

    public static final int SIP_CODE_SERVER_TIMEOUT = 504;
    public static final int SIP_CODE_BUSY_EVERYWHERE = 600;
    public static final int SIP_CODE_DECLINE = 603;

    public static final int SIP_CODE_FORBIDDEN = 403;
    public static final String REASON_NOT_REGISTERED = "User not registered";
    public static final String REASON_NOT_AUTHORIZED_FOR_PRESENCE = "not authorized for presence";

    enum RequestError {
        TASK_RELEASED,
        DEVICE_CAPABILITY_EMPTY
    }

    private RequestError mRequestError;

    private Integer mCommandErrorCode;
    private Integer mNetworkResponseCode;
    private String mNetworkResponseReason;
    private List<String> mRemoteCaps;

    public UceRequestResponse() {
    }

    public void setRequestError(RequestError requestError) {
        mRequestError = requestError;
    }

    /**
     * Get the capability error.
     */
    public int getCapabilityErrorFromRequestError() {
        return RcsUceAdapter.ERROR_GENERIC_FAILURE;
    }

    public void setCommandError(int code) {
        mCommandErrorCode = code;
    }

    public int getCommandError() {
        return (mCommandErrorCode == null) ? -1 : mCommandErrorCode;
    }

    /**
     * Set the network response result.
     */
    public void setNetworkResponse(int code, String reason, List<String> remoteCaps) {
        mNetworkResponseCode = code;
        mNetworkResponseReason = reason;
        mRemoteCaps = remoteCaps;
    }

    /**
     * Get the network response result code.
     */
    public int getNetworkResponseCode() {
        return (mNetworkResponseCode == null) ? -1 : mNetworkResponseCode;
    }

    /**
     * Get the reason of the network response.
     */
    public String getNetworkResponseReason() {
        return mNetworkResponseReason;
    }

    /**
     * Convert from the List<String> to RcsContactUceCapability
     */
    public List<RcsContactUceCapability> getRemoteCaps() {
        // TODO: convert from mRemoteCaps to RcsContactUceCapability
        return null;
    }

    /**
     * This method convert from the command error code which are defined in the
     * RcsCapabilityExchangeImplBase to the Capabilities error code which are defined in the
     * RcsUceAdapter.
     */
    public int convertCmdErrorToCapabilityError() {
        if (mCommandErrorCode == null) {
            return RcsUceAdapter.ERROR_GENERIC_FAILURE;
        }

        int uceError = RcsUceAdapter.ERROR_GENERIC_FAILURE;
        switch (mCommandErrorCode) {
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_SERVICE_UNKNOWN:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_GENERIC_FAILURE:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_INVALID_PARAM:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_FETCH_ERROR:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_NOT_SUPPORTED:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_NOT_FOUND:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_SERVICE_UNAVAILABLE:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_NO_CHANGE:
                uceError = RcsUceAdapter.ERROR_GENERIC_FAILURE;
                break;
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_REQUEST_TIMEOUT:
                uceError = RcsUceAdapter.ERROR_REQUEST_TIMEOUT;
                break;
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_INSUFFICIENT_MEMORY:
                uceError = RcsUceAdapter.ERROR_INSUFFICIENT_MEMORY;
                break;
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_LOST_NETWORK_CONNECTION:
                uceError = RcsUceAdapter.ERROR_LOST_NETWORK;
                break;
            default:
                uceError = RcsUceAdapter.ERROR_GENERIC_FAILURE;
                break;
        }
        return uceError;
    }

    public boolean isSipCodeNeedRetry(int sipCode) {
        switch (sipCode) {
            case SIP_CODE_REQUEST_TIMEOUT:
            case SIP_CODE_INTERVAL_TOO_BRIEF:
            case SIP_CODE_TEMPORARILY_UNAVAILABLE:
            case SIP_CODE_BUSY_HERE:
            case SIP_CODE_SERVER_INTERVAL_ERROR:
            case SIP_CODE_SERVICE_UNAVAILABLE:
            case SIP_CODE_SERVER_TIMEOUT:
            case SIP_CODE_BUSY_EVERYWHERE:
            case SIP_CODE_DECLINE:
                return true;
        }
        return false;
    }

    public int convertSipCodeErrorToCapabilityError() {
        if (mNetworkResponseCode == null) {
            return RcsUceAdapter.ERROR_GENERIC_FAILURE;
        }

        if (mNetworkResponseCode == SIP_CODE_FORBIDDEN) {
            final String reason = mNetworkResponseReason;
            if (reason == null || reason.isEmpty()) {
                return RcsUceAdapter.ERROR_FORBIDDEN;
            } else if (REASON_NOT_REGISTERED.equalsIgnoreCase(reason)) {
                return RcsUceAdapter.ERROR_NOT_REGISTERED;
            } else if (REASON_NOT_AUTHORIZED_FOR_PRESENCE.equalsIgnoreCase(reason)) {
                return RcsUceAdapter.ERROR_NOT_AUTHORIZED;
            }
        }

        return RcsUceAdapter.ERROR_GENERIC_FAILURE;
    }
}
