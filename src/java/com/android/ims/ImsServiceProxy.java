/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.app.PendingIntent;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.ims.feature.ImsFeature;
import android.util.Log;

import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.IImsCallSessionListener;
import com.android.ims.internal.IImsConfig;
import com.android.ims.internal.IImsEcbm;
import com.android.ims.internal.IImsMultiEndpoint;
import com.android.ims.internal.IImsRegistrationListener;
import com.android.ims.internal.IImsServiceController;
import com.android.ims.internal.IImsServiceFeatureListener;
import com.android.ims.internal.IImsUt;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A container of the IImsServiceController binder, which implements all of the ImsFeatures that
 * the platform currently supports: MMTel and RCS.
 * @hide
 */

public class ImsServiceProxy {

    protected String LOG_TAG = "ImsServiceProxy";

    protected final int mSlotId;
    protected IBinder mBinder;
    private final int mSupportedFeature;

    // Start by assuming the proxy is available for usage.
    private boolean mIsAvailable = true;
    // ImsFeature Status from the ImsService. Cached.
    private Integer mFeatureStatusCached = null;
    private Set<StatusCallback> mStatusCallbacks = new HashSet<>();
    private final Object mLock = new Object();

    /**
     * Any IMS API that implements ImsService callbacks should extend this class to manage
     * local callbacks that are registered in this process as well as manage when a binder
     * connection to the ImsService should be established (via {@link #createCallbackBinder()}) and
     * when it is not available any longer and should be cleaned up
     * (via {@link #onBinderNotAvailable()}).
     *
     * @param <T> The callback that this class will be managing.
     */
    public abstract static class CallbackManager<T> {
        private final Object mSyncObject = new Object();
        private Set<T> mLocalCallbacks;
        private boolean mIsBinderConnected;

        public CallbackManager() {
            mLocalCallbacks = new HashSet<>();
        }

        /**
         * Adds callback to the list of local callbacks. If the ImsService Binder connection is
         * severed, this list will be cleared and will have to be repopulated when the ImsService
         * comes back up.
         * @param callback Adds this callback to the list of local callbacks that will be notified
         * when {@link #notifyCallback(Consumer)} is called.
         *
         * @throws ImsException if the underlying callback Binder failed to be created. This will
         * happen if the connection to the ImsService does not exist.
         */
        public final void addCallback(T callback) throws ImsException {
            if (callback == null) {
                return;
            }
            maybeCreateBinderConnection();

            synchronized (mSyncObject) {
                if (!mIsBinderConnected) {
                    throw new ImsException("Unable to create Callback Binder.",
                            ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN);
                }
                mLocalCallbacks.add(callback);
            }
        }

        /**
         * Called by the ImsManager when the Binder has become unavailable.
         */
        public final void notifyBinderNotAvailable(){
            synchronized (mSyncObject) {
                mIsBinderConnected = false;
                mLocalCallbacks.clear();
            }
            onBinderNotAvailable();
        }

        /**
         * Call with action to notify all callbacks that are currently registered.
         */
        public final void notifyCallback(Consumer<T> action) {
            synchronized (mSyncObject) {
                mLocalCallbacks.forEach(action);
            }
        }

        // Create a binder connection if it doesn't already exist.
        private void maybeCreateBinderConnection() {
            synchronized (mSyncObject) {
                // Binder is already connected, no need to create a new one.
                if (mIsBinderConnected) {
                    return;
                }
            }
            boolean connectionResult = createCallbackBinder();
            synchronized (mSyncObject) {
                mIsBinderConnected = connectionResult;
            }
        }

        /**
         * Called when the Binder connection to the ImsService should be created.
         * @return true if the connection was created, false otherwise.
         */
        protected abstract boolean createCallbackBinder();

        /**
         * Called when the ImsManager has received a callback that the binder is no longer
         * available. At this point, all callbacks have been cleared and the Binder connection
         * needs to be cleaned up.
         */
        protected abstract void onBinderNotAvailable();
    }

    /**
     * Implement to receive notifications when the ImsFeature status has changed.
     */
    public static class StatusCallback {
        /**
         * The Feature's state (Defined in {@link ImsFeature}) has changed. Query
         * {@link ImsFeature#getFeatureState()} to get the new state.
         */
        public void onFeatureStateChanged() {
            // Default Implementation
        }

        /**
         * The feature has become unavailable due to the service crashing or switching to another
         * ImsService.
         */
        public void onFeatureUnavailable() {
            // Default Implementation
        }
    }

    private final IImsServiceFeatureListener mListenerBinder =
            new IImsServiceFeatureListener.Stub() {

        @Override
        public void imsFeatureCreated(int slotId, int feature) throws RemoteException {
            // The feature has been re-enabled. This may happen when the service crashes.
            synchronized (mLock) {
                if (!mIsAvailable && mSlotId == slotId && feature == mSupportedFeature) {
                    Log.i(LOG_TAG, "Feature enabled on slotId: " + slotId + " for feature: " +
                            feature);
                    mIsAvailable = true;
                }
            }
        }

        @Override
        public void imsFeatureRemoved(int slotId, int feature) throws RemoteException {
            synchronized (mLock) {
                if (mIsAvailable && mSlotId == slotId && feature == mSupportedFeature) {
                    Log.i(LOG_TAG, "Feature disabled on slotId: " + slotId + " for feature: " +
                            feature);
                    mIsAvailable = false;
                    if (!mStatusCallbacks.isEmpty()) {
                        mStatusCallbacks.forEach(StatusCallback::onFeatureUnavailable);
                    }
                }
            }
        }

        @Override
        public void imsStatusChanged(int slotId, int feature, int status) throws RemoteException {
            synchronized (mLock) {
                Log.i(LOG_TAG, "imsStatusChanged: slot: " + slotId + " feature: " + feature +
                        " status: " + status);
                if (mSlotId == slotId && feature == mSupportedFeature) {
                    mFeatureStatusCached = status;
                    if (!mStatusCallbacks.isEmpty()) {
                        mStatusCallbacks.forEach(StatusCallback::onFeatureStateChanged);
                    }
                }
            }
        }
    };

    public ImsServiceProxy(int slotId, IBinder binder, int featureType) {
        mSlotId = slotId;
        mBinder = binder;
        mSupportedFeature = featureType;
    }

    public ImsServiceProxy(int slotId, int featureType) {
        this(slotId, null, featureType);
    }

    public IImsServiceFeatureListener getListener() {
        return mListenerBinder;
    }

    public void setBinder(IBinder binder) {
        mBinder = binder;
    }

    public int startSession(PendingIntent incomingCallIntent, IImsRegistrationListener listener)
            throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).startSession(mSlotId, mSupportedFeature,
                    incomingCallIntent, listener);
        }
    }

    public void endSession(int sessionId) throws RemoteException {
        synchronized (mLock) {
            // Only check to make sure the binder connection still exists. This method should
            // still be able to be called when the state is STATE_NOT_AVAILABLE.
            checkBinderConnection();
            getServiceInterface(mBinder).endSession(mSlotId, mSupportedFeature, sessionId);
        }
    }

    public boolean isConnected(int callServiceType, int callType)
            throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).isConnected(mSlotId, mSupportedFeature,
                    callServiceType, callType);
        }
    }

    public boolean isOpened() throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).isOpened(mSlotId, mSupportedFeature);
        }
    }

    public void addRegistrationListener(IImsRegistrationListener listener)
    throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            getServiceInterface(mBinder).addRegistrationListener(mSlotId, mSupportedFeature,
                    listener);
        }
    }

    public void removeRegistrationListener(IImsRegistrationListener listener)
            throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            getServiceInterface(mBinder).removeRegistrationListener(mSlotId, mSupportedFeature,
                    listener);
        }
    }

    public ImsCallProfile createCallProfile(int sessionId, int callServiceType, int callType)
            throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).createCallProfile(mSlotId, mSupportedFeature,
                    sessionId, callServiceType, callType);
        }
    }

    public IImsCallSession createCallSession(int sessionId, ImsCallProfile profile,
            IImsCallSessionListener listener) throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).createCallSession(mSlotId, mSupportedFeature,
                    sessionId, profile, listener);
        }
    }

    public IImsCallSession getPendingCallSession(int sessionId, String callId)
            throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).getPendingCallSession(mSlotId, mSupportedFeature,
                    sessionId, callId);
        }
    }

    public IImsUt getUtInterface() throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).getUtInterface(mSlotId, mSupportedFeature);
        }
    }

    public IImsConfig getConfigInterface() throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).getConfigInterface(mSlotId, mSupportedFeature);
        }
    }

    public void turnOnIms() throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            getServiceInterface(mBinder).turnOnIms(mSlotId, mSupportedFeature);
        }
    }

    public void turnOffIms() throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            getServiceInterface(mBinder).turnOffIms(mSlotId, mSupportedFeature);
        }
    }

    public IImsEcbm getEcbmInterface() throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).getEcbmInterface(mSlotId, mSupportedFeature);
        }
    }

    public void setUiTTYMode(int uiTtyMode, Message onComplete)
            throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            getServiceInterface(mBinder).setUiTTYMode(mSlotId, mSupportedFeature, uiTtyMode,
                    onComplete);
        }
    }

    public IImsMultiEndpoint getMultiEndpointInterface() throws RemoteException {
        synchronized (mLock) {
            checkServiceIsReady();
            return getServiceInterface(mBinder).getMultiEndpointInterface(mSlotId,
                    mSupportedFeature);
        }
    }

    /**
     * @return the current feature status, defined in {@link ImsFeature.ImsState}.
     */
    public int getFeatureStatus() {
        synchronized (mLock) {
            if (isBinderAlive() && mFeatureStatusCached != null) {
                Log.i(LOG_TAG, "getFeatureStatus - returning cached: " + mFeatureStatusCached);
                return mFeatureStatusCached;
            }
        }
        // Don't synchronize on Binder call.
        Integer status = retrieveFeatureStatus();
        synchronized (mLock) {
            if (status == null) {
                return ImsFeature.STATE_NOT_AVAILABLE;
            }
            // Cache only non-null value for feature status.
            mFeatureStatusCached = status;
        }
        Log.i(LOG_TAG, "getFeatureStatus - returning " + status);
        return status;
    }

    /**
     * Internal method used to retrieve the feature status from the corresponding ImsService.
     */
    private Integer retrieveFeatureStatus() {
        if (mBinder != null) {
            try {
                return getServiceInterface(mBinder).getFeatureStatus(mSlotId, mSupportedFeature);
            } catch (RemoteException e) {
                // Status check failed, don't update cache
            }
        }
        return null;
    }

    /**
     * @param c Callback that will fire when the feature status has changed.
     */
    public void addStatusCallback(StatusCallback c) {
        mStatusCallbacks.add(c);
    }

    /**
     * @return Returns true if the ImsService is ready to take commands, false otherwise. If this
     * method returns false, it doesn't mean that the Binder connection is not available (use
     * {@link #isBinderReady()} to check that), but that the ImsService is not accepting commands
     * at this time.
     *
     * For example, for DSDS devices, only one slot can be {@link ImsFeature#STATE_READY} to take
     * commands at a time, so the other slot must stay at {@link ImsFeature#STATE_NOT_AVAILABLE}.
     */
    private boolean isBinderReady() {
        return isBinderAlive() && getFeatureStatus() == ImsFeature.STATE_READY;
    }

    /**
     * @return false if the binder connection is no longer alive.
     */
    public boolean isBinderAlive() {
        synchronized (mLock) {
            return mIsAvailable && mBinder != null && mBinder.isBinderAlive();
        }
    }

    private void checkServiceIsReady() throws RemoteException {
        if (!isBinderReady()) {
            throw new RemoteException("ImsServiceProxy is not ready to accept commands.");
        }
    }

    private IImsServiceController getServiceInterface(IBinder b) {
        return IImsServiceController.Stub.asInterface(b);
    }

    protected void checkBinderConnection() throws RemoteException {
        if (!isBinderAlive()) {
            throw new RemoteException("ImsServiceProxy is not available for that feature.");
        }
    }
}
