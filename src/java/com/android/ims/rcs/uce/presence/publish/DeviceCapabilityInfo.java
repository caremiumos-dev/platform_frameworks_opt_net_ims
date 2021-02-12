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
import android.telecom.TelecomManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactPresenceTuple.ServiceCapabilities;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsContactUceCapability.PresenceBuilder;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities;
import android.util.Log;

import com.android.ims.rcs.uce.util.UceUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Supplier;

/**
 * Stores the device's capabilities information.
 */
public class DeviceCapabilityInfo {
    private static final String LOG_TAG = UceUtils.getLogPrefix() + "DeviceCapabilityInfo";

    private static final String OVERRIDE_ADD_OPERATION = "add";
    private static final String OVERRIDE_REMOVE_OPERATION = "remove";
    private static final String OVERRIDE_CLEAR_OPERATION = "clear";
    private static final String OVERRIDE_LIST_OPERATION = "list";

    private static final HashMap<String, String> TEST_CAPABILITY_MAP = new HashMap<>();
    static {
        TEST_CAPABILITY_MAP.put(RcsContactPresenceTuple.SERVICE_ID_MMTEL, "mmtel");
        TEST_CAPABILITY_MAP.put(RcsContactPresenceTuple.SERVICE_ID_CHAT_V1, "chat_v1");
        TEST_CAPABILITY_MAP.put(RcsContactPresenceTuple.SERVICE_ID_CHAT_V2, "chat_v2");
        TEST_CAPABILITY_MAP.put(RcsContactPresenceTuple.SERVICE_ID_FT, "ft");
        TEST_CAPABILITY_MAP.put(RcsContactPresenceTuple.SERVICE_ID_FT_OVER_SMS, "ft_sms");
        TEST_CAPABILITY_MAP.put(RcsContactPresenceTuple.SERVICE_ID_GEO_PUSH, "geo_push");
        TEST_CAPABILITY_MAP.put(RcsContactPresenceTuple.SERVICE_ID_GEO_PUSH_VIA_SMS,
                "geo_push_sms");
        TEST_CAPABILITY_MAP.put(RcsContactPresenceTuple.SERVICE_ID_CALL_COMPOSER, "call_comp");
        TEST_CAPABILITY_MAP.put(RcsContactPresenceTuple.SERVICE_ID_POST_CALL, "call_post");
        TEST_CAPABILITY_MAP.put(RcsContactPresenceTuple.SERVICE_ID_SHARED_MAP, "map");
        TEST_CAPABILITY_MAP.put(RcsContactPresenceTuple.SERVICE_ID_SHARED_SKETCH, "sketch");
        TEST_CAPABILITY_MAP.put(RcsContactPresenceTuple.SERVICE_ID_CHATBOT, "chatbot");
        TEST_CAPABILITY_MAP.put(RcsContactPresenceTuple.SERVICE_ID_CHATBOT_STANDALONE,
                "chatbot_sa");
        TEST_CAPABILITY_MAP.put(RcsContactPresenceTuple.SERVICE_ID_CHATBOT_ROLE, "chatbot_role");
    }

    private final int mSubId;

    // The mmtel feature is registered or not
    private boolean mMmtelRegistered;

    // The network type which ims mmtel registers on.
    private int mMmtelNetworkRegType;

    // The rcs feature is registered or not
    private boolean mRcsRegistered;

    // The network type which ims rcs registers on.
    private int mRcsNetworkRegType;

    // The MMTel capabilities of this subscription Id
    private MmTelFeature.MmTelCapabilities mMmTelCapabilities;

    // Caps to add to the existing PUBLISH if they do not already exist (see TEST_CAPABILITY_MAP)
    private final ArrayList<String> mOverrideAddCaps = new ArrayList<>();
    // Caps to remove from the existing PUBLISH (see TEST_CAPABILITY_MAP)
    private final ArrayList<String> mOverrideRemoveCaps = new ArrayList<>();


    // Whether the settings are changed or not
    private int mTtyPreferredMode;
    private boolean mAirplaneMode;
    private boolean mMobileData;
    private boolean mVtSetting;

    public DeviceCapabilityInfo(int subId) {
        mSubId = subId;
        reset();
    }

    /**
     * Reset all the status.
     */
    public synchronized void reset() {
        logd("reset");
        mMmtelRegistered = false;
        mMmtelNetworkRegType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
        mRcsRegistered = false;
        mRcsNetworkRegType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
        mTtyPreferredMode = TelecomManager.TTY_MODE_OFF;
        mAirplaneMode = false;
        mMobileData = true;
        mVtSetting = true;
        mMmTelCapabilities = new MmTelCapabilities();
    }

    public synchronized boolean isImsRegistered() {
        return mMmtelRegistered;
    }

    /**
     * Update the status that IMS MMTEL is registered.
     */
    public synchronized void updateImsMmtelRegistered(int type) {
        StringBuilder builder = new StringBuilder();
        builder.append("IMS MMTEL registered: original state=").append(mMmtelRegistered)
                .append(", changes type from ").append(mMmtelNetworkRegType)
                .append(" to ").append(type);
        logi(builder.toString());

        if (!mMmtelRegistered) {
            mMmtelRegistered = true;
        }

        if (mMmtelNetworkRegType != type) {
            mMmtelNetworkRegType = type;
        }
    }

    /**
     * Update the status that IMS MMTEL is unregistered.
     */
    public synchronized void updateImsMmtelUnregistered() {
        logi("IMS MMTEL unregistered: original state=" + mMmtelRegistered);
        if (mMmtelRegistered) {
            mMmtelRegistered = false;
        }
        mMmtelNetworkRegType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
    }

    /**
     * Update the status that IMS RCS is registered.
     */
    public synchronized void updateImsRcsRegistered(int type) {
        StringBuilder builder = new StringBuilder();
        builder.append("IMS RCS registered: original state=").append(mRcsRegistered)
                .append(", changes type from ").append(mRcsNetworkRegType)
                .append(" to ").append(type);
        logi(builder.toString());

        if (!mRcsRegistered) {
            mRcsRegistered = true;
        }

        if (mRcsNetworkRegType != type) {
            mRcsNetworkRegType = type;
        }
    }

    /**
     * Update the status that IMS RCS is unregistered.
     */
    public synchronized void updateImsRcsUnregistered() {
        logi("IMS RCS unregistered: original state=" + mRcsRegistered);
        if (mRcsRegistered) {
            mRcsRegistered = false;
        }
        mRcsNetworkRegType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
    }

    /**
     * Update the TTY preferred mode.
     * @return {@code true} if tty preferred mode is changed, {@code false} otherwise.
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
     * Update airplane mode state.
     * @return {@code true} if the airplane mode is changed, {@code false} otherwise.
     */
    public synchronized boolean updateAirplaneMode(boolean state) {
        if (mAirplaneMode != state) {
            logd("Airplane mode changes from " + mAirplaneMode + " to " + state);
            mAirplaneMode = state;
            return true;
        }
        return false;
    }

    /**
     * Update mobile data setting.
     * @return {@code true} if the mobile data setting is changed, {@code false} otherwise.
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
     * Update VT setting.
     * @return {@code true} if vt setting is changed, {@code false}.otherwise.
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
     * @return {@code true} if the mmtel capabilities are changed, {@code false} otherwise.
     */
    public synchronized boolean updateMmtelCapabilitiesChanged(MmTelCapabilities capabilities) {
        if (capabilities == null) {
            return false;
        }
        boolean oldVolteAvailable = isVolteAvailable(mMmtelNetworkRegType, mMmTelCapabilities);
        boolean oldVoWifiAvailable = isVoWifiAvailable(mMmtelNetworkRegType, mMmTelCapabilities);
        boolean oldVtAvailable = isVtAvailable(mMmtelNetworkRegType, mMmTelCapabilities);
        boolean oldViWifiAvailable = isViWifiAvailable(mMmtelNetworkRegType, mMmTelCapabilities);
        boolean oldCallComposerAvailable = isCallComposerAvailable(mMmTelCapabilities);

        boolean volteAvailable = isVolteAvailable(mMmtelNetworkRegType, capabilities);
        boolean voWifiAvailable = isVoWifiAvailable(mMmtelNetworkRegType, capabilities);
        boolean vtAvailable = isVtAvailable(mMmtelNetworkRegType, capabilities);
        boolean viWifiAvailable = isViWifiAvailable(mMmtelNetworkRegType, capabilities);
        boolean callComposerAvailable = isCallComposerAvailable(capabilities);

        logd("updateMmtelCapabilitiesChanged: from " + mMmTelCapabilities + " to " + capabilities);

        // Update to the new mmtel capabilities
        mMmTelCapabilities = deepCopyCapabilities(capabilities);

        if (oldVolteAvailable != volteAvailable
                || oldVoWifiAvailable != voWifiAvailable
                || oldVtAvailable != vtAvailable
                || oldViWifiAvailable != viWifiAvailable
                || oldCallComposerAvailable != callComposerAvailable) {
            return true;
        }
        return false;
    }

    public synchronized String overrideCapabilitiesChanged(Context context, String command,
            String capabilties) {
        ArrayList<String> overrideList = null;
        switch (command) {
            case OVERRIDE_ADD_OPERATION:
                overrideList = mOverrideAddCaps;
                break;
            case OVERRIDE_REMOVE_OPERATION:
                overrideList = mOverrideRemoveCaps;
                break;
            case OVERRIDE_LIST_OPERATION:
                // Intentionally nothing here
                break;
            case OVERRIDE_CLEAR_OPERATION:
                mOverrideAddCaps.clear();
                mOverrideRemoveCaps.clear();
                break;
        }
        if (overrideList != null) {
            overrideList.clear();
            String[] caps = capabilties.split(",");
            for (String cap : caps) {
                if (TEST_CAPABILITY_MAP.containsValue(cap)) {
                    overrideList.add(cap);
                }
            }
        }

        // Update mCurrentCaps and return it.
        RcsContactUceCapability caps = getDeviceCapabilities(context);
        return getReadableServiceIds(caps);
    }

    private String getReadableServiceIds(RcsContactUceCapability c) {
        StringBuilder builder = new StringBuilder(" [");
        for (RcsContactPresenceTuple tuple : c.getCapabilityTuples()) {
            builder.append("(");
            String id = TEST_CAPABILITY_MAP.getOrDefault(tuple.getServiceId(),
                    tuple.getServiceId());
            String version = tuple.getServiceVersion();
            builder.append("id=");
            builder.append(id);
            builder.append(", v=");
            builder.append(version);
            ServiceCapabilities servCaps = tuple.getServiceCapabilities();
            if(servCaps != null) {
                builder.append(", aud=");
                builder.append(servCaps.isAudioCapable());
                builder.append(", vid=");
                builder.append(servCaps.isVideoCapable());
                builder.append(", suppDup=");
                builder.append(servCaps.getSupportedDuplexModes());
                builder.append(", unsuppDup=");
                builder.append(servCaps.getUnsupportedDuplexModes());
            }
            builder.append(") ");
        }
        builder.append("] ");
        return builder.toString();
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

    private boolean isCallComposerAvailable(MmTelCapabilities capabilities) {
        return capabilities.isCapable(
                MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER);
    }

    /**
     * Get the device's capabilities.
     */
    public synchronized RcsContactUceCapability getDeviceCapabilities(Context context) {
        Uri uri = PublishUtils.getPublishingUri(context, mSubId);
        if (uri == null) {
            logw("getDeviceCapabilities: uri is empty");
            return null;
        }
        PresenceBuilder presenceBuilder = new PresenceBuilder(uri,
                RcsContactUceCapability.SOURCE_TYPE_CACHED,
                RcsContactUceCapability.REQUEST_RESULT_FOUND);

        //MMTEL
        addMmTelCapability(presenceBuilder, uri);

        // Call Composer
        addCapability(presenceBuilder, RcsContactPresenceTuple.SERVICE_ID_CALL_COMPOSER, "2.0", uri,
                () -> mMmTelCapabilities != null && mMmTelCapabilities.isCapable(
                MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER));

        // Chat V1
        addCapability(presenceBuilder, RcsContactPresenceTuple.SERVICE_ID_CHAT_V1, "1.0", uri,
                () -> false /*do not support this yet*/);

        // Chat V2
        addCapability(presenceBuilder, RcsContactPresenceTuple.SERVICE_ID_CHAT_V2, "2.0", uri,
                () -> false /*do not support this yet*/);

        // File Transfer
        addCapability(presenceBuilder, RcsContactPresenceTuple.SERVICE_ID_FT, "1.0", uri,
                () -> false /*do not support this yet*/);

        // File Transfer over SMS
        addCapability(presenceBuilder, RcsContactPresenceTuple.SERVICE_ID_FT_OVER_SMS, "1.0", uri,
                () -> false /*do not support this yet*/);

        // GEO push
        addCapability(presenceBuilder, RcsContactPresenceTuple.SERVICE_ID_GEO_PUSH, "1.0", uri,
                () -> false /*do not support this yet*/);

        // GEO push over SMS
        addCapability(presenceBuilder, RcsContactPresenceTuple.SERVICE_ID_GEO_PUSH_VIA_SMS, "1.0",
                uri, () -> false /*do not support this yet*/);

        // post call communication
        addCapability(presenceBuilder, RcsContactPresenceTuple.SERVICE_ID_POST_CALL, "1.0", uri,
                () -> false /*do not support this yet*/);

        // shared map
        addCapability(presenceBuilder, RcsContactPresenceTuple.SERVICE_ID_SHARED_MAP, "1.0", uri,
                () -> false /*do not support this yet*/);

        // shared sketch
        addCapability(presenceBuilder, RcsContactPresenceTuple.SERVICE_ID_SHARED_SKETCH, "1.0",
                uri, () -> false /*do not support this yet*/);

        // chatbot
        // Assume 1.0 right now, upgrade to detecting version based on botversion tag
        addCapability(presenceBuilder, RcsContactPresenceTuple.SERVICE_ID_CHATBOT, "1.0", uri,
                () -> false /*do not support this yet*/);

        // chatbot standalone
        // Assume 1.0 right now, upgrade to detecting version based on botversion tag
        addCapability(presenceBuilder, RcsContactPresenceTuple.SERVICE_ID_CHATBOT_STANDALONE, "1.0",
                uri, () -> false /*do not support this yet*/);

        // chatbot role
        // Assume 1.0 right now, upgrade to detecting version based on botversion tag
        addCapability(presenceBuilder, RcsContactPresenceTuple.SERVICE_ID_CHATBOT_ROLE, "1.0",
                uri, () -> false /*do not support this yet*/);

        return presenceBuilder.build();
    }

    private synchronized void addMmTelCapability(
            RcsContactUceCapability.PresenceBuilder presenceBuilder, Uri contactUri) {
        if (isRemovedOverride(RcsContactPresenceTuple.SERVICE_ID_MMTEL)) {
            return;
        }

        ServiceCapabilities.Builder servCapsBuilder = new ServiceCapabilities.Builder(
                hasVolteCapability(), hasVtCapability());
        servCapsBuilder.addSupportedDuplexMode(ServiceCapabilities.DUPLEX_MODE_FULL);
        RcsContactPresenceTuple.Builder tupleBuilder = new RcsContactPresenceTuple.Builder(
                RcsContactPresenceTuple.TUPLE_BASIC_STATUS_OPEN,
                RcsContactPresenceTuple.SERVICE_ID_MMTEL, "1.0");
        tupleBuilder.setContactUri(contactUri).setServiceCapabilities(servCapsBuilder.build());
        presenceBuilder.addCapabilityTuple(tupleBuilder.build());
    }

    // Check if the device has the VoLTE capability
    private synchronized boolean hasVolteCapability() {
        boolean isCapable = mMmTelCapabilities != null
                && mMmTelCapabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_VOICE);

        return isCapableWithOverride(isCapable, RcsContactPresenceTuple.SERVICE_ID_MMTEL);
    }

    // Check if the device has the VT capability
    private synchronized boolean hasVtCapability() {
        boolean isCapable = mMmTelCapabilities != null
                && mMmTelCapabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_VIDEO);

        return isCapableWithOverride(isCapable, RcsContactPresenceTuple.SERVICE_ID_MMTEL);
    }

    private synchronized void addCapability(RcsContactUceCapability.PresenceBuilder presenceBuilder,
            String serviceId, String version, Uri contactUri, Supplier<Boolean> capableCheck) {
        boolean isCapable = isCapableWithOverride(capableCheck.get(), serviceId);
        if (!isCapable) return;

        RcsContactPresenceTuple.Builder builder = new RcsContactPresenceTuple.Builder(
                RcsContactPresenceTuple.TUPLE_BASIC_STATUS_OPEN, serviceId, version);
        presenceBuilder.addCapabilityTuple(builder.setContactUri(contactUri).build());
    }

    /**
     * Checks override testing values and returns whether or not the service iD should be incldued
     * in the PIDF XML.
     * @param isCapable Is the serviceId capable
     * @param serviceId The service ID that the check should be performed for.
     * @return true if the capability should be added to PIDF XML, false if it should not be.
     */
    private boolean isCapableWithOverride(boolean isCapable, String serviceId) {
        if (isAddedOverride(serviceId)) {
            return true;
        }
        return isCapable && !isRemovedOverride(serviceId);
    }

    private boolean isRemovedOverride(String serviceId) {
        return mOverrideRemoveCaps.contains(TEST_CAPABILITY_MAP.get(serviceId));
    }

    private boolean isAddedOverride(String serviceId) {
        return mOverrideAddCaps.contains(TEST_CAPABILITY_MAP.get(serviceId));
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
        if (capabilities.isCapable(MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER)) {
            mmTelCapabilities.addCapabilities(MmTelCapabilities.CAPABILITY_TYPE_CALL_COMPOSER);
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
