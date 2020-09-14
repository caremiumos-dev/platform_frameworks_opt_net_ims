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

import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.ims.ImsException;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.RcsUceAdapter.CapabilitiesCallback;
import android.telephony.ims.RcsUceAdapter.PublishState;
import android.telephony.ims.RcsUceAdapter.PublishStateCallback;
import android.telephony.ims.aidl.IRcsUcePublishStateCallback;
import android.util.Log;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.eab.EabCapabilityResult;
import com.android.ims.rcs.uce.eab.EabController;
import com.android.ims.rcs.uce.eab.EabControllerImpl;
import com.android.ims.rcs.uce.options.OptionsController;
import com.android.ims.rcs.uce.options.OptionsControllerImpl;
import com.android.ims.rcs.uce.presence.publish.PublishController;
import com.android.ims.rcs.uce.presence.publish.PublishControllerImpl;
import com.android.ims.rcs.uce.presence.subscribe.SubscribeController;
import com.android.ims.rcs.uce.presence.subscribe.SubscribeControllerImpl;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The UceController will manage the RCS UCE requests on a per subscription basis. When it receives
 * the UCE requests from the RCS applications and from the ImsService, it will coordinate the
 * cooperation between the publish/subscribe/options components to complete the requests.
 */
public class UceController {

    private static final String LOG_TAG = "UceController";

    /**
     * The callback interface is called by the internal controllers to receive information from
     * others controllers
     */
    public interface UceControllerCallback {
        /**
         * Retrieve the capabilities associated with the giving uris from the cache.
         */
        EabCapabilityResult getCapabilitiesFromCache(@NonNull List<Uri> uris);

        /**
         * Retrieve the contact's capabilities from the availability cache.
         */
        EabCapabilityResult getAvailabilityFromCache(@NonNull Uri uri);

        /**
         * Store the giving capabilities to the cache.
         */
        void saveCapabilities(List<RcsContactUceCapability> contactCapabilities);

        /**
         * Retrieve the device's capabilities.
         */
        RcsContactUceCapability getDeviceCapabilities();

        /**
         * Trigger the capabilities request with OPTIONS
         */
        void requestCapabilitiesByOptions(@NonNull Uri contactUri,
                @NonNull RcsContactUceCapability ownCapabilities,
                @NonNull RcsCapabilityExchangeImplAdapter.OptionsResponseCallback callback);

        /**
         * The method is called when the given contacts' capabilities are expired and need to be
         * refreshed.
         */
        void refreshCapabilities(@NonNull List<Uri> contactNumbers,
                @NonNull CapabilitiesCallback callback);

        /**
         * The method is called when the EabController and the PublishController want to receive
         * published state changes.
         */
        void registerPublishStateCallback(@NonNull IRcsUcePublishStateCallback c);

        /**
         * Remove the existing PublishStateCallback.
         */
        void unregisterPublishStateCallback(@NonNull IRcsUcePublishStateCallback c);
    }

    private final int mSubId;
    private final Context mContext;
    private volatile boolean mIsRcsConnected;
    private volatile boolean mIsDestroyedFlag;
    private volatile RcsFeatureManager mRcsFeatureManager;

    private UceControllerHandler mHandler;
    private UceRequestTaskManager mTaskManager;

    private EabController mEabController;
    private PublishController mPublishController;
    private SubscribeController mSubscribeController;
    private OptionsController mOptionsController;

    public UceController(Context context, int subId) {
        mSubId = subId;
        mContext = context;
        mIsRcsConnected = false;
        mIsDestroyedFlag = false;
        logi("create");

        initHandler();
        initRequestTaskManager();
        initControllers();
    }

    private void initHandler() {
        HandlerThread handlerThread = new HandlerThread("UceControllerHandlerThread");
        handlerThread.start();
        mHandler = new UceControllerHandler(handlerThread.getLooper());
    }

    private void initRequestTaskManager() {
        mTaskManager = new UceRequestTaskManager(mContext, mSubId, mHandler.getLooper());
    }

    private void initControllers() {
        // Pass the looper to each controllers.
        final Looper looper = mHandler.getLooper();

        // EabController
        mEabController = new EabControllerImpl(mContext, mSubId, looper);
        mEabController.setUceRequestCallback(mCtrlCallback);
        // PublishController
        mPublishController = new PublishControllerImpl(mContext, mSubId, looper);
        // SubscribeController
        mSubscribeController = new SubscribeControllerImpl(mContext, mSubId, looper);
        mSubscribeController.setUceRequestCallback(mCtrlCallback);
        //OptionsController
        mOptionsController = new OptionsControllerImpl(mContext, mSubId, looper);
    }

    /**
     * The RcsFeature has been connected to the framework. This method runs on main thread.
     */
    public void onRcsConnected(RcsFeatureManager manager) {
        logi("onRcsConnected");
        mIsRcsConnected = true;
        mRcsFeatureManager = manager;

        mEabController.onRcsConnected(manager);
        mPublishController.onRcsConnected(manager);
        mSubscribeController.onRcsConnected(manager);
        mOptionsController.onRcsConnected(manager);
    }

    /**
     * The framework has lost the binding to the RcsFeature. This method runs on main thread.
     */
    public void onRcsDisconnected() {
        logi("onRcsDisconnected");
        mIsRcsConnected = false;

        mEabController.onRcsDisconnected();
        mPublishController.onRcsDisconnected();
        mSubscribeController.onRcsDisconnected();
        mOptionsController.onRcsDisconnected();

        if (mRcsFeatureManager != null) {
            mRcsFeatureManager.releaseConnection();
            mRcsFeatureManager = null;
        }
    }

    /**
     * Notify to destroy this instance. This instance is unusable after destroyed.
     */
    public void onDestroy() {
        logi("onDestroy");
        mIsDestroyedFlag = true;
        mTaskManager.onDestroy();

        mEabController.onDestroy();
        mPublishController.onDestroy();
        mSubscribeController.onDestroy();
        mOptionsController.onDestroy();

        mHandler.getLooper().quit();
    }

    // The implementation of the interface UceControllerCallback.
    private final UceControllerCallback mCtrlCallback = new UceControllerCallback() {
        @Override
        public EabCapabilityResult getCapabilitiesFromCache(List<Uri> uris) {
            return mEabController.getCapabilities(uris);
        }

        @Override
        public EabCapabilityResult getAvailabilityFromCache(Uri contactUri) {
            return mEabController.getAvailability(contactUri);
        }

        @Override
        public void saveCapabilities(List<RcsContactUceCapability> contactCapabilities) {
            mEabController.saveCapabilities(contactCapabilities);
        }

        @Override
        public RcsContactUceCapability getDeviceCapabilities() {
            return mPublishController.getDeviceCapabilities();
        }

        @Override
        public void requestCapabilitiesByOptions(Uri uri, RcsContactUceCapability ownCapabilities,
                RcsCapabilityExchangeImplAdapter.OptionsResponseCallback callback) {
            mOptionsController.sendCapabilitiesRequest(uri, ownCapabilities, callback);
        }

        @Override
        public void refreshCapabilities(@NonNull List<Uri> contactNumbers, 
                @NonNull CapabilitiesCallback callback) {
            logd("refreshCapabilities: " + contactNumbers.size());
            requestCapabilities(contactNumbers, callback);
        }

        @Override
        public void registerPublishStateCallback(@NonNull IRcsUcePublishStateCallback c) {
            logd("UceControllerCallback: registerPublishStateCallback");
            registerPublishStateCallback(c);
        }

        @Override
        public void unregisterPublishStateCallback(@NonNull IRcsUcePublishStateCallback c) {
            logd("UceControllerCallback: unregisterPublishStateCallback");
            unregisterPublishStateCallback(c);
        }
    };

    /**
     * Request to get the contacts' capabilities. This method will retrieve the capabilities from
     * the cache If the capabilities are out of date, it will trigger another request to get the
     * latest contact's capabilities from the carrier network.
     */
    public void requestCapabilities(@NonNull List<Uri> uriList, @NonNull CapabilitiesCallback c) {
        Objects.requireNonNull(uriList);
        Objects.requireNonNull(c);

        if (isUnavailable()) {
            logw("requestCapabilities: controller is unavailable");
            c.onError(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return;
        }
        logd("requestCapabilities");

        // Trigger the capabilities request task
        mTaskManager.triggerCapabilityRequestTask(mCtrlCallback, uriList, c);
    }

    /**
     * Request to get the contact's capabilities. It will check the availability cache first. If
     * the capability in the availability cache is expired then it will retrieve the capability
     * from the carrier network.
     */
    public void requestAvailability(@NonNull Uri uri, @NonNull CapabilitiesCallback c) {
        Objects.requireNonNull(uri);
        Objects.requireNonNull(c);

        if (isUnavailable()) {
            logw("requestAvailability: controller is unavailable");
            c.onError(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return;
        }
        logd("requestAvailability");

        // Trigger the availability request task
        mTaskManager.triggerAvailabilityRequestTask(mCtrlCallback, uri, c);
    }

    /**
     * Publish the device's capabilities. This request is triggered from the ImsService.
     */
    public void onRequestPublishCapabilitiesFromService(int triggerType) {
        logd("onRequestPublishCapabilitiesFromService: " + triggerType);
        mPublishController.publishCapabilities(triggerType);
    }

    /**
     * This method is triggered by the ImsService to notify framework that the device's
     * capabilities has been unpublished from the network.
     */
    public void onUnpublish() {
        logd("onUnpublish");
        mPublishController.onUnpublish();
    }

    /**
     * Request publish the device's capabilities. This request is from the ImsService to send the
     * capabilities to the remote side.
     */
    public void retrieveOptionsCapabilitiesForRemote(@NonNull Uri contactUri,
            @NonNull List<String> remoteCapabilities,
            @NonNull CapabilityExchangeListenerAdapter.OptionsRequestCallback callback) {
        Objects.requireNonNull(contactUri);
        Objects.requireNonNull(remoteCapabilities);
        Objects.requireNonNull(callback);

        if (mIsDestroyedFlag) {
            logw("retrieveOptionsCapabilitiesForRemote: controller is destroyed.");
            callback.respondToCapabilityRequestWithError(
                    UceRequestResponse.SIP_CODE_SERVICE_UNAVAILABLE,
                    UceRequestResponse.SIP_SERVICE_UNAVAILABLE);
            return;
        }

        logd("retrieveOptionsCapabilitiesForRemote");
        mHandler.sendOptionsCapabilitiesRetrievalMessage(contactUri, remoteCapabilities, callback);
    }

    /**
     * Register a {@link PublishStateCallback} to receive the published state changed.
     */
    public void registerPublishStateCallback(@NonNull IRcsUcePublishStateCallback c) {
        Objects.requireNonNull(c);
        mPublishController.registerPublishStateCallback(c);
    }

    /**
     * Removes an existing {@link PublishStateCallback}.
     */
    public void unregisterPublishStateCallback(@NonNull IRcsUcePublishStateCallback c) {
        Objects.requireNonNull(c);
        mPublishController.unregisterPublishStateCallback(c);
    }

    /**
     * Get the UCE publish state if the PUBLISH is supported by the carrier.
     */
    public @PublishState int getUcePublishState() {
        return mPublishController.getUcePublishState();
    }

    private class UceControllerHandler extends Handler {
        private static final int MSG_OPTIONS_CAP_RETRIEVAL = 1;

        public UceControllerHandler(Looper looper) {
            super(looper);
        }
        
        public void sendOptionsCapabilitiesRetrievalMessage(Uri contactUri,
                List<String> remoteCapabilities,
                CapabilityExchangeListenerAdapter.OptionsRequestCallback callback) {

            OptionsRetrievalParam param = new OptionsRetrievalParam(contactUri,
                    remoteCapabilities, callback);
            Message message = obtainMessage();
            message.what = MSG_OPTIONS_CAP_RETRIEVAL;
            message.obj = param;
            sendMessage(message);
        }

        @Override
        public void handleMessage(Message message) {
            if (isUnavailable()) {
                return;
            }
            switch (message.what) {
                case MSG_OPTIONS_CAP_RETRIEVAL:
                    OptionsRetrievalParam param = (OptionsRetrievalParam) message.obj;
                    handleOptionsCapabilitiesRetrieval(param);
                    break;
            }
        }
    }

    private void handleOptionsCapabilitiesRetrieval(OptionsRetrievalParam param) {
        Uri contactUri = param.getContactUri();
        List<String> remoteCapabilities = param.getRemoteCapabilities();
        CapabilityExchangeListenerAdapter.OptionsRequestCallback callback = param.getCallback();

        RcsContactUceCapability remoteCap = convertToRcsContactUceCapability(remoteCapabilities);
        mEabController.saveCapabilities(Arrays.asList(remoteCap));
        RcsContactUceCapability deviceCapabilities = mPublishController.getDeviceCapabilities();

        // Trigger callback
        if (deviceCapabilities == null) {
            logw("handleOptionsCapabilitiesRetrieval: controller is unavailable");
            callback.respondToCapabilityRequestWithError(
                    UceRequestResponse.SIP_CODE_SERVICE_UNAVAILABLE,
                    UceRequestResponse.SIP_SERVICE_UNAVAILABLE);
        } else {
            callback.respondToCapabilityRequest(deviceCapabilities);
        }
    }

    private RcsContactUceCapability convertToRcsContactUceCapability(List<String> capabilities) {
        // TODO: Implement this method
        return null;
    }

    public int getSubId() {
        return mSubId;
    }

    private boolean isUnavailable() {
        if (!mIsRcsConnected || mIsDestroyedFlag) {
            return true;
        }
        return false;
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
