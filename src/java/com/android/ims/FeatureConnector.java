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

import android.annotation.IntDef;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.feature.ImsFeature;

import com.android.ims.internal.IImsServiceFeatureCallback;
import com.android.internal.annotations.VisibleForTesting;
import com.android.telephony.Rlog;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Helper class for managing a connection to the ImsFeature manager.
 */
public class FeatureConnector<U extends IFeatureConnector> {
    private static final String TAG = "FeatureConnector";
    private static final boolean DBG = false;

    /**
     * This Connection has become unavailable due to the ImsService being disconnected due to
     * an event such as SIM Swap, carrier configuration change, etc...
     *
     * {@link Listener#connectionReady}  will be called when a new Manager is available.
     */
    public static final int UNAVAILABLE_REASON_DISCONNECTED = 0;

    /**
     * This Connection has become unavailable due to the ImsService moving to the NOT_READY state.
     *
     * {@link Listener#connectionReady}  will be called when the manager moves back to ready.
     */
    public static final int UNAVAILABLE_REASON_NOT_READY = 1;

    /**
     * IMS is not supported on this device. This should be considered a permanent error and
     * a Manager will never become available.
     */
    public static final int UNAVAILABLE_REASON_IMS_UNSUPPORTED = 2;

    /**
     * The server of this information has crashed or otherwise generated an error that will require
     * a retry to connect. This is rare, however in this case, {@link #disconnect()} and
     * {@link #connect()} will need to be called again to recreate the connection with the server.
     * <p>
     * Only applicable if this is used outside of the server's own process.
     */
    public static final int UNAVAILABLE_REASON_SERVER_UNAVAILABLE = 3;

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "UNAVAILABLE_REASON_", value = {
            UNAVAILABLE_REASON_DISCONNECTED,
            UNAVAILABLE_REASON_NOT_READY,
            UNAVAILABLE_REASON_IMS_UNSUPPORTED,
            UNAVAILABLE_REASON_SERVER_UNAVAILABLE
    })
    public @interface UnavailableReason {}

    public interface ManagerFactory<U extends IFeatureConnector> {
        /**
         * Create a manager instance, which will connect to the FeatureConnection.
         */
        U createManager(Context context, int phoneId);
    }

    public interface Listener<U extends IFeatureConnector> {
        /**
         * ImsFeature manager is connected to the underlying IMS implementation.
         */
        void connectionReady(U manager) throws ImsException;

        /**
         * The underlying IMS implementation is unavailable and can not be used to communicate.
         */
        void connectionUnavailable(@UnavailableReason int reason);
    }

    private final IImsServiceFeatureCallback mCallback = new IImsServiceFeatureCallback.Stub() {

        @Override
        public void imsFeatureCreated(IBinder f, IImsConfig c, IImsRegistration r,
                int currentState, long initialCapabilities) {
            log("imsFeatureCreated: f=" + f + ", c=" + c + ", r=" + r + ", state=" +
                    ImsFeature.STATE_LOG_MAP.get(currentState) + ", caps ="
                    + initialCapabilities);
            synchronized (mLock) {
                mManager.associate(f, c, r);
                mManager.updateFeatureCapabilities(initialCapabilities);
                mDisconnectedReason = null;
                imsStatusChanged(currentState);
            }
        }

        @Override
        public void imsFeatureRemoved(@UnavailableReason int reason) {
            log("imsFeatureRemoved: reason=" + reason);
            synchronized (mLock) {
                // only generate new events if the disconnect event isn't the same as before.
                if (mDisconnectedReason != null && mDisconnectedReason.equals(reason)) {
                    log("imsFeatureRemoved: ignore");
                    return;
                }
                mManager.invalidate();
                mDisconnectedReason = reason;
                mExecutor.execute(() -> mListener.connectionUnavailable(reason));
            }
        }

        @Override
        public void imsStatusChanged(int status) {
            log("imsStatusChanged: status=" + status);
            synchronized (mLock) {
                if (mDisconnectedReason != null) {
                    log("imsStatusChanged: ignore");
                    return;
                }
                mManager.updateFeatureState(status);
                final U manager = mManager;
                final boolean isReady = mReadyFilter.contains(status);
                boolean didReadyChange = isReady ^ mLastReadyState;
                mLastReadyState = isReady;
                if (!didReadyChange) {
                    log("imsStatusChanged: ready didn't change, ignore");
                    return;
                }
                mExecutor.execute(() -> {
                    try {
                        if (isReady) {
                            notifyReady(manager);
                        } else {
                            notifyNotReady();
                        }
                    } catch (ImsException e) {
                        if (e.getCode()
                                == ImsReasonInfo.CODE_LOCAL_IMS_NOT_SUPPORTED_ON_DEVICE) {
                            mListener.connectionUnavailable(UNAVAILABLE_REASON_IMS_UNSUPPORTED);
                        } else {
                            notifyNotReady();
                        }
                    }
                });
            }
        }

        @Override
        public void updateCapabilities(long capabilities) {
            synchronized (mLock) {
                if (mDisconnectedReason != null) {
                    log("updateCapabilities: ignore");
                    return;
                }
                mManager.updateFeatureCapabilities(capabilities);
            }
        }
    };

    private final int mPhoneId;
    private final Context mContext;
    private final ManagerFactory<U> mFactory;
    private final Listener<U> mListener;
    private final Executor mExecutor;
    private final Object mLock = new Object();
    private final String mLogPrefix;
    private final List<Integer> mReadyFilter = new ArrayList<>();

    private U mManager;
    // Start in disconnected state;
    private Integer mDisconnectedReason = UNAVAILABLE_REASON_DISCONNECTED;
    // Record state to cut down on logging
    private boolean mIsOneShot = false;
    // Stop redundant connectionAvailable if the ready filter contains multiple states.
    // Also, do not send the first unavailable until after we have moved to available once.
    private boolean mLastReadyState = false;



    @VisibleForTesting
    public FeatureConnector(Context context, int phoneId, ManagerFactory<U> factory,
            String logPrefix, List<Integer> readyFilter, Listener<U> listener, Executor executor) {
        mContext = context;
        mPhoneId = phoneId;
        mFactory = factory;
        mLogPrefix = logPrefix;
        mReadyFilter.addAll(readyFilter);
        mListener = listener;
        mExecutor = executor;
    }

    /**
     * Start the creation of a connection to the underlying ImsService implementation. When the
     * service is connected, {@link FeatureConnector.Listener#connectionReady} will be
     * called with an active instance.
     *
     * If this device does not support an ImsStack (i.e. doesn't support
     * {@link PackageManager#FEATURE_TELEPHONY_IMS} feature), this method will do nothing.
     */
    public void connect() {
        if (DBG) log("connect");
        if (!isSupported()) {
            mExecutor.execute(() -> mListener.connectionUnavailable(
                    UNAVAILABLE_REASON_IMS_UNSUPPORTED));
            logw("connect: not supported.");
            return;
        }
        synchronized (mLock) {
            mIsOneShot = false;
            if (mManager == null) {
                mManager = mFactory.createManager(mContext, mPhoneId);
            }
            mManager.registerFeatureListener(mPhoneId, mCallback, false /*oneShot*/);
        }
    }

    public void connectForOneShot() {
        if (DBG) log("connectForOneShot");
        if (!isSupported()) {
            mExecutor.execute(() -> mListener.connectionUnavailable(
                    UNAVAILABLE_REASON_IMS_UNSUPPORTED));
            logw("connectForOneShot: not supported.");
            return;
        }
        synchronized (mLock) {
            mIsOneShot = true;
            if (mManager == null) {
                mManager = mFactory.createManager(mContext, mPhoneId);
            }
            mManager.registerFeatureListener(mPhoneId, mCallback, true /*oneShot*/);
        }
    }

    // Check if this ImsFeature is supported or not.
    private boolean isSupported() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS);
    }

    /**
     * Disconnect from the ImsService Implementation and clean up. When this is complete,
     * {@link FeatureConnector.Listener#connectionUnavailable(int)} will be called one last time.
     */
    public void disconnect() {
        if (DBG) log("disconnect");
        synchronized (mLock) {
            mManager.unregisterFeatureListener(mCallback);
            try {
                mCallback.imsFeatureRemoved(UNAVAILABLE_REASON_DISCONNECTED);
            } catch (RemoteException ignore) {} // local call
        }
    }

    // Should be called on executor
    private void notifyReady(U manager) throws ImsException {
        try {
            if (DBG) log("notifyReady");
            mListener.connectionReady(manager);
        }
        catch (ImsException e) {
            if(DBG) log("notifyReady exception: " + e.getMessage());
            throw e;
        }
    }

    // Should be called on executor.
    private void notifyNotReady() {
        if (DBG) log("notifyNotReady");
        mListener.connectionUnavailable(UNAVAILABLE_REASON_NOT_READY);
    }

    private final void log(String message) {
        // cut down on log spam
        synchronized (mLock) {
            if (mIsOneShot) return;
        }
        Rlog.d(TAG, "[" + mLogPrefix + ", " + mPhoneId + "] " + message);
    }

    private final void logw(String message) {
        Rlog.w(TAG, "[" + mLogPrefix + ", " + mPhoneId + "] " + message);
    }
}
