/*
 * Copyright (c) 2019 The Android Open Source Project
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

package com.android.ims;

import android.content.Context;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.aidl.IRcsFeatureListener;
import android.telephony.ims.feature.CapabilityChangeRequest;
import android.telephony.ims.feature.RcsFeature.RcsImsCapabilities;
import android.util.Log;

import com.android.ims.RcsFeatureConnection.RcsFeatureConnectionCallback;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;

public class RcsFeatureManager {
    private static final String TAG = "RcsFeatureManager";

    private final int mSlotId;
    private final Context mContext;
    private RcsFeatureConnection mRcsFeatureConnection;

    public RcsFeatureManager(Context context, int slotId) {
        Log.d(TAG, "RcsFeatureManager slotId: " + slotId);
        mContext = context;
        mSlotId = slotId;

        mRcsFeatureConnection = RcsFeatureConnection.create(mContext, mSlotId);
        mRcsFeatureConnection.setConnectionCallback(mConnectionCallback);
    }

    /**
     * The callback to receive updated from RcsFeatureConnection
     */
    public RcsFeatureConnectionCallback mConnectionCallback =
            new RcsFeatureConnectionCallback() {
        @Override
        public void RcsFeatureCreated() {
            Log.d(TAG, "RcsFeatureCreated");
            setRcsFeatureListener();
            changeEnabledCapabilitiesAfterRcsFeatureCreated();
        }

        @Override
        public void RcsFeatureRemoved() {
        }

        @Override
        public void RcsFeatureStatusChanged(int status) {
        }
    };

    /**
     * Set listener and it will trigger onFeatureReady in RcsFeature
     */
    private void setRcsFeatureListener() {
        try {
            mRcsFeatureConnection.setRcsFeatureListener(mRcsFeatureListener);
        } catch (RemoteException e) {
            Log.e(TAG, "setRcsFeatureListener " + e);
        }
    }

    /**
     * The callback to receive updated from RcsFeature
     */
    protected IRcsFeatureListener mRcsFeatureListener = new IRcsFeatureListener.Stub() {
        @Override
        public void onCommandUpdate(int commandCode, int operationToken) {
        }

        @Override
        public void onNetworkResponse(int code, String reason, int operationToken) {
        }

        @Override
        public void onCapabilityRequestResponsePresence(
                List<RcsContactUceCapability> infos, int operationToken) {
        }

        @Override
        public void onNotifyUpdateCapabilities() {
        }

        @Override
        public void onUnpublish() {
        }

        @Override
        public void onCapabilityRequestResponseOptions(
                int code, String reason, RcsContactUceCapability info, int operationToken) {
        }

        @Override
        public void onRemoteCapabilityRequest(
                Uri contactUri, RcsContactUceCapability remoteInfo, int operationToken) {
        }
    };

    /**
     * Enable/Disable UCE capabilities after RcsFeature has already been created.
     */
    private void changeEnabledCapabilitiesAfterRcsFeatureCreated() {
        if (isOptionsSupported()) {
            enableRcsUceCapability(RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE);
        } else if (isPresenceSupported()) {
            enableRcsUceCapability(RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE);
        } else {
            disableRcsUceCapabilities();
        }
    }

    /**
     * Enable UCE capabilities with given type.
     * @param capabilityType the
     */
    public void enableRcsUceCapability(
            @RcsImsCapabilities.RcsImsCapabilityFlag int capabilityType) {
        try {
            int radioTech = mRcsFeatureConnection.getRegistrationTech();
            CapabilityChangeRequest request = new CapabilityChangeRequest();
            request.addCapabilitiesToEnableForTech(capabilityType, radioTech);

            Log.i(TAG, "enableRcsUceCapability: " + capabilityType + ", radioTech=" + radioTech);
            mRcsFeatureConnection.changeEnabledCapabilities(request, null);
        } catch (RemoteException e) {
            Log.e(TAG, "enableRcsUceCapability " + e);
        }
    }

    /**
     * Disable UCE capabilities.
     */
    public void disableRcsUceCapabilities() {
        try {
            int radioTech = mRcsFeatureConnection.getRegistrationTech();
            CapabilityChangeRequest request = new CapabilityChangeRequest();
            request.addCapabilitiesToDisableForTech(
                    RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE, radioTech);
            request.addCapabilitiesToDisableForTech(
                    RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE, radioTech);

            Log.i(TAG, "disableRcsUceCapabilities: radioTech=" + radioTech);
            mRcsFeatureConnection.changeEnabledCapabilities(request, null);
        } catch (RemoteException e) {
            Log.e(TAG, "disableRcsUceCapabilities " + e);
        }
    }

    private boolean isOptionsSupported() {
        return isCapabilityTypeSupported(mContext, mSlotId,
            RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE);
    }

    private boolean isPresenceSupported() {
        return isCapabilityTypeSupported(mContext, mSlotId,
            RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE);
    }

    /**
     * Check if RCS UCE feature is supported by carrier.
     */
    public static boolean isRcsUceSupportedByCarrier(Context context, int slotId) {
        boolean isOptionsSupported = isCapabilityTypeSupported(
            context, slotId, RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE);
        boolean isPresenceSupported = isCapabilityTypeSupported(
            context, slotId, RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE);

        Log.i(TAG, "isRcsUceSupported: options=" + isOptionsSupported
            + ", Presence=" + isPresenceSupported);

        return isOptionsSupported | isPresenceSupported;
    }

    /*
     * Check if the given type of capability is supported.
     */
    private static boolean isCapabilityTypeSupported(
        Context context, int slotId, int capabilityType) {
        int subId = sSubscriptionManagerProxy.getSubId(slotId);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.e(TAG, "isCapabilityTypeSupported: Getting subIds is failure! slotId=" + slotId);
            return false;
        }

        CarrierConfigManager configManager =
            (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager == null) {
            return false;
        }

        PersistableBundle b = configManager.getConfigForSubId(subId);
        if (b == null) {
            return false;
        }

        if (capabilityType == RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE) {
            return b.getBoolean(CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL, false);
        } else if (capabilityType == RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE) {
            return b.getBoolean(CarrierConfigManager.KEY_USE_RCS_PRESENCE_BOOL, false);
        }
        return false;
    }

    /**
     * Testing interface used to mock SubscriptionManager in testing
     * @hide
     */
    @VisibleForTesting
    public interface SubscriptionManagerProxy {
        /**
         * Mock-able interface for {@link SubscriptionManager#getSubId(int)} used for testing.
         */
        int getSubId(int slotId);
    }

    private static SubscriptionManagerProxy sSubscriptionManagerProxy
            = slotId -> {
                int[] subIds = SubscriptionManager.getSubId(slotId);
                if (subIds != null) {
                    Log.d(TAG, "getSubId : " + subIds[0]);
                    return subIds[0];
                }
                return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            };

    /**
     * Testing function used to mock SubscriptionManager in testing
     * @hide
     */
    @VisibleForTesting
    public static void setSubscriptionManager(SubscriptionManagerProxy proxy) {
        sSubscriptionManagerProxy = proxy;
    }
}
