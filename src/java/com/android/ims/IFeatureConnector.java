/*
 * Copyright (c) 2019 The Android Open Source Project
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

package com.android.ims;

import android.os.IBinder;
import android.os.IInterface;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsRegistration;

import com.android.ims.internal.IImsServiceFeatureCallback;

public interface IFeatureConnector {
    void registerFeatureListener(int slotId, IImsServiceFeatureCallback cb, boolean oneShot);
    void unregisterFeatureListener(IImsServiceFeatureCallback cb);
    void associate(IBinder feature, IImsConfig c, IImsRegistration r);
    void invalidate();
    void updateFeatureState(int state);
    void updateFeatureCapabilities(long capabilities);
}