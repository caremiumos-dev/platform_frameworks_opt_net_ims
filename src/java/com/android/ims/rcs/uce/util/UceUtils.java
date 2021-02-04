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
import android.net.Uri;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.ProvisioningManager;
import android.text.TextUtils;
import android.util.Log;

public class UceUtils {

    private static final String LOG_PREFIX = "RcsUce.";
    private static final String LOG_TAG = LOG_PREFIX + "UceUtils";

    private static final String SCHEME_SIP = "sip";
    private static final String SCHEME_TEL = "tel";
    private static final String DOMAIN_SEPARATOR = "@";

    // The task ID of the UCE request
    private static long TASK_ID = 0L;

    /**
     * Get the log prefix of RCS UCE
     */
    public static String getLogPrefix() {
        return LOG_PREFIX;
    }

    /**
     * Generate the unique UCE request task id.
     */
    public static synchronized long generateTaskId() {
        return ++TASK_ID;
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

    /**
     * Check whether or not this carrier supports the exchange of phone numbers with the carrier's
     * presence server.
     */
    public static boolean isPresenceCapExchangeEnabled(Context context, int subId) {
        CarrierConfigManager configManager = context.getSystemService(CarrierConfigManager.class);
        if (configManager == null) {
            return false;
        }
        PersistableBundle config = configManager.getConfigForSubId(subId);
        if (config == null) {
            return false;
        }
        return config.getBoolean(
                CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_CAPABILITY_EXCHANGE_BOOL);
    }

    /**
     * Check if Presence is supported by the carrier.
     */
    public static boolean isPresenceSupported(Context context, int subId) {
        CarrierConfigManager configManager = context.getSystemService(CarrierConfigManager.class);
        if (configManager == null) {
            return false;
        }
        PersistableBundle config = configManager.getConfigForSubId(subId);
        if (config == null) {
            return false;
        }
        return config.getBoolean(CarrierConfigManager.Ims.KEY_ENABLE_PRESENCE_PUBLISH_BOOL);
    }

    /**
     * Check if SIP OPTIONS is supported by the carrier.
     */
    public static boolean isSipOptionsSupported(Context context, int subId) {
        CarrierConfigManager configManager = context.getSystemService(CarrierConfigManager.class);
        if (configManager == null) {
            return false;
        }
        PersistableBundle config = configManager.getConfigForSubId(subId);
        if (config == null) {
            return false;
        }
        return config.getBoolean(CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL);
    }

    public static Uri getDeviceContactUri(Context context, int subId) {
        TelephonyManager telephonyManager = getTelephonyManager(context, subId);
        if (telephonyManager == null) {
            Log.w(LOG_TAG, "getPublishingUri: TelephonyManager is null");
            return null;
        }

        // Get the contact uri from ISIM.
        Uri contactUri = getContactUriFromIsim(telephonyManager);
        if (contactUri != null) {
            Log.d(LOG_TAG, "getPublishingUri: impu");
            return contactUri;
        } else {
            Log.d(LOG_TAG, "getPublishingUri: line number");
            return getContactUriFromLine1Number(telephonyManager);
        }
    }

    private static Uri getContactUriFromIsim(TelephonyManager telephonyManager) {
        // Get the home network domain and the array of the public user identities
        String domain = telephonyManager.getIsimDomain();
        String[] impus = telephonyManager.getIsimImpu();

        if (TextUtils.isEmpty(domain) || impus == null) {
            return null;
        }

        for (String impu : impus) {
            Uri impuUri = Uri.parse(impu);
            if (SCHEME_SIP.equals(impuUri.getScheme()) &&
                    impuUri.getSchemeSpecificPart().endsWith(domain)) {
                return impuUri;
            }
        }
        return null;
    }

    private static Uri getContactUriFromLine1Number(TelephonyManager telephonyManager) {
        String phoneNumber = formatPhoneNumber(telephonyManager.getLine1Number());
        if (TextUtils.isEmpty(phoneNumber)) {
            Log.w(LOG_TAG, "Cannot get the phone number");
            return null;
        }

        String domain = telephonyManager.getIsimDomain();
        if (!TextUtils.isEmpty(domain)) {
            return Uri.fromParts(SCHEME_SIP, phoneNumber + DOMAIN_SEPARATOR + domain, null);
        } else {
            return Uri.fromParts(SCHEME_TEL, phoneNumber, null);
        }
    }

    private static String formatPhoneNumber(final String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            Log.w(LOG_TAG, "formatPhoneNumber: phone number is empty");
            return null;
        }
        String number = PhoneNumberUtils.stripSeparators(phoneNumber);
        return PhoneNumberUtils.normalizeNumber(number);
    }

    private static TelephonyManager getTelephonyManager(Context context, int subId) {
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        if (telephonyManager == null) {
            return null;
        } else {
            return telephonyManager.createForSubscriptionId(subId);
        }
    }
}
