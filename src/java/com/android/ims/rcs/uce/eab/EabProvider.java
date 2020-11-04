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

public class EabProvider extends ContentProvider {

    public static final Uri CONTACT_URI = Uri.parse("content://eab/contact");
    public static final Uri COMMON_URI = Uri.parse("content://eab/common");
    public static final Uri PRESENCE_URI = Uri.parse("content://eab/presence");
    public static final Uri OPTION_URI = Uri.parse("content://eab/option");
    public static final Uri ALL_DATA_URI = Uri.parse("content://eab/all");

    @VisibleForTesting
    public static final String AUTHORITY = "eab";

    private static final String TAG = "EabProvider";
    private static final int DATABASE_VERSION = 1;

    private static final String EAB_CONTACT_TABLE_NAME = "eab_contact";
    private static final String EAB_COMMON_TABLE_NAME = "eab_common";
    private static final String EAB_PRESENCE_TUPLE_TABLE_NAME = "eab_presence";
    private static final String EAB_OPTION_TABLE_NAME = "eab_option";

    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    private static final int URL_CONTACT = 1;
    private static final int URL_COMMON = 2;
    private static final int URL_PRESENCE = 3;
    private static final int URL_OPTION = 4;
    private static final int URL_ALL = 5;
    private static final int URL_PRESENCE_WITH_SUBID = 6;
    private static final int URL_OPTION_WITH_SUBID = 7;
    private static final int URL_ALL_WITH_SUBID = 8;
    private static final int URL_ALL_WITH_SUBID_AND_PHONE_NUMBER = 9;

    static {
        URI_MATCHER.addURI(AUTHORITY, "contact", URL_CONTACT);
        URI_MATCHER.addURI(AUTHORITY, "common", URL_COMMON);
        URI_MATCHER.addURI(AUTHORITY, "presence", URL_PRESENCE);
        URI_MATCHER.addURI(AUTHORITY, "presence/#", URL_PRESENCE_WITH_SUBID);
        URI_MATCHER.addURI(AUTHORITY, "option", URL_OPTION);
        URI_MATCHER.addURI(AUTHORITY, "option/#", URL_OPTION_WITH_SUBID);
        URI_MATCHER.addURI(AUTHORITY, "all", URL_ALL);
        URI_MATCHER.addURI(AUTHORITY, "all/#", URL_ALL_WITH_SUBID);
        URI_MATCHER.addURI(AUTHORITY, "all/#/*", URL_ALL_WITH_SUBID_AND_PHONE_NUMBER);
    }

    private static final String JOIN_ALL_TABLES =
            // join common table
            " LEFT JOIN " + EAB_COMMON_TABLE_NAME
            + " ON contact." + ContactColumns.ID
            + "=" + EAB_COMMON_TABLE_NAME + "." + PresenceTupleColumns.EAB_CONTACT_ID

            // join option table
            + " LEFT JOIN " + EAB_OPTION_TABLE_NAME
            + " ON contact." + ContactColumns.ID
            + "=" + EAB_OPTION_TABLE_NAME + "." + PresenceTupleColumns.EAB_CONTACT_ID

            // join presence table
            + " LEFT JOIN " + EAB_PRESENCE_TUPLE_TABLE_NAME
            + " ON contact." + ContactColumns.ID
            + "=" + EAB_PRESENCE_TUPLE_TABLE_NAME + "." + PresenceTupleColumns.EAB_CONTACT_ID;

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

            case URL_OPTION_WITH_SUBID:
                subIdString = uri.getLastPathSegment();
                try {
                    subId = Integer.parseInt(subIdString);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "NumberFormatException" + e);
                    return null;
                }
                qb.appendWhereStandalone(OptionColumns.SUB_ID + "=" +subId);
            case URL_OPTION:
                qb.setTables(EAB_OPTION_TABLE_NAME);
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
                        + " OR " + OptionColumns.SUB_ID + "=" + subId);
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
                        + " OR " + OptionColumns.SUB_ID + "=" + subId);

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
            case URL_OPTION:
                tableName = EAB_OPTION_TABLE_NAME;
                break;
        }
        if (!TextUtils.isEmpty(tableName)) {
            result = db.insertWithOnConflict(tableName, null, contentValues,
                    SQLiteDatabase.CONFLICT_REPLACE);
            Log.d(TAG, "Insert uri: " + match + " ID: " + result);
        } else {
            Log.d(TAG, "Insert. Not support URI.");
        }
        return uri;
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
            case URL_OPTION:
                tableName = EAB_OPTION_TABLE_NAME;
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
            case URL_OPTION:
                tableName = EAB_OPTION_TABLE_NAME;
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

    static class ContactColumns {
        static final String ID = "_id";
        static final String NAME = "name";
        static final String PHONE_NUMBER = "phone_number";
        static final String RAW_CONTACT_ID = "raw_contact_id";
    }

    static class EabCommonColumns {
        static final String ID = "common_id";
        static final String EAB_CONTACT_ID = "eab_contact_id";
        static final String MECHANISM = "mechanism";
        static final String REQUEST_RESULT = "request_result";
    }

    static class PresenceTupleColumns {
        static final String ID = "presence_id";
        static final String EAB_CONTACT_ID = "eab_contact_id";
        static final String SUB_ID = "presence_sub_id";
        static final String STATUS = "status";
        static final String SERVICE_ID = "service_id";
        static final String SERVICE_VERSION = "service_version";
        static final String DESCRIPTION = "description";
        static final String DUPLEX_MODE = "duplex_mode";
        static final String UNSUPPORTED_DUPLEX_MODE = "unsupported_duplex_mode";
        static final String TIMESTAMP = "presence_timestamp";
        static final String AUDIO_CAPABLE = "audio_capable";
        static final String VIDEO_CAPABLE = "video_capable";
    }

    static class OptionColumns {
        static final String ID = "option_id";
        static final String EAB_CONTACT_ID = "eab_contact_id";
        static final String SUB_ID = "option_sub_id";
        static final String FEATURE_TAG = "feature_tag";
        static final String TIMESTAMP = "option_timestamp";
    }

    protected static final class EabDatabaseHelper extends SQLiteOpenHelper {
        private static final String DB_NAME = "EabDatabase";
        private static final List<String> CONTACT_UNIQUE_FIELDS = new ArrayList<>();
        private static final List<String> COMMON_UNIQUE_FIELDS = new ArrayList<>();

        static {
            CONTACT_UNIQUE_FIELDS.add(ContactColumns.PHONE_NUMBER);
            CONTACT_UNIQUE_FIELDS.add(ContactColumns.RAW_CONTACT_ID);

            COMMON_UNIQUE_FIELDS.add(PresenceTupleColumns.EAB_CONTACT_ID);
        }
        @VisibleForTesting
        public static final String SQL_CREATE_CONTACT_TABLE = "CREATE TABLE "
                + EAB_CONTACT_TABLE_NAME
                + " ("
                + ContactColumns.ID + " INTEGER PRIMARY KEY,"
                + ContactColumns.RAW_CONTACT_ID + " INTEGER DEFAULT -1,"
                + ContactColumns.NAME + " Text DEFAULT '',"
                + ContactColumns.PHONE_NUMBER + " Text DEFAULT '',"
                + "UNIQUE (" + TextUtils.join(", ", CONTACT_UNIQUE_FIELDS) + "))";

        @VisibleForTesting
        public static final String SQL_CREATE_COMMON_TABLE = "CREATE TABLE "
                + EAB_COMMON_TABLE_NAME
                + " ("
                + EabCommonColumns.ID + " INTEGER PRIMARY KEY,"
                + EabCommonColumns.EAB_CONTACT_ID + " INTEGER DEFAULT -1,"
                + EabCommonColumns.MECHANISM + " TEXT DEFAULT '',"
                + EabCommonColumns.REQUEST_RESULT + " INTEGER DEFAULT '',"
                + "UNIQUE (" + TextUtils.join(", ", COMMON_UNIQUE_FIELDS) + "))";

        @VisibleForTesting
        public static final String SQL_CREATE_PRESENCE_TUPLE_TABLE = "CREATE TABLE "
                + EAB_PRESENCE_TUPLE_TABLE_NAME
                + " ("
                + PresenceTupleColumns.ID + " INTEGER PRIMARY KEY,"
                + PresenceTupleColumns.EAB_CONTACT_ID + " INTEGER DEFAULT -1,"
                + PresenceTupleColumns.SUB_ID + " INTEGER DEFAULT -1,"
                + PresenceTupleColumns.STATUS + " TEXT DEFAULT '',"
                + PresenceTupleColumns.SERVICE_ID + " TEXT DEFAULT '',"
                + PresenceTupleColumns.SERVICE_VERSION + " TEXT DEFAULT '',"
                + PresenceTupleColumns.DESCRIPTION + " TEXT DEFAULT '',"
                + PresenceTupleColumns.DUPLEX_MODE + " TEXT DEFAULT '',"
                + PresenceTupleColumns.TIMESTAMP + " TEXT DEFAULT '',"
                + PresenceTupleColumns.UNSUPPORTED_DUPLEX_MODE + " TEXT DEFAULT '',"
                + PresenceTupleColumns.AUDIO_CAPABLE + " BOOLEAN DEFAULT 0,"
                + PresenceTupleColumns.VIDEO_CAPABLE + " BOOLEAN DEFAULT 0)";

        @VisibleForTesting
        public static final String SQL_CREATE_OPTION_TABLE = "CREATE TABLE "
                + EAB_OPTION_TABLE_NAME
                + " ("
                + OptionColumns.ID + " INTEGER PRIMARY KEY,"
                + OptionColumns.EAB_CONTACT_ID + " INTEGER DEFAULT -1,"
                + OptionColumns.SUB_ID + " INTEGER DEFAULT -1,"
                + OptionColumns.TIMESTAMP + " TEXT DEFAULT '',"
                + OptionColumns.FEATURE_TAG + " TEXT DEFAULT '')";

        EabDatabaseHelper(Context context) {
            super(context, DB_NAME, null, DATABASE_VERSION);
        }

        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SQL_CREATE_CONTACT_TABLE);
            db.execSQL(SQL_CREATE_COMMON_TABLE);
            db.execSQL(SQL_CREATE_PRESENCE_TUPLE_TABLE);
            db.execSQL(SQL_CREATE_OPTION_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
            Log.d(TAG, "DB upgrade from " + oldVersion + " to " + newVersion);
        }
    }
}
