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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.IBinder;
import android.os.IInterface;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsMmTelFeature;
import android.telephony.ims.aidl.IImsRcsFeature;
import android.telephony.ims.aidl.IImsRegistration;

import androidx.test.filters.SmallTest;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class ImsServiceConnectionRepositoryTest extends ImsTestBase {

    private static final int TEST_SUB_ID_1 = 1;
    private static final int TEST_SUB_ID_2 = 2;

    @Mock IImsMmTelFeature mMockMmTelFeatureA;
    @Mock IImsMmTelFeature mMockMmTelFeatureB;
    @Mock IImsRcsFeature mMockRcsFeatureA;
    @Mock IImsConfig mMockImsConfig;
    @Mock IImsRegistration mMockImsRegistration;

    @Mock ImsServiceConnectionRepository.Callback<IImsMmTelFeature> mConnectionCallback;
    @Mock ImsServiceConnectionRepository.Callback<IImsRcsFeature> mConnectionCallback2;

    private ImsServiceConnectionRepository mRepository;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mRepository = new ImsServiceConnectionRepository();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testGetInterfaceExists() throws Exception {
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> fc =
                getFeatureContainer(mMockMmTelFeatureA);
        mRepository.updateConnection(TEST_SUB_ID_1, IImsMmTelFeature.class, fc);
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> resultFc =
                mRepository.getIfExists(TEST_SUB_ID_1, IImsMmTelFeature.class).orElse(null);
        assertNotNull("returned connection should not be null!", resultFc);
        assertEquals("returned connection does not match the set connection",
                fc, resultFc);
    }

    @Test
    @SmallTest
    public void testGetInterfaceDoesntExist() throws Exception {
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> fc =
                mRepository.getIfExists(TEST_SUB_ID_1,
                IImsMmTelFeature.class).orElse(null);
        assertNull("returned connection should be null!", fc);
    }

    @Test
    @SmallTest
    public void testGetInterfaceRemoveDoesntExist() throws Exception {
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> fc =
                getFeatureContainer(mMockMmTelFeatureA);
        mRepository.updateConnection(TEST_SUB_ID_1, IImsMmTelFeature.class, fc);
        mRepository.updateConnection(TEST_SUB_ID_1, IImsMmTelFeature.class, null);
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> resultFc =
                mRepository.getIfExists(TEST_SUB_ID_1,
                IImsMmTelFeature.class).orElse(null);
        assertNull("returned connection should be null!", resultFc);
    }

    @Test
    @SmallTest
    public void testGetInterfaceUpdateExists() throws Exception {
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> fcA =
                getFeatureContainer(mMockMmTelFeatureA);
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> fcB =
                getFeatureContainer(mMockMmTelFeatureA);
        mRepository.updateConnection(TEST_SUB_ID_1, IImsMmTelFeature.class, fcA);
        mRepository.updateConnection(TEST_SUB_ID_1, IImsMmTelFeature.class, fcB);
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> resultFc =
                mRepository.getIfExists(TEST_SUB_ID_1,
                        IImsMmTelFeature.class).orElse(null);
        assertNotNull("returned connection should not be null!", resultFc);
        assertEquals("returned connection does not match the set connection",
                fcB, resultFc);
    }

    @Test
    @SmallTest
    public void testGetMultipleInterfacesExists() throws Exception {
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> fcA =
                getFeatureContainer(mMockMmTelFeatureA);
        ImsServiceConnectionRepository.FeatureContainer<IImsRcsFeature> fcB =
                getFeatureContainer(mMockRcsFeatureA);
        mRepository.updateConnection(TEST_SUB_ID_1, IImsMmTelFeature.class, fcA);
        mRepository.updateConnection(TEST_SUB_ID_1, IImsRcsFeature.class, fcB);
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> resultFcA =
                mRepository.getIfExists(TEST_SUB_ID_1,
                        IImsMmTelFeature.class).orElse(null);
        ImsServiceConnectionRepository.FeatureContainer<IImsRcsFeature> resultFcB =
                mRepository.getIfExists(TEST_SUB_ID_1,
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
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> fcA =
                getFeatureContainer(mMockMmTelFeatureA);
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        verify(mConnectionCallback, never()).onConnectionAvailable(fcA);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
        mRepository.updateConnection(TEST_SUB_ID_1, IImsMmTelFeature.class, fcA);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(fcA);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
    }

    @Test
    @SmallTest
    public void testRemoveCallback() throws Exception {
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> fcA =
                getFeatureContainer(mMockMmTelFeatureA);
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> fcB =
                getFeatureContainer(mMockMmTelFeatureB);
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        mRepository.updateConnection(TEST_SUB_ID_1, IImsMmTelFeature.class, fcA);
        mRepository.unregisterForConnectionUpdates(mConnectionCallback);

        mRepository.updateConnection(TEST_SUB_ID_1, IImsMmTelFeature.class, fcB);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(fcA);
        verify(mConnectionCallback, never()).onConnectionAvailable(fcB);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
    }

    @Test
    @SmallTest
    public void testAddSameCallback() throws Exception {
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> fcA =
                getFeatureContainer(mMockMmTelFeatureA);
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        mRepository.updateConnection(TEST_SUB_ID_1, IImsMmTelFeature.class, fcA);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(fcA);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
    }

    @Test
    @SmallTest
    public void testListenAfterUpdate() throws Exception {
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> fcA =
                getFeatureContainer(mMockMmTelFeatureA);
        mRepository.updateConnection(TEST_SUB_ID_1, IImsMmTelFeature.class, fcA);
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(fcA);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
    }

    @Test
    @SmallTest
    public void testListenNoUpdate() throws Exception {
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        verify(mConnectionCallback, never()).onConnectionAvailable(any());
        verify(mConnectionCallback, never()).onConnectionUnavailable();
    }

    @Test
    @SmallTest
    public void testListenNull() throws Exception {
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> fcA =
                getFeatureContainer(mMockMmTelFeatureA);
        mRepository.updateConnection(TEST_SUB_ID_1, IImsMmTelFeature.class, fcA);
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        mRepository.updateConnection(TEST_SUB_ID_1, IImsMmTelFeature.class, null);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(fcA);
        verify(mConnectionCallback, times(1)).onConnectionUnavailable();
    }

    @Test
    @SmallTest
    public void testMultipleListeners() throws Exception {
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> fcA =
                getFeatureContainer(mMockMmTelFeatureA);
        ImsServiceConnectionRepository.FeatureContainer<IImsRcsFeature> fcB =
                getFeatureContainer(mMockRcsFeatureA);
        mRepository.updateConnection(TEST_SUB_ID_1, IImsMmTelFeature.class, fcA);
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(fcA);
        verify(mConnectionCallback, never()).onConnectionUnavailable();

        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, IImsRcsFeature.class,
                mConnectionCallback2, Runnable::run);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(fcA);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
        verify(mConnectionCallback2, never()).onConnectionAvailable(any());
        verify(mConnectionCallback2, never()).onConnectionUnavailable();

        mRepository.updateConnection(TEST_SUB_ID_1, IImsRcsFeature.class, fcB);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(fcA);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
        verify(mConnectionCallback2, times(1)).onConnectionAvailable(fcB);
        verify(mConnectionCallback2, never()).onConnectionUnavailable();
    }

    @Test
    @SmallTest
    public void testMultiplePhones() throws Exception {
        ImsServiceConnectionRepository.FeatureContainer<IImsMmTelFeature> fcA =
                getFeatureContainer(mMockMmTelFeatureA);
        ImsServiceConnectionRepository.FeatureContainer<IImsRcsFeature> fcB =
                getFeatureContainer(mMockRcsFeatureA);
        mRepository.updateConnection(TEST_SUB_ID_1, IImsMmTelFeature.class, fcA);
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, IImsMmTelFeature.class,
                mConnectionCallback, Runnable::run);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(fcA);
        verify(mConnectionCallback, never()).onConnectionUnavailable();

        mRepository.registerForConnectionUpdates(TEST_SUB_ID_2, IImsRcsFeature.class,
                mConnectionCallback2, Runnable::run);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(fcA);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
        verify(mConnectionCallback2, never()).onConnectionAvailable(any());
        verify(mConnectionCallback2, never()).onConnectionUnavailable();

        mRepository.updateConnection(TEST_SUB_ID_2, IImsRcsFeature.class, fcB);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(fcA);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
        verify(mConnectionCallback2, times(1)).onConnectionAvailable(fcB);
        verify(mConnectionCallback2, never()).onConnectionUnavailable();
    }

    private <T extends IInterface> ImsServiceConnectionRepository.FeatureContainer<T>
    getFeatureContainer(T feature) {
        return new ImsServiceConnectionRepository.FeatureContainer<>(feature, mMockImsConfig,
                mMockImsRegistration);
    }
}
