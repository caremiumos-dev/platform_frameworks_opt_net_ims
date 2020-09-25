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

package com.android.ims.rcs.uce.util;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ProvisioningManager;
import android.util.Log;

public class UceSettingUtils {
    private static final String LOG_TAG = "UceSettingUtils";

    // Default throttle for publication in milliseconds
    private static final int DEFAULT_PUBLISH_THROTTLE_MS = 60000;

    // The maximum number of the publication retries.
    private static final int DEFAULT_PUBLISH_MAXIMUM_NUM_RETRIES = 3;

    public static int getPublishMaximumRetries() {
        return DEFAULT_PUBLISH_MAXIMUM_NUM_RETRIES;
    }

    public static int getPublishThrottle(int subId) {
        int throttle = DEFAULT_PUBLISH_THROTTLE_MS;
        try {
            ProvisioningManager manager = ProvisioningManager.createForSubscriptionId(subId);
            throttle = manager.getProvisioningIntValue(
                    ProvisioningManager.KEY_RCS_PUBLISH_SOURCE_THROTTLE_MS);
        } catch (Exception e) {
            Log.w(LOG_TAG, e.getMessage());
        }
        return throttle;
    }

    public static boolean isEabProvisioned(Context context, int subId) {
        boolean isProvisioned = false;
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            Log.w(LOG_TAG, "isEabProvisioned: invalid subscriptionId " + subId);
            return false;
        }
        CarrierConfigManager configManager = (CarrierConfigManager)
                context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager != null) {
            PersistableBundle config = configManager.getConfigForSubId(subId);
            if (config != null && !config.getBoolean(
                    CarrierConfigManager.KEY_CARRIER_VOLTE_PROVISIONED_BOOL)) {
                return true;
            }
        }
        try {
            ProvisioningManager manager = ProvisioningManager.createForSubscriptionId(subId);
            isProvisioned = manager.getProvisioningIntValue(
                    ProvisioningManager.KEY_EAB_PROVISIONING_STATUS)
                    == ProvisioningManager.PROVISIONING_VALUE_ENABLED;
        } catch (Exception e) {
            Log.w(LOG_TAG, "isEabProvisioned: exception=" + e.getMessage());
        }
        return isProvisioned;
    }
}