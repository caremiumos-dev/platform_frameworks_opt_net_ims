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

import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.telephony.ims.ImsException;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.RcsUceAdapter.PublishState;
import android.telephony.ims.aidl.IRcsUcePublishStateCallback;
import android.util.Log;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.UceController.UceControllerCallback;
import com.android.ims.rcs.uce.RcsCapabilityExchangeImplAdapter.PublishResponseCallback;
import com.android.ims.rcs.uce.UceSettingUtil;
import com.android.internal.annotations.GuardedBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * The implementataion class of the interface SubscribeController.
 */
public class PublishControllerImpl implements PublishController {

    private static final String LOG_TAG = "PublishControllerImpl";

    private final Context mContext;
    private final int mSubId;
    private volatile boolean mIsRcsConnected;
    private volatile boolean mIsDestroyedFlag;
    private volatile RcsFeatureManager mRcsFeatureManager;

    private final Object mPublishStateLock = new Object();

    // The device publish state
    @GuardedBy("mPublishStateLock")
    private @PublishState int mPublishState;

    // The callbacks to notify publish state changed.
    @GuardedBy("mPublishStateLock")
    private final RemoteCallbackList<IRcsUcePublishStateCallback> mPublishStateCallbacks;

    // The information of device's capabilities
    private final DeviceCapabilityInfo mDeviceCapabilityInfo;

    // The listener to receive device capabilities changed
    private final DeviceCapabilityChangedListener mDeviceCapabilitiesListener;

    private final PublishControllerHandler mPublishHandler;

    // Execute the PUBLISH operation and handle the response callback
    private final PublishRequestExecutor mPublishExecutor;

    public PublishControllerImpl(Context context, int subId, Looper looper) {
        mContext = context;
        mSubId = subId;
        logi("create");

        mPublishState = RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED;
        mPublishStateCallbacks = new RemoteCallbackList<>();

        mPublishHandler = new PublishControllerHandler(looper);
        mPublishExecutor = new PublishRequestExecutor();

        mDeviceCapabilityInfo = new DeviceCapabilityInfo(mSubId);
        mDeviceCapabilitiesListener = new DeviceCapabilityChangedListener(mContext, mSubId,
                mDeviceCapabilityInfo, mDeviceStatusChangedCallback, looper);

        // Turn on the listener to receive the device changes.
        mDeviceCapabilitiesListener.turnOnListener();
    }

    @Override
    public void onRcsConnected(RcsFeatureManager manager) {
        logi("onRcsConnected");
        mIsRcsConnected = true;
        mRcsFeatureManager = manager;
    }

    @Override
    public void onRcsDisconnected() {
        logi("onRcsDisconnected");
        mIsRcsConnected = false;
        mRcsFeatureManager = null;
    }

    /**
     * Should be called before destroying this instance. This instance is not usable after this
     * method is called.
     */
    @Override
    public void onDestroy() {
        logi("onDestroy");
        mIsDestroyedFlag = true;
        mDeviceCapabilitiesListener.onDestroy();  // It will turn off the listener automatically.
        mPublishHandler.onDestroy();
        synchronized (mPublishStateLock) {
            clearPublishStateCallbacks();
        }
    }

    /**
     * Return the UCE Publish state.
     */
    @Override
    public @PublishState int getUcePublishState() {
        synchronized (mPublishStateLock) {
            return mPublishState;
        }
    }

    /**
     * Notify that the device's capabilities has been unpublished from the network.
     */
    @Override
    public void onUnpublish() {
        logd("onUnpublish");
        updatePublishState(RcsUceAdapter.PUBLISH_STATE_NOT_PUBLISHED);
    }

    /**
     * Update the publish state and notify the publish state callback.
     */
    private void updatePublishState(@PublishState int publishState) {
        synchronized (mPublishStateLock) {
            if (mIsDestroyedFlag) return;
            logd("publish state changes from " + mPublishState + " to " + publishState);
            if (mPublishState == publishState) return;
            mPublishState = publishState;

            // Trigger the publish state changed in handler thread since it may take time.
            mPublishHandler.sendNotifyPublishStateChangedMessage();
        }
    }

    /**
     * Register a {@link PublishStateCallback} to listen to the published state changed.
     */
    @Override
    public void registerPublishStateCallback(@NonNull IRcsUcePublishStateCallback c) {
        Objects.requireNonNull(c);
        synchronized (mPublishStateLock) {
            if (mIsDestroyedFlag) return;
            mPublishStateCallbacks.register(c);
        }
    }

    /**
     * Removes an existing {@link PublishStateCallback}.
     */
    @Override
    public void unregisterPublishStateCallback(@NonNull IRcsUcePublishStateCallback c) {
        Objects.requireNonNull(c);
        synchronized (mPublishStateLock) {
            if (mIsDestroyedFlag) return;
            mPublishStateCallbacks.unregister(c);
        }
    }

    // Clear the publish state callback since the publish controller instance is destroyed.
    private void clearPublishStateCallbacks() {
        synchronized (mPublishStateLock) {
            logd("clearPublishStateCallbacks");
            final int lastIndex = mPublishStateCallbacks.getRegisteredCallbackCount() - 1;
            for (int index = lastIndex; index >= 0; index--) {
                IRcsUcePublishStateCallback callback =
                        mPublishStateCallbacks.getRegisteredCallbackItem(index);
                mPublishStateCallbacks.unregister(callback);
            }
        }
    }

    /**
     * Retrieve the device's capabilities.
     */
    @Override
    public RcsContactUceCapability getDeviceCapabilities() {
        return mDeviceCapabilityInfo.getDeviceCapabilities(mContext);
    }

    /**
     * The callback is to receive the device changed and publish the device's capabilities.
     */
    private final DeviceStatusChangedCallback mDeviceStatusChangedCallback =
            new DeviceStatusChangedCallback() {
                @Override
                public void onTtyPreferredModeChanged() {
                    logd("onTtyPreferredModeChanged");
                    mPublishHandler.requestPublish(false /* resetRetryCount */);
                }

                @Override
                public void onAirplaneModeChanged() {
                    logd("onAirplaneModeChanged");
                    boolean resetRetry = (!mDeviceCapabilityInfo.isAirplaneMode()) ? true : false;
                    mPublishHandler.requestPublish(resetRetry);
                }

                @Override
                public void onMobileDataChanged() {
                    logd("onMobileDataChanged");
                    mPublishHandler.requestPublish(false);
                }

                @Override
                public void onVtSettingChanged() {
                    logd("onVtSettingChanged");
                    mPublishHandler.requestPublish(false);
                }

                @Override
                public void onImsMmtelRegistered() {
                    logd("onImsMmtelRegistered");
                    mPublishHandler.requestPublish(true);
                }

                @Override
                public void onImsMmtelUnregistered() {
                    logd("onImsMmtelUnregistered");
                    mPublishHandler.requestPublish(false);
                }

                @Override
                public void onFeatureCapabilityChanged() {
                    logd("onFeatureCapabilityChanged");
                    mPublishHandler.requestPublish(false);
                }

                @Override
                public void onProvisioningChanged() {
                    if (!UceSettingUtil.isEabProvisioned(mContext, mSubId)) {
                        logd("onProvisioningChanged: EAB provisioned disabled");
                        return;
                    }
                    logd("onProvisioningChanged");
                    mPublishHandler.requestPublish(true);
                }
            };

    /**
     * Publish the device's capabilities to the network. This method is triggered by ImsService.
     */
    @Override
    public void publishCapabilities(int triggerType) {
        logi("Receive the publish request: type=" + triggerType);
        mPublishHandler.requestPublish(true);
    }

   private class PublishControllerHandler extends Handler {
        private static final int MSG_NOTIFY_STATE_CHANGE = 1;
        private static final int MSG_REQUEST_PUBLISH = 2;

        private static final int MAX_RETRY_COUNT = 3;

        private final Object mHandlerLock = new Object();

        // The retry increase time interval
        private final long mIncreaseTimeInterval =
                UceSettingUtil.getDefaultPublishRequestTimeInterval();

        // The retry count of the PUBLISH request
        private int mRetryCount;

        // The timestamp of allowing to the PUBLISH request.
        private Instant mAllowePublishTimestamp;

        // Set the flag if there is a new publish request during publishing.
        private boolean mPendingPublishRequest;

        public PublishControllerHandler(Looper looper) {
            super(looper);
        }

        /**
         * Notify the handler is destroyed.
         */
        public void onDestroy() {
            synchronized (mHandlerLock) {
                removeMessages(MSG_NOTIFY_STATE_CHANGE);
                removeMessages(MSG_REQUEST_PUBLISH);
                mPendingPublishRequest = false;
            }
        }

        /**
         * Send the message to trigger the publish state changed callback.
         */
        public void sendNotifyPublishStateChangedMessage() {
            synchronized (mHandlerLock) {
                sendEmptyMessage(MSG_NOTIFY_STATE_CHANGE);
            }
        }

        /**
         * Send the publish message to the handler. The pending flag is set if the publish request
         * is running.
         */
        public void requestPublish(boolean resetRetryCount) {
            synchronized (mHandlerLock) {
                if (mIsDestroyedFlag) return;
                logd("requestPublish: resetRetryCount=" + resetRetryCount);

                if (resetRetryCount) {
                    resetRetryCount();
                }

                // Set the pending flag if there is a PUBLISH request running now.
                if (mPublishExecutor.isRunning()) {
                    logd("requestPublish: set the pending flag.");
                    mPendingPublishRequest = true;
                } else {
                    logd("requestPublish: add request");
                    mPendingPublishRequest = false;
                    sendEmptyMessage(MSG_REQUEST_PUBLISH);
                }
            }
        }

        /**
         * The publish request is failed and is able to retry.
         */
        void retryPublishRequest() {
            synchronized (mHandlerLock) {
                increaseRetryCount();
                resendRequestToExecuteAfterTheAllowedTime();
            }
        }


        @Override
        public void handleMessage(Message msg) {
            if (mIsDestroyedFlag) return;

            logd("handleMessage: " + msg.what);
            switch (msg.what) {
                case MSG_NOTIFY_STATE_CHANGE:
                    handleNotifyPublishStateChanged();
                    break;

                case MSG_REQUEST_PUBLISH:
                    if (!isPublishAllowed()) {
                        break;
                    }
                    mPublishExecutor.execute();
                    break;
            }
        }

        private boolean isPublishAllowed() {
            synchronized (mHandlerLock) {
                // Do not retry if the request reaches the max retry count.
                if (isReachMaxRetryCount()) {
                    logw("isPublishAllowed: reach max retry count");
                    return false;
                }

                // Skip and resend the message with a delay time if the publish request is
                // executed too frequently.
                if (!isCurrentTimeAllowed()) {
                    logw("isPublishAllowed: The current time is not allowed to publish");
                    resendRequestToExecuteAfterTheAllowedTime();
                    return false;
                }

                // Do not request publish if the IMS is not registered. When the IMS is
                // registered afterward, a new PUBLISH request will be triggered.
                if (!mDeviceCapabilityInfo.isImsRegistered()) {
                    logw("isPublishAllowed: IMS is not registered");
                    return false;
                }

                if (mPublishExecutor.isRunning()) {
                    logw("isPublishAllowed: The executor is running now");
                    return false;
                }
            }
            return true;
        }

        private boolean isReachMaxRetryCount() {
            synchronized (mHandlerLock) {
                return (mRetryCount > MAX_RETRY_COUNT) ? true : false;
            }
        }

        // Check if the current timestamp is allowed to request publish.
        private boolean isCurrentTimeAllowed() {
            synchronized (mHandlerLock) {
                Instant now = Instant.now();
                return (now.isAfter(mAllowePublishTimestamp)) ? true : false;
            }
        }

        private void resendRequestToExecuteAfterTheAllowedTime() {
            synchronized (mHandlerLock) {
                removeMessages(MSG_REQUEST_PUBLISH);

                // Setup the delay to the time which publish request is allowed to execute.
                long delayTime = ChronoUnit.MILLIS.between(Instant.now(), mAllowePublishTimestamp);
                if (delayTime < 0) {
                    delayTime = 0L;
                }

                // Remove pending flag since the request will be retried later.
                mPendingPublishRequest = false;
                sendEmptyMessageDelayed(MSG_REQUEST_PUBLISH, delayTime);
                logd("resendRequestToExecuteAfterTheAllowedTime: " + ((delayTime) / 1000));
            }
        }

        // Reset the retry count and adjust the request allowed time to the current time.
        void resetRetryCount() {
            synchronized (mHandlerLock) {
                logd("resetRetryCount");
                mRetryCount = 0;
                // Adjust the allowed timestamp of the request
                setupAllowePublishTimestamp(0L);
            }
        }

        // Increase the retry count and adjust the request allowed time based on the retry count.
        void increaseRetryCount() {
            synchronized (mHandlerLock) {
                mRetryCount++;
                logd("increaseRetryCount: " + mRetryCount);

                int count = (mRetryCount > MAX_RETRY_COUNT) ? MAX_RETRY_COUNT : mRetryCount;
                long delay = count * mIncreaseTimeInterval * 1000;
                setupAllowePublishTimestamp(delay);
            }
        }

        // Setup the timestamp to allow request PUBLISH with the specific delay time
        private void setupAllowePublishTimestamp(long delay) {
            synchronized (mHandlerLock) {
                logd("setupAllowePublishTimestamp: delay=" + delay);
                if (mAllowePublishTimestamp == null) {
                    // For the first time initialization.
                    mAllowePublishTimestamp = Instant.now();
                } else if (delay == 0) {
                    // The retry count is reset
                    mAllowePublishTimestamp = Instant.now();
                } else if (delay > 0) {
                    mAllowePublishTimestamp = Instant.now().plus(Duration.ofMillis(delay));
                }
                logd("The new allowed publish timestamp: " + mAllowePublishTimestamp.toString());
            }
        }
    }

    private class PublishRequestExecutor {
        private static final String EXEXUTOR_LOG_TAG = "Executor";
        private volatile boolean mIsRunning;
        private PublishRequestResponse mResponse;

        PublishRequestExecutor() {
            mIsRunning = false;
        }

        public boolean isRunning() {
            return mIsRunning;
        }

        public void execute() {
            logd(EXEXUTOR_LOG_TAG + ": execute");
            mIsRunning = true;

            mResponse = new PublishRequestResponse();

            // Get the latest device's capabilities.
            RcsContactUceCapability deviceCapability = getDeviceCapabilities();

            // Convert it to pidf xml format.
            String pidfFormat = convertToPidfFormat(deviceCapability);

            // Publish the capabilities to the Presence server.
            publishCapabilities(pidfFormat);
        }

        private String convertToPidfFormat(RcsContactUceCapability capabilities) {
            // TODO: implement the pidf converter
            return null;
        }

        private void publishCapabilities(String pidfFormat) {
            logi(LOG_TAG + ": publishCapabilities");
            // TODO: Implement the publish capabilities in RcsFeatureManager
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
            if (mIsDestroyedFlag) {
                logd(LOG_TAG + ": handleCommandError: already destroyed code=" + code);
                mIsRunning = false;
                return;
            }

            mResponse.onCommandErrorCode(code);
            logd(LOG_TAG + ": handleCommandError: code=" + mResponse.toString());

            if (mResponse.needRetry()) {
                mPublishHandler.retryPublishRequest();
            } else {
                int publishResult = mResponse.convertCommandErrorCodeToPublishState();
                updatePublishState(publishResult);
            }
            mIsRunning = false;
        }

        /**
         * Handle the network response of the publish request.
         */
        public void handleNetworkResponse(int sipCode, String reason) {
            if (mIsDestroyedFlag) {
                logd(LOG_TAG + ": handleNetworkResponse: already destroyed code=" + sipCode);
                mIsRunning = false;
                return;
            }

            mResponse.onNetworkResponse(sipCode, reason);
            logd(LOG_TAG + ": handleNetworkResponse: " + mResponse.toString());

            if (mResponse.needRetry()) {
                mPublishHandler.retryPublishRequest();
            } else {
                if (mResponse.isSuccess()) {
                    mPublishHandler.resetRetryCount();
                }
                int publishResult = mResponse.convertNetworkResponseToPublishState();
                updatePublishState(publishResult);
            }
            mIsRunning = false;
        }
    }

    private void handleNotifyPublishStateChanged() {
        synchronized (mPublishStateLock) {
            if (mIsDestroyedFlag) return;
            logd("Notify publish state changed: " + mPublishState);
            mPublishStateCallbacks.broadcast(c -> {
                try {
                    c.onPublishStateChanged(mPublishState);
                } catch (RemoteException e) {
                    logw("Notify publish state changed error: " + e);
                }
            });
            logd("Notify publish state changed: completed");
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
