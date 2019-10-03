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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telephony.ims.feature.ImsFeature;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.Executor;

public abstract class FeatureConnector extends Handler {
    private static final String TAG = "FeatureConnector";

    // Initial condition for ims connection retry.
    private static final int IMS_RETRY_STARTING_TIMEOUT_MS = 500; // ms

    // Ceiling bitshift amount for service query timeout, calculated as:
    // 2^mImsServiceRetryCount * IMS_RETRY_STARTING_TIMEOUT_MS, where
    // mImsServiceRetryCount ∊ [0, CEILING_SERVICE_RETRY_COUNT].
    private static final int CEILING_SERVICE_RETRY_COUNT = 6;

    public interface Listener {
        /**
         * ImsManager is connected to the underlying IMS implementation.
         */
        void connectionReady(ImsManager manager) throws ImsException;

        /**
         * RcsFeatureManager is connected to the underlying IMS implementation.
         */
        void connectionReady(RcsFeatureManager manager) throws ImsException;

        /**
         * The underlying IMS implementation is unavailable and can not be used to communicate.
         */
        void connectionUnavailable();
    }

    public interface RetryTimeout {
        int get();
    }

    protected final int mPhoneId;
    protected final Context mContext;
    protected final Listener mListener;
    protected final Executor mExecutor;
    protected final Object mLock = new Object();
    protected final String mLogPrefix;

    protected int mRetryCount = 0;

    @VisibleForTesting
    public RetryTimeout mRetryTimeout = () -> {
        synchronized (mLock) {
            int timeout = (1 << mRetryCount) * IMS_RETRY_STARTING_TIMEOUT_MS;
            if (mRetryCount <= CEILING_SERVICE_RETRY_COUNT) {
                mRetryCount++;
            }
            return timeout;
        }
    };

    public FeatureConnector(Context context, int phoneId, Listener listener) {
        mContext = context;
        mPhoneId = phoneId;
        mListener = listener;
        mExecutor = new HandlerExecutor(this);
        mLogPrefix = "?";
    }

    public FeatureConnector(Context context, int phoneId, Listener listener, String logPrefix) {
        mContext = context;
        mPhoneId = phoneId;
        mListener = listener;
        mExecutor = new HandlerExecutor(this);
        mLogPrefix = logPrefix;
    }

    @VisibleForTesting
    public FeatureConnector(Context context, int phoneId, Listener listener, Executor executor,
            String logPrefix) {
        mContext = context;
        mPhoneId = phoneId;
        mListener= listener;
        mExecutor = executor;
        mLogPrefix = logPrefix;
    }

    @VisibleForTesting
    public FeatureConnector(Context context, int phoneId, Listener listener, Executor executor,
            Looper looper) {
        super(looper);
        mContext = context;
        mPhoneId = phoneId;
        mListener= listener;
        mExecutor = executor;
        mLogPrefix = "?";
    }

    /**
     * Start the creation of a connection to the underlying ImsService implementation. When the
     * service is connected, {@link Listener#connectionReady(ImsManager)} will be called with
     * an active ImsManager instance or {@link Listener#connectionReady(RcsFeatureManager)} will
     * be called with an active RcsFeatureManager instance.
     *
     * If this device does not support an ImsStack (i.e. doesn't support
     * {@link PackageManager#FEATURE_TELEPHONY_IMS} feature), this method will do nothing.
     */
    public void connect() {
        if (!isSupported()) {
            Log.i(TAG, "connect: not support!");
            return;
        }
        mRetryCount = 0;
        // Send a message to connect to the Ims Service and open a connection through
        // getImsService().
        post(mGetServiceRunnable);
    }

    /**
     * Disconnect from the ImsService Implementation and clean up. When this is complete,
     * {@link ImsManager.Connector.Listener#connectionUnavailable()} will be called one last time.
     */
    public void disconnect() {
        removeCallbacks(mGetServiceRunnable);
    }

    /**
     * Check if this ImsFeature is supported or not. 
     */
    protected abstract boolean isSupported();

    @VisibleForTesting
    public final Runnable mGetServiceRunnable = () -> {
        try {
            getImsService();
        } catch (ImsException e) {
            retryGetImsService();
        }
    };

    public abstract void getImsService() throws ImsException;
    public abstract void retryGetImsService();

    // Callback fires when IMS Feature changes state
    protected FeatureConnection.IFeatureUpdate mNotifyStatusChangedCallback =
            new FeatureConnection.IFeatureUpdate() {
                @Override
                public void notifyStateChanged() {
                    mExecutor.execute(() -> {
                        try {
                            int status = ImsFeature.STATE_UNAVAILABLE;
                            synchronized (mLock) {
                                status = getImsFeatureStatus();
                            }
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
                                    Log.w(TAG, "Unexpected State!");
                                }
                            }
                        } catch (ImsException e) {
                            // Could not get the ImsService, retry!
                            notifyNotReady();
                            retryGetImsService();
                        }
                    });
                }

                @Override
                public void notifyUnavailable() {
                    mExecutor.execute(() -> {
                        notifyNotReady();
                        retryGetImsService();
                    });
                }
            };

    public abstract int getImsFeatureStatus() throws ImsException;
    public abstract void notifyReady() throws ImsException;

    protected void notifyNotReady() {
        mListener.connectionUnavailable();
    }
    
    protected String getLogMessage(String message) {
        return "Connector-[" + mLogPrefix + "] " + message;
    }
}
