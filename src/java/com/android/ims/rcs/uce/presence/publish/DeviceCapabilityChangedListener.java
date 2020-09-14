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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsMmTelManager.CapabilityCallback;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities;
import android.util.Log;

import java.util.concurrent.Executor;

/**
 * The class to listener the device capabilities changed.
 */
public class DeviceCapabilityChangedListener {

    private static final String LOG_TAG = "DeviceCapListener";

    private final int mSubId;
    private final Context mContext;

    private final DeviceCapabilityInfo mCapabilityInfo;
    private final DeviceStatusChangedCallback mCapChangedCallback;

    // Whether this class instance is destroyed.
    private volatile boolean mIsDestroyed = false;

    // Whether the capabilities listener is turned on.
    private volatile boolean mListenerTurnedOn;

    private final RegisterCallbackHandler mHandler;

    private final Object mLock = new Object();

    public DeviceCapabilityChangedListener(Context context, int subId, DeviceCapabilityInfo info,
            DeviceStatusChangedCallback callback, Looper looper) {
        mContext = context;
        mSubId = subId;
        mCapabilityInfo = info;
        mCapChangedCallback = callback;
        logi("create");

        mListenerTurnedOn = false;
        mHandler = new RegisterCallbackHandler(this, looper);
    }

    /**
     * Notify the instance is destroyed
     */
    public void onDestroy() {
        logi("onDestroy");
        turnOffListener();
        mIsDestroyed = true;
    }

    /**
     * Turn on the device capabilities changed listener
     */
    public void turnOnListener() {
        synchronized (mLock) {
            if (mIsDestroyed) {
                logw("turnOnListener: This instance is already destroyed");
                return;
            } else if (mSubId <= SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                logw("turnOnListener: invalid subscriptionId");
                return;
            }
            if (mListenerTurnedOn) return;
            logi("turnOnListener");
            registerReceivers();
            registerImsProvisionCallback();
        }
    }

    /**
     * Turn off the device capability change listener
     */
    public void turnOffListener() {
        synchronized (mLock) {
            if (!mListenerTurnedOn) return;
            logi("turnOffListener");
            unregisterReceivers();
            unregisterImsProvisionCallback();
        }
    }

    /*
     * Register receivers to listen to the data changed
     */
    private void registerReceivers() {
        logd("registerReceivers");
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED);
        mContext.registerReceiver(mReceiver, filter);

        ContentResolver resolver = mContext.getContentResolver();
        if (resolver != null) {
            // Listen to the mobile data content changed.
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.MOBILE_DATA), false,
                    mMobileDataObserver);
            // Listen to the SIM info content changed.
            resolver.registerContentObserver(Telephony.SimInfo.CONTENT_URI, false,
                    mSimInfoContentObserver);
        }
    }

    private void unregisterReceivers() {
        logd("unregisterReceivers");
        mContext.unregisterReceiver(mReceiver);
        ContentResolver resolver = mContext.getContentResolver();
        if (resolver != null) {
            resolver.unregisterContentObserver(mMobileDataObserver);
            resolver.unregisterContentObserver(mSimInfoContentObserver);
        }
    }

    private void registerImsProvisionCallback() {
        logd("registerImsProvisionCallback");
        try {
            final Executor executor = mContext.getMainExecutor();

            // Register IMS MMTEL callback
            ImsMmTelManager mmtelManager = getImsMmTelManager(mSubId);
            if (mmtelManager != null) {
                mmtelManager.registerImsRegistrationCallback(executor, mMmtelRegistrationCallback);
                mmtelManager.registerMmTelCapabilityCallback(executor, mMmtelCapabilityCallback);
            }

            // Register provisioning changed callback
            ProvisioningManager provisioning = ProvisioningManager.createForSubscriptionId(mSubId);
            provisioning.registerProvisioningChangedCallback(executor, mProvisionChangedCallback);
            logd("registerImsProvisionCallback");
        } catch (ImsException e) {
            logw("registerImsProvisionCallback error: " + e);
            // Unregister the callback
            unregisterImsProvisionCallback();
            // Retry registering IMS content change callback
            mHandler.sendRegisterImsContentChangedMessage();
        }
    }

    private void unregisterImsProvisionCallback() {
        logd("unregisterImsProvisionCallback");

        // Clear the registering IMS callback message from the handler thread
        mHandler.removeRegisterImsContentChangedMessage();

        // Unregister IMS callback
        ImsMmTelManager imsMmtelManager = getImsMmTelManager(mSubId);
        if (imsMmtelManager != null) {
            try {
                imsMmtelManager.unregisterImsRegistrationCallback(mMmtelRegistrationCallback);
            } catch (RuntimeException e) {
                logw("unregister MMTel registration error: " + e.getMessage());
            }
            try {
                imsMmtelManager.unregisterMmTelCapabilityCallback(mMmtelCapabilityCallback);
            } catch (RuntimeException e) {
                logw("unregister MMTel capability error: " + e.getMessage());
            }
        }

        // Unregister provision changed callback
        ProvisioningManager provisioningMgr = ProvisioningManager.createForSubscriptionId(mSubId);
        try {
            provisioningMgr.unregisterProvisioningChangedCallback(mProvisionChangedCallback);
        } catch (RuntimeException e) {
            logw("unregister provisioning callback error: " + e.getMessage());
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            switch (intent.getAction()) {
                case TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED:
                    int preferredMode = intent.getIntExtra(TelecomManager.EXTRA_TTY_PREFERRED_MODE,
                            TelecomManager.TTY_MODE_OFF);
                    handleTtyPreferredModeChanged(preferredMode);
                    break;

                case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                    boolean airplaneMode = intent.getBooleanExtra("state", false);
                    handleAirplaneModeChanged(airplaneMode);
                    break;
            }
        }
    };

    private ContentObserver mMobileDataObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            boolean isEnabled = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.MOBILE_DATA, 1) == 1;
            handleMobileDataChanged(isEnabled);
        }
    };

    private ContentObserver mSimInfoContentObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            ImsMmTelManager ims = getImsMmTelManager(mSubId);
            if (ims == null) {
                logw("SimInfo changed error: MmTelManager is null");
                return;
            }

            try {
                boolean isEnabled = ims.isVtSettingEnabled();
                handleVtSettingChanged(isEnabled);
            } catch (RuntimeException e) {
                logw("SimInfo changed error: " + e);
            }
        }
    };

    private ImsMmTelManager getImsMmTelManager(int subId) {
        try {
            ImsManager imsManager = mContext.getSystemService(
                    android.telephony.ims.ImsManager.class);
            return (imsManager == null) ? null : imsManager.getImsMmTelManager(subId);
        } catch (IllegalArgumentException e) {
            logw("getImsMmTelManager error: " + e.getMessage());
            return null;
        }
    }

    private RegistrationManager.RegistrationCallback mMmtelRegistrationCallback =
            new RegistrationManager.RegistrationCallback() {
        @Override
        public void onRegistered(int imsTransportType) {
            synchronized (mLock) {
                logi("onRegistered: " + imsTransportType);
                handleImsMmtelRegistered(imsTransportType);
            }
        }

        @Override
        public void onUnregistered(ImsReasonInfo info) {
            synchronized (mLock) {
                logi("onUnregistered: reason=" + info);
                handleImsMmtelUnregistered();
            }
        }
    };

    private ImsMmTelManager.CapabilityCallback mMmtelCapabilityCallback =
            new CapabilityCallback() {
        @Override
        public void onCapabilitiesStatusChanged(MmTelCapabilities capabilities) {
            if (capabilities == null) {
                logw("onCapabilitiesStatusChanged: parameter is null");
                return;
            }
            synchronized (mLock) {
                handleMmtelCapabilitiesStatusChanged(capabilities);
            }
        }
    };

    private ProvisioningManager.Callback mProvisionChangedCallback =
            new ProvisioningManager.Callback() {
        @Override
        public void onProvisioningIntChanged(int item, int value) {
            logi("onProvisioningIntChanged: item=" + item + ", value=" + value);
            switch (item) {
                case ProvisioningManager.KEY_EAB_PROVISIONING_STATUS:
                case ProvisioningManager.KEY_VOLTE_PROVISIONING_STATUS:
                case ProvisioningManager.KEY_VT_PROVISIONING_STATUS:
                    handleProvisioningChanged();
                    break;
            }
        }
    };

    private class RegisterCallbackHandler extends Handler {
        private static final int EVENT_REGISTER_IMS_CONTENT_CHANGE = 1;

        private static final long REGISTER_IMS_CHANGED_DELAY = 5000L;  // 5 seconds

        RegisterCallbackHandler(DeviceCapabilityChangedListener listener, Looper looper) {
            super(looper);
        }

        private void sendRegisterImsContentChangedMessage() {
            Message msg = obtainMessage(EVENT_REGISTER_IMS_CONTENT_CHANGE);
            sendMessageDelayed(msg, REGISTER_IMS_CHANGED_DELAY);
        }

        private void removeRegisterImsContentChangedMessage() {
            removeMessages(EVENT_REGISTER_IMS_CONTENT_CHANGE);
        }

        @Override
        public void handleMessage(Message msg) {
            logd("handleMessage: " + msg.what);
            switch (msg.what) {
                case EVENT_REGISTER_IMS_CONTENT_CHANGE:
                    registerImsProvisionCallback();
                    break;
            }
        }
    }

    private void handleTtyPreferredModeChanged(int preferredMode) {
        logi("TTY preferred mode changed: " + preferredMode);
        boolean isChanged = mCapabilityInfo.updateTtyPreferredMode(preferredMode);
        if (isChanged) {
            mCapChangedCallback.onTtyPreferredModeChanged();
        }
    }

    private void handleAirplaneModeChanged(boolean state) {
        logi("Airplane mode changed: " + state);
        boolean isChanged = mCapabilityInfo.updateAirplaneMode(state);
        if (isChanged) {
            mCapChangedCallback.onAirplaneModeChanged();
        }
    }

    private void handleMobileDataChanged(boolean isEnabled) {
        logi("Mobile data changed: " + isEnabled);
        boolean isChanged = mCapabilityInfo.updateMobileData(isEnabled);
        if (isChanged) {
            mCapChangedCallback.onMobileDataChanged();
        }
    }

    private void handleVtSettingChanged(boolean isEnabled) {
        logi("VT setting changed: " + isEnabled);
        boolean isChanged = mCapabilityInfo.updateVtSetting(isEnabled);
        if (isChanged) {
            mCapChangedCallback.onVtSettingChanged();
        }
    }

    /*
     * This method is called when the MMTEL is registered.
     */
    private void handleImsMmtelRegistered(int imsTransportType) {
        mCapabilityInfo.updateImsMmtelRegistered(imsTransportType);
        mCapChangedCallback.onImsMmtelRegistered();
    }

    /*
     * This method is called when the MMTEL is unregistered.
     */
    private void handleImsMmtelUnregistered() {
        mCapabilityInfo.updateImsMmtelUnregistered();
        mCapChangedCallback.onImsMmtelUnregistered();
    }

    private void handleMmtelCapabilitiesStatusChanged(MmTelCapabilities capabilities) {
        logi("MMTel capabilities status changed");
        boolean isChanged = mCapabilityInfo.updateMmtelCapabilitiesChanged(capabilities);
        if (isChanged) {
            mCapChangedCallback.onFeatureCapabilityChanged();
        }
    }

    private void handleProvisioningChanged() {
        mCapChangedCallback.onProvisioningChanged();
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
