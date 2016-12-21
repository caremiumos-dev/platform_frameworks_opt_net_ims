/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.ims;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.UserHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.util.SparseArray;

import com.android.ims.feature.ImsFeature;
import com.android.ims.internal.IImsService;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.PhoneConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Creates a list of ImsServices that are available to bind to based on the Device configuration
 * overlay value "config_ims_package" and Carrier Configuration value "config_ims_package_override".
 * These ImsServices are then bound to in the following order:
 *
 * 1. Carrier Config defined override value per SIM.
 * 2. Device overlay default value (including no SIM case).
 *
 * ImsManager can then retrieve the binding to the correct ImsService using {@link #getImsFeature}
 * on a per-slot and per feature basis.
 */

public class ImsResolver {

    public static final String SERVICE_INTERFACE = "android.ims.ImsService";
    public static final String METADATA_EMERGENCY_VOICE_FEATURE
            = "android.ims.EMERGENCY_VOICE_FEATURE";
    public static final String METADATA_VOICE_FEATURE = "android.ims.VOICE_FEATURE";
    public static final String METADATA_RCS_FEATURE = "android.ims.RCS_FEATURE";

    private static class ImsServiceInfo {
        public ComponentName name;
        public List<Integer> supportedFeatures;
    }

    // Receives broadcasts from the system involving changes to the installed applications. If
    // an ImsService that we are configured to use is installed, we must bind to it.
    private BroadcastReceiver mAppChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final String packageName = intent.getData().getSchemeSpecificPart();
            switch (action) {
                case Intent.ACTION_PACKAGE_ADDED:
                // intentional fall-through
                case Intent.ACTION_PACKAGE_CHANGED:
                    possiblyAddedImsService(packageName);
                    break;
                case Intent.ACTION_PACKAGE_REMOVED:
                    possiblyRemovedImsService(packageName);
                    break;
                default:
                    return;
            }
        }
    };

    // Receives the broadcast that a new Carrier Config has been loaded in order to possibly
    // unbind from one service and bind to another.
    private BroadcastReceiver mConfigChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            int slotId = mSubscriptionManagerProxy.getSlotId(
                    intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                            SubscriptionManager.INVALID_SUBSCRIPTION_ID));

            if (slotId == SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                Log.i(TAG, "Received SIM change for invalid slot id.");
                return;
            }

            Log.i(TAG, "Received Carrier Config Changed for Slot: " + slotId);

            carrierConfigChanged(slotId);
        }
    };

    public interface DeviceServiceQuery {
        /**
         * @return A String containing the package name of the device's default ImsService or null
         * if IMS is not supported by default.
         */
        String getPackageName();
    }

    // Testing interface to mock SubscriptionManager
    interface SubscriptionManagerProxy {
        int getSlotId(int id);
    }

    private static final String TAG = "ImsResolver";
    private SubscriptionManagerProxy mSubscriptionManagerProxy = SubscriptionManager::getSlotId;
    private DeviceServiceQuery mDeviceServiceQuery;
    private Context mContext;

    // Package name of the default device service.
    private String mDeviceService;
    // List index corresponds to slot Id associated with the service package name.
    private List<String> mCarrierServices;

    // List index corresponds to Slot Id, Maps ImsFeature.FEATURE->bound IImsService
    private SparseArray<SparseArray<IImsService>> mBoundImsServicesBySlot;

    // Map of supported features and the package names of the ImsServices that support them.
    private HashMap<Integer, List<ImsServiceInfo>> mInstalledServiceCache;

    public ImsResolver(Context context, DeviceServiceQuery deviceServiceQuery) {
        mContext = context;
        mDeviceServiceQuery = deviceServiceQuery;

        IntentFilter appChangedFilter = new IntentFilter();
        appChangedFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        appChangedFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        appChangedFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        appChangedFilter.addDataScheme("package");
        context.registerReceiverAsUser(mAppChangedReceiver, UserHandle.ALL, appChangedFilter, null,
                null);

        context.registerReceiver(mConfigChangedReceiver, new IntentFilter(
                CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));

        mDeviceService = getDeviceImsServicePackageName();
    }

    @VisibleForTesting
    public void setSubscriptionManagerProxy(SubscriptionManagerProxy proxy) {
        mSubscriptionManagerProxy = proxy;
    }

    /**
     * Returns the {@link IImsService} that corresponds to the given slot Id and IMS feature or
     * {@link null} if the service is not available.
     * @param slotId The SIM slot that we are requesting the {@link IImsService} for.
     * @param feature The IMS Feature we are requesting.
     * @return {@link IImsService} interface for the feature specified or {@link null} if it is
     * unavailable.
     */
    public IImsService getImsFeature(int slotId, @ImsFeature.Feature int feature) {
        SparseArray<IImsService> services = mBoundImsServicesBySlot.get(slotId);
        if (services == null) {
            return null;
        }
        return services.get(feature);
    }

    private void putImsService(int slotId, @ImsFeature.Feature int feature, IImsService service) {
        SparseArray<IImsService> services = mBoundImsServicesBySlot.get(slotId);
        if (services == null) {
            services = new SparseArray<>();
            mBoundImsServicesBySlot.put(feature, services);
        }
        services.put(slotId, service);
    }

    private String getDeviceImsServicePackageName() {
        return mDeviceServiceQuery.getPackageName();
    }

    private void possiblyAddedImsService(String packageName) {

    }

    private void possiblyRemovedImsService(String packageName) {

    }

    private void carrierConfigChanged(int slotId) {

    }

    private List<ImsServiceInfo> getImsServiceInfo(String packageName) {
        List<ImsServiceInfo> infos = new ArrayList<>(1);

        Intent serviceIntent = new Intent(SERVICE_INTERFACE);
        if (packageName != null) {
            serviceIntent.setPackage(packageName);
        }

        PackageManager packageManager = mContext.getPackageManager();
        for (ResolveInfo entry : packageManager.queryIntentServicesAsUser(
                serviceIntent,
                PackageManager.GET_META_DATA,
                ActivityManager.getCurrentUser())) {
            ServiceInfo serviceInfo = entry.serviceInfo;

            if (serviceInfo != null) {
                ImsServiceInfo info = new ImsServiceInfo();
                info.name = new ComponentName(serviceInfo.packageName, serviceInfo.name);
                // Add all supported features
                if (serviceInfo.metaData != null) {
                    if(serviceInfo.metaData.getBoolean(METADATA_EMERGENCY_VOICE_FEATURE, false)) {
                        info.supportedFeatures.add(ImsFeature.EMERGENCY_VOICE);
                    }
                    if(serviceInfo.metaData.getBoolean(METADATA_VOICE_FEATURE, false)) {
                        info.supportedFeatures.add(ImsFeature.VOICE);
                    }
                    if(serviceInfo.metaData.getBoolean(METADATA_RCS_FEATURE, false)) {
                        info.supportedFeatures.add(ImsFeature.RCS);
                    }
                }
                infos.add(info);
            }
        }
        return infos;
    }
}