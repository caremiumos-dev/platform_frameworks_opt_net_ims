/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.ims.testapps;

import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.ims.ImsServiceBase;
import android.util.Log;

import com.android.ims.internal.IImsServiceController;

public class TestImsService extends ImsServiceBase {

    private static final String LOG_TAG = "TestImsService";

    private IBinder mBinder = new IImsServiceController.Stub() {

        @Override
        public void createImsFeature(int subId, int feature) throws RemoteException {
            Log.i(LOG_TAG, "createImsFeature - subId: " + subId + " feature:" + feature);
        }

        @Override
        public void removeImsFeature(int subId, int feature) throws RemoteException {
            Log.i(LOG_TAG, "createImsFeature - subId: " + subId + " feature:" + feature);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(LOG_TAG, "onBind called");
        return mBinder;
    }
}
