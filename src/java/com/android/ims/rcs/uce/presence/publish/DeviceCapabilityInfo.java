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
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.TelephonyManager;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities;
import android.util.Log;

/**
 * Stores the device's capabilities information.
 */
public class DeviceCapabilityInfo {
    private static final String LOG_TAG = "DeviceCapabilityInfo";

    private final int mSubId;

    // The mmtel feature is registered or not
    private boolean mImsMmtelRegistered;

    // The network type which ims registers on.
    private int mNetworkRegType;

    // The MMTel capabilities of this subscription Id
    private MmTelFeature.MmTelCapabilities mMmTelCapabilities;

    private int mTtyPreferredMode;
    private boolean mAirplaneMode;
    private boolean mMobileData;
    private boolean mVtSetting;

    public DeviceCapabilityInfo(int subId) {
        mSubId = subId;
        reset();
    }

    /**
     * reset all the status.
     */
    public synchronized void reset() {
        logd("reset");
        mImsMmtelRegistered = false;
        mNetworkRegType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
        mTtyPreferredMode = TelecomManager.TTY_MODE_OFF;
        mAirplaneMode = false;
        mMobileData = true;
        mVtSetting = true;
        mMmTelCapabilities = new MmTelCapabilities();
    }

    public synchronized boolean isImsRegistered() {
        return mImsMmtelRegistered;
    }

    /**
     * Update the status that IMS MMTEL is registered.
     */
    public synchronized void updateImsMmtelRegistered(int type) {
        StringBuilder builder = new StringBuilder();
        builder.append("IMS MMTEL registered: original state=").append(mImsMmtelRegistered)
                .append(", changes type from ").append(mNetworkRegType).append(" to ").append(type);
        logi(builder.toString());

        if (!mImsMmtelRegistered) {
            mImsMmtelRegistered = true;
        }

        if (mNetworkRegType != type) {
            mNetworkRegType = type;
        }
    }

    /**
     * Update the status that IMS MMTEL is unregistered.
     */
    public synchronized void updateImsMmtelUnregistered() {
        logi("IMS MMTEL unregistered: original state=" + mImsMmtelRegistered);
        if (mImsMmtelRegistered) {
            mImsMmtelRegistered = false;
        }
        mNetworkRegType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
    }

    /**
     * Update the TTY preferred mode
     * @return true if tty preferred mode is changed.
     */
    public synchronized boolean updateTtyPreferredMode(int ttyMode) {
        if (mTtyPreferredMode != ttyMode) {
            logd("TTY preferred mode changes from " + mTtyPreferredMode + " to " + ttyMode);
            mTtyPreferredMode = ttyMode;
            return true;
        }
        return false;
    }

    /**
     * Update airplane mode state
     * @return true if the airplane mode is changed.
     */
    public synchronized boolean updateAirplaneMode(boolean state) {
        if (mAirplaneMode != state) {
            logd("Airplane mode changes from " + mAirplaneMode + " to " + state);
            mAirplaneMode = state;
            return true;
        }
        return false;
    }

    public synchronized boolean isAirplaneMode() {
        return mAirplaneMode;
    }

    /**
     * Update mobile data setting
     * @return true if the mobile data setting is changed.
     */
    public synchronized boolean updateMobileData(boolean mobileData) {
        if (mMobileData != mobileData) {
            logd("Mobile data changes from " + mMobileData + " to " + mobileData);
            mMobileData = mobileData;
            return true;
        }
        return false;
    }

    /**
     * Update VT setting
     * @return true if vt setting is changed
     */
    public synchronized boolean updateVtSetting(boolean vtSetting) {
        if (mVtSetting != vtSetting) {
            logd("VT setting changes from " + mVtSetting + " to " + vtSetting);
            mVtSetting = vtSetting;
            return true;
        }
        return false;
    }

    /**
     * Update the MMTEL capabilities if the capabilities is changed.
     */
    public synchronized boolean updateMmtelCapabilitiesChanged(MmTelCapabilities capabilities) {
        if (capabilities == null) {
            return false;
        }
        boolean oldVolteAvailable = isVolteAvailable(mNetworkRegType, mMmTelCapabilities);
        boolean oldVoWifiAvailable = isVoWifiAvailable(mNetworkRegType, mMmTelCapabilities);
        boolean oldVtAvailable = isVtAvailable(mNetworkRegType, mMmTelCapabilities);
        boolean oldViWifiAvailable = isViWifiAvailable(mNetworkRegType, mMmTelCapabilities);

        boolean volteAvailable = isVolteAvailable(mNetworkRegType, capabilities);
        boolean voWifiAvailable = isVoWifiAvailable(mNetworkRegType, capabilities);
        boolean vtAvailable = isVtAvailable(mNetworkRegType, capabilities);
        boolean viWifiAvailable = isViWifiAvailable(mNetworkRegType, capabilities);

        logd("updateMmtelCapabilitiesChanged: from " + mMmTelCapabilities + " to " + capabilities);

        // Update to the new mmtel capabilities
        mMmTelCapabilities = deepCopyCapabilities(capabilities);

        if (oldVolteAvailable != volteAvailable
                || oldVoWifiAvailable != voWifiAvailable
                || oldVtAvailable != vtAvailable
                || oldViWifiAvailable != viWifiAvailable) {
            return true;
        }
        return false;
    }

    private boolean isVolteAvailable(int networkRegType, MmTelCapabilities capabilities) {
        return (networkRegType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                && capabilities.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE);
    }

    private boolean isVoWifiAvailable(int networkRegType, MmTelCapabilities capabilities) {
        return (networkRegType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                && capabilities.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE);
    }

    private boolean isVtAvailable(int networkRegType, MmTelCapabilities capabilities) {
        return (networkRegType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                && capabilities.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO);
    }

    private boolean isViWifiAvailable(int networkRegType, MmTelCapabilities capabilities) {
        return (networkRegType == AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                && capabilities.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO);
    }

    /**
     * Get the device capabilities
     */
    public synchronized RcsContactUceCapability getDeviceCapabilities(Context context) {
        TelephonyManager telephonyMgr = context.getSystemService(TelephonyManager.class)
                .createForSubscriptionId(mSubId);

        Uri uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, telephonyMgr.getLine1Number(), null);
        RcsContactUceCapability.Builder builder = new RcsContactUceCapability.Builder(uri);
        if (hasVolteCapability()) {
            builder.add(RcsContactUceCapability.CAPABILITY_IP_VOICE_CALL);
        }
        if (hasVtCapability()) {
            builder.add(RcsContactUceCapability.CAPABILITY_IP_VIDEO_CALL);
        }
        return builder.build();
    }

    /**
     * Check if device has the volte capability
     */
    public synchronized boolean hasVolteCapability() {
        if (mMmTelCapabilities != null
                && mMmTelCapabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_VOICE)) {
            return true;
        }
        return false;
    }

    /**
     * Check if device has the VT capability
     */
    public synchronized boolean hasVtCapability() {
        if (mMmTelCapabilities != null
                && mMmTelCapabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_VIDEO)) {
            return true;
        }
        return false;
    }

    private synchronized MmTelCapabilities deepCopyCapabilities(MmTelCapabilities capabilities) {
        MmTelCapabilities mmTelCapabilities = new MmTelCapabilities();
        if (capabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_VOICE)) {
            mmTelCapabilities.addCapabilities(MmTelCapabilities.CAPABILITY_TYPE_VOICE);
        }
        if (capabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_VIDEO)) {
            mmTelCapabilities.addCapabilities(MmTelCapabilities.CAPABILITY_TYPE_VIDEO);
        }
        if (capabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_UT)) {
            mmTelCapabilities.addCapabilities(MmTelCapabilities.CAPABILITY_TYPE_UT);
        }
        if (capabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_SMS)) {
            mmTelCapabilities.addCapabilities(MmTelCapabilities.CAPABILITY_TYPE_SMS);
        }
        return mmTelCapabilities;
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
