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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides the ability to query the enhanced address book databases(A.K.A. EAB) based on
 * both SIP options and UCE presence server data.
 *
 * <p>
 * There are 4 tables in EAB DB:
 * <ul>
 *     <li><em>Contact:</em> It stores the name and phone number of the contact.
 *
 *     <li><em>Common:</em> It's a general table for storing the query results and the mechanisms of
 *     querying UCE capabilities. It should be 1:1 mapped to the contact table and has a foreign
 *     key(eab_contact_id) that refers to the id of contact table. If the value of mechanism is
 *     1 ({@link android.telephony.ims.RcsContactUceCapability#CAPABILITY_MECHANISM_PRESENCE}), the
 *     capability information should be stored in presence table, if the value of mechanism is
 *     2({@link android.telephony.ims.RcsContactUceCapability#CAPABILITY_MECHANISM_OPTIONS}), it
 *     should be stored in options table.
 *
 *     <li><em>Presence:</em> It stores the information
 *     ({@link android.telephony.ims.RcsContactUceCapability}) that queried through presence server.
 *     It should be *:1 mapped to the common table and has a foreign key(eab_common_id) that refers
 *     to the id of common table.
 *
 *     <li><em>Options:</em> It stores the information
 *     ({@link android.telephony.ims.RcsContactUceCapability}) that queried through SIP OPTIONS. It
 *     should be *:1 mapped to the common table and it has a foreign key(eab_common_id) that refers
 *     to the id of common table.
 * </ul>
 * </p>
 */
public class EabProvider extends ContentProvider {
    // The public URI for operating Eab DB. They support query, insert, delete and update.
    public static final Uri CONTACT_URI = Uri.parse("content://eab/contact");
    public static final Uri COMMON_URI = Uri.parse("content://eab/common");
    public static final Uri PRESENCE_URI = Uri.parse("content://eab/presence");
    public static final Uri OPTIONS_URI = Uri.parse("content://eab/options");

    // The public URI for querying EAB DB. Only support query.
    public static final Uri ALL_DATA_URI = Uri.parse("content://eab/all");

    @VisibleForTesting
    public static final String AUTHORITY = "eab";

    private static final String TAG = "EabProvider";
    private static final int DATABASE_VERSION = 1;

    private static final String EAB_CONTACT_TABLE_NAME = "eab_contact";
    private static final String EAB_COMMON_TABLE_NAME = "eab_common";
    private static final String EAB_PRESENCE_TUPLE_TABLE_NAME = "eab_presence";
    private static final String EAB_OPTIONS_TABLE_NAME = "eab_options";

    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    private static final int URL_CONTACT = 1;
    private static final int URL_COMMON = 2;
    private static final int URL_PRESENCE = 3;
    private static final int URL_OPTIONS = 4;
    private static final int URL_ALL = 5;
    private static final int URL_ALL_WITH_SUB_ID_AND_PHONE_NUMBER = 6;

    static {
        URI_MATCHER.addURI(AUTHORITY, "contact", URL_CONTACT);
        URI_MATCHER.addURI(AUTHORITY, "common", URL_COMMON);
        URI_MATCHER.addURI(AUTHORITY, "presence", URL_PRESENCE);
        URI_MATCHER.addURI(AUTHORITY, "options", URL_OPTIONS);
        URI_MATCHER.addURI(AUTHORITY, "all", URL_ALL);
        URI_MATCHER.addURI(AUTHORITY, "all/#/*", URL_ALL_WITH_SUB_ID_AND_PHONE_NUMBER);
    }

    private static final String QUERY_CONTACT_TABLE =
            " SELECT * FROM " + EAB_CONTACT_TABLE_NAME;

    private static final String JOIN_ALL_TABLES =
            // join common table
            " INNER JOIN " + EAB_COMMON_TABLE_NAME
            + " ON " + EAB_CONTACT_TABLE_NAME + "." + ContactColumns._ID
            + "=" + EAB_COMMON_TABLE_NAME + "." + EabCommonColumns.EAB_CONTACT_ID

            // join options table
            + " LEFT JOIN " + EAB_OPTIONS_TABLE_NAME
            + " ON " + EAB_OPTIONS_TABLE_NAME + "." + EabCommonColumns._ID
            + "=" + EAB_OPTIONS_TABLE_NAME + "." + OptionsColumns.EAB_COMMON_ID

            // join presence table
            + " LEFT JOIN " + EAB_PRESENCE_TUPLE_TABLE_NAME
            + " ON " + EAB_PRESENCE_TUPLE_TABLE_NAME + "." + EabCommonColumns._ID
            + "=" + EAB_PRESENCE_TUPLE_TABLE_NAME + "."
            + PresenceTupleColumns.EAB_COMMON_ID;

    /**
     * The contact table's columns.
     */
    public static class ContactColumns implements BaseColumns {

        /**
         * The name of contact that stored in contact provider. It refer to the
         * {@link android.provider.ContactsContract.CommonDataKinds.Phone#DISPLAY_NAME}.
         * <P>Type: TEXT</P>
         */
        public static final String NAME = "name";

        /**
         * The phone number that stored in contact provider. It refer to the
         * {@link android.provider.ContactsContract.CommonDataKinds.Phone#NUMBER}.
         * <P>Type: TEXT</P>
         */
        public static final String PHONE_NUMBER = "phone_number";

        /**
         * The ID of contact that store in contact provider. It refer to the
         * {@link android.provider.ContactsContract.Data#RAW_CONTACT_ID}.
         * <P>Type: INTEGER</P>
         */
        public static final String RAW_CONTACT_ID = "raw_contact_id";

        /**
         * The ID of phone number that store in contact provider. It refer to the
         * {@link android.provider.ContactsContract.Data#_ID}.
         * <P>Type:  INTEGER</P>
         */
        public static final String DATA_ID = "data_id";
    }

    /**
     * The common table's columns. The eab_contact_id should refer to the id of contact table.
     */
    public static class EabCommonColumns implements BaseColumns {

        /**
         * A reference to the {@link ContactColumns#_ID} that this data belongs to.
         * <P>Type:  INTEGER</P>
         */
        public static final String EAB_CONTACT_ID = "eab_contact_id";

        /**
         * The mechanism of querying UCE capability. Possible values defined in
         * {@link android.telephony.ims.RcsContactUceCapability#CapabilityMechanism }.
         * <P>Type: Text</P>
         */
        public static final String MECHANISM = "mechanism";

        /**
         * The result of querying UCE capability. Possible values defined in
         * {@link android.telephony.ims.RcsContactUceCapability#RequestResult }.
         * <P>Type: Text</P>
         */
        public static final String REQUEST_RESULT = "request_result";

        /**
         * The subscription id.
         * <P>Type:  INTEGER</P>
         */
        public static final String SUBSCRIPTION_ID = "subscription_id";
    }

    /**
     * This is used to generate a instance of {@link android.telephony.ims.RcsContactPresenceTuple}.
     * See that class for more information on each of these parameters.
     */
    public static class PresenceTupleColumns implements BaseColumns {

        /**
         * A reference to the {@link ContactColumns#_ID} that this data belongs to.
         * <P>Type:  INTEGER</P>
         */
        public static final String EAB_COMMON_ID = "eab_common_id";

        /**
         * The basic status of service capabilities. Possible values defined in
         * {@link android.telephony.ims.RcsContactPresenceTuple#BasicStatus}
         * <P>Type:  TEXT</P>
         */
        public static final String BASIC_STATUS = "basic_status";

        /**
         * The service id of service capabilities.
         * <P>Type:  TEXT</P>
         */
        public static final String SERVICE_ID = "service_id";

        /**
         * The contact uri of service capabilities.
         * <P>Type:  TEXT</P>
         */
        public static final String CONTACT_URI = "contact_uri";

        /**
         * The service version of service capabilities.
         * <P>Type:  TEXT</P>
         */
        public static final String SERVICE_VERSION = "service_version";

        /**
         * The description of service capabilities.
         * <P>Type:  TEXT</P>
         */
        public static final String DESCRIPTION = "description";

        /**
         * The supported duplex mode of service capabilities. Possible values defined in
         * {@link android.telephony.ims.RcsContactPresenceTuple#DuplexMode}
         * <P>Type:  TEXT</P>
         */
        public static final String DUPLEX_MODE = "duplex_mode";

        /**
         * The unsupported duplex mode of service capabilities. Possible values defined in
         * {@link android.telephony.ims.RcsContactPresenceTuple#DuplexMode}
         * <P>Type:  TEXT</P>
         */
        public static final String UNSUPPORTED_DUPLEX_MODE = "unsupported_duplex_mode";

        /**
         * The presence request timestamp.
         * <P>Type:  TEXT</P>
         */
        public static final String REQUEST_TIMESTAMP = "presence_request_timestamp";

        /**
         * The audio capable.
         * <P>Type:  BOOLEAN </P>
         */
        public static final String AUDIO_CAPABLE = "audio_capable";

        /**
         * The video capable.
         * <P>Type:  BOOLEAN </P>
         */
        public static final String VIDEO_CAPABLE = "video_capable";
    }

    /**
     * This is used to generate a instance of {@link android.telephony.ims.RcsContactPresenceTuple}.
     * See that class for more information on each of these parameters.
     */
    public static class OptionsColumns implements BaseColumns {

        /**
         * A reference to the {@link ContactColumns#_ID} that this data belongs to.
         * <P>Type:  INTEGER</P>
         */
        public static final String EAB_COMMON_ID = "eab_common_id";

        /**
         * The feature tag of options capabilities
         * <P>Type:  TEXT</P>
         */
        public static final String FEATURE_TAG = "feature_tag";

        /**
         * The request timestamp of options capabilities.
         * <P>Type:  TEXT</P>
         */
        public static final String REQUEST_TIMESTAMP = "options_request_timestamp";
    }

    @VisibleForTesting
    public static final class EabDatabaseHelper extends SQLiteOpenHelper {
        private static final String DB_NAME = "EabDatabase";
        private static final List<String> CONTACT_UNIQUE_FIELDS = new ArrayList<>();
        private static final List<String> COMMON_UNIQUE_FIELDS = new ArrayList<>();

        static {
            CONTACT_UNIQUE_FIELDS.add(ContactColumns.RAW_CONTACT_ID);
            CONTACT_UNIQUE_FIELDS.add(ContactColumns.DATA_ID);

            COMMON_UNIQUE_FIELDS.add(EabCommonColumns.EAB_CONTACT_ID);
        }

        @VisibleForTesting
        public static final String SQL_CREATE_CONTACT_TABLE = "CREATE TABLE "
                + EAB_CONTACT_TABLE_NAME
                + " ("
                + ContactColumns._ID + " INTEGER PRIMARY KEY, "
                + ContactColumns.NAME + " Text DEFAULT NULL, "
                + ContactColumns.PHONE_NUMBER + " Text DEFAULT NULL, "
                + ContactColumns.RAW_CONTACT_ID + " INTEGER DEFAULT -1, "
                + ContactColumns.DATA_ID + " INTEGER DEFAULT -1, "
                + "UNIQUE (" + TextUtils.join(", ", CONTACT_UNIQUE_FIELDS) + ")"
                + ");";

        @VisibleForTesting
        public static final String SQL_CREATE_COMMON_TABLE = "CREATE TABLE "
                + EAB_COMMON_TABLE_NAME
                + " ("
                + EabCommonColumns._ID + " INTEGER PRIMARY KEY, "
                + EabCommonColumns.EAB_CONTACT_ID + " INTEGER DEFAULT -1, "
                + EabCommonColumns.MECHANISM + " TEXT DEFAULT NULL, "
                + EabCommonColumns.REQUEST_RESULT + " INTEGER DEFAULT -1, "
                + EabCommonColumns.SUBSCRIPTION_ID + " INTEGER DEFAULT -1, "
                + "UNIQUE (" + TextUtils.join(", ", COMMON_UNIQUE_FIELDS) + ")"
                + ");";

        @VisibleForTesting
        public static final String SQL_CREATE_PRESENCE_TUPLE_TABLE = "CREATE TABLE "
                + EAB_PRESENCE_TUPLE_TABLE_NAME
                + " ("
                + PresenceTupleColumns._ID + " INTEGER PRIMARY KEY, "
                + PresenceTupleColumns.EAB_COMMON_ID + " INTEGER DEFAULT -1, "
                + PresenceTupleColumns.BASIC_STATUS + " TEXT DEFAULT NULL, "
                + PresenceTupleColumns.SERVICE_ID + " TEXT DEFAULT NULL, "
                + PresenceTupleColumns.SERVICE_VERSION + " TEXT DEFAULT NULL, "
                + PresenceTupleColumns.DESCRIPTION + " TEXT DEFAULT NULL, "
                + PresenceTupleColumns.REQUEST_TIMESTAMP + " TEXT DEFAULT NULL, "
                + PresenceTupleColumns.CONTACT_URI + " TEXT DEFAULT NULL, "

                // For ServiceCapabilities
                + PresenceTupleColumns.DUPLEX_MODE + " TEXT DEFAULT NULL, "
                + PresenceTupleColumns.UNSUPPORTED_DUPLEX_MODE + " TEXT DEFAULT NULL, "
                + PresenceTupleColumns.AUDIO_CAPABLE + " BOOLEAN DEFAULT NULL, "
                + PresenceTupleColumns.VIDEO_CAPABLE + " BOOLEAN DEFAULT NULL"
                + ");";

        @VisibleForTesting
        public static final String SQL_CREATE_OPTIONS_TABLE = "CREATE TABLE "
                + EAB_OPTIONS_TABLE_NAME
                + " ("
                + OptionsColumns._ID + " INTEGER PRIMARY KEY, "
                + OptionsColumns.EAB_COMMON_ID + " INTEGER DEFAULT -1, "
                + OptionsColumns.REQUEST_TIMESTAMP + " TEXT DEFAULT NULL, "
                + OptionsColumns.FEATURE_TAG + " TEXT DEFAULT NULL "
                + ");";

        EabDatabaseHelper(Context context) {
            super(context, DB_NAME, null, DATABASE_VERSION);
        }

        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SQL_CREATE_CONTACT_TABLE);
            db.execSQL(SQL_CREATE_COMMON_TABLE);
            db.execSQL(SQL_CREATE_PRESENCE_TUPLE_TABLE);
            db.execSQL(SQL_CREATE_OPTIONS_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
            Log.d(TAG, "DB upgrade from " + oldVersion + " to " + newVersion);
        }
    }

    private EabDatabaseHelper mOpenHelper;

    @Override
    public boolean onCreate() {
        mOpenHelper = new EabDatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        SQLiteDatabase db = getReadableDatabase();
        int match = URI_MATCHER.match(uri);
        int subId;
        String subIdString;

        Log.d(TAG, "Query URI: " + match);

        switch (match) {
            case URL_CONTACT:
                qb.setTables(EAB_CONTACT_TABLE_NAME);
                break;

            case URL_COMMON:
                qb.setTables(EAB_COMMON_TABLE_NAME);
                break;

            case URL_PRESENCE:
                qb.setTables(EAB_PRESENCE_TUPLE_TABLE_NAME);
                break;

            case URL_OPTIONS:
                qb.setTables(EAB_OPTIONS_TABLE_NAME);
                break;

            case URL_ALL_WITH_SUB_ID_AND_PHONE_NUMBER:
                List<String> pathSegment = uri.getPathSegments();

                subIdString = pathSegment.get(1);
                try {
                    subId = Integer.parseInt(subIdString);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "NumberFormatException" + e);
                    return null;
                }
                qb.appendWhereStandalone(EabCommonColumns.SUBSCRIPTION_ID + "=" + subId);

                String phoneNumber = pathSegment.get(2);
                String whereClause;
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.e(TAG, "phone number is null");
                    return null;
                }
                whereClause = " where " + ContactColumns.PHONE_NUMBER + "='" + phoneNumber + "' ";
                qb.setTables(
                        "((" + QUERY_CONTACT_TABLE + whereClause + ") AS " + EAB_CONTACT_TABLE_NAME
                                + JOIN_ALL_TABLES + ")");
                break;

            case URL_ALL:
                qb.setTables("(" + QUERY_CONTACT_TABLE + JOIN_ALL_TABLES + ")");
                break;

            default:
                Log.d(TAG, "Query failed. Not support URL.");
                return null;
        }
        return qb.query(db, projection, selection, selectionArgs, null, null, null);
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        SQLiteDatabase db = getWritableDatabase();
        int match = URI_MATCHER.match(uri);
        long result = 0;
        String tableName = "";
        switch (match) {
            case URL_CONTACT:
                tableName = EAB_CONTACT_TABLE_NAME;
                break;
            case URL_COMMON:
                tableName = EAB_COMMON_TABLE_NAME;
                break;
            case URL_PRESENCE:
                tableName = EAB_PRESENCE_TUPLE_TABLE_NAME;
                break;
            case URL_OPTIONS:
                tableName = EAB_OPTIONS_TABLE_NAME;
                break;
        }
        if (!TextUtils.isEmpty(tableName)) {
            result = db.insertWithOnConflict(tableName, null, contentValues,
                    SQLiteDatabase.CONFLICT_REPLACE);
            Log.d(TAG, "Insert uri: " + match + " ID: " + result);
        } else {
            Log.d(TAG, "Insert. Not support URI.");
        }
        return Uri.withAppendedPath(uri, String.valueOf(result));
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        SQLiteDatabase db = getWritableDatabase();
        int match = URI_MATCHER.match(uri);
        int result = 0;
        String tableName = "";
        switch (match) {
            case URL_CONTACT:
                tableName = EAB_CONTACT_TABLE_NAME;
                break;
            case URL_COMMON:
                tableName = EAB_COMMON_TABLE_NAME;
                break;
            case URL_PRESENCE:
                tableName = EAB_PRESENCE_TUPLE_TABLE_NAME;
                break;
            case URL_OPTIONS:
                tableName = EAB_OPTIONS_TABLE_NAME;
                break;
        }

        if (TextUtils.isEmpty(tableName)) {
            Log.d(TAG, "bulkInsert. Not support URI.");
            return 0;
        }

        try {
            // Batch all insertions in a single transaction to improve efficiency.
            db.beginTransaction();
            for (ContentValues contentValue : values) {
                if (contentValue != null) {
                    db.insertWithOnConflict(tableName, null, contentValue,
                            SQLiteDatabase.CONFLICT_REPLACE);
                    result++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        Log.d(TAG, "bulkInsert uri: " + match + " count: " + result);
        return result;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = getWritableDatabase();
        int match = URI_MATCHER.match(uri);
        int result = 0;
        String tableName = "";
        switch (match) {
            case URL_CONTACT:
                tableName = EAB_CONTACT_TABLE_NAME;
                break;
            case URL_COMMON:
                tableName = EAB_COMMON_TABLE_NAME;
                break;
            case URL_PRESENCE:
                tableName = EAB_PRESENCE_TUPLE_TABLE_NAME;
                break;
            case URL_OPTIONS:
                tableName = EAB_OPTIONS_TABLE_NAME;
                break;
        }
        if (!TextUtils.isEmpty(tableName)) {
            result = db.delete(tableName, selection, selectionArgs);
            Log.d(TAG, "Delete uri: " + match + " result: " + result);
        } else {
            Log.d(TAG, "Delete. Not support URI.");
        }
        return result;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String selection,
            String[] selectionArgs) {
        SQLiteDatabase db = getWritableDatabase();
        int match = URI_MATCHER.match(uri);
        int result = 0;
        String tableName = "";
        switch (match) {
            case URL_CONTACT:
                tableName = EAB_CONTACT_TABLE_NAME;
                break;
            case URL_COMMON:
                tableName = EAB_COMMON_TABLE_NAME;
                break;
            case URL_PRESENCE:
                tableName = EAB_PRESENCE_TUPLE_TABLE_NAME;
                break;
            case URL_OPTIONS:
                tableName = EAB_OPTIONS_TABLE_NAME;
                break;
        }
        if (!TextUtils.isEmpty(tableName)) {
            result = db.updateWithOnConflict(tableName, contentValues,
                    selection, selectionArgs, SQLiteDatabase.CONFLICT_REPLACE);
            Log.d(TAG, "Update uri: " + match + " result: " + result);
        } else {
            Log.d(TAG, "Update. Not support URI.");
        }
        return result;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @VisibleForTesting
    public SQLiteDatabase getWritableDatabase() {
        Log.d(TAG, "getWritableDatabase");
        return mOpenHelper.getWritableDatabase();
    }

    @VisibleForTesting
    public SQLiteDatabase getReadableDatabase() {
        Log.d(TAG, "getReadableDatabase");
        return mOpenHelper.getReadableDatabase();
    }
}
