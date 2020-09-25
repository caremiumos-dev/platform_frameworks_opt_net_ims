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

package com.android.ims.rcs.uce.presence.publish;

import android.telephony.ims.ImsException;
import android.telephony.ims.RcsUceAdapter;
import android.util.Log;

import com.android.ims.rcs.uce.RcsCapabilityExchangeImplAdapter;
import com.android.ims.rcs.uce.RcsCapabilityExchangeImplAdapter.PublishResponseCallback;

/**
 * The response class of the PUBLISH request.
 */
public class PublishRequestResponse {

    private static final String LOG_TAG = "PublishRequestResponse";

    private static final int NETWORK_SIP_CODE_SUCCESS = 200;
    private static final int NETWORK_SIP_CODE_REQUEST_TIMEOUT = 408;
    private static final int NETWORK_SIP_CODE_INTERVAL_TOO_BRIEF = 423;
    private static final int NETWORK_SIP_CODE_TEMPORARILY_UNAVAILABLE = 480;
    private static final int NETWORK_SIP_CODE_BUSY = 486;
    private static final int NETWORK_SIP_CODE_SERVER_INTERNAL_ERROR = 500;
    private static final int NETWORK_SIP_CODE_SERVICE_UNAVAILABLE = 503;
    private static final int NETWORK_SIP_CODE_SERVER_TIMEOUT = 504;
    private static final int NETWORK_SIP_CODE_BUSY_EVERYWHERE = 600;
    private static final int NETWORK_SIP_CODE_DECLINE = 603;

    private final long mTaskId;
    private int mCommandErrorCode;
    private int mNetworkResponseSipCode;
    private String mNetworkResponseReason;

    private volatile boolean mNeedRetry;

    public PublishRequestResponse(long taskId) {
        mTaskId = taskId;
        mNeedRetry = false;
    }

    public long getTaskId() {
        return mTaskId;
    }

    public void onCommandErrorCode(int errorCode) {
        mCommandErrorCode = errorCode;
        handleCommandError();
    }

    public void onNetworkResponse(int sipCode, String reason) {
        mNetworkResponseSipCode = sipCode;
        mNetworkResponseReason = reason;
        handleNetworkResponse();
    }


    /**
     * This method will be called when receive the commend error callback.
     */
    public void handleCommandError() {
        switch(mCommandErrorCode) {
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_REQUEST_TIMEOUT:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_INSUFFICIENT_MEMORY:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_LOST_NETWORK_CONNECTION:
            case RcsCapabilityExchangeImplAdapter.COMMAND_CODE_SERVICE_UNAVAILABLE:
                mNeedRetry = true;
                break;
        }
    }

    /**
     * This method will be called when receive the network response of the PUBLISH request.
     */
    public void handleNetworkResponse() {
        switch (mNetworkResponseSipCode) {
            case NETWORK_SIP_CODE_REQUEST_TIMEOUT:
            case NETWORK_SIP_CODE_INTERVAL_TOO_BRIEF:
            case NETWORK_SIP_CODE_TEMPORARILY_UNAVAILABLE:
            case NETWORK_SIP_CODE_BUSY:
            case NETWORK_SIP_CODE_SERVER_INTERNAL_ERROR:
            case NETWORK_SIP_CODE_SERVICE_UNAVAILABLE:
            case NETWORK_SIP_CODE_SERVER_TIMEOUT:
            case NETWORK_SIP_CODE_BUSY_EVERYWHERE:
            case NETWORK_SIP_CODE_DECLINE:
                mNeedRetry = true;
                break;
        }
    }

    /*
     * Check whether the publishing request is successful.
     */
    public boolean isSuccess() {
        return (mNetworkResponseSipCode == NETWORK_SIP_CODE_SUCCESS) ? true : false;
    }

    /**
     * Check whether the publishing request needs to be retried.
     */
    public boolean needRetry() {
        return mNeedRetry;
    }

    /**
     * Convert the command error code to the PUBLISH state
     */
    public int convertCommandErrorCodeToPublishState() {
        if (RcsCapabilityExchangeImplAdapter.COMMAND_CODE_REQUEST_TIMEOUT == mCommandErrorCode) {
            return RcsUceAdapter.PUBLISH_STATE_REQUEST_TIMEOUT;
        }
        return RcsUceAdapter.PUBLISH_STATE_OTHER_ERROR;
    }

    /**
     * Convert the network sip code to the PUBLISH state
     */
    public int convertNetworkResponseToPublishState() {
        if (NETWORK_SIP_CODE_REQUEST_TIMEOUT == mNetworkResponseSipCode) {
            return RcsUceAdapter.PUBLISH_STATE_REQUEST_TIMEOUT;
        }
        return RcsUceAdapter.PUBLISH_STATE_OTHER_ERROR;
    }

    /**
     * Get the information of the publish request response.
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("taskId=").append(mTaskId)
                .append(", onCommandCode=").append(mCommandErrorCode)
                .append(", onNetworkResponse=").append(mNetworkResponseSipCode)
                .append(", mNetworkResponseReason=").append(mNetworkResponseReason)
                .append(", isSuccess=").append(isSuccess())
                .append(", retry=").append(mNeedRetry);
        return builder.toString();
    }
}
