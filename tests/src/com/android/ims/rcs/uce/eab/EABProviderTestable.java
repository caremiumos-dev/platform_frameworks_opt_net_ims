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

import static com.android.ims.rcs.uce.eab.EABProvider.EABDatabaseHelper.SQL_CREATE_COMMON_TABLE;
import static com.android.ims.rcs.uce.eab.EABProvider.EABDatabaseHelper.SQL_CREATE_CONTACT_TABLE;
import static com.android.ims.rcs.uce.eab.EABProvider.EABDatabaseHelper.SQL_CREATE_OPTION_TABLE;
import static com.android.ims.rcs.uce.eab.EABProvider.EABDatabaseHelper.SQL_CREATE_PRESENCE_TUPLE_TABLE;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class EABProviderTestable extends EABProvider {
    private static final String TAG = EABProviderTestable.class.getSimpleName();

    private InMemoryEABProviderDbHelper mDbHelper;

    @Override
    public boolean onCreate() {
        Log.d(TAG, "onCreate called");
        mDbHelper = new InMemoryEABProviderDbHelper();
        return true;
    }

    // close mDbHelper database object
    protected void closeDatabase() {
        mDbHelper.close();
    }

    void initializeForTesting(Context context) {
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = EABProvider.AUTHORITY;

        attachInfoForTesting(context, providerInfo);
    }

    @Override
    public SQLiteDatabase getReadableDatabase() {
        Log.d(TAG, "getReadableDatabase called" + mDbHelper.getReadableDatabase());
        return mDbHelper.getReadableDatabase();
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        Log.d(TAG, "getWritableDatabase called" + mDbHelper.getWritableDatabase());
        return mDbHelper.getWritableDatabase();
    }

    /**
     * An in memory DB for EABProviderTestable to use
     */
    public static class InMemoryEABProviderDbHelper extends SQLiteOpenHelper {
        public InMemoryEABProviderDbHelper() {
            super(null,      // no context is needed for in-memory db
                    null,      // db file name is null for in-memory db
                    null,      // CursorFactory is null by default
                    1);        // db version is no-op for tests
            Log.d(TAG, "InMemoryEABProviderDbHelper creating in-memory database");
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            //set up the EAB table
            Log.d(TAG, "InMemoryEABProviderDbHelper onCreate");
            db.execSQL(SQL_CREATE_CONTACT_TABLE);
            db.execSQL(SQL_CREATE_COMMON_TABLE);
            db.execSQL(SQL_CREATE_PRESENCE_TUPLE_TABLE);
            db.execSQL(SQL_CREATE_OPTION_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.d(TAG, "InMemoryEABProviderDbHelper onUpgrade");
            return;
        }
    }
}
