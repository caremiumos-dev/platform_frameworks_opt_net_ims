/*
 * Copyright (c) 2021 The Android Open Source Project
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

import static android.telephony.ims.stub.RcsCapabilityExchangeImplBase.COMMAND_CODE_GENERIC_FAILURE;
import static com.android.ims.rcs.uce.util.NetworkSipCode.SIP_CODE_SERVER_INTERNAL_ERROR;

import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;

import com.android.ims.rcs.uce.request.CapabilityRequestResponse.ResponseRecord;
import com.android.ims.rcs.uce.request.RemoteOptionsRequest.RemoteOptResponse;
import com.android.ims.rcs.uce.request.UceRequestCoordinator.RequestResult;
import com.android.ims.rcs.uce.request.UceRequestCoordinator.RequestResultCreator;

/**
 * The factory provides all of the RequestResult creator. When the UceRequestCoordinator receives
 * the request updated and wants to finished the request, it needs to get the corresponding creator
 * to create the RequestResult instance.
 */
public class RequestResultCreatorFactory {

    // The default capability error code.
    protected static final int DEFAULT_ERROR_CODE = RcsUceAdapter.ERROR_GENERIC_FAILURE;

    private static final RequestResultCreator sCreatorForSuccess = (taskId, response) -> {
        return RequestResult.createSuccessResult(taskId);
    };

    private static final RequestResultCreator sCreatorForRequestError = (taskId, response) -> {
        if (!(response instanceof CapabilityRequestResponse)) {
            return RequestResult.createFailedResult(taskId, DEFAULT_ERROR_CODE, 0L);
        }
        CapabilityRequestResponse capRequestResponse = (CapabilityRequestResponse) response;
        ResponseRecord record = capRequestResponse.getResponseRecord();
        int errorCode = record.getRequestInternalError().orElse(DEFAULT_ERROR_CODE);
        long retryAfter = record.getRetryAfterMillis();
        return RequestResult.createFailedResult(taskId, errorCode, retryAfter);
    };

    private static final RequestResultCreator sCreatorForCommandError = (taskId, response) -> {
        if (!(response instanceof CapabilityRequestResponse)) {
            return RequestResult.createFailedResult(taskId, DEFAULT_ERROR_CODE, 0L);
        }
        CapabilityRequestResponse capRequestResponse = (CapabilityRequestResponse) response;
        ResponseRecord record = capRequestResponse.getResponseRecord();
        int cmdError = record.getCommandError().orElse(COMMAND_CODE_GENERIC_FAILURE);
        int errorCode = CapabilityRequestResponse.getCapabilityErrorFromCommandError(cmdError);
        long retryAfter = record.getRetryAfterMillis();
        return RequestResult.createFailedResult(taskId, errorCode, retryAfter);
    };

    private static final RequestResultCreator sSubscribeNetworkRespErrorCreator =
            (taskId, response) -> {
        if (!(response instanceof CapabilityRequestResponse)) {
            return RequestResult.createFailedResult(taskId, DEFAULT_ERROR_CODE, 0L);
        }
        CapabilityRequestResponse capRequestResponse = (CapabilityRequestResponse) response;
        ResponseRecord record = capRequestResponse.getResponseRecord();
        int errorCode = CapabilityRequestResponse.getCapabilityErrorFromSipCode(record);
        long retryAfter = record.getRetryAfterMillis();
        return RequestResult.createFailedResult(taskId, errorCode, retryAfter);
    };

    private static final RequestResultCreator sOptionsNetworkRespCreator = (taskId, response) -> {
        if (!(response instanceof CapabilityRequestResponse)) {
            return RequestResult.createFailedResult(taskId, DEFAULT_ERROR_CODE, 0L);
        }
        CapabilityRequestResponse capRequestResponse = (CapabilityRequestResponse) response;
        if (capRequestResponse.isNetworkResponseOK()) {
            return RequestResult.createSuccessResult(taskId);
        } else {
            ResponseRecord record = capRequestResponse.getResponseRecord();
            int errorCode = CapabilityRequestResponse.getCapabilityErrorFromSipCode(record);
            long retryAfter = record.getRetryAfterMillis();
            return RequestResult.createFailedResult(taskId, errorCode, retryAfter);
        }
    };

    private static final RequestResultCreator sCreatorForRequestTerminated = (taskId, response) -> {
        if (!(response instanceof CapabilityRequestResponse)) {
            return RequestResult.createFailedResult(taskId, DEFAULT_ERROR_CODE, 0L);
        }
        CapabilityRequestResponse capRequestResponse = (CapabilityRequestResponse) response;
        ResponseRecord record = capRequestResponse.getResponseRecord();
        long retryAfterMillis = capRequestResponse.getRetryAfterMillis();
        int errorCode = CapabilityRequestResponse.getCapabilityErrorFromSipCode(record);
        // If the network response is failed or the retryAfter is not zero, this request is failed.
        if (!capRequestResponse.isNetworkResponseOK() || retryAfterMillis > 0L) {
            return RequestResult.createFailedResult(taskId, errorCode, retryAfterMillis);
        } else {
            return RequestResult.createSuccessResult(taskId);
        }
    };

    private static final RequestResultCreator sRemoteOptResponseCreator = (taskId, response) -> {
        if (!(response instanceof RemoteOptResponse)) {
            return RequestResult.createFailedResult(taskId, DEFAULT_ERROR_CODE, 0L);
        }
        RemoteOptResponse remoteOptResponse = (RemoteOptResponse) response;
        RcsContactUceCapability capability = remoteOptResponse.getRcsContactCapability();
        if (capability != null) {
            return RequestResult.createSuccessResult(taskId);
        } else {
            int errorCode = remoteOptResponse.getErrorSipCode().orElse(
                    SIP_CODE_SERVER_INTERNAL_ERROR);
            return RequestResult.createFailedResult(taskId, errorCode, 0L);
        }
    };

    public static RequestResultCreator getSuccessfulRequestResultCreator() {
        return sCreatorForSuccess;
    }

    public static RequestResultCreator getRequestErrorCreator() {
        return sCreatorForRequestError;
    }

    public static RequestResultCreator getCommandErrorCreator() {
        return sCreatorForCommandError;
    }

    public static RequestResultCreator getSubscribeNetworkRespErrorCreator() {
        return sSubscribeNetworkRespErrorCreator;
    }

    public static RequestResultCreator getsOptionsNetworkRespCreator() {
        return sOptionsNetworkRespCreator;
    }

    public static RequestResultCreator getRequestTerminatedCreator() {
        return sCreatorForRequestTerminated;
    }

    public static RequestResultCreator getRemoteOptRequestResultCreator() {
        return sRemoteOptResponseCreator;
    }
}
