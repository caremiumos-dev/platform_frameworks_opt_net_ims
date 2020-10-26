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

import android.content.Context;
import android.os.RemoteException;
import android.telephony.ims.RcsContactUceCapability;
import android.text.TextUtils;
import android.util.Log;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.presence.pidfparser.PidfParser;
import com.android.ims.rcs.uce.presence.publish.PublishController.PublishControllerCallback;
import com.android.ims.rcs.uce.presence.publish.PublishController.PublishTriggerType;
import com.android.ims.rcs.uce.util.UceUtils;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Send the publish request and handle the response of the publish request result.
 */
public class PublishProcessor {

    private static final String LOG_TAG = "PublishProcessor";

    // The length of time waiting for the response callback.
    private static final long RESPONSE_CALLBACK_WAITING_TIME = 60000L;

    private final int mSubId;
    private final Context mContext;

    // Manage the state of the publish processor.
    private PublishProcessorState mProcessorState;

    // The information of the device's capabilities.
    private DeviceCapabilityInfo mDeviceCapabilities;

    // The callback of the PublishController
    private PublishControllerCallback mPublishCtrlCallback;

    // Receive the result callback of the publish request from ImsService
    private PublishRequestResponse mResponseCallback;

    private RcsFeatureManager mRcsFeatureManager;

    private volatile boolean mIsDestroyed;
    private final Object mLock = new Object();

    public PublishProcessor(Context context, int subId, DeviceCapabilityInfo capabilityInfo,
            PublishControllerCallback publishCtrlCallback) {
        mSubId = subId;
        mContext = context;
        mDeviceCapabilities = capabilityInfo;
        mPublishCtrlCallback = publishCtrlCallback;
        mProcessorState = new PublishProcessorState();
    }

    /**
     * The RcsFeature has been connected to the framework.
     */
    public void onRcsConnected(RcsFeatureManager featureManager) {
        synchronized (mLock) {
            logi("onRcsConnected");
            mRcsFeatureManager = featureManager;
        }
    }

    /**
     * The framework has lost the binding to the RcsFeature.
     */
    public void onRcsDisconnected() {
        synchronized (mLock) {
            logi("onRcsDisconnected");
            mRcsFeatureManager = null;
        }
    }

    /**
     * Set the destroy flag
     */
    public void onDestroy() {
        logi("onDestroy");
        mIsDestroyed = true;
    }

    /**
     * Execute the publish request. This method is called by the handler of the PublishController.
     * @param triggerType The type of triggering the publish request.
     */
    public synchronized void doPublish(@PublishTriggerType int triggerType) {
        if (mIsDestroyed) return;

        logi("doPublish: trigger type=" + triggerType);

        // Check if it should reset the retry count.
        if (isResetRetryNeeded(triggerType)) {
            mProcessorState.resetRetryCount();
        }

        // Return if this request is not allowed to execute.
        if (!isRequestAllowed()) {
            return;
        }

        // Get the latest device's capabilities.
        RcsContactUceCapability deviceCapability =
                mDeviceCapabilities.getDeviceCapabilities(mContext);
        if (deviceCapability == null) {
            logw("doPublish: device capability is null");
            return;
        }

        // Convert the device's capabilities to pidf format.
        String pidfXml = PidfParser.convertToPidf(deviceCapability);
        if (TextUtils.isEmpty(pidfXml)) {
            logw("doPublish: pidfXml is empty");
            return;
        }

        // Publish to the Presence server.
        publishCapabilities(pidfXml);
    }

    // Check if the giving trigger type should reset the retry count.
    private boolean isResetRetryNeeded(@PublishTriggerType int triggerType) {
        // Do no reset the retry count if the request is triggered by the previous failed retry.
        if (triggerType == PublishController.PUBLISH_TRIGGER_RETRY) {
            return false;
        }
        return true;
    }

    // Check if the publish request is allowed to execute.
    private boolean isRequestAllowed() {
        synchronized (mLock) {
            // Check if the instance is destroyed.
            if (mIsDestroyed) {
                logd("isPublishAllowed: This instance is already destroyed");
                return false;
            }

            // Check if it has provisioned.
            if (!UceUtils.isEabProvisioned(mContext, mSubId)) {
                logd("isPublishAllowed: NOT provisioned");
                return false;
            }

            // Do not request publish if the IMS is not registered. When the IMS is registered
            // afterward, a new PUBLISH request will be triggered.
            if (!mDeviceCapabilities.isImsRegistered()) {
                logd("isPublishAllowed: IMS is not registered");
                return false;
            }

            // Set the pending flag if there's already a request running now.
            if (mProcessorState.isPublishingNow()) {
                logd("isPublishAllowed: There is already a request running now");
                mProcessorState.setPendingRequest(true);
                return false;
            }

            // Check if the publishing request has reached the maximum number of retries.
            if (mProcessorState.isReachMaximumRetries()) {
                logd("isPublishAllowed: It has reached maximum number of retries");
                return false;
            }

            // Skip this request and re-send the request with the delay time if the publish request
            // executes too frequently.
            if (!mProcessorState.isCurrentTimeAllowed()) {
                long delayTime = mProcessorState.getDelayTimeToAllowPublish();
                mPublishCtrlCallback.requestPublishFromInternal(
                        PublishController.PUBLISH_TRIGGER_RETRY, delayTime);
                logd("isPublishAllowed: Current time is not allowed, resend this request");
                return false;
            }
        }
        return true;
    }

    // Publish the device capabilities with the giving pidf
    private void publishCapabilities(String pidfXml) {
        if (mIsDestroyed) {
            logw("publishCapabilities: This instance is already destroyed");
            return;
        }

        // Check if the RCS is connected.
        RcsFeatureManager featureManager = null;
        synchronized (mLock) {
            featureManager = mRcsFeatureManager;
        }
        if (featureManager == null) {
            logw("publishCapabilities: NOT connected");
            return;
        }

        try {
            // Set publishing flag
            mProcessorState.setPublishingFlag(true);

            // Clear the pending request flag since we're publishing the latest device's capability
            mProcessorState.setPendingRequest(false);

            // Generate a unique taskId to track this request.
            long taskId = mProcessorState.generatePublishTaskId();
            synchronized (mLock) {
                mResponseCallback = new PublishRequestResponse(this, taskId);
            }

            logi("publishCapabilities: taskId=" + taskId);

            // request publication
            featureManager.requestPublication(pidfXml, mResponseCallback);

            // Send a request canceled timer to avoid waiting too long for the response callback.
            mPublishCtrlCallback.setupRequestCanceledTimer(taskId, RESPONSE_CALLBACK_WAITING_TIME);

        } catch (RemoteException e) {
            logw("publishCapabilities: exception=" + e.getMessage());
            // Exception occurred, end this request.
            setRequestEnded();
            checkAndSendPendingRequest();
        }
   }

    /*
     * The request is failed with receiving the command error callback. This method is called by
     * PublishRequestResponse#onCommandError
     */
    public void onCommandError(int errorCode) {
        synchronized (mLock) {
            logd("onCommandError: error code=" + errorCode);

            if (checkRequestRespValid()) {
                setRequestEnded();
                checkAndSendPendingRequest();
                return;
            }

            logd("onCommandError: " + mResponseCallback.toString());

            if (mResponseCallback.needRetry()) {
                // Resend the request if the error code requires to retry
                mProcessorState.increaseRetryCount();

                // Reset the pending flag since it is going to re-send a publish request.
                mProcessorState.setPendingRequest(false);

                // Re-send a publish request
                long delayTime = mProcessorState.getDelayTimeToAllowPublish();
                mPublishCtrlCallback.requestPublishFromInternal(
                        PublishController.PUBLISH_TRIGGER_RETRY, delayTime);
            } else {
                // Update the publish state if the request is failed and doesn't need to retry.
                int publishState = mResponseCallback.getPublishStateByCmdErrorCode();
                mPublishCtrlCallback.updatePublishRequestResult(publishState);

                // Check if there is a pending request
                checkAndSendPendingRequest();
            }

            // End this request
            setRequestEnded();
        }
    }

    /*
     * Handle the network response of the publish request.
     */
    public void onNetworkResponse(int sipCode, String reason) {
        synchronized (mLock) {
            logd("onNetworkResponse: code=" + sipCode + ", reason=" + reason);

            if (checkRequestRespValid()) {
                setRequestEnded();
                checkAndSendPendingRequest();
                return;
            }

            logd("onNetworkResponse: " + mResponseCallback.toString());

            if (mResponseCallback.needRetry()) {
                // Resend the request if the sip code requires to retry.
                mProcessorState.increaseRetryCount();

                // Reset the pending flag since it is going to re-send a publish request.
                mProcessorState.setPendingRequest(false);

                // Re-send a publish request
                long delayTime = mProcessorState.getDelayTimeToAllowPublish();
                mPublishCtrlCallback.requestPublishFromInternal(
                        PublishController.PUBLISH_TRIGGER_RETRY, delayTime);
            } else {
                // Reset the retry count if the publish is success.
                if (mResponseCallback.isRequestSuccess()) {
                    mProcessorState.resetRetryCount();
                }
                // Update the publish state if the request doesn't need to retry.
                int publishResult = mResponseCallback.getPublishStateByNetworkResponse();
                mPublishCtrlCallback.updatePublishRequestResult(publishResult);

                // Check if there is a pending request
                checkAndSendPendingRequest();
            }

            // End this request
            setRequestEnded();
        }
    }

    // Check if the request response callback is valid.
    private boolean checkRequestRespValid() {
        if (!mProcessorState.isPublishingNow()) {
            logd("checkRequestRespValid: the request is finished");
            return false;
        }

        if (mResponseCallback == null) {
            logd("checkRequestRespValid: response callback is null");
            return false;
        }

        // Abandon this response callback if the current taskId is different to the response
        // callback taskId. This response callback is obsoleted.
        long taskId = mProcessorState.getCurrentTaskId();
        long responseTaskId = mResponseCallback.getTaskId();
        if (taskId != responseTaskId) {
            logd("checkRequestRespValid: invalid taskId! current taskId=" + taskId
                    + ", response callback taskId=" + responseTaskId);
            return false;
        }

        if (mIsDestroyed) {
            logd("checkRequestRespValid: is already destroyed! taskId=" + taskId);
            return false;
        }
        return true;
    }

    // Cancel the publishing request since it has token too long for waiting the response callback.
    void cancelPublishRequest(long taskId) {
        logd("cancelPublishRequest: taskId=" + taskId);
        synchronized (mLock) {
            if (mResponseCallback == null || mResponseCallback.getTaskId() != taskId) {
                return;
            }
        }
        setRequestEnded();
        checkAndSendPendingRequest();
    }

    private void setRequestEnded() {
        synchronized (mLock) {
            if (mResponseCallback != null) {
                mResponseCallback.onDestroy();
                mResponseCallback = null;
            }
            mProcessorState.setPublishingFlag(false);
            mPublishCtrlCallback.clearRequestCanceledTimer();
        }
    }

    private void checkAndSendPendingRequest() {
        synchronized (mLock) {
            if (mIsDestroyed) return;
            if (mProcessorState.hasPendingRequest()) {
                logd("checkAndSendPendingRequest: send pending request");
                mProcessorState.setPublishingFlag(false);

                long delayTime = mProcessorState.getDelayTimeToAllowPublish();
                mPublishCtrlCallback.requestPublishFromInternal(
                        PublishController.PUBLISH_TRIGGER_RETRY, delayTime);
            }
        }
    }

    @VisibleForTesting
    public void setProcessorState(PublishProcessorState processorState) {
        mProcessorState = processorState;
    }

    @VisibleForTesting
    public void setPublishRequestResponse(PublishRequestResponse responseCallback) {
        mResponseCallback = responseCallback;
    }

    private void logd(String log) {
       Log.d(LOG_TAG, getLogPrefix().append(log).toString());
    }

    private void logi(String log) {
       Log.i(LOG_TAG, getLogPrefix().append(log).toString());
    }

   private void logw(String log) {
       Log.w(LOG_TAG, getLogPrefix().append(log).toString());
   }

   private StringBuilder getLogPrefix() {
       StringBuilder builder = new StringBuilder("[");
       builder.append(mSubId);
       builder.append("] ");
       return builder;
   }
}
