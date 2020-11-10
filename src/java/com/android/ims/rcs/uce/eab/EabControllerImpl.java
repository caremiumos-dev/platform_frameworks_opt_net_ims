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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactPresenceTuple.ServiceCapabilities;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsContactUceCapability.OptionsBuilder;
import android.telephony.ims.RcsContactUceCapability.PresenceBuilder;
import android.telephony.ims.RcsContactUceCapability.RcsUcsCapabilityBuilder;
import android.text.TextUtils;
import android.util.Log;

import com.android.ims.RcsFeatureManager;
import com.android.ims.rcs.uce.UceController.UceControllerCallback;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The implementation of EabController.
 */
public class EabControllerImpl implements EabController {
    private static final String TAG = "EabControllerImpl";

    // 90 days
    private static final int DEFAULT_CAPABILITY_CACHE_EXPIRATION_SEC = 90 * 24 * 60 * 60;
    private static final int DEFAULT_AVAILABILITY_CACHE_EXPIRATION_SEC = 60;

    private final Context mContext;
    private final int mSubId;
    private UceControllerCallback mUceControllerCallback;
    private final Looper mLooper;
    private volatile boolean mIsSetDestroyedFlag = false;
    private final AvailabilityCache mAvailabilityCache;


    public EabControllerImpl(Context context, int subId, UceControllerCallback c, Looper looper) {
        mContext = context;
        mSubId = subId;
        mUceControllerCallback = c;
        mLooper = looper;
        mAvailabilityCache = new AvailabilityCache();
    }

    @Override
    public void onDestroy() {
        logi("onDestroy");
        mIsSetDestroyedFlag = true;
        mAvailabilityCache.release();
    }

    @Override
    public void onRcsConnected(RcsFeatureManager manager) {
    }

    @Override
    public void onRcsDisconnected() {
    }

    /**
     * Retrieve the contacts' capabilities from the EAB database.
     */
    @Override
    public @NonNull
    EabCapabilityResult getCapabilities(@NonNull List<Uri> uris) {
        Objects.requireNonNull(uris);
        if (mIsSetDestroyedFlag) {
            return new EabCapabilityResult(new ArrayList<>(), new ArrayList<>());
        }

        Log.d(TAG, "getCapabilities(): " + uris.size());

        List<String> numbers = uris.stream()
                .map(EabControllerImpl::getNumberFromUri).collect(Collectors.toList());

        // Query the db based on phone number
        Uri queryUri = Uri.withAppendedPath(
                Uri.withAppendedPath(EabProvider.ALL_DATA_URI, String.valueOf(mSubId)),
                TextUtils.join(",", numbers));
        Cursor cursor = mContext.getContentResolver().query(
                queryUri, null, null, null, null);

        EabCapabilityResult result;
        if (cursor == null || cursor.getCount() == 0) {
            Log.d(TAG, "All uris not in contact table.");
            result = new EabCapabilityResult(new ArrayList<>(), uris);
        } else {
            // Aggregate the result into RcsUcsCapabilityBuilder
            Map<String, RcsUcsCapabilityBuilder> capabilityList = new HashMap<>();
            Map<String, Boolean> expiredStatusList = new HashMap<>();
            while (cursor.moveToNext()) {
                String phoneNumber = getStringValue(cursor,
                        EabProvider.ContactColumns.PHONE_NUMBER);
                RcsContactUceCapability.RcsUcsCapabilityBuilder builder;

                if (capabilityList.containsKey(phoneNumber)) {
                    builder = capabilityList.get(phoneNumber);
                    updateCapability(cursor, builder);
                } else {
                    builder = createNewBuilder(cursor);
                    capabilityList.put(phoneNumber, builder);
                }

                // If one of capability has expired, it is considered contact are expired
                Boolean isExpired = expiredStatusList.get(phoneNumber);
                if (isExpired == null || !isExpired) {
                    expiredStatusList.put(phoneNumber, isCapabilityExpired(cursor));
                }
            }

            // Generate the result
            result = generateEabCapabilityResult(capabilityList, expiredStatusList);
            if (uris.size() != capabilityList.size()) {
                // if there are some contacts not in DB, put them in expired list.
                Log.d(TAG, "There are some contacts not in contact table.");
                result.getExpiredContacts().addAll(getMissingContact(capabilityList, uris));
            }
        }

        if (cursor != null) {
            cursor.close();
        }
        return result;
    }

    /**
     * Retrieve the contact's capabilities from the availability cache.
     */
    @Override
    public @NonNull
    EabCapabilityResult getAvailability(@NonNull Uri contactUri) {
        Objects.requireNonNull(contactUri);
        if (mIsSetDestroyedFlag) {
            return new EabCapabilityResult(new ArrayList<>(),
                    Collections.singletonList(contactUri));
        }

        Log.d(TAG, "getAvailability(). ");

        String number = getNumberFromUri(contactUri);
        RcsContactUceCapability capability = mAvailabilityCache.getAvailability(number);

        // Put it into the capabilities list if it is not expired.
        if (capability != null) {
            return new EabCapabilityResult(Collections.singletonList(capability),
                    new ArrayList<>());
        } else {
            return new EabCapabilityResult(new ArrayList<>(),
                    Collections.singletonList(contactUri));
        }
    }

    /**
     * Update the availability catch and save the capabilities to the EAB database.
     */
    @Override
    public void saveCapabilities(@NonNull List<RcsContactUceCapability> contactCapabilities) {
        Objects.requireNonNull(contactCapabilities);
        if (mIsSetDestroyedFlag) {
            return;
        }
        Log.d(TAG, "Save capabilities: " + contactCapabilities.size());

        // Update the availability cache
        mAvailabilityCache.refreshAvailabilityCache(contactCapabilities);

        // Update the capabilities
        for (RcsContactUceCapability capability : contactCapabilities) {
            String phoneNumber = getNumberFromUri(capability.getContactUri());
            Cursor c = mContext.getContentResolver().query(
                    EabProvider.CONTACT_URI, null, EabProvider.ContactColumns.PHONE_NUMBER + "=?",
                    new String[]{phoneNumber}, null);

            if (c != null && c.moveToNext()) {
                int id = getIntValue(c, EabProvider.ContactColumns.ID);
                if (capability.getCapabilityMechanism() == CAPABILITY_MECHANISM_PRESENCE) {
                    Log.d(TAG, "Insert presence capability");
                    deleteOldPresenceCapability(id);
                    insertNewPresenceCapability(id, capability);
                } else if (capability.getCapabilityMechanism() == CAPABILITY_MECHANISM_OPTIONS) {
                    Log.d(TAG, "Insert options capability");
                    deleteOldOptionCapability(id);
                    insertNewOptionCapability(id, capability);
                }
            } else {
                Log.e(TAG, "The phone number can't find in contact table. ");
                int id = insertNewContact(phoneNumber);
                if (capability.getCapabilityMechanism() == CAPABILITY_MECHANISM_PRESENCE) {
                    insertNewPresenceCapability(id, capability);
                } else if (capability.getCapabilityMechanism() == CAPABILITY_MECHANISM_OPTIONS) {
                    insertNewOptionCapability(id, capability);
                }
            }

            if (c != null) {
                c.close();
            }
        }
    }

    /**
     * Set the callback for sending the request to UceController.
     */
    @Override
    public void setUceRequestCallback(@NonNull UceControllerCallback callback) {
        Objects.requireNonNull(callback);
        if (mIsSetDestroyedFlag) {
            return;
        }
        mUceControllerCallback = callback;
    }

    private void updateCapability(Cursor cursor, RcsUcsCapabilityBuilder builder) {
        if (builder instanceof PresenceBuilder) {
            ((PresenceBuilder) builder).addCapabilityTuple(createPresenceTuple(cursor));
        } else {
            ((OptionsBuilder) builder).addFeatureTag(createOptionTuple(cursor));
        }
    }

    private RcsUcsCapabilityBuilder createNewBuilder(Cursor cursor) {
        int mechanism = getIntValue(cursor, EabProvider.EabCommonColumns.MECHANISM);
        String phoneNumber = getStringValue(cursor, EabProvider.ContactColumns.PHONE_NUMBER);
        int result = getIntValue(cursor, EabProvider.EabCommonColumns.REQUEST_RESULT);

        if (mechanism == CAPABILITY_MECHANISM_PRESENCE) {
            PresenceBuilder builder = new PresenceBuilder(
                    Uri.parse(phoneNumber), CAPABILITY_MECHANISM_PRESENCE, result);
            builder.addCapabilityTuple(createPresenceTuple(cursor));
            return builder;
        } else {
            return new OptionsBuilder(Uri.parse(phoneNumber));
        }
    }

    private String createOptionTuple(Cursor cursor) {
        return getStringValue(cursor, EabProvider.OptionsColumns.FEATURE_TAG);
    }

    private RcsContactPresenceTuple createPresenceTuple(Cursor cursor) {
        String phoneNumber = getStringValue(cursor, EabProvider.ContactColumns.PHONE_NUMBER);

        // RcsContactPresenceTuple fields
        String status = getStringValue(cursor, EabProvider.PresenceTupleColumns.STATUS);
        String serviceId = getStringValue(cursor, EabProvider.PresenceTupleColumns.SERVICE_ID);
        String version = getStringValue(cursor, EabProvider.PresenceTupleColumns.SERVICE_VERSION);
        String description = getStringValue(cursor, EabProvider.PresenceTupleColumns.DESCRIPTION);
        String timeStamp = getStringValue(cursor, EabProvider.PresenceTupleColumns.TIMESTAMP);

        // ServiceCapabilities fields
        String audioCapableStr = getStringValue(cursor,
                EabProvider.PresenceTupleColumns.AUDIO_CAPABLE);
        String videoCapableStr = getStringValue(cursor,
                EabProvider.PresenceTupleColumns.VIDEO_CAPABLE);
        String duplexModes = getStringValue(cursor,
                EabProvider.PresenceTupleColumns.DUPLEX_MODE);
        String unsupportedDuplexModes = getStringValue(cursor,
                EabProvider.PresenceTupleColumns.UNSUPPORTED_DUPLEX_MODE);
        String[] duplexModeList, unsupportedDuplexModeList;

        if (!TextUtils.isEmpty(duplexModes)) {
            duplexModeList = duplexModes.split(",");
        } else {
            duplexModeList = new String[0];
        }
        if (!TextUtils.isEmpty(unsupportedDuplexModes)) {
            unsupportedDuplexModeList = unsupportedDuplexModes.split(",");
        } else {
            unsupportedDuplexModeList = new String[0];
        }

        // Create ServiceCapabilities
        ServiceCapabilities serviceCapabilities = null;
        if (!TextUtils.isEmpty(audioCapableStr)
                || !TextUtils.isEmpty(videoCapableStr)
                || !TextUtils.isEmpty(duplexModes)
                || !TextUtils.isEmpty(unsupportedDuplexModes)) {
            boolean audioCapable = Boolean.parseBoolean(audioCapableStr);
            boolean videoCapable = Boolean.parseBoolean(videoCapableStr);

            ServiceCapabilities.Builder serviceCapabilitiesBuilder =
                    new ServiceCapabilities.Builder(audioCapable, videoCapable);
            for (String duplexMode : duplexModeList) {
                serviceCapabilitiesBuilder.addSupportedDuplexMode(duplexMode);
            }
            for (String unsupportedDuplex : unsupportedDuplexModeList) {
                serviceCapabilitiesBuilder.addUnsupportedDuplexMode(unsupportedDuplex);
            }
            serviceCapabilities = serviceCapabilitiesBuilder.build();
        }

        // Create RcsContactPresenceTuple
        RcsContactPresenceTuple rcsContactPresenceTuple =
                new RcsContactPresenceTuple.Builder(status, serviceId, version)
                        .addDescription(description)
                        .addContactUri(Uri.parse(phoneNumber))
                        .addServiceCapabilities(serviceCapabilities)
                        .addTimeStamp(timeStamp)
                        .build();

        return rcsContactPresenceTuple;
    }

    private boolean isCapabilityExpired(Cursor cursor) {
        int mechanism = getIntValue(cursor, EabProvider.EabCommonColumns.MECHANISM);
        long timestamp = 0;
        if (mechanism == CAPABILITY_MECHANISM_PRESENCE) {
            timestamp = getLongValue(cursor, EabProvider.PresenceTupleColumns.TIMESTAMP);
        } else if (mechanism == CAPABILITY_MECHANISM_OPTIONS) {
            timestamp = getLongValue(cursor, EabProvider.OptionsColumns.TIMESTAMP);
        }

        Instant instant = Instant.ofEpochSecond(timestamp)
                .plus(getCapabilityCacheExpiration(), ChronoUnit.SECONDS);

        return instant.isBefore(Instant.now());
    }

    private List<Uri> getMissingContact(Map<String, RcsUcsCapabilityBuilder> capabilityList,
            List<Uri> requestContact) {
        List<Uri> missingContact = new ArrayList<>();
        for (Uri contact : requestContact) {
            String phoneNumber = getNumberFromUri(contact);
            if (!capabilityList.containsKey(phoneNumber)) {
                missingContact.add(contact);
            }
        }
        return missingContact;
    }

    private EabCapabilityResult generateEabCapabilityResult(
            Map<String, RcsUcsCapabilityBuilder> capabilityList,
            Map<String, Boolean> expiredStatusList) {
        List<RcsContactUceCapability> contactCapabilities = new ArrayList<>();
        List<Uri> expiredContacts = new ArrayList<>();

        for (Map.Entry<String, RcsUcsCapabilityBuilder> entry : capabilityList.entrySet()) {
            String phoneNumber = entry.getKey();
            RcsContactUceCapability capability = (entry.getValue()).build();
            if (expiredStatusList.get(phoneNumber)) {
                expiredContacts.add(capability.getContactUri());
            } else {
                contactCapabilities.add(capability);
            }
        }
        return new EabCapabilityResult(contactCapabilities, expiredContacts);
    }

    private long getCapabilityCacheExpiration() {
        long value = -1;
        try {
            ProvisioningManager pm = ProvisioningManager.createForSubscriptionId(mSubId);
            value = pm.getProvisioningIntValue(
                    ProvisioningManager.KEY_RCS_CAPABILITIES_CACHE_EXPIRATION_SEC);
        } catch (Exception ex) {
            Log.e(TAG, "Exception in getCapabilityCacheExpiration(): " + ex);
        }

        if (value <= 0) {
            value = DEFAULT_CAPABILITY_CACHE_EXPIRATION_SEC;
            Log.e(TAG, "The capability expiration cannot be less than 0.");
        }
        return value;
    }

    private long getAvailabilityCacheExpiration() {
        long value = -1;
        try {
            ProvisioningManager pm = ProvisioningManager.createForSubscriptionId(mSubId);
            value = pm.getProvisioningIntValue(
                    ProvisioningManager.KEY_RCS_AVAILABILITY_CACHE_EXPIRATION_SEC);
        } catch (Exception ex) {
            Log.e(TAG, "Exception in getAvailabilityCacheExpiration(): " + ex);
        }

        if (value <= 0) {
            value = DEFAULT_AVAILABILITY_CACHE_EXPIRATION_SEC;
            Log.e(TAG, "The Availability expiration cannot be less than 0.");
        }
        return value;
    }

    private int insertNewContact(String phoneNumber) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(EabProvider.ContactColumns.PHONE_NUMBER, phoneNumber);
        Uri result = mContext.getContentResolver().insert(EabProvider.CONTACT_URI, contentValues);
        return Integer.valueOf(result.getLastPathSegment());
    }

    private void deleteOldPresenceCapability(int id) {
        Cursor c = mContext.getContentResolver().query(
                EabProvider.COMMON_URI,
                new String[] {EabProvider.EabCommonColumns.ID},
                EabProvider.EabCommonColumns.EAB_CONTACT_ID + "=?",
                new String[]{String.valueOf(id)}, null);

        if (c != null && c.getCount() > 0) {
            int commonId = c.getInt(c.getColumnIndex(EabProvider.EabCommonColumns.ID));
            mContext.getContentResolver().delete(
                    EabProvider.PRESENCE_URI, EabProvider.PresenceTupleColumns.EAB_COMMON_ID + "=?",
                    new String[]{String.valueOf(commonId)});
        }

        if (c != null) {
            c.close();
        }
    }

    private void insertNewPresenceCapability(int id, RcsContactUceCapability capability) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(EabProvider.EabCommonColumns.EAB_CONTACT_ID, id);
        contentValues.put(EabProvider.EabCommonColumns.MECHANISM, CAPABILITY_MECHANISM_PRESENCE);
        contentValues.put(EabProvider.EabCommonColumns.REQUEST_RESULT,
                capability.getRequestResult());
        Uri result = mContext.getContentResolver().insert(EabProvider.COMMON_URI, contentValues);
        int commonId = Integer.valueOf(result.getLastPathSegment());

        ContentValues[] presenceContent = new ContentValues[capability.getPresenceTuples().size()];
        for (int i = 0; i < presenceContent.length; i++) {
            RcsContactPresenceTuple tuple = capability.getPresenceTuples().get(i);
            ServiceCapabilities serviceCapabilities = tuple.getServiceCapabilities();
            String duplexMode = null, unsupportedDuplexMode = null;
            if (serviceCapabilities != null) {
                List<String> duplexModes = serviceCapabilities.getSupportedDuplexModes();
                if (duplexModes != null && duplexModes.size() != 0) {
                    duplexMode = TextUtils.join(",", duplexModes);
                }

                List<String> unsupportedDuplexModes =
                        serviceCapabilities.getSupportedDuplexModes();
                if (unsupportedDuplexModes != null && unsupportedDuplexModes.size() != 0) {
                    unsupportedDuplexMode =
                            TextUtils.join(",", unsupportedDuplexModes);
                }
            }

            contentValues = new ContentValues();
            contentValues.put(EabProvider.PresenceTupleColumns.EAB_COMMON_ID, commonId);
            contentValues.put(EabProvider.PresenceTupleColumns.SUB_ID, mSubId);
            contentValues.put(EabProvider.PresenceTupleColumns.STATUS, tuple.getStatus());
            contentValues.put(EabProvider.PresenceTupleColumns.SERVICE_ID, tuple.getServiceId());
            contentValues.put(EabProvider.PresenceTupleColumns.SERVICE_VERSION,
                    tuple.getServiceVersion());
            contentValues.put(EabProvider.PresenceTupleColumns.DESCRIPTION,
                    tuple.getServiceDescription());
            contentValues.put(EabProvider.PresenceTupleColumns.TIMESTAMP, tuple.getTimestamp());
            if (serviceCapabilities != null) {
                contentValues.put(EabProvider.PresenceTupleColumns.DUPLEX_MODE, duplexMode);
                contentValues.put(EabProvider.PresenceTupleColumns.UNSUPPORTED_DUPLEX_MODE,
                        unsupportedDuplexMode);

                contentValues.put(EabProvider.PresenceTupleColumns.AUDIO_CAPABLE,
                        serviceCapabilities.isAudioCapable());
                contentValues.put(EabProvider.PresenceTupleColumns.VIDEO_CAPABLE,
                        serviceCapabilities.isVideoCapable());
            }
            presenceContent[i] = contentValues;
        }
        mContext.getContentResolver().bulkInsert(EabProvider.PRESENCE_URI, presenceContent);
    }

    private void deleteOldOptionCapability(int id) {
        Cursor c = mContext.getContentResolver().query(
                EabProvider.COMMON_URI,
                new String[] {EabProvider.EabCommonColumns.ID},
                EabProvider.EabCommonColumns.EAB_CONTACT_ID + "=?",
                new String[]{String.valueOf(id)}, null);

        if (c != null && c.getCount() > 0) {
            int commonId = c.getInt(c.getColumnIndex(EabProvider.EabCommonColumns.ID));
            mContext.getContentResolver().delete(
                    EabProvider.OPTIONS_URI, EabProvider.OptionsColumns.EAB_COMMON_ID + "=?",
                    new String[]{String.valueOf(commonId)});
        }

        if (c != null) {
            c.close();
        }
    }

    private void insertNewOptionCapability(int id, RcsContactUceCapability capability) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(EabProvider.EabCommonColumns.EAB_CONTACT_ID, id);
        contentValues.put(EabProvider.EabCommonColumns.MECHANISM, CAPABILITY_MECHANISM_OPTIONS);
        contentValues.put(EabProvider.EabCommonColumns.REQUEST_RESULT,
                capability.getRequestResult());
        Uri result = mContext.getContentResolver().insert(EabProvider.COMMON_URI, contentValues);

        int commonId = Integer.valueOf(result.getLastPathSegment());
        ContentValues[] optionContent = new ContentValues[capability.getOptionsFeatureTags().size()];

        for (int i = 0; i < optionContent.length;i ++) {
            String feature = capability.getOptionsFeatureTags().get(i);
            contentValues = new ContentValues();
            contentValues.put(EabProvider.OptionsColumns.EAB_COMMON_ID, commonId);
            contentValues.put(EabProvider.OptionsColumns.SUB_ID, mSubId);
            contentValues.put(EabProvider.OptionsColumns.FEATURE_TAG, feature);
            contentValues.put(EabProvider.OptionsColumns.TIMESTAMP, Instant.now().getEpochSecond());
            optionContent[i] = contentValues;
        }
        mContext.getContentResolver().bulkInsert(EabProvider.OPTIONS_URI, optionContent);
    }

    /**
     * The class is to store the availability information which include the contact capability and
     * the expired timestamp.
     */
    private static class AvailabilityInfo {

        public final RcsContactUceCapability mAvailability;
        public final Instant mExpiredTimestamp;

        AvailabilityInfo(RcsContactUceCapability capability, Instant expiredTimestamp) {
            mAvailability = capability;
            mExpiredTimestamp = expiredTimestamp;
        }
    }

    /**
     * The class is help to manage the availability cache.
     */
    private class AvailabilityCache {

        private final Map<String, AvailabilityInfo> mAvailabilityCache = new HashMap<>();
        private final Object mLock = new Object();

        AvailabilityCache() {
            mAvailabilityCache.clear();
        }

        public void release() {
            synchronized (mLock) {
                mAvailabilityCache.clear();
            }
        }

        /**
         * Get the availability from the cache. Return null if the availability has not been cached
         * or the availability is expired.
         */
        public @Nullable
        RcsContactUceCapability getAvailability(String uri) {
            AvailabilityInfo availabilityInfo = null;
            synchronized (mLock) {
                availabilityInfo = mAvailabilityCache.get(uri);
            }

            if (availabilityInfo != null
                    && (Instant.now().isBefore(availabilityInfo.mExpiredTimestamp))) {
                return availabilityInfo.mAvailability;
            }
            return null;
        }

        /**
         * Update the availability cache with the giving capability list.
         */
        public void refreshAvailabilityCache(List<RcsContactUceCapability> capabilityList) {
            Instant timestamp = getAvailabilityCacheExpiredTimestamp();
            capabilityList.forEach((capability) ->
                    refreshAvailabilityCache(capability, timestamp));
        }

        private void refreshAvailabilityCache(RcsContactUceCapability capability,
                Instant expiredTimestamp) {
            String number = getNumberFromUri(capability.getContactUri());
            AvailabilityInfo availabilityInfo = new AvailabilityInfo(capability, expiredTimestamp);

            synchronized (mLock) {
                mAvailabilityCache.put(number, availabilityInfo);
            }
        }

        private Instant getAvailabilityCacheExpiredTimestamp() {
            Duration expiredDuration = Duration.ofSeconds(getAvailabilityCacheExpiration());
            Instant timestamp = Instant.now().plusSeconds(expiredDuration.getSeconds());
            return timestamp;
        }
    }

    private String getStringValue(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndex(column));
    }

    private int getIntValue(Cursor cursor, String column) {
        return cursor.getInt(cursor.getColumnIndex(column));
    }

    private long getLongValue(Cursor cursor, String column) {
        return cursor.getLong(cursor.getColumnIndex(column));
    }

    private static String getNumberFromUri(Uri uri) {
        String number = uri.getSchemeSpecificPart();
        String[] numberParts = number.split("[@;:]");
        if (numberParts.length == 0) {
            return null;
        }
        return numberParts[0];
    }

    private void logi(String log) {
        Log.i(TAG, getLogPrefix().append(log).toString());
    }

    private StringBuilder getLogPrefix() {
        StringBuilder builder = new StringBuilder("[");
        builder.append(mSubId);
        builder.append("] ");
        return builder;
    }
}
