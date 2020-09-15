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

package com.android.ims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.IBinder;
import android.os.IInterface;
import android.telephony.ims.ImsService;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsMmTelFeature;
import android.telephony.ims.aidl.IImsRcsFeature;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.feature.ImsFeature;

import androidx.test.filters.SmallTest;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.ims.internal.IImsServiceFeatureCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class FeatureConnectionRepositoryTest extends ImsTestBase {

    private static final int TEST_PHONE_ID_1 = 1;
    private static final int TEST_PHONE_ID_2 = 2;
    private static final long TEST_SERVICE_CAPS = ImsService.CAPABILITY_EMERGENCY_OVER_MMTEL;

    @Mock IBinder mMockMmTelFeatureA;
    @Mock IBinder mMockMmTelFeatureB;
    @Mock IBinder mMockRcsFeatureA;
    @Mock IImsConfig mMockImsConfig;
    @Mock IImsRegistration mMockImsRegistration;

    @Mock IImsServiceFeatureCallback mConnectionCallback;
    @Mock IBinder mConnectionCallbackBinder;
    @Mock IImsServiceFeatureCallback mConnectionCallback2;
    @Mock IBinder mConnectionCallback2Binder;

    private FeatureConnectionRepository mRepository;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mRepository = new FeatureConnectionRepository();
        when(mConnectionCallbackBinder.isBinderAlive()).thenReturn(true);
        when(mConnectionCallback2Binder.isBinderAlive()).thenReturn(true);
        when(mConnectionCallback.asBinder()).thenReturn(mConnectionCallbackBinder);
        when(mConnectionCallback2.asBinder()).thenReturn(mConnectionCallback2Binder);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testGetInterfaceExists() throws Exception {
        FeatureConnectionRepository.Container fc =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fc);
        FeatureConnectionRepository.Container resultFc =
                mRepository.getIfExists(TEST_PHONE_ID_1, IImsMmTelFeature.class).orElse(null);
        assertNotNull("returned connection should not be null!", resultFc);
        assertEquals("returned connection does not match the set connection",
                fc, resultFc);
    }

    @Test
    @SmallTest
    public void testGetInterfaceDoesntExist() throws Exception {
        FeatureConnectionRepository.Container fc =
                mRepository.getIfExists(TEST_PHONE_ID_1,
                IImsMmTelFeature.class).orElse(null);
        assertNull("returned connection should be null!", fc);
    }

    @Test
    @SmallTest
    public void testGetInterfaceRemoveDoesntExist() throws Exception {
        FeatureConnectionRepository.Container fc =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fc);
        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, null);
        FeatureConnectionRepository.Container resultFc =
                mRepository.getIfExists(TEST_PHONE_ID_1,
                IImsMmTelFeature.class).orElse(null);
        assertNull("returned connection should be null!", resultFc);
    }

    @Test
    @SmallTest
    public void testGetInterfaceUpdateExists() throws Exception {
        FeatureConnectionRepository.Container fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        FeatureConnectionRepository.Container fcB =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fcA);
        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fcB);
        FeatureConnectionRepository.Container resultFc =
                mRepository.getIfExists(TEST_PHONE_ID_1,
                        IImsMmTelFeature.class).orElse(null);
        assertNotNull("returned connection should not be null!", resultFc);
        assertEquals("returned connection does not match the set connection",
                fcB, resultFc);
    }

    @Test
    @SmallTest
    public void testGetMultipleInterfacesExists() throws Exception {
        FeatureConnectionRepository.Container fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        FeatureConnectionRepository.Container fcB =
                getFeatureContainer(mMockRcsFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fcA);
        mRepository.addConnection(TEST_PHONE_ID_1, IImsRcsFeature.class, fcB);
        FeatureConnectionRepository.Container resultFcA =
                mRepository.getIfExists(TEST_PHONE_ID_1,
                        IImsMmTelFeature.class).orElse(null);
        FeatureConnectionRepository.Container resultFcB =
                mRepository.getIfExists(TEST_PHONE_ID_1,
                        IImsRcsFeature.class).orElse(null);
        assertNotNull("returned connection should not be null!", resultFcA);
        assertNotNull("returned connection should not be null!", resultFcB);
        assertEquals("returned connection does not match the set connection",
                fcA, resultFcA);
        assertEquals("returned connection does not match the set connection",
                fcB, resultFcB);
    }

    @Test
    @SmallTest
    public void testListenForUpdate() throws Exception {
        FeatureConnectionRepository.Container fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fcA);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
    }

    @Test
    @SmallTest
    public void testListenNoUpdateForStaleListener() throws Exception {
        FeatureConnectionRepository.Container fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fcA);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());

        when(mConnectionCallbackBinder.isBinderAlive()).thenReturn(false);
        // Listener is "dead", so we should not get this update
        mRepository.notifyFeatureStateChanged(TEST_PHONE_ID_1, IImsMmTelFeature.class,
                ImsFeature.STATE_READY);
        verify(mConnectionCallback, never()).imsStatusChanged(ImsFeature.STATE_READY);
    }

    @Test
    @SmallTest
    public void testListenForUpdateStateChanged() throws Exception {
        FeatureConnectionRepository.Container fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fcA);
        FeatureConnectionRepository.Container resultFc =
                mRepository.getIfExists(TEST_PHONE_ID_1, IImsMmTelFeature.class).orElse(null);
        assertNotNull(resultFc);
        assertEquals(ImsFeature.STATE_UNAVAILABLE, resultFc.getState());
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
        verify(mConnectionCallback, never()).imsStatusChanged(anyInt());

        mRepository.notifyFeatureStateChanged(TEST_PHONE_ID_1, IImsMmTelFeature.class,
                ImsFeature.STATE_READY);
        verify(mConnectionCallback).imsStatusChanged(ImsFeature.STATE_READY);
        assertEquals(ImsFeature.STATE_READY, resultFc.getState());
    }

    @Test
    @SmallTest
    public void testListenForUpdateCapsChanged() throws Exception {
        FeatureConnectionRepository.Container fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);

        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fcA);
        FeatureConnectionRepository.Container resultFc =
                mRepository.getIfExists(TEST_PHONE_ID_1, IImsMmTelFeature.class).orElse(null);
        assertNotNull(resultFc);
        assertEquals(TEST_SERVICE_CAPS, resultFc.getCapabilities());
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());

        mRepository.notifyFeatureCapabilitiesChanged(TEST_PHONE_ID_1, IImsMmTelFeature.class, 0);
        verify(mConnectionCallback).updateCapabilities(0);
        assertEquals(0, resultFc.getCapabilities());
    }


    @Test
    @SmallTest
    public void testRemoveCallback() throws Exception {
        FeatureConnectionRepository.Container fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        FeatureConnectionRepository.Container fcB =
                getFeatureContainer(mMockMmTelFeatureB, TEST_SERVICE_CAPS);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fcA);
        mRepository.unregisterForConnectionUpdates(mConnectionCallback);

        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fcB);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verifyFeatureCreatedCalled(0 /*times*/, mConnectionCallback, fcB);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
    }

    @Test
    @SmallTest
    public void testAddSameCallback() throws Exception {
        FeatureConnectionRepository.Container fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fcA);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
    }

    @Test
    @SmallTest
    public void testListenAfterUpdate() throws Exception {
        FeatureConnectionRepository.Container fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fcA);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
    }

    @Test
    @SmallTest
    public void testListenNoUpdate() throws Exception {
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        verify(mConnectionCallback, never()).imsFeatureCreated(any(), any(), any(), anyInt(),
                anyLong());
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
    }

    @Test
    @SmallTest
    public void testListenNull() throws Exception {
        FeatureConnectionRepository.Container fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fcA);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        mRepository.removeConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback).imsFeatureRemoved(
                FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
    }

    @Test
    @SmallTest
    public void testMultipleListeners() throws Exception {
        FeatureConnectionRepository.Container fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        FeatureConnectionRepository.Container fcB =
                getFeatureContainer(mMockRcsFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fcA);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());

        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, IImsRcsFeature.class,
                mConnectionCallback2, Runnable::run);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
        verify(mConnectionCallback2, never()).imsFeatureCreated(any(), any(), any(), anyInt(),
                anyLong());
        verify(mConnectionCallback2, never()).imsFeatureRemoved(anyInt());

        mRepository.addConnection(TEST_PHONE_ID_1, IImsRcsFeature.class, fcB);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback2, fcB);
        verify(mConnectionCallback2, never()).imsFeatureRemoved(anyInt());
    }

    @Test
    @SmallTest
    public void testMultiplePhones() throws Exception {
        FeatureConnectionRepository.Container fcA =
                getFeatureContainer(mMockMmTelFeatureA, TEST_SERVICE_CAPS);
        FeatureConnectionRepository.Container fcB =
                getFeatureContainer(mMockRcsFeatureA, TEST_SERVICE_CAPS);
        mRepository.addConnection(TEST_PHONE_ID_1, IImsMmTelFeature.class, fcA);
        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());

        mRepository.registerForConnectionUpdates(TEST_PHONE_ID_2, IImsRcsFeature.class,
                mConnectionCallback2, Runnable::run);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
        verify(mConnectionCallback2, never()).imsFeatureCreated(any(), any(), any(), anyInt(),
                anyLong());
        verify(mConnectionCallback2, never()).imsFeatureRemoved(anyInt());

        mRepository.addConnection(TEST_PHONE_ID_2, IImsRcsFeature.class, fcB);
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback, fcA);
        verify(mConnectionCallback, never()).imsFeatureRemoved(anyInt());
        verifyFeatureCreatedCalled(1 /*times*/, mConnectionCallback2, fcB);
        verify(mConnectionCallback2, never()).imsFeatureRemoved(anyInt());
    }

    private void verifyFeatureCreatedCalled(int timesCalled, IImsServiceFeatureCallback cb,
            FeatureConnectionRepository.Container fc) throws Exception {
        verify(cb, times(timesCalled)).imsFeatureCreated(fc.imsFeature, mMockImsConfig,
                mMockImsRegistration, fc.getState(), fc.getCapabilities());
    }

    private FeatureConnectionRepository.Container getFeatureContainer(IBinder feature, long caps) {
        return new FeatureConnectionRepository.Container(feature, mMockImsConfig,
                mMockImsRegistration, caps);
    }
}
