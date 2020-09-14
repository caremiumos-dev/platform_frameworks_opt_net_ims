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
//import com.android.ims.rcs.uce.presence.publish.PublishControllerImpl;
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

    private UceControllerHandler mHandler;

    private UceRequestTaskManager mTaskManager;
    private RcsFeatureManager mRcsFeatureManager;

    private EabController mEabController;
    private PublishController mPublishController;
    private SubscribeController mSubscribeController;
    private OptionsController mOptionsController;

    private final Object mLock = new Object();

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
        HandlerThread handlerThread = new HandlerThread("UceControllerHT");
        handlerThread.start();
        mHandler = new UceControllerHandler(handlerThread.getLooper());
    }

    private void initRequestTaskManager() {
        mTaskManager = new UceRequestTaskManager(mContext, mSubId, mHandler.getLooper());
    }

    private void initControllers() {
        final Looper looper = mHandler.getLooper();
        // EabController
        mEabController = new EabControllerImpl(mContext, mSubId, looper);
        mEabController.setUceRequestCallback(mCtrlCallback);
        // PublishController
//        mPublishController = new PublishControllerImpl(mContext, mSubId, looper);
        // SubscribeController
        mSubscribeController = new SubscribeControllerImpl(mContext, mSubId, looper);
        mSubscribeController.setUceRequestCallback(mCtrlCallback);
        //OptionsController
        mOptionsController = new OptionsControllerImpl(mContext, mSubId, looper);
    }

    /**
     * The RcsFeature has been connected to the framework.
     */
    public void onRcsConnected(RcsFeatureManager manager) {
        logi("onRcsConnected");
        mIsRcsConnected = true;
        mRcsFeatureManager = manager;

        mPublishController.onRcsConnected(manager);
        mSubscribeController.onRcsConnected(manager);
        mOptionsController.onRcsConnected(manager);
    }

    /**
     * The framework has lost the binding to the RcsFeature.
     */
    public void onRcsDisconnected() {
        logi("onRcsDisconnected");
        mIsRcsConnected = false;

        mPublishController.onRcsDisconnected();
        mSubscribeController.onRcsDisconnected();
        mOptionsController.onRcsDisconnected();

        if (mRcsFeatureManager != null) {
            mRcsFeatureManager.releaseConnection();
            mRcsFeatureManager = null;
        }
    }

    /**
     * Notify to destroy this instance. The UceController instance is unusable after destroyed.
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
            if (isUnavailable()) {
                return null;
            }
            return mEabController.getCapabilities(uris);
        }

        @Override
        public EabCapabilityResult getAvailabilityFromCache(Uri contactUri) {
            if (isUnavailable()) {
                return null;
            }
            return mEabController.getAvailability(contactUri);
        }

        @Override
        public void saveCapabilities(List<RcsContactUceCapability> contactCapabilities) {
            if (isUnavailable()) {
                return;
            }
            mEabController.saveCapabilities(contactCapabilities);
        }

        @Override
        public RcsContactUceCapability getDeviceCapabilities() {
            if (isUnavailable()) {
                return null;
            }
            // The PublishController is responsible for maintaining the device's capabilities.
            return mPublishController.getDeviceCapabilities();
        }

        @Override
        public void requestCapabilitiesByOptions(Uri uri, RcsContactUceCapability ownCapabilities,
                RcsCapabilityExchangeImplAdapter.OptionsResponseCallback callback) {
            if (isUnavailable()) {
                try {
                    callback.onCommandError(
                            RcsCapabilityExchangeImplAdapter.COMMAND_CODE_GENERIC_FAILURE);
                } catch (ImsException e) {}
                return;
            }
            mOptionsController.sendCapabilitiesRequest(uri, ownCapabilities, callback);
        }

        @Override
        public void refreshCapabilities(@NonNull List<Uri> contactNumbers,
                @NonNull CapabilitiesCallback callback) {
            if (isUnavailable()) {
                callback.onError(RcsUceAdapter.ERROR_GENERIC_FAILURE);
                return;
            }
            logd("refreshCapabilities: " + contactNumbers.size());
            //requestCapabilities(contactNumbers, callback);
        }

        @Override
        public void registerPublishStateCallback(@NonNull IRcsUcePublishStateCallback c) {
            if (isUnavailable()) {
                return;
            }
            logd("UceControllerCallback: registerPublishStateCallback");
            mPublishController.registerPublishStateCallback(c);
        }

        @Override
        public void unregisterPublishStateCallback(@NonNull IRcsUcePublishStateCallback c) {
            if (isUnavailable()) {
                return;
            }
            logd("UceControllerCallback: unregisterPublishStateCallback");
            mPublishController.unregisterPublishStateCallback(c);
        }
    };

    private class UceControllerHandler extends Handler {
        public UceControllerHandler(Looper looper) {
            super(looper);
        }
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
