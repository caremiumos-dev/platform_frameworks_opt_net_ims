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

package com.android.ims.rcs.uce.eab;

import static android.telephony.ims.RcsContactUceCapability.CAPABILITY_MECHANISM_OPTIONS;
import static android.telephony.ims.RcsContactUceCapability.CAPABILITY_MECHANISM_PRESENCE;

import static com.android.ims.rcs.uce.eab.EabControllerImpl.getCapabilityCacheExpiration;

import android.app.AlarmManager;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.CarrierConfigManager;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.aidl.IRcsUceControllerCallback;
import android.util.Log;

import com.android.ims.rcs.uce.UceController;

import java.util.ArrayList;
import java.util.List;

public final class EabBulkCapabilityUpdater {
    private static final String TAG = "EabCapabilityUpdater";

    private static final int NOT_INIT_LAST_UPDATED_TIME = -1;

    private static final String ACTION_CAPABILITY_EXPIRED = "action_capability_expired";
    private static final String LAST_UPDATED_TIME_KEY = "eab_last_updated_time";
    private static final Uri USER_EAB_SETTING = Uri.withAppendedPath(Telephony.SimInfo.CONTENT_URI,
            Telephony.SimInfo.COLUMN_IMS_RCS_UCE_ENABLED);

    private int mSubId;
    private Context mContext;
    private UceController.UceControllerCallback mUceControllerCallback;
    private IRcsUceControllerCallback mRcsUceControllerCallback;
    private Handler mHandler;

    private AlarmManager.OnAlarmListener mCapabilityExpiredListener;
    private ContactChangedListener mContactProviderListener;
    private EabSettingsListener mEabSettingListener;

    private boolean isContactProviderListenerRegistered = false;
    private boolean isEabSettingListenerRegistered = false;

    /**
     * Listen capability expired intent
     */
    private class CapabilityExpiredListener implements AlarmManager.OnAlarmListener {
        @Override
        public void onAlarm() {
            Log.d(TAG, "Capability expired.");
            try {
                mUceControllerCallback.refreshCapabilities(
                        getExpiredContactList(),
                        mRcsUceControllerCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "CapabilityExpiredListener RemoteException", e);
            }
        }
    }

    /**
     * Listen contact provider change
     */
    private class ContactChangedListener extends ContentObserver {
        public ContactChangedListener(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            Log.d(TAG, "Contact changed");
            syncContactAndRefreshCapabilities();
        }
    }

    /**
     * Listen eab settings change.
     */
    private class EabSettingsListener extends ContentObserver {
        public EabSettingsListener(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            boolean isUserEnableUce = isUserEnableUce();
            Log.d(TAG, "EAB user setting changed: " + isUserEnableUce);
            if (isUserEnableUce) {
                mHandler.post(new SyncContactRunnable());
            } else {
                unRegisterContactProviderListener();
                cancelTimeAlert(mContext);
            }
        }
    }

    private class SyncContactRunnable implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "Sync contact from contact provider");
            syncContactAndRefreshCapabilities();
            registerContactProviderListener();
            registerEabUserSettingsListener();
        }
    }

    public EabBulkCapabilityUpdater(Context context,
            int subId,
            UceController.UceControllerCallback uceControllerCallback,
            IRcsUceControllerCallback rcsUceControllerCallback,
            Looper looper) {
        mContext = context;
        mSubId = subId;
        mHandler = new Handler(looper);
        mUceControllerCallback = uceControllerCallback;
        mEabSettingListener = new EabSettingsListener(mHandler);
        mContactProviderListener = new ContactChangedListener(mHandler);
        mCapabilityExpiredListener = new CapabilityExpiredListener();
        mRcsUceControllerCallback = rcsUceControllerCallback;

        Log.d(TAG, "create EabBulkCapabilityUpdater() subId: " + mSubId);

        enableBulkCapability();
        updateExpiredTimeAlert();
    }

    private void enableBulkCapability() {
        boolean isUserEnableUce = isUserEnableUce();
        //TODO: update carrier config name
        boolean isSupportBulkCapabilityExchange = true;//getBooleanCarrierConfig("TODO", mSubId);

        Log.d(TAG, "isUserEnableUce: " + isUserEnableUce
                + ", isSupportBulkCapabilityExchange: " + isSupportBulkCapabilityExchange);

        if (isUserEnableUce && isSupportBulkCapabilityExchange) {
            mHandler.post(new SyncContactRunnable());
        } else if (!isUserEnableUce && isSupportBulkCapabilityExchange) {
            registerEabUserSettingsListener();
        } else {
            Log.d(TAG, "Not support bulk capability exchange.");
        }
    }

    private void syncContactAndRefreshCapabilities() {
        List<Uri> refreshContacts;

        refreshContacts = EabContactSyncController.syncContactToEabProvider(mContext);
        Log.d(TAG, "refresh contacts number: " + refreshContacts.size());

        if (mUceControllerCallback == null) {
            Log.d(TAG, "mUceControllerCallback is null.");
            return;
        }

        try {
            mUceControllerCallback.refreshCapabilities(refreshContacts, mRcsUceControllerCallback);
        } catch (RemoteException e) {
            Log.e(TAG, "mUceControllerCallback RemoteException.", e);
        }
    }

    protected void updateExpiredTimeAlert() {
        boolean isUserEnableUce = isUserEnableUce();
        //TODO: update carrier config name
        boolean isSupportBulkCapabilityExchange = true;//getBooleanCarrierConfig("TODO", mSubId);

        Log.d(TAG, " updateExpiredTimeAlert(), isUserEnableUce: " + isUserEnableUce
                + ", isSupportBulkCapabilityExchange: " + isSupportBulkCapabilityExchange);

        if (isUserEnableUce && isSupportBulkCapabilityExchange) {
            long expiredTimestamp = getLeastExpiredTimestamp();
            if (expiredTimestamp == Long.MAX_VALUE) {
                Log.d(TAG, "Can't find min timestamp in eab provider");
                return;
            }
            expiredTimestamp += getCapabilityCacheExpiration(mSubId);
            Log.d(TAG, "set time alert at " + expiredTimestamp);
            cancelTimeAlert(mContext);
            setTimeAlert(mContext, expiredTimestamp);
        }
    }

    private long getLeastExpiredTimestamp() {
        String selection = "("
                // Query presence timestamp
                + EabProvider.EabCommonColumns.MECHANISM + "=" + CAPABILITY_MECHANISM_PRESENCE
                + " AND "
                + EabProvider.PresenceTupleColumns.REQUEST_TIMESTAMP + " IS NOT NULL) "

                // Query options timestamp
                + " OR " + "(" + EabProvider.EabCommonColumns.MECHANISM + "="
                + CAPABILITY_MECHANISM_OPTIONS + " AND "
                + EabProvider.OptionsColumns.REQUEST_TIMESTAMP + " IS NOT NULL) "

                // filter by sub id
                + " AND " + EabProvider.EabCommonColumns.SUBSCRIPTION_ID + "=" + mSubId;

        long minTimestamp = Long.MAX_VALUE;
        Cursor result = mContext.getContentResolver().query(EabProvider.ALL_DATA_URI, null,
                selection,
                null, null);
        while (result.moveToNext()) {
            int mechanism = result.getInt(
                    result.getColumnIndex(EabProvider.EabCommonColumns.MECHANISM));
            long timestamp;
            if (mechanism == CAPABILITY_MECHANISM_PRESENCE) {
                timestamp = result.getLong(result.getColumnIndex(
                        EabProvider.PresenceTupleColumns.REQUEST_TIMESTAMP));
            } else {
                timestamp = result.getLong(result.getColumnIndex(
                        EabProvider.OptionsColumns.REQUEST_TIMESTAMP));
            }

            if (timestamp < minTimestamp) {
                minTimestamp = timestamp;
            }
        }
        result.close();
        return minTimestamp;
    }

    private void setTimeAlert(Context context, long wakeupTime) {
        Log.d(TAG, " setTimeAlert: " + wakeupTime);
        AlarmManager am = context.getSystemService(AlarmManager.class);
        am.set(AlarmManager.RTC_WAKEUP, wakeupTime * 1000, TAG, mCapabilityExpiredListener,
                mHandler);
    }

    private void cancelTimeAlert(Context context) {
        Log.d(TAG, "cancelTimeAlert.");
        AlarmManager am = context.getSystemService(AlarmManager.class);
        am.cancel(mCapabilityExpiredListener);
    }

    private boolean getBooleanCarrierConfig(String key, int subId) {
        CarrierConfigManager mConfigManager = mContext.getSystemService(CarrierConfigManager.class);
        PersistableBundle b = null;
        if (mConfigManager != null) {
            b = mConfigManager.getConfigForSubId(subId);
        }
        if (b != null) {
            return b.getBoolean(key);
        } else {
            return CarrierConfigManager.getDefaultConfig().getBoolean(key);
        }
    }

    private boolean isUserEnableUce() {
        ImsManager manager = mContext.getSystemService(ImsManager.class);
        if (manager == null) {
            Log.e(TAG, "ImsManager is null");
            return false;
        }
        try {
            ImsRcsManager rcsManager = manager.getImsRcsManager(mSubId);
            return (rcsManager != null) && rcsManager.getUceAdapter().isUceSettingEnabled();
        } catch (Exception e) {
            Log.e(TAG, "hasUserEnabledUce: exception = " + e.getMessage());
        }
        return false;
    }

    private List<Uri> getExpiredContactList() {
        List<Uri> refreshList = new ArrayList<>();
        long expiredTime = (System.currentTimeMillis() / 1000)
                + getCapabilityCacheExpiration(mSubId);
        String selection = "("
                + EabProvider.EabCommonColumns.MECHANISM + "=" + CAPABILITY_MECHANISM_PRESENCE
                + " AND " + EabProvider.PresenceTupleColumns.REQUEST_TIMESTAMP + "<"
                + expiredTime + ")";
        selection += " OR " + "(" + EabProvider.EabCommonColumns.MECHANISM + "="
                + CAPABILITY_MECHANISM_OPTIONS + " AND "
                + EabProvider.OptionsColumns.REQUEST_TIMESTAMP + "<" + expiredTime + ")";

        Cursor result = mContext.getContentResolver().query(EabProvider.ALL_DATA_URI, null,
                selection,
                null, null);
        while (result.moveToNext()) {
            String phoneNumber = result.getString(
                    result.getColumnIndex(EabProvider.ContactColumns.PHONE_NUMBER));
            refreshList.add(Uri.parse(phoneNumber));
        }
        result.close();
        return refreshList;
    }

    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        cancelTimeAlert(mContext);
        unRegisterContactProviderListener();
        unRegisterEabUserSettings();
    }

    protected static void setLastUpdatedTime(Context context, long timestamp) {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.edit().putLong(LAST_UPDATED_TIME_KEY, timestamp).apply();
    }

    protected static long getLastUpdatedTime(Context context) {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getLong(LAST_UPDATED_TIME_KEY, NOT_INIT_LAST_UPDATED_TIME);
    }

    private void registerContactProviderListener() {
        Log.d(TAG, "registerContactProviderListener");
        isContactProviderListenerRegistered = true;
        mContext.getContentResolver().registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                mContactProviderListener);
    }

    private void registerEabUserSettingsListener() {
        Log.d(TAG, "registerEabUserSettingsListener");
        isEabSettingListenerRegistered = true;
        mContext.getContentResolver().registerContentObserver(
                USER_EAB_SETTING,
                true,
                mEabSettingListener);
    }

    private void unRegisterContactProviderListener() {
        Log.d(TAG, "unRegisterContactProviderListener");
        if (isContactProviderListenerRegistered) {
            mContext.getContentResolver().unregisterContentObserver(mContactProviderListener);
        }
    }

    private void unRegisterEabUserSettings() {
        if (isEabSettingListenerRegistered) {
            mContext.getContentResolver().unregisterContentObserver(mEabSettingListener);
        }
    }

    public void setUceRequestCallback(UceController.UceControllerCallback uceControllerCallback) {
        mUceControllerCallback = uceControllerCallback;
    }
}
