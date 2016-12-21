/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.ims;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.HashSet;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for ImsResolver
 */
public class ImsResolverTest extends ImsTestBase {

    @Mock Context mMockContext;
    @Mock ImsResolver.SubscriptionManagerProxy mTestSubscriptionManagerProxy;
    private ImsResolver mTestImsResolver;
    private BroadcastReceiver mTestPackageBroadcastReceiver;
    private BroadcastReceiver mTestCarrierConfigReceiver;


    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mTestImsResolver = new ImsResolver(mMockContext, "", 2);
        mTestImsResolver.setSubscriptionManagerProxy(mTestSubscriptionManagerProxy);
        ArgumentCaptor<BroadcastReceiver> packageBroadcastCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        ArgumentCaptor<BroadcastReceiver> carrierConfigCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mMockContext).registerReceiverAsUser(packageBroadcastCaptor.capture(), any(),
                any(), any(), any());
        verify(mMockContext).registerReceiver(carrierConfigCaptor.capture(), any());
        mTestCarrierConfigReceiver = carrierConfigCaptor.capture();
        mTestPackageBroadcastReceiver = packageBroadcastCaptor.capture();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        mTestImsResolver = null;
        super.tearDown();
    }
    
    private ResolveInfo getResolveInfo(ComponentName name, HashSet<String> features,
            boolean isPermissionGranted) {
        ResolveInfo info = new ResolveInfo();
        info.serviceInfo = new ServiceInfo();
        info.serviceInfo.packageName = name.getPackageName();
        info.serviceInfo.name = name.getClassName();
        for(String s : features) {
            info.serviceInfo.metaData.putBoolean(s, true);
        }
        if(isPermissionGranted) {
            info.serviceInfo.permission = Manifest.permission.BIND_IMS_SERVICE;
        }
        return info;
    }
}
