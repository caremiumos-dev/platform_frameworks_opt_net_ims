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
import android.os.IInterface;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.feature.ImsFeature;

import com.android.ims.internal.IImsServiceFeatureCallback;
import com.android.internal.annotations.VisibleForTesting;
import com.android.telephony.Rlog;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;

/**
 * Helper class for managing a connection to the ImsFeature manager.
 */
public class FeatureConnector<T extends IInterface, U extends IFeatureConnector<T>> {
    private static final String TAG = "FeatureConnector";
    private static final boolean DBG = false;

    /**
     * This Connection has become unavailable due to the ImsService being disconnected due to
     * an event such as SIM Swap, carrier configuration change, etc...
     *
     * {@link #connectionReady} will be called when a new Manager is available.
     */
    public static final int UNAVAILABLE_REASON_DISCONNECTED = 0;

    /**
     * IMS is not supported on this device. This should be considered a permanent error and
     * a Manager will never become available.
     */
    public static final int UNAVAILABLE_REASON_IMS_UNSUPPORTED = 1;

    /**
     * The server of this information has crashed or otherwise generated an error that will require
     * a retry to connect. This is rare, however in this case, {@link #disconnect()} and
     * {@link #connect()} will need to be called again to recreate the connection with the server.
     * <p>
     * Only applicable if this is used outside of the server's own process.
     */
    public static final int UNAVAILABLE_REASON_SERVER_UNAVAILABLE = 2;

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "UNAVAILABLE_REASON_", value = {
            UNAVAILABLE_REASON_DISCONNECTED,
            UNAVAILABLE_REASON_IMS_UNSUPPORTED,
            UNAVAILABLE_REASON_SERVER_UNAVAILABLE
    })
    public @interface UnavailableReason {}

    public interface ManagerFactory<T extends IInterface, U extends IFeatureConnector<T>> {
        /**
         * Create a manager instance, which will connect to the FeatureConnection.
         */
        U createManager(Context context, int phoneId);
    }

    public interface Listener<U extends IFeatureConnector<?>> {
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
                long initialCapabilities) {
            synchronized (mLock) {
                if (mDisconnectedReason == null) {
                    // Already connected, ignore
                    return;
                }
                mManager.associate(f, c, r);
                mManager.updateFeatureCapabilities(initialCapabilities);
                mDisconnectedReason = null;
            }
        }

        @Override
        public void imsFeatureRemoved(@UnavailableReason int reason) {
            synchronized (mLock) {
                // only generate new events if the disconnect event isn't the same as before.
                if (mDisconnectedReason != null && mDisconnectedReason.equals(reason)) {
                    return;
                }
                mManager.invalidate();
                mDisconnectedReason = reason;
                mExecutor.execute(() -> mListener.connectionUnavailable(reason));
            }
        }

        @Override
        public void imsStatusChanged(int status) {
            synchronized (mLock) {
                if (mDisconnectedReason != null) {
                    return;
                }
                mManager.updateFeatureState(status);
                mExecutor.execute(() -> {
                    try {
                        switch (status) {
                            case ImsFeature.STATE_READY: {
                                notifyReady();
                                break;
                            }
                            case ImsFeature.STATE_INITIALIZING:
                                // fall through
                            case ImsFeature.STATE_UNAVAILABLE: {
                                notifyNotReady();
                                break;
                            }
                            default: {
                                logw("Unexpected status: " + status);
                            }
                        }
                    } catch (ImsException e) {
                        if (e.getCode() == ImsReasonInfo.CODE_LOCAL_IMS_NOT_SUPPORTED_ON_DEVICE) {
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
                    return;
                }
                mManager.updateFeatureCapabilities(capabilities);
            }
        }
    };

    private final int mPhoneId;
    private final Context mContext;
    private final ManagerFactory<T, U> mFactory;
    private final Executor mExecutor;
    private final Object mLock = new Object();
    private final String mLogPrefix;
    private Integer mDisconnectedReason = null;

    private U mManager;

    @VisibleForTesting
    public Listener<U> mListener;

    @VisibleForTesting
    public FeatureConnector(Context context, int phoneId, ManagerFactory<T, U> factory,
            Listener<U> listener, Executor executor, String logPrefix) {
        mContext = context;
        mPhoneId = phoneId;
        mFactory = factory;
        mListener = listener;
        mExecutor = executor;
        mLogPrefix = logPrefix;
    }

    /**
     * Start the creation of a connection to the underlying ImsService implementation. When the
     * service is connected, {@link FeatureConnector.Listener#connectionReady(Object)} will be
     * called with an active instance.
     *
     * If this device does not support an ImsStack (i.e. doesn't support
     * {@link PackageManager#FEATURE_TELEPHONY_IMS} feature), this method will do nothing.
     */
    public void connect() {
        if (DBG) log("connect");
        if (!isSupported()) {
            logw("connect: not supported.");
            return;
        }
        createManager();
    }

    // Check if this ImsFeature is supported or not.
    private boolean isSupported() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS);
    }

    /**
     * Disconnect from the ImsService Implementation and clean up. When this is complete,
     * {@link FeatureConnector.Listener#connectionUnavailable()} will be called one last time.
     */
    public void disconnect() {
        if (DBG) log("disconnect");

        mManager.unregisterFeatureListener(mPhoneId, mCallback);
        notifyNotReady();
    }

    private U createManager() {
        U manager = mFactory.createManager(mContext, mPhoneId);
        manager.registerFeatureListener(mPhoneId, mCallback);
        return manager;
    }

    private void notifyReady() throws ImsException {
        U manager;
        synchronized (mLock) {
            manager = mManager;
        }
        try {
            if (DBG) log("notifyReady");
            mListener.connectionReady(manager);
        }
        catch (ImsException e) {
            if(DBG) log("notifyReady exception: " + e.getMessage());
            throw e;
        }
    }

    private void notifyNotReady() {
        if (DBG) log("notifyNotReady");
        mListener.connectionUnavailable(UNAVAILABLE_REASON_DISCONNECTED);
    }

    private final void log(String message) {
        Rlog.d(TAG, "[" + mLogPrefix + ", " + mPhoneId + "] " + message);
    }

    private final void logw(String message) {
        Rlog.w(TAG, "[" + mLogPrefix + ", " + mPhoneId + "] " + message);
    }
}
