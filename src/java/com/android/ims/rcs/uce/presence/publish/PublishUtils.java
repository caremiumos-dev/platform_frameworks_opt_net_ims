package com.android.ims.rcs.uce.presence.publish;

import android.content.Context;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

class PublishUtils {
    private static final String LOG_TAG = "PublishUtils";

    private static final String SCHEME_SIP = "sip";
    private static final String SCHEME_TEL = "tel";
    private static final String DOMAIN_SEPARATOR = "@";

    private static final PublishUtils sInstance = new PublishUtils();

    public static final PublishUtils getInstance() {
        return sInstance;
    }

    private PublishUtils() {
    }

    public Uri getPublishingUri(Context context, int subId) {
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

    private Uri getContactUriFromIsim(TelephonyManager telephonyManager) {
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

    private Uri getContactUriFromLine1Number(TelephonyManager telephonyManager) {
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

    private String formatPhoneNumber(final String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            Log.w(LOG_TAG, "formatPhoneNumber: phone number is empty");
            return null;
        }
        String number = PhoneNumberUtils.stripSeparators(phoneNumber);
        return PhoneNumberUtils.normalizeNumber(number);
    }

    private TelephonyManager getTelephonyManager(Context context, int subId) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
        if (telephonyManager == null) {
            return null;
        } else {
            return telephonyManager.createForSubscriptionId(subId);
        }
    }
}
