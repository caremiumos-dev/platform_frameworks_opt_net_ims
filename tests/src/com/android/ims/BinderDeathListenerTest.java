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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.IBinder;
import android.os.IInterface;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.concurrent.CountDownLatch;

@RunWith(AndroidJUnit4.class)
public class BinderDeathListenerTest extends ImsTestBase {

    @Mock IInterface mInterface;
    @Mock IBinder mIBinder;

    private BinderDeathListener<IInterface> mInterfaceListener;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        when(mInterface.asBinder()).thenReturn(mIBinder);
        mInterfaceListener = new BinderDeathListener<>(mInterface);
        verify(mIBinder).linkToDeath(mInterfaceListener, 0 /*flags*/);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testGetConnection() {
        assertEquals(mInterface, mInterfaceListener.getConnection());
        mInterfaceListener = new BinderDeathListener<>(null);
        assertNull(mInterfaceListener.getConnection());
    }

    @Test
    @SmallTest
    public void testAddListenerAndDie() throws Exception {
        CountDownLatch l = new CountDownLatch(1);
        assertTrue(mInterfaceListener.addDeathListener(l::countDown));
        mInterfaceListener.binderDied();
        assertEquals(0, l.getCount());
        assertNull(mInterfaceListener.getConnection());
        verify(mIBinder).unlinkToDeath(mInterfaceListener, 0 /*flags*/);
    }

    @Test
    @SmallTest
    public void testAddListenerAlreadyDead() throws Exception {
        mInterfaceListener.binderDied();
        CountDownLatch l = new CountDownLatch(1);
        assertFalse(mInterfaceListener.addDeathListener(l::countDown));
    }
}
