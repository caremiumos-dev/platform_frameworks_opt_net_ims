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

import android.telephony.ims.RcsContactTerminatedReason;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.RcsUceAdapter.ErrorCode;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase.CommandCode;

import com.android.ims.rcs.uce.presence.pidfparser.PidfParserUtils;
import com.android.ims.rcs.uce.util.NetworkSipCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The container of the result of the capabilities request.
 */
public class CapabilityRequestResponse extends UceRequest.RequestResponse {
    /**
     * Record the data of the capability request result.
     */
    public static class ResponseRecord {
        // The error code when the request encounters internal errors. It is empty if there is no
        // internal error.
        private @ErrorCode Optional<Integer> mRequestInternalError;

        // The command error code of the request. It is assigned by the callback "onCommandError"
        private @CommandCode Optional<Integer> mCommandError;

        // The SIP code of the network response. It is assigned by the callback "onNetworkResponse"
        private Optional<Integer> mNetworkRespSipCode;

        // The reason of the network response. It is assigned by the callback "onNetworkResponse"
        private Optional<String> mReasonPhrase;

        // The response sip code from the reason header
        private Optional<Integer> mReasonHeaderCause;

        // The phrase from the reason header
        private Optional<String> mReasonHeaderText;

        // The reason why the this request was terminated. This value is assigned by the callback
        // "onTerminated"
        private Optional<String> mTerminatedReason;

        // How long after this request can be retried. This value is assigned by the callback
        // "onTerminated"
        private Optional<Long> mRetryAfterMillis;

        // The list of the valid capabilities which is retrieved from the cache.
        private final List<RcsContactUceCapability> mCachedCapabilityList;

        // The list of the updated capabilities. This is assigned by the callback
        // "onNotifyCapabilitiesUpdate"
        private final List<RcsContactUceCapability> mUpdatedCapabilityList;

        // The list of the terminated resource. This is assigned by the callback
        // "onResourceTerminated"
        private final List<RcsContactUceCapability> mTerminatedResource;

        // The list of the remote contact's capability.
        private final Set<String> mRemoteCaps;

        public ResponseRecord() {
            mRequestInternalError = Optional.empty();
            mCommandError = Optional.empty();
            mNetworkRespSipCode = Optional.empty();
            mReasonPhrase = Optional.empty();
            mReasonHeaderCause = Optional.empty();
            mReasonHeaderText = Optional.empty();
            mTerminatedReason = Optional.empty();
            mRetryAfterMillis = Optional.of(0L);
            mTerminatedResource = new ArrayList<>();
            mCachedCapabilityList = new ArrayList<>();
            mUpdatedCapabilityList = new ArrayList<>();
            mRemoteCaps = new HashSet<>();
        }

        // Set the error code when the request encounters internal unexpected errors.
        public synchronized void setRequestInternalError(@ErrorCode int errorCode) {
            mRequestInternalError = Optional.of(errorCode);
        }

        // Get the request internal error code.
        public synchronized Optional<Integer> getRequestInternalError() {
            return mRequestInternalError;
        }

        // Set the command error which is sent from ImsService and set the capability error code.
        public synchronized void setCommandError(@CommandCode int commandError) {
            mCommandError = Optional.of(commandError);
        }

        public synchronized Optional<Integer> getCommandError() {
            return mCommandError;
        }

        // Set the network response of this request which is sent by the network.
        public synchronized void setNetworkResponseCode(int sipCode, String reason) {
            mNetworkRespSipCode = Optional.of(sipCode);
            mReasonPhrase = Optional.ofNullable(reason);
        }

        //Set the network response of this request which is sent by the network.
        public synchronized void setNetworkResponseCode(int sipCode, String reasonPhrase,
                int reasonHeaderCause, String reasonHeaderText) {
            mNetworkRespSipCode = Optional.of(sipCode);
            mReasonPhrase = Optional.ofNullable(reasonPhrase);
            mReasonHeaderCause = Optional.of(reasonHeaderCause);
            mReasonHeaderText = Optional.ofNullable(reasonHeaderText);
        }

        // Get the sip code of the network response.
        public synchronized Optional<Integer> getNetworkRespSipCode() {
            return mNetworkRespSipCode;
        }

        // Get the reason of the network response.
        public synchronized Optional<String> getReasonPhrase() {
            return mReasonPhrase;
        }

        // Get the response sip code from the reason header.
        public synchronized Optional<Integer> getReasonHeaderCause() {
            return mReasonHeaderCause;
        }

        // Get the response phrae from the reason header.
        public synchronized Optional<String> getReasonHeaderText() {
            return mReasonHeaderText;
        }

        // Add the capabilities which are retrieved from the cache.
        public synchronized void addCachedCapabilities(List<RcsContactUceCapability> capList) {
            mCachedCapabilityList.addAll(capList);
        }

        // Clear the cached capabilities when the cached capabilities have been sent to client.
        public synchronized void removeCachedContactCapabilities() {
            mCachedCapabilityList.clear();
        }

        // Return the cached capabilities.
        public synchronized List<RcsContactUceCapability> getCachedContactCapability() {
            return Collections.unmodifiableList(mCachedCapabilityList);
        }

        // Add the updated contact capabilities which sent from ImsService.
        public synchronized void addUpdatedCapabilities(List<RcsContactUceCapability> capList) {
            mUpdatedCapabilityList.addAll(capList);
        }

        // Remove the given capabilities from the UpdatedCapabilityList.
        public synchronized void removeUpdatedCapabilities(List<RcsContactUceCapability> capList) {
            mUpdatedCapabilityList.removeAll(capList);
        }

        // Get all the updated capabilities to trigger the capability receive callback.
        public synchronized List<RcsContactUceCapability> getUpdatedContactCapability() {
            return Collections.unmodifiableList(mUpdatedCapabilityList);
        }

        // Add the terminated resources which sent from ImsService.
        public synchronized void addTerminatedResource(List<RcsContactUceCapability> resourceList) {
            mTerminatedResource.addAll(resourceList);
        }

         // Remove the given capabilities from the mTerminatedResource.
        public synchronized void removeTerminatedResources(List<RcsContactUceCapability> list) {
            mTerminatedResource.removeAll(list);
        }

        // Get the terminated resources which sent from ImsService.
        public synchronized List<RcsContactUceCapability> getTerminatedResources() {
            return Collections.unmodifiableList(mTerminatedResource);
        }

        // Set the reason and retry-after info when the callback onTerminated is called.
        public synchronized void setTerminated(String reason, long retryAfterMillis) {
            mTerminatedReason = Optional.ofNullable(reason);
            mRetryAfterMillis = Optional.of(retryAfterMillis);
        }

        // Return the terminated reason, empty string if the value is not present.
        public synchronized String getTerminatedReason() {
            return mTerminatedReason.orElse("");
        }

        // Return the retryAfterMillis, 0L if the value is not present.
        public synchronized long getRetryAfterMillis() {
            return mRetryAfterMillis.orElse(0L);
        }

        public synchronized void addRemoteCapability(String capability) {
            mRemoteCaps.add(capability);
        }

        public synchronized Set<String> getRemoteCapability() {
            return Collections.unmodifiableSet(mRemoteCaps);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            return builder.append("RequestInternalError=").append(mRequestInternalError.orElse(-1))
                    .append(", CommandErrorCode=").append(mCommandError.orElse(-1))
                    .append(", NetworkResponseCode=").append(mNetworkRespSipCode.orElse(-1))
                    .append(", NetworkResponseReason=").append(mReasonPhrase.orElse(""))
                    .append(", ReasonHeaderCause=").append(mReasonHeaderCause.orElse(-1))
                    .append(", ReasonHeaderText=").append(mReasonHeaderText.orElse(""))
                    .append(", TerminatedReason=").append(mTerminatedReason.orElse(""))
                    .append(", RetryAfterMillis=").append(mRetryAfterMillis.orElse(0L))
                    .append(", Terminated resource size=" + mTerminatedResource.size())
                    .append(", cached capability size=" + mCachedCapabilityList.size())
                    .append(", Updated capability size=" + mUpdatedCapabilityList.size())
                    .append(", RemoteCaps size=" + mRemoteCaps.size())
                    .toString();
        }
    }

    // The record of the capability request result.
    private final ResponseRecord mResponseRecord;

    public CapabilityRequestResponse() {
        mResponseRecord = new ResponseRecord();
    }

    // TODO: copy
    public ResponseRecord getResponseRecord() {
        return mResponseRecord;
    }

    /**
     * Set the error code when the request encounters internal unexpected errors.
     * @param errorCode the error code of the internal request error.
     */
    public synchronized void setRequestInternalError(@ErrorCode int errorCode) {
        mResponseRecord.setRequestInternalError(errorCode);
    }

    /**
     * Get the request internal error code.
     */
    public synchronized Optional<Integer> getInternalErrorCode() {
        return mResponseRecord.getRequestInternalError();
    }

    /**
     * Set the command error code which is sent from ImsService and set the capability error code.
     */
    public void setCommandError(@CommandCode int commandError) {
        mResponseRecord.setCommandError(commandError);
    }

    /**
     * Set the network response of this request which is sent by the network.
     */
    public void setNetworkResponseCode(int sipCode, String reason) {
        mResponseRecord.setNetworkResponseCode(sipCode, reason);
    }

    /**
     * Set the network response of this request which is sent by the network.
     */
    public void setNetworkResponseCode(int sipCode, String reasonPhrase,
            int reasonHeaderCause, String reasonHeaderText) {
        mResponseRecord.setNetworkResponseCode(sipCode, reasonPhrase, reasonHeaderCause,
                reasonHeaderText);
    }

    /**
     * Check if the network response is success.
     * @return true if the network response code is OK or Accepted and the Reason header cause
     * is either not present or OK.
     */
    public boolean isNetworkResponseOK() {
        final int sipCodeOk = NetworkSipCode.SIP_CODE_OK;
        final int sipCodeAccepted = NetworkSipCode.SIP_CODE_ACCEPTED;
        Optional<Integer> respSipCode = mResponseRecord.getNetworkRespSipCode();
        if (respSipCode.filter(c -> (c == sipCodeOk || c == sipCodeAccepted)).isPresent() &&
                (!mResponseRecord.getReasonHeaderCause().isPresent()
                        || mResponseRecord.getReasonHeaderCause().filter(c -> c == sipCodeOk)
                                .isPresent())) {
            return true;
        }
        return false;
    }

    /**
     * Check whether the request is forbidden or not.
     * @return true if the Reason header sip code is 403(Forbidden) or the response sip code is 403.
     */
    public boolean isRequestForbidden() {
        if (mResponseRecord.getReasonHeaderCause().isPresent()) {
            return mResponseRecord.getReasonHeaderCause().filter(c ->
                    c == NetworkSipCode.SIP_CODE_FORBIDDEN).isPresent();
        } else {
            return mResponseRecord.getNetworkRespSipCode().filter(c ->
                    c == NetworkSipCode.SIP_CODE_FORBIDDEN).isPresent();
        }
    }

    public boolean isNotFound() {
        final int notFound = NetworkSipCode.SIP_CODE_NOT_FOUND;
        final int notExistAnywhere = NetworkSipCode.SIP_CODE_DOES_NOT_EXIST_ANYWHERE;
        Optional<Integer> reasonHeaderCause = mResponseRecord.getReasonHeaderCause();
        Optional<Integer> respSipCode = mResponseRecord.getNetworkRespSipCode();
        if (reasonHeaderCause.filter(c -> c == notFound || c == notExistAnywhere).isPresent() ||
                respSipCode.filter(c -> c == notFound || c == notExistAnywhere).isPresent()) {
            return true;
        }
        return false;
    }

    /**
     * Add the capabilities which are retrieved from the cache.
     */
    public void addCachedCapabilities(List<RcsContactUceCapability> capabilityList) {
        mResponseRecord.addCachedCapabilities(capabilityList);
    }

    /**
     * Clear the cached capabilities when the cached capabilities have been sent to client.
     */
    public void removeCachedContactCapabilities() {
        mResponseRecord.removeCachedContactCapabilities();
    }

    /**
     * @return the cached capabilities.
     */
    public List<RcsContactUceCapability> getCachedContactCapability() {
        return mResponseRecord.getCachedContactCapability();
    }

    /**
     * Add the updated contact capabilities which sent from ImsService.
     */
    public void addUpdatedCapabilities(List<RcsContactUceCapability> capabilityList) {
        mResponseRecord.addUpdatedCapabilities(capabilityList);
    }

    /**
     * Remove the given capabilities from the UpdatedCapabilityList when these capabilities have
     * updated to the requester.
     */
    public void removeUpdatedCapabilities(List<RcsContactUceCapability> capabilityList) {
        mResponseRecord.removeUpdatedCapabilities(capabilityList);
    }

    /**
     * Get all the updated capabilities to trigger the capability receive callback.
     */
    public List<RcsContactUceCapability> getUpdatedContactCapability() {
        return mResponseRecord.getUpdatedContactCapability();
    }

    /**
     * Add the terminated resources which sent from ImsService.
     */
    public void addTerminatedResource(List<RcsContactTerminatedReason> terminatedResource) {
        // Convert the RcsContactTerminatedReason to RcsContactUceCapability
        List<RcsContactUceCapability> capabilityList = terminatedResource.stream()
                .filter(Objects::nonNull)
                .map(reason -> PidfParserUtils.getTerminatedCapability(
                        reason.getContactUri(), reason.getReason())).collect(Collectors.toList());

        // Save the terminated resource.
        mResponseRecord.addTerminatedResource(capabilityList);
    }

    /*
     * Remove the given capabilities from the mTerminatedResource when these capabilities have
     * updated to the requester.
     */
    public void removeTerminatedResources(List<RcsContactUceCapability> terminatedResourceList) {
        mResponseRecord.removeTerminatedResources(terminatedResourceList);
    }

    /**
     * Get the terminated resources which sent from ImsService.
     */
    public List<RcsContactUceCapability> getTerminatedResources() {
        return mResponseRecord.getTerminatedResources();
    }

    /**
     * Set the reason and retry-after info when the callback onTerminated is called.
     * @param reason The reason why this request is terminated.
     * @param retryAfterMillis How long to wait before retry this request.
     */
    public void setTerminated(String reason, long retryAfterMillis) {
        mResponseRecord.setTerminated(reason, retryAfterMillis);
    }

    /**
     * @return Return the retryAfterMillis, 0L if the value is not present.
     */
    public long getRetryAfterMillis() {
        return mResponseRecord.getRetryAfterMillis();
    }

    /**
     * Set the remote's capabilities which are sent from the network.
     */
    public void setRemoteCapabilities(Set<String> remoteCaps) {
        // Set the remote capabilities
        if (remoteCaps != null) {
            remoteCaps.stream().filter(Objects::nonNull).forEach(cap ->
                    mResponseRecord.addRemoteCapability(cap));
        }
    }

    /**
     * This method convert from the command error code which are defined in the
     * RcsCapabilityExchangeImplBase to the Capabilities error code which are defined in the
     * RcsUceAdapter.
     */
    public static int getCapabilityErrorFromCommandError(@CommandCode int cmdError) {
        int uceError;
        switch (cmdError) {
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_SERVICE_UNKNOWN:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_GENERIC_FAILURE:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_INVALID_PARAM:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_FETCH_ERROR:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_NOT_SUPPORTED:
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_NO_CHANGE:
                uceError = RcsUceAdapter.ERROR_GENERIC_FAILURE;
                break;
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_NOT_FOUND:
                uceError = RcsUceAdapter.ERROR_NOT_FOUND;
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
            case RcsCapabilityExchangeImplBase.COMMAND_CODE_SERVICE_UNAVAILABLE:
                uceError = RcsUceAdapter.ERROR_SERVER_UNAVAILABLE;
                break;
            default:
                uceError = RcsUceAdapter.ERROR_GENERIC_FAILURE;
                break;
        }
        return uceError;
    }

    /**
     * Convert the SIP error code which sent by ImsService to the capability error code.
     */
    public static int getCapabilityErrorFromSipCode(ResponseRecord responseRecord) {
        int sipError;
        String respReason;
        // Check the sip code in the Reason header first if the Reason Header is present.
        if (responseRecord.getReasonHeaderCause().isPresent()) {
            sipError = responseRecord.getReasonHeaderCause().get();
            respReason = responseRecord.getReasonHeaderText().orElse("");
        } else {
            sipError = responseRecord.getNetworkRespSipCode().orElse(-1);
            respReason = responseRecord.getReasonPhrase().orElse("");
        }
        int uceError;
        switch (sipError) {
            case NetworkSipCode.SIP_CODE_FORBIDDEN:   // 403
                if (NetworkSipCode.SIP_NOT_REGISTERED.equalsIgnoreCase(respReason)) {
                    // Not registered with IMS. Device shall register to IMS.
                    uceError = RcsUceAdapter.ERROR_NOT_REGISTERED;
                } else if (NetworkSipCode.SIP_NOT_AUTHORIZED_FOR_PRESENCE.equalsIgnoreCase(
                        respReason)) {
                    // Not provisioned for EAB. Device shall not retry.
                    uceError = RcsUceAdapter.ERROR_NOT_AUTHORIZED;
                } else {
                    // The network has responded SIP 403 error with no reason.
                    uceError = RcsUceAdapter.ERROR_FORBIDDEN;
                }
                break;
            case NetworkSipCode.SIP_CODE_REQUEST_TIMEOUT:        // 408
                uceError = RcsUceAdapter.ERROR_REQUEST_TIMEOUT;
                break;
            case NetworkSipCode.SIP_CODE_INTERVAL_TOO_BRIEF:     // 423
                // Rejected by the network because the requested expiry interval is too short.
                uceError = RcsUceAdapter.ERROR_GENERIC_FAILURE;
                break;
            case NetworkSipCode.SIP_CODE_SERVER_INTERNAL_ERROR:  // 500
            case NetworkSipCode.SIP_CODE_SERVICE_UNAVAILABLE:    // 503
                // The network is temporarily unavailable or busy.
                uceError = RcsUceAdapter.ERROR_SERVER_UNAVAILABLE;
                break;
            default:
                uceError = RcsUceAdapter.ERROR_GENERIC_FAILURE;
                break;
        }
        return uceError;
    }

    @Override
    public String toString() {
        return mResponseRecord.toString();
    }
}
