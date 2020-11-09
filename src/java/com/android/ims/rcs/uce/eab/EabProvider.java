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
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides the ability to query the enhanced address book databases(A.K.A. EAB) which is
 * stored in a SQLite database.
 *
 * There are 4 tables in EAB DB:
 * Contact:  It stores the name and phone number of the contact.
 *
 * Common:   It's a general table for storing the query results and the mechanisms of querying UCE
 *           capabilities. It should be 1:1 mapped to the contact table and has a foreign
 *           key(eab_contact_id) that refers to the id of contact table. If the value of mechanism
 *           is 1(CAPABILITY_MECHANISM_PRESENCE), the capability information should be stored in
 *           presence table, if the value of mechanism is 2(CAPABILITY_MECHANISM_OPTIONS), it
 *           should be stored in options table.
 *
 * Presence: It stores the information(@see RcsContactUceCapability) that queried through presence
 *           server. It should be *:1 mapped to the common table and has a foreign
 *           key(eab_common_id) that refers to the id of common table.
 *
 * Options:  It stores the information(@see RcsContactUceCapability) that queried through SIP
 *           OPTIONS. It should be *:1 mapped to the common table and it has a foreign
 *           key(eab_common_id) that refers to the id of common table.
 */
public class EabProvider extends ContentProvider {
    // The public URI for operating Eab DB. They support query, insert, delete and update.
    public static final Uri CONTACT_URI = Uri.parse("content://eab/contact");
    public static final Uri COMMON_URI = Uri.parse("content://eab/common");
    public static final Uri PRESENCE_URI = Uri.parse("content://eab/presence");
    public static final Uri OPTIONS_URI = Uri.parse("content://eab/options");
    // The public URI for operating Eab DB. Only support query.
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
    private static final int URL_PRESENCE_WITH_SUBID = 6;
    private static final int URL_OPTIONS_WITH_SUBID = 7;
    private static final int URL_ALL_WITH_SUBID = 8;
    private static final int URL_ALL_WITH_SUBID_AND_PHONE_NUMBER = 9;

    static {
        URI_MATCHER.addURI(AUTHORITY, "contact", URL_CONTACT);
        URI_MATCHER.addURI(AUTHORITY, "common", URL_COMMON);
        URI_MATCHER.addURI(AUTHORITY, "presence", URL_PRESENCE);
        URI_MATCHER.addURI(AUTHORITY, "presence/#", URL_PRESENCE_WITH_SUBID);
        URI_MATCHER.addURI(AUTHORITY, "options", URL_OPTIONS);
        URI_MATCHER.addURI(AUTHORITY, "options/#", URL_OPTIONS_WITH_SUBID);
        URI_MATCHER.addURI(AUTHORITY, "all", URL_ALL);
        URI_MATCHER.addURI(AUTHORITY, "all/#", URL_ALL_WITH_SUBID);
        URI_MATCHER.addURI(AUTHORITY, "all/#/*", URL_ALL_WITH_SUBID_AND_PHONE_NUMBER);
    }

    private static final String JOIN_ALL_TABLES =
            // join common table
            " LEFT JOIN " + EAB_COMMON_TABLE_NAME
                    + " ON contact." + ContactColumns.ID
                    + "=" + EAB_COMMON_TABLE_NAME + "." + EabCommonColumns.EAB_CONTACT_ID

                    // join options table
                    + " LEFT JOIN " + EAB_OPTIONS_TABLE_NAME
                    + " ON " + EabCommonColumns.ID
                    + "=" + EAB_OPTIONS_TABLE_NAME + "." + OptionsColumns.EAB_COMMON_ID

                    // join presence table
                    + " LEFT JOIN " + EAB_PRESENCE_TUPLE_TABLE_NAME
                    + " ON " + EabCommonColumns.ID
                    + "=" + EAB_PRESENCE_TUPLE_TABLE_NAME + "." + PresenceTupleColumns.EAB_COMMON_ID;

    /**
     * The contact table's columns.
     */
    public static class ContactColumns {
        public static final String ID = "_id";
        public static final String NAME = "name";
        public static final String PHONE_NUMBER = "phone_number";
        public static final String RAW_CONTACT_ID = "raw_contact_id";
    }

    /**
     * The common table's columns. The eab_contact_id should refer to the id of contact table.
     */
    public static class EabCommonColumns {
        public static final String ID = "common_id";
        public static final String EAB_CONTACT_ID = "eab_contact_id";
        public static final String MECHANISM = "mechanism";
        public static final String REQUEST_RESULT = "request_result";
    }

    /**
     * The presence table's columns. The eab_common_id should refer to the id of common table.
     */
    public static class PresenceTupleColumns {
        public static final String ID = "presence_id";
        public static final String EAB_COMMON_ID = "eab_common_id";
        public static final String SUB_ID = "presence_sub_id";
        public static final String STATUS = "status";
        public static final String SERVICE_ID = "service_id";
        public static final String SERVICE_VERSION = "service_version";
        public static final String DESCRIPTION = "description";
        public static final String DUPLEX_MODE = "duplex_mode";
        public static final String UNSUPPORTED_DUPLEX_MODE = "unsupported_duplex_mode";
        public static final String TIMESTAMP = "presence_timestamp";
        public static final String AUDIO_CAPABLE = "audio_capable";
        public static final String VIDEO_CAPABLE = "video_capable";
    }

    /**
     * The options table's columns. The eab_common_id should refer to the id of common table.
     */
    public static class OptionsColumns {
        public static final String ID = "options_id";
        public static final String EAB_COMMON_ID = "eab_common_id";
        public static final String SUB_ID = "options_sub_id";
        public static final String FEATURE_TAG = "feature_tag";
        public static final String TIMESTAMP = "options_timestamp";
    }

    protected static final class EabDatabaseHelper extends SQLiteOpenHelper {
        private static final String DB_NAME = "EabDatabase";
        private static final List<String> CONTACT_UNIQUE_FIELDS = new ArrayList<>();
        private static final List<String> COMMON_UNIQUE_FIELDS = new ArrayList<>();

        static {
            CONTACT_UNIQUE_FIELDS.add(ContactColumns.PHONE_NUMBER);
            CONTACT_UNIQUE_FIELDS.add(ContactColumns.RAW_CONTACT_ID);

            COMMON_UNIQUE_FIELDS.add(EabCommonColumns.EAB_CONTACT_ID);
        }

        @VisibleForTesting
        public static final String SQL_CREATE_CONTACT_TABLE = "CREATE TABLE "
                + EAB_CONTACT_TABLE_NAME
                + " ("
                + ContactColumns.ID + " INTEGER PRIMARY KEY, "
                + ContactColumns.RAW_CONTACT_ID + " INTEGER DEFAULT -1, "
                + ContactColumns.NAME + " Text DEFAULT '', "
                + ContactColumns.PHONE_NUMBER + " Text DEFAULT '', "
                + "UNIQUE (" + TextUtils.join(", ", CONTACT_UNIQUE_FIELDS) + ")"
                + ");";

        @VisibleForTesting
        public static final String SQL_CREATE_COMMON_TABLE = "CREATE TABLE "
                + EAB_COMMON_TABLE_NAME
                + " ("
                + EabCommonColumns.ID + " INTEGER PRIMARY KEY, "
                + EabCommonColumns.EAB_CONTACT_ID + " INTEGER DEFAULT -1, "
                + EabCommonColumns.MECHANISM + " TEXT DEFAULT '', "
                + EabCommonColumns.REQUEST_RESULT + " INTEGER DEFAULT '', "
                + "UNIQUE (" + TextUtils.join(", ", COMMON_UNIQUE_FIELDS) + ")"
                + ");";

        @VisibleForTesting
        public static final String SQL_CREATE_PRESENCE_TUPLE_TABLE = "CREATE TABLE "
                + EAB_PRESENCE_TUPLE_TABLE_NAME
                + " ("
                + PresenceTupleColumns.ID + " INTEGER PRIMARY KEY, "
                + PresenceTupleColumns.EAB_COMMON_ID + " INTEGER DEFAULT -1, "
                + PresenceTupleColumns.SUB_ID + " INTEGER DEFAULT -1, "
                + PresenceTupleColumns.STATUS + " TEXT DEFAULT '', "
                + PresenceTupleColumns.SERVICE_ID + " TEXT DEFAULT '', "
                + PresenceTupleColumns.SERVICE_VERSION + " TEXT DEFAULT '', "
                + PresenceTupleColumns.DESCRIPTION + " TEXT DEFAULT '', "
                + PresenceTupleColumns.DUPLEX_MODE + " TEXT DEFAULT '', "
                + PresenceTupleColumns.TIMESTAMP + " TEXT DEFAULT '', "
                + PresenceTupleColumns.UNSUPPORTED_DUPLEX_MODE + " TEXT DEFAULT '', "
                + PresenceTupleColumns.AUDIO_CAPABLE + " BOOLEAN DEFAULT 0, "
                + PresenceTupleColumns.VIDEO_CAPABLE + " BOOLEAN DEFAULT 0"
                + ");";

        @VisibleForTesting
        public static final String SQL_CREATE_OPTIONS_TABLE = "CREATE TABLE "
                + EAB_OPTIONS_TABLE_NAME
                + " ("
                + OptionsColumns.ID + " INTEGER PRIMARY KEY, "
                + OptionsColumns.EAB_COMMON_ID + " INTEGER DEFAULT -1, "
                + OptionsColumns.SUB_ID + " INTEGER DEFAULT -1, "
                + OptionsColumns.TIMESTAMP + " TEXT DEFAULT '', "
                + OptionsColumns.FEATURE_TAG + " TEXT DEFAULT '' "
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
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
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

            case URL_PRESENCE_WITH_SUBID:
                subIdString = uri.getLastPathSegment();
                try {
                    subId = Integer.parseInt(subIdString);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "NumberFormatException" + e);
                    return null;
                }
                qb.appendWhereStandalone(PresenceTupleColumns.SUB_ID + "=" +subId);
            case URL_PRESENCE:
                qb.setTables(EAB_PRESENCE_TUPLE_TABLE_NAME);
                break;

            case URL_OPTIONS_WITH_SUBID:
                subIdString = uri.getLastPathSegment();
                try {
                    subId = Integer.parseInt(subIdString);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "NumberFormatException" + e);
                    return null;
                }
                qb.appendWhereStandalone(OptionsColumns.SUB_ID + "=" +subId);
            case URL_OPTIONS:
                qb.setTables(EAB_OPTIONS_TABLE_NAME);
                break;

            case URL_ALL_WITH_SUBID:
                subIdString = uri.getLastPathSegment();
                try {
                    subId = Integer.parseInt(subIdString);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "NumberFormatException" + e);
                    return null;
                }
                qb.appendWhereStandalone( PresenceTupleColumns.SUB_ID+ "=" + subId
                        + " OR " + OptionsColumns.SUB_ID + "=" + subId);
            case URL_ALL:
                String contactSql = "(SELECT * from " + EAB_CONTACT_TABLE_NAME + ") contact ";
                qb.setTables(contactSql + JOIN_ALL_TABLES);
                break;

            case URL_ALL_WITH_SUBID_AND_PHONE_NUMBER:
                List<String> pathSegment = uri.getPathSegments();
                subIdString = pathSegment.get(1);
                try {
                    subId = Integer.parseInt(subIdString);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "NumberFormatException" + e);
                    return null;
                }
                qb.appendWhereStandalone( PresenceTupleColumns.SUB_ID+ "=" + subId
                        + " OR " + OptionsColumns.SUB_ID + "=" + subId);

                // Handle the phone number
                String phoneNumberList = pathSegment.get(2);
                StringBuilder whereClause = new StringBuilder();
                if (phoneNumberList == null) {
                    phoneNumberList = "";
                }
                if (!TextUtils.isEmpty(phoneNumberList)) {
                    String[] phoneNumbers = phoneNumberList.split(",");
                    for (int i = 0; i < phoneNumbers.length; i++) {
                        if (whereClause.length() > 0) {
                            whereClause.append(" OR ");
                        }
                        whereClause.append(" phone_number=? ");
                    }

                    if (selectionArgs != null) {
                        String[] newSelectionArgs =
                                new String[selectionArgs.length + phoneNumbers.length];
                        System.arraycopy(phoneNumbers, 0, newSelectionArgs, 0,
                                phoneNumbers.length);
                        System.arraycopy(selectionArgs, 0, newSelectionArgs, phoneNumbers.length,
                                selectionArgs.length);
                        selectionArgs = newSelectionArgs;
                    } else {
                        selectionArgs = phoneNumbers;
                    }

                    if (whereClause.length() > 0) {
                        whereClause.insert(0, " WHERE ");
                    }
                }

                String filterSql = "(SELECT * from " + EAB_CONTACT_TABLE_NAME
                        + whereClause + ") contact ";
                qb.setTables(filterSql + JOIN_ALL_TABLES);
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
            for (ContentValues contentValue: values) {
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
