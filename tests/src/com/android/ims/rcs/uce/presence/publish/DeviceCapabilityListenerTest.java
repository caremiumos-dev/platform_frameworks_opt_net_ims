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

package com.android.ims.rcs.uce.presence.publish;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.RegistrationManager.RegistrationCallback;
import android.telephony.ims.feature.MmTelFeature;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;
import com.android.ims.rcs.uce.presence.publish.PublishController.PUBLISH_TRIGGER_TYPE;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class DeviceCapabilityListenerTest extends ImsTestBase {

    @Mock DeviceCapabilityInfo mDeviceCapability;
    @Mock PublishController.PublishControllerCallback mCallback;
    @Mock ImsMmTelManager mImsMmTelManager;
    @Mock DeviceCapabilityListenerImpl.ImsMmTelManagerFactory mImsMmTelMgrFactory;
    @Mock ProvisioningManager mProvisioningManager;
    @Mock DeviceCapabilityListenerImpl.ProvisioningManagerFactory mProvisioningMgrFactory;

    int mSubId = 1;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        doReturn(mImsMmTelManager).when(mImsMmTelMgrFactory).createImsMmTelManager(anyInt());
        doReturn(mProvisioningManager).when(mProvisioningMgrFactory).
                createProvisioningManager(anyInt());

        doReturn(true).when(mDeviceCapability).updateTtyPreferredMode(anyInt());
        doReturn(true).when(mDeviceCapability).updateAirplaneMode(anyBoolean());
        doReturn(true).when(mDeviceCapability).updateMobileData(anyBoolean());
        doReturn(true).when(mDeviceCapability).updateVtSetting(anyBoolean());
        doReturn(true).when(mDeviceCapability).updateVtSetting(anyBoolean());
        doReturn(true).when(mDeviceCapability).updateMmtelCapabilitiesChanged(any());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testTurnOnListener() throws Exception {
        DeviceCapabilityListener deviceCapListener = createDeviceCapabilityListener();

        deviceCapListener.turnOnListener();

        verify(mContext).registerReceiver(any(), any());
        verify(mProvisioningManager).registerProvisioningChangedCallback(any(), any());
    }

    @Test
    @SmallTest
    public void testTurnOffListener() throws Exception {
        DeviceCapabilityListener deviceCapListener = createDeviceCapabilityListener();
        deviceCapListener.turnOnListener();

        // Turn off the listener
        deviceCapListener.turnOffListener();

        verify(mContext).unregisterReceiver(any());
        verify(mProvisioningManager).unregisterProvisioningChangedCallback(any());
    }

    @Test
    @SmallTest
    public void testDestroy() throws Exception {
        DeviceCapabilityListener deviceCapListener = createDeviceCapabilityListener();
        deviceCapListener.turnOnListener();

        // The listener is destroyed.
        deviceCapListener.onDestroy();

        verify(mContext).unregisterReceiver(any());
        verify(mProvisioningManager).unregisterProvisioningChangedCallback(any());
    }

    @Test
    @SmallTest
    public void testTtyPreferredModeChange() throws Exception {
        DeviceCapabilityListenerImpl deviceCapListener = createDeviceCapabilityListener();
        final BroadcastReceiver receiver = deviceCapListener.mReceiver;

        Intent intent = new Intent(TelecomManager.ACTION_TTY_PREFERRED_MODE_CHANGED);
        receiver.onReceive(mContext, intent);

        verify(mDeviceCapability).updateTtyPreferredMode(anyInt());
        verify(mCallback).requestPublishFromInternal(PUBLISH_TRIGGER_TYPE.TTY_PREFERRED_CHANGED,
                0L);
    }

    @Test
    @SmallTest
    public void testAirplaneModeChange() throws Exception {
        DeviceCapabilityListenerImpl deviceCapListener = createDeviceCapabilityListener();
        final BroadcastReceiver receiver = deviceCapListener.mReceiver;

        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        receiver.onReceive(mContext, intent);

        verify(mDeviceCapability).updateAirplaneMode(anyBoolean());
        verify(mCallback).requestPublishFromInternal(PUBLISH_TRIGGER_TYPE.AIRPLANE_MODE_CHANGED,
                0L);
    }

    @Test
    @SmallTest
    public void testMmtelRegistration() throws Exception {
        DeviceCapabilityListenerImpl deviceCapListener = createDeviceCapabilityListener();
        RegistrationCallback registrationCallback = deviceCapListener.mMmtelRegistrationCallback;

        registrationCallback.onRegistered(anyInt());

        verify(mDeviceCapability).updateImsMmtelRegistered(anyInt());
        verify(mCallback).requestPublishFromInternal(PUBLISH_TRIGGER_TYPE.MMTEL_REGISTERED, 0L);
    }

    @Test
    @SmallTest
    public void testMmTelUnregistration() throws Exception {
        DeviceCapabilityListenerImpl deviceCapListener = createDeviceCapabilityListener();
        RegistrationCallback registrationCallback = deviceCapListener.mMmtelRegistrationCallback;

        ImsReasonInfo info = new ImsReasonInfo();
        registrationCallback.onUnregistered(info);

        verify(mDeviceCapability).updateImsMmtelUnregistered();
        verify(mCallback).requestPublishFromInternal(PUBLISH_TRIGGER_TYPE.MMTEL_UNREGISTERED, 0L);
    }

    @Test
    @SmallTest
    public void testMmtelCapabilityChange() throws Exception {
        DeviceCapabilityListenerImpl deviceCapListener = createDeviceCapabilityListener();
        ImsMmTelManager.CapabilityCallback callback = deviceCapListener.mMmtelCapabilityCallback;

        MmTelFeature.MmTelCapabilities capabilities = new MmTelFeature.MmTelCapabilities();
        callback.onCapabilitiesStatusChanged(capabilities);

        verify(mDeviceCapability).updateMmtelCapabilitiesChanged(capabilities);
        verify(mCallback).requestPublishFromInternal(PUBLISH_TRIGGER_TYPE.MMTEL_CAPABILITY_CHANGED,
                0L);
    }

    private DeviceCapabilityListenerImpl createDeviceCapabilityListener() {
        DeviceCapabilityListenerImpl deviceCapListener = new DeviceCapabilityListenerImpl(mContext,
                mSubId, mDeviceCapability, mCallback, Looper.getMainLooper());
        deviceCapListener.setImsMmTelManagerFactory(mImsMmTelMgrFactory);
        deviceCapListener.setProvisioningMgrFactory(mProvisioningMgrFactory);
        return deviceCapListener;
    }
}
