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

import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.NoSuchElementException;

/**
 * Keeps track of the connection to a Binder and lets listeners know if it has died.
 */
public class BinderDeathListener<T extends IInterface> implements IBinder.DeathRecipient {
    private final Object mLock = new Object();
    private final T mConnection;
    private final ArrayList<Runnable> mDeathListeners = new ArrayList<>();
    private boolean isDead = false;

    public BinderDeathListener(T connection) {
        synchronized(mLock) {
            mConnection = connection;
            if (mConnection == null) {
                binderDied();
                return;
            }
            try {
                connection.asBinder().linkToDeath(this, 0 /*flags*/);
            } catch (RemoteException e) {
                binderDied();
            }
        }
    }

    @Override
    public void binderDied() {
        synchronized (mLock) {
            isDead = true;
            mDeathListeners.forEach(Runnable::run);
            mDeathListeners.clear();
            if (mConnection != null) {
                try {
                    mConnection.asBinder().unlinkToDeath(this, 0 /*flags*/);
                } catch (NoSuchElementException e) {
                    // No need to worry about this, this means the death recipient was never linked.
                }
            }
        }
    }

    /**
     * Adds a Runnable to be run when this Binder dies.
     * Note: There should be no assumptions here as to which Thread this Runnable is called on.
     * @return true if the runnable was added, false if the connection to telephony is already
     * dead and needs to be
     */
    public boolean addDeathListener(Runnable r) {
        synchronized (mLock) {
            if (isDead) return false;
            return mDeathListeners.add(r);
        }
    }

    public T getConnection() {
        synchronized (mLock) {
            if (isDead) return null;
            return mConnection;
        }
    }
}
