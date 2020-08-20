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

    private static class TestConnection implements IInterface {
        @Override
        public IBinder asBinder() {
            return null;
        }
    }
    private static class TestConnection2 implements IInterface {
        @Override
        public IBinder asBinder() {
            return null;
        }
    }
    @Mock ImsServiceConnectionRepository.Callback<TestConnection> mConnectionCallback;
    @Mock ImsServiceConnectionRepository.Callback<TestConnection2> mConnectionCallback2;

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
        TestConnection connection = new TestConnection();
        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection.class, connection);
        TestConnection resultConnection = mRepository.getIfExists(TEST_SUB_ID_1,
                TestConnection.class).orElse(null);
        assertNotNull("returned connection should not be null!", resultConnection);
        assertEquals("returned connection does not match the set connection",
                connection, resultConnection);
    }

    @Test
    @SmallTest
    public void testGetInterfaceDoesntExist() throws Exception {
        TestConnection resultConnection = mRepository.getIfExists(TEST_SUB_ID_1,
                TestConnection.class).orElse(null);
        assertNull("returned connection should be null!", resultConnection);
    }

    @Test
    @SmallTest
    public void testGetInterfaceRemoveDoesntExist() throws Exception {
        TestConnection connection = new TestConnection();
        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection.class, connection);
        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection.class, null);
        TestConnection resultConnection = mRepository.getIfExists(TEST_SUB_ID_1,
                TestConnection.class).orElse(null);
        assertNull("returned connection should be null!", resultConnection);
    }

    @Test
    @SmallTest
    public void testGetInterfaceUpdateExists() throws Exception {
        TestConnection connectionA = new TestConnection();
        TestConnection connectionB = new TestConnection();
        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection.class, connectionA);
        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection.class, connectionB);
        TestConnection resultConnection = mRepository.getIfExists(TEST_SUB_ID_1,
                TestConnection.class).orElse(null);
        assertNotNull("returned connection should not be null!", resultConnection);
        assertEquals("returned connection does not match the set connection",
                connectionB, resultConnection);
    }

    @Test
    @SmallTest
    public void testGetMultipleInterfacesExists() throws Exception {
        TestConnection connectionA = new TestConnection();
        TestConnection2 connectionB = new TestConnection2();
        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection.class, connectionA);
        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection2.class, connectionB);
        TestConnection resultConnectionA = mRepository.getIfExists(TEST_SUB_ID_1,
                TestConnection.class).orElse(null);
        TestConnection2 resultConnectionB = mRepository.getIfExists(TEST_SUB_ID_1,
                TestConnection2.class).orElse(null);
        assertNotNull("returned connection should not be null!", resultConnectionA);
        assertNotNull("returned connection should not be null!", resultConnectionB);
        assertEquals("returned connection does not match the set connection",
                connectionA, resultConnectionA);
        assertEquals("returned connection does not match the set connection",
                connectionB, resultConnectionB);
    }

    @Test
    @SmallTest
    public void testListenForUpdate() throws Exception {
        TestConnection connection = new TestConnection();
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, TestConnection.class,
                mConnectionCallback, Runnable::run);
        verify(mConnectionCallback, never()).onConnectionAvailable(connection);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection.class, connection);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(connection);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
    }

    @Test
    @SmallTest
    public void testRemoveCallback() throws Exception {
        TestConnection connection = new TestConnection();
        TestConnection connection2 = new TestConnection();
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, TestConnection.class,
                mConnectionCallback, Runnable::run);
        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection.class, connection);
        mRepository.unregisterForConnectionUpdates(mConnectionCallback);

        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection.class, connection2);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(connection);
        verify(mConnectionCallback, never()).onConnectionAvailable(connection2);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
    }

    @Test
    @SmallTest
    public void testAddSameCallback() throws Exception {
        TestConnection connection = new TestConnection();
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, TestConnection.class,
                mConnectionCallback, Runnable::run);
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, TestConnection.class,
                mConnectionCallback, Runnable::run);
        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection.class, connection);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(connection);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
    }

    @Test
    @SmallTest
    public void testListenAfterUpdate() throws Exception {
        TestConnection connection = new TestConnection();
        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection.class, connection);
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, TestConnection.class,
                mConnectionCallback, Runnable::run);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(connection);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
    }

    @Test
    @SmallTest
    public void testListenNoUpdate() throws Exception {
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, TestConnection.class,
                mConnectionCallback, Runnable::run);
        verify(mConnectionCallback, never()).onConnectionAvailable(any());
        verify(mConnectionCallback, never()).onConnectionUnavailable();
    }

    @Test
    @SmallTest
    public void testListenNull() throws Exception {
        TestConnection connection = new TestConnection();
        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection.class, connection);
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, TestConnection.class,
                mConnectionCallback, Runnable::run);
        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection.class, null);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(connection);
        verify(mConnectionCallback, times(1)).onConnectionUnavailable();
    }

    @Test
    @SmallTest
    public void testMultipleListeners() throws Exception {
        TestConnection connection = new TestConnection();
        TestConnection2 connection2 = new TestConnection2();
        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection.class, connection);
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, TestConnection.class,
                mConnectionCallback, Runnable::run);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(connection);
        verify(mConnectionCallback, never()).onConnectionUnavailable();

        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, TestConnection2.class,
                mConnectionCallback2, Runnable::run);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(connection);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
        verify(mConnectionCallback2, never()).onConnectionAvailable(any());
        verify(mConnectionCallback2, never()).onConnectionUnavailable();

        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection2.class, connection2);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(connection);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
        verify(mConnectionCallback2, times(1)).onConnectionAvailable(connection2);
        verify(mConnectionCallback2, never()).onConnectionUnavailable();
    }

    @Test
    @SmallTest
    public void testMultiplePhones() throws Exception {
        TestConnection connection = new TestConnection();
        TestConnection2 connection2 = new TestConnection2();
        mRepository.updateConnection(TEST_SUB_ID_1, TestConnection.class, connection);
        mRepository.registerForConnectionUpdates(TEST_SUB_ID_1, TestConnection.class,
                mConnectionCallback, Runnable::run);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(connection);
        verify(mConnectionCallback, never()).onConnectionUnavailable();

        mRepository.registerForConnectionUpdates(TEST_SUB_ID_2, TestConnection2.class,
                mConnectionCallback2, Runnable::run);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(connection);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
        verify(mConnectionCallback2, never()).onConnectionAvailable(any());
        verify(mConnectionCallback2, never()).onConnectionUnavailable();

        mRepository.updateConnection(TEST_SUB_ID_2, TestConnection2.class, connection2);
        verify(mConnectionCallback, times(1)).onConnectionAvailable(connection);
        verify(mConnectionCallback, never()).onConnectionUnavailable();
        verify(mConnectionCallback2, times(1)).onConnectionAvailable(connection2);
        verify(mConnectionCallback2, never()).onConnectionUnavailable();
    }
}
