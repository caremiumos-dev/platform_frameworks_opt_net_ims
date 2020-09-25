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
import android.telephony.ims.ImsException;
import android.telephony.ims.RcsContactUceCapability;
import android.util.Log;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.RcsCapabilityExchangeImplAdapter.PublishResponseCallback;
import com.android.ims.rcs.uce.presence.pidf.PidfParser;
import com.android.ims.rcs.uce.presence.pidf.PidfParserImpl;
import com.android.ims.rcs.uce.presence.publish.PublishController.PUBLISH_TRIGGER_TYPE;
import com.android.ims.rcs.uce.presence.publish.PublishController.PublishControllerCallback;
import com.android.ims.rcs.uce.util.UceSettingUtils;
import com.android.ims.rcs.uce.util.UceUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class PublishProcessorImpl implements PublishProcessor {

    private static final String LOG_TAG = "PublishProcessor";

    public interface PidfParserFactory {
        PidfParser createPidfParser();
    }

    private final int mSubId;
    private final Context mContext;
    private final RequestParam mRequestParam;
    private final PublishControllerCallback mCallback;
    private final DeviceCapabilityInfo mDeviceCapabilities;

    private PublishRequestResponse mResponse;
    private RcsFeatureManager mRcsFeatureManager;

    private PidfParser mPidfParser;
    private PidfParserFactory mPidfParserFactory = () -> new PidfParserImpl();

    private volatile boolean mIsDestroyed;
    private final Object mLock = new Object();

    public PublishProcessorImpl(Context context, int subId, DeviceCapabilityInfo capabilityInfo,
            PublishControllerCallback callback) {
        mSubId = subId;
        mContext = context;
        mCallback = callback;
        mDeviceCapabilities = capabilityInfo;

        mRequestParam = new RequestParam();
        logd("create: throttle=" + mRequestParam.getPublishThrottle() + ", maximum retries="
                + mRequestParam.getMaximumRetries());
    }

    @Override
    public void onRcsConnected(RcsFeatureManager featureManager) {
        synchronized (mLock) {
            mRcsFeatureManager = featureManager;
        }
    }

    @Override
    public void onRcsDisconnected() {
        synchronized (mLock) {
            mRcsFeatureManager = null;
        }
    }

    @Override
    public void onDestroy() {
        logi("onDestroy");
        mIsDestroyed = true;
        mRequestParam.reset();
    }

    /**
     * Execute the publish request. This method is called by the handler of the PublishController.
     * @param triggerType The type of triggering the publish request.
     */
    @Override
    public void doPublish(PUBLISH_TRIGGER_TYPE triggerType) {
        if (mIsDestroyed) return;
        logi("doPublish: " + triggerType);

        // Check if the retry count should be reset.
        if (isTriggerTypeShouldResetRetries(triggerType)) {
            mRequestParam.resetRetryCount();
        }

        // Check if the request is allowed to execute.
        if (!isPublishAllowed()) {
            return;
        }

        // Generate a unique taskId to represent this publish request.
        long taskId = mRequestParam.generatePublishTaskId();
        logi("doPublish: taskId=" + taskId);

        // Get the latest device's capabilities and convert it to the string of pidf.
        RcsContactUceCapability deviceCapability = getDeviceCapabilities();
        String pidf = convertToPidf(deviceCapability);

        // The response instance of this publish request.
        mResponse = new PublishRequestResponse(taskId);

        // Publish the capabilities to the Presence server.
        publishCapabilities(pidf);
    }

    // Check if the trigger type should reset the retry count.
    private boolean isTriggerTypeShouldResetRetries(PUBLISH_TRIGGER_TYPE triggerType) {
        // Do no reset the retry count if the request is triggered by the previous failed retry.
        if (triggerType == PUBLISH_TRIGGER_TYPE.REQUEST_FAILED_RETRY) {
            return false;
        }
        return true;
    }

    /**
     * Check if the publish request is allowed to execute.
     * @return true If the publish request is allowed, false otherwise.
     */
    public boolean isPublishAllowed() {
        synchronized (mLock) {
            // Check if it has provisioned.
            if (!UceSettingUtils.isEabProvisioned(mContext, mSubId)) {
                logw("isPublishAllowed: NOT provisioned");
                return false;
            }

            // Do not request publish if the IMS is not registered. When the IMS is
            // registered afterward, a new PUBLISH request will be triggered.
            if (!mDeviceCapabilities.isImsRegistered()) {
                logw("isPublishAllowed: IMS is not registered");
                return false;
            }

            // Set the pending flag if there's already a request running.
            if (mRequestParam.isPublishing()) {
                logw("isPublishAllowed: The executor is running now");
                mRequestParam.setPendingRequest(true);
                return false;
            }

            // Check if the request reaches the maximum retries.
            if (mRequestParam.isReachMaximumRetries()) {
                logw("isPublishAllowed: reach maximum retries");
                return false;
            }

            // Skip and resend the request with the delay time if the publish request is executed
            // too frequently.
            if (!mRequestParam.isCurrentTimeAllowed()) {
                logw("isPublishAllowed: The current time is not allowed to publish");
                mRequestParam.resendRequestDelayToAllowedTime();
                return false;
            }
        }
        return true;
    }

    private RcsContactUceCapability getDeviceCapabilities() {
        return mDeviceCapabilities.getDeviceCapabilities(mContext);
    }

    private String convertToPidf(RcsContactUceCapability capabilities) {
        synchronized (mLock) {
            if (mPidfParser == null) {
                mPidfParser = mPidfParserFactory.createPidfParser();
            }
        }
        return mPidfParser.convertToPidf(capabilities);
    }

    private void publishCapabilities(String pidf) {
        if (mIsDestroyed) {
            logw("publishCapabilities: already destroyed");
            return;
        }

        RcsFeatureManager featureManager = null;
        synchronized (mLock) {
            featureManager = mRcsFeatureManager;
        }
        if (featureManager == null) {
            logw("publishCapabilities: RCS is not connected.");
            return;
        }

        logi("publishCapabilities: " + mRequestParam.getTaskId());
        try {
            featureManager.requestPublication(pidf, mResponseCallback);
        } catch (RemoteException e) {
            logw("publishCapabilities: exception=" + e.getMessage());
        }
    }

    // The response callback of the PUBLISH request.
    private PublishResponseCallback mResponseCallback = new PublishResponseCallback() {
        @Override
        public void onCommandError(int code) throws ImsException {
            handleCommandError(code);
        }

        @Override
        public void onNetworkResponse(int code, String reason) {
            handleNetworkResponse(code, reason);
        }
    };

    /**
     * Handle the command error of the publish request.
     */
    public void handleCommandError(int code) throws ImsException {
        if (mIsDestroyed) {
            logd("handleCommandError: already destroyed! code=" + code);
            return;
        }

        long responseTaskId = mResponse.getTaskId();
        long paramTaskId = mRequestParam.getTaskId();
        if (responseTaskId != paramTaskId) {
            logd("handleCommandError: invalid taskId=" + responseTaskId + ", " + paramTaskId);
            return;
        }

        mResponse.onCommandErrorCode(code);
        logd("handleCommandError: " + mResponse.toString());

        if (mResponse.needRetry()) {
            mRequestParam.increaseRetryCount();
            mRequestParam.resendRequestDelayToAllowedTime();
        } else {
            int publishResult = mResponse.convertCommandErrorCodeToPublishState();
            mCallback.updatePublishRequestResult(publishResult);
        }

        // Receive the command error. Reset the taskId since the request is finish.
        mRequestParam.resetPublishTaskId();
    }

    /**
     * Handle the network response of the publish request.
     */
    public void handleNetworkResponse(int sipCode, String reason) {
        if (mIsDestroyed) {
            logd(LOG_TAG + ": handleNetworkResponse: already destroyed code=" + sipCode);
            return;
        }

        long responseTaskId = mResponse.getTaskId();
        long paramTaskId = mRequestParam.getTaskId();
        if (responseTaskId != paramTaskId) {
            logd("handleNetworkResponse: invalid taskId=" + responseTaskId + ", " + paramTaskId);
            return;
        }

        mResponse.onNetworkResponse(sipCode, reason);
        logd("handleNetworkResponse: " + mResponse.toString());

        if (mResponse.needRetry()) {
            mRequestParam.resendRequestDelayToAllowedTime();
            mRequestParam.increaseRetryCount();
        } else {
            if (mResponse.isSuccess()) {
                mRequestParam.resetRetryCount();
            }
            int publishResult = mResponse.convertNetworkResponseToPublishState();
            mCallback.updatePublishRequestResult(publishResult);
        }

        // Receive the server response. Reset the taskId since the request is finish.
        mRequestParam.resetPublishTaskId();
    }

    /**
     * The helper class to manage the request parameters.
     */
    private class RequestParam {
        private static final long DEFAULT_TASK_ID = 0L;

        private long mPublishTaskId;
        private boolean mPendingRequest;

        private int mRetryCount;
        private Instant mAllowedTimestamp;
        private final int mMaximumRetries;
        private final long mPublishThrottle;

        private final Object mParamLock = new Object();

        public RequestParam() {
            mMaximumRetries = UceSettingUtils.getPublishMaximumRetries();
            mPublishThrottle = UceSettingUtils.getPublishThrottle(mSubId);
        }

        public void reset() {
            synchronized (mParamLock) {
                resetPublishTaskId();
                setPendingRequest(false);
            }
        }

        public int getMaximumRetries() {
            return mMaximumRetries;
        }

        public long getPublishThrottle() {
            return mPublishThrottle;
        }

        private long generatePublishTaskId() {
            synchronized (mParamLock) {
                mPublishTaskId = UceUtils.generateTaskId();
                return mPublishTaskId;
            }
        }

        private void resetPublishTaskId() {
            synchronized (mParamLock) {
                logd("resetPublishTaskId");
                mPublishTaskId = DEFAULT_TASK_ID;
            }
        }

        public long getTaskId() {
            synchronized (mParamLock) {
                return mPublishTaskId;
            }
        }

        // The publishing request is running if the taskId is not zero.
        private boolean isPublishing() {
            synchronized (mParamLock) {
                return (mPublishTaskId == DEFAULT_TASK_ID) ? false : true;
            }
        }

        private void setPendingRequest(boolean pendingRequest) {
            synchronized (mParamLock) {
                mPendingRequest = pendingRequest;
            }
        }

        private boolean isReachMaximumRetries() {
            synchronized (mParamLock) {
                return (mRetryCount > mMaximumRetries) ? true : false;
            }
        }

        // Check if the current timestamp is allowed to request publish.
        private boolean isCurrentTimeAllowed() {
            synchronized (mParamLock) {
                Instant now = Instant.now();
                return (now.isAfter(mAllowedTimestamp)) ? true : false;
            }
        }

        private void resendRequestDelayToAllowedTime() {
            synchronized (mParamLock) {
                // Setup the delay to the time which publish request is allowed to execute.
                long delayTime = ChronoUnit.MILLIS.between(Instant.now(), mAllowedTimestamp);
                if (delayTime < 0) {
                    delayTime = 0L;
                }

                logd("resendRequestDelayToAllowedTime: " + ((delayTime) / 1000));
                mCallback.requestPublishFromInternal(
                        PUBLISH_TRIGGER_TYPE.REQUEST_FAILED_RETRY, delayTime);
            }
        }

        private void resetRetryCount() {
            synchronized (mParamLock) {
                logd("resetRetryCount");
                mRetryCount = 0;
                // Adjust the allowed timestamp of the request
                adjustAllowedPublishTimestamp(0L);
            }
        }

        public void increaseRetryCount() {
            synchronized (mParamLock) {
                mRetryCount++;
                int count = (mRetryCount > mMaximumRetries) ? mMaximumRetries : mRetryCount;
                long delay = count * mPublishThrottle;
                adjustAllowedPublishTimestamp(delay);
            }
        }

        // Adjust the timestamp to allow request PUBLISH with the specific delay time
        private void adjustAllowedPublishTimestamp(long delay) {
            synchronized (mParamLock) {
                logd("adjustAllowedPublishTimestamp: delay=" + delay);
                if (mAllowedTimestamp == null) {
                    // For the first time initialization.
                    mAllowedTimestamp = Instant.now();
                } else if (delay == 0) {
                    // The retry count is reset
                    mAllowedTimestamp = Instant.now();
                } else if (delay > 0) {
                    mAllowedTimestamp = Instant.now().plus(Duration.ofMillis(delay));
                }
                logd("The new allowed publish timestamp: " + mAllowedTimestamp.toString());
            }
        }
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
