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

import android.content.Context;
import android.net.Uri;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ProvisioningManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.ims.internal.ContactNumberUtils;

/**
 * The util class of the UCE Setting.
 */
public class UceSettingUtil {

    private static final String LOG_TAG = "UceSettingUtil";

    private static final long DEFAULT_PUBLISH_TIME_INTERVAL = 60L;  // 60 seconds

    /**
     * Check if SIP OPTIONS is supported by the carrier.
     */
    public static boolean isSipOptionsSupported(Context context, int subId) {
        CarrierConfigManager configManager = (CarrierConfigManager)
                context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager == null) {
            return false;
        }
        PersistableBundle config = configManager.getConfigForSubId(subId);
        if (config == null) {
            return false;
        }
        return config.getBoolean(CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL);
    }

    /**
     * Check if Presence is supported by the carrier.
     */
    public static boolean isPresenceSupported(Context context, int subId) {
        CarrierConfigManager configManager = (CarrierConfigManager)
                context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager == null) {
            return false;
        }
        PersistableBundle config = configManager.getConfigForSubId(subId);
        if (config == null) {
            return false;
        }
        return config.getBoolean(CarrierConfigManager.KEY_USE_RCS_PRESENCE_BOOL);
    }
}
