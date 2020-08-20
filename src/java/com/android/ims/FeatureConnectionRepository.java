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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.telephony.ims.ImsService;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.feature.ImsFeature;
import android.util.LocalLog;
import android.util.Log;

import com.android.ims.internal.IImsServiceFeatureCallback;
import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * A repository of ImsFeature connections made available by an ImsService once it has been
 * successfully bound.
 *
 * Provides the ability for listeners to register callbacks and the repository notify registered
 * listeners when a connection has been created/removed for a specific connection type.
 */
public class FeatureConnectionRepository {

    private static final String TAG = "FeatureConnRepo";

    /**
     * Contains an IBinder linking to the appropriate ImsFeature as well as the associated
     * interfaces.
     */
    public static class Container {

        /**
         * ImsFeature that is being tracked.
         */
        public final IBinder imsFeature;

        /**
         * IImsConfig interface that should be associated with the ImsFeature.
         */
        public final IImsConfig imsConfig;

        /**
         * IImsRegistration interface that should be associated with this ImsFeature.
         */
        public final IImsRegistration imsRegistration;

        /**
         * State of the feature that is being tracked.
         */
        private @ImsFeature.ImsState int mState = ImsFeature.STATE_UNAVAILABLE;

        /**
         * Capabilities of this ImsService.
         */
        private @ImsService.ImsServiceCapability long mCapabilities;
        /**
         * Contains the ImsFeature IInterface as well as the ImsService interfaces associated with
         * that feature.
         * @param iFace IInterface connection to the ImsFeature.
         * @param iConfig IImsConfig interface associated with the ImsFeature.
         * @param iReg IImsRegistration interface associated with the ImsFeature
         * @param initialCaps The initial capabilities that the ImsService supports.
         */
        public Container(@NonNull IBinder iFace, @NonNull IImsConfig iConfig,
                @NonNull IImsRegistration iReg, long initialCaps) {
            imsFeature = iFace;
            imsConfig = iConfig;
            imsRegistration = iReg;
            mCapabilities = initialCaps;
        }

        /**
         * @return the capabilties that are associated with the ImsService that this ImsFeature
         * belongs to.
         */
        public @ImsService.ImsServiceCapability long getCapabilities() {
            return mCapabilities;
        }

        /**
         * Update the capabilities that are associated with the ImsService that this ImsFeature
         * belongs to.
         */
        private void updateCapabilities(@ImsService.ImsServiceCapability long caps) {
            mCapabilities = caps;
        }

        /**
         * @return The state of the ImsFeature.
         */
        public @ImsFeature.ImsState int getState() {
            return mState;
        }

        /**
         * Update the capabilities that are associated with the ImsService that this ImsFeature
         * belongs to.
         */
        private void updateState(@ImsFeature.ImsState int state) {
            mState = state;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Container that = (Container) o;
            return imsFeature.equals(that.imsFeature) &&
                    imsConfig.equals(that.imsConfig) &&
                    imsRegistration.equals(that.imsRegistration) &&
                    mState == that.mState &&
                    mCapabilities == that.mCapabilities;
        }

        @Override
        public int hashCode() {
            return Objects.hash(imsFeature, imsConfig, imsRegistration, mState, mCapabilities);
        }

        @Override
        public String toString() {
            return "FeatureContainer{" +
                    "imsFeature=" + imsFeature +
                    ", imsConfig=" + imsConfig +
                    ", imsRegistration=" + imsRegistration +
                    ", state=" + ImsFeature.STATE_LOG_MAP.get(mState) +
                    ", capabilities = " + mCapabilities +
                    '}';
        }
    }

    /**
     * Internal class representing a listener that is listening for changes to specific
     * ImsFeature instances.
     */
    private static class ListenerContainer {
        private final IImsServiceFeatureCallback mCallback;
        private final Executor mExecutor;

        public ListenerContainer(@NonNull IImsServiceFeatureCallback c, @NonNull Executor e) {
            mCallback = c;
            mExecutor = e;
        }

        public void notifyCallback(Container connection) {
            if (connection == null) {
                mExecutor.execute(() -> {
                    try {
                        mCallback.imsFeatureRemoved(
                                FeatureConnector.UNAVAILABLE_REASON_DISCONNECTED);
                    } catch (RemoteException e) {
                        // This listener will eventually be caught and removed during stale checks.
                    }
                });
            }
            else {
                // cache in case these parameters change while in executor queue
                final int state = connection.getState();
                final long capabilities = connection.getCapabilities();
                mExecutor.execute(() -> {
                    try {
                        mCallback.imsFeatureCreated(connection.imsFeature,
                                connection.imsConfig, connection.imsRegistration,
                                state, capabilities);
                    } catch (RemoteException e) {
                        // This listener will eventually be caught and removed during stale checks.
                    }
                });
            }
        }

        public void notifyStateChanged(int state) {
            mExecutor.execute(() -> {
                try {
                    mCallback.imsStatusChanged(state);
                } catch (RemoteException e) {
                    // This listener will eventually be caught and removed during stale checks.
                }
            });
        }

        public void notifyUpdateCapabilties(long caps) {
            mExecutor.execute(() -> {
                try {
                    mCallback.updateCapabilities(caps);
                } catch (RemoteException e) {
                    // This listener will eventually be caught and removed during stale checks.
                }
            });
        }

        public boolean isStale() {
            return !mCallback.asBinder().isBinderAlive();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListenerContainer that = (ListenerContainer) o;
            // Do not count executor for equality.
            return mCallback.equals(that.mCallback);
        }

        @Override
        public int hashCode() {
            // Do not use executor for hash.
            return Objects.hash(mCallback);
        }

        @Override
        public String toString() {
            return "ListenerContainer{" + "cb=" + mCallback + '}';
        }
    }

    private static class UpdateMapper {
        public final int phoneId;
        public final Class<?> type;
        private final List<ListenerContainer> mListeners = new ArrayList<>();
        private Container mFeatureContainer;

        public UpdateMapper(int pId, Class<?> t) {
            phoneId = pId;
            type = t;
        }

        public void setFeatureContainer(Container c) {
            if (Objects.equals(c, mFeatureContainer)) return;
            mFeatureContainer = c;
            mListeners.forEach(l -> l.notifyCallback(mFeatureContainer));
        }

        public Container getFeatureContainer() {
            return mFeatureContainer;
        }

        public boolean addListener(ListenerContainer c) {
            removeStaleListeners();
            if (mListeners.contains(c)) return false;

            if (mFeatureContainer != null) {
                // Do not call back until the feature container has been set.
                c.notifyCallback(mFeatureContainer);
            }
            return mListeners.add(c);
        }

        public void removeListener(IImsServiceFeatureCallback callback) {
            removeStaleListeners();
            List<ListenerContainer> oldListeners = mListeners.stream()
                    .filter((c) -> Objects.equals(c.mCallback, callback))
                    .collect(Collectors.toList());
            mListeners.removeAll(oldListeners);
        }

        public void notifyStateUpdated(int newState) {
            removeStaleListeners();
            if (mFeatureContainer != null) {
                if (mFeatureContainer.getState() != newState) {
                    mFeatureContainer.updateState(newState);
                    mListeners.forEach(l -> l.notifyStateChanged(newState));
                }
            }
        }

        public void notifyUpdateCapabilities(long caps) {
            removeStaleListeners();
            if (mFeatureContainer != null) {
                if (mFeatureContainer.getCapabilities() != caps) {
                    mFeatureContainer.updateCapabilities(caps);
                    mListeners.forEach(l -> l.notifyUpdateCapabilties(caps));
                }
            }
        }

        private void removeStaleListeners() {
            List<ListenerContainer> staleListeners = mListeners.stream().filter(
                    ListenerContainer::isStale)
                    .collect(Collectors.toList());
            mListeners.removeAll(staleListeners);
        }
    }

    private final Object mLock = new Object();
    @GuardedBy(value="mLock")
    private final List<UpdateMapper> mFeatures = new ArrayList<>();
    @GuardedBy(value="mLock")
    private final LocalLog mLocalLog = new LocalLog(50 /*lines*/);

    public FeatureConnectionRepository() {
        logInfoLineLocked(-1, "FeatureConnectionRepository - created");
    }

    /**
     * Get the requested connection now if it exists. This method is blocking and may block if
     * waiting for access to the requested interface.
     * @param phoneId The phone ID that the connection is related to.
     * @param type The {@link Class<T>} type that the callback will be registered for.
     * @param <T> The Interface Type.
     * @return The Container containing the requested ImsFeature if it exists.
     */
    public <T extends IInterface> Optional<Container> getIfExists(
            int phoneId, @NonNull Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("this method does not accept null arguments");
        }
        synchronized (mLock) {
            UpdateMapper m = getUpdateMapper(phoneId, type);
            return Optional.ofNullable(m.getFeatureContainer());
        }
    }

    /**
     * Register a callback that will receive updates when the requested Connection becomes available
     * or unavailable for the specified phone ID.
     * @param phoneId The phone ID that the connection will be related to.
     * @param type The {@link Class<T>} type that the callback will be registered for.
     * @param callback The callback that will be used to notify when the callback is
     *                 available/unavailable.
     * @param executor The executor that the callback will be run on.
     * @param <T> The Interface Type.
     */
    public <T extends IInterface> void registerForConnectionUpdates(int phoneId,
            @NonNull Class<T> type, @NonNull IImsServiceFeatureCallback callback,
            @NonNull Executor executor) {
        if (type == null || callback == null || executor == null) {
            throw new IllegalArgumentException("this method does not accept null arguments");
        }
        ListenerContainer container = new ListenerContainer(callback, executor);
        synchronized (mLock) {
            UpdateMapper m = getUpdateMapper(phoneId, type);
            boolean result = m.addListener(container);
            logVerboseLineLocked(phoneId, "registerForConnectionUpdates - add result: " + result);
            logInfoLineLocked(phoneId, "registerForConnectionUpdates - conn  " + container);
        }
    }

    /**
     * Unregister for updates to a previously registered callback.
     *
     * @param callback The callback to unregister.
     */
    public void unregisterForConnectionUpdates(@NonNull IImsServiceFeatureCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("this method does not accept null arguments");
        }
        synchronized (mLock) {
            for (UpdateMapper m : mFeatures) {
                m.removeListener(callback);
            }
        }
    }

    /**
     * Add a connection associated with a specific connection type. If one already exists, it will
     * be replaced.
     * @param phoneId The phone ID associated with this connection.
     * @param type The class of the connection.
     * @param newConnection The new connection, either an {@link IInterface} or {@code null} if
     *                      the connection is no longer available.
     * @param <T> The connection class type.
     */
    public <T extends IInterface> void addConnection(int phoneId, @NonNull Class<T> type,
            @Nullable Container newConnection) {
        if (type == null) {
            throw new IllegalArgumentException("The type must be non-null");
        }
        synchronized (mLock) {
            UpdateMapper m = getUpdateMapper(phoneId, type);
            m.setFeatureContainer(newConnection);
        }
    }

    /**
     * Remove the connection associated with a specific connection type.
     * @param phoneId The phone ID associated with this connection.
     * @param type The class of the connection.
     * @param <T> The connection class type.
     */
    public <T extends IInterface> Container removeConnection(int phoneId, @NonNull Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("The type must be non-null");
        }
        Container oldConnection;
        synchronized (mLock) {
            UpdateMapper m = getUpdateMapper(phoneId, type);
            oldConnection = m.getFeatureContainer();
            m.setFeatureContainer(null);
        }
        return oldConnection;
    }

    /**
     * Prints the dump of log events that have occurred on this repository.
     */
    public void dump(PrintWriter printWriter) {
        mLocalLog.dump(printWriter);
    }

    /**
     * Notify listeners that the state of a specific ImsFeature that this repository is
     * tracking have changed.
     */
    public <T extends IInterface> void notifyFeatureStateChanged(int phoneId,
            @NonNull Class<T> type, @ImsFeature.ImsState int state) {
        synchronized (mLock) {
            UpdateMapper m = getUpdateMapper(phoneId, type);
            m.notifyStateUpdated(state);
        }
    }

    /**
     * Notify listeners that the capabilities of a specific ImsFeature that this repository is
     * tracking have changed.
     */
    public <T extends IInterface> void notifyFeatureCapabilitiesChanged(int phoneId,
            @NonNull Class<T> type, @ImsService.ImsServiceCapability long capabilities) {
        synchronized (mLock) {
            UpdateMapper m = getUpdateMapper(phoneId, type);
            m.notifyUpdateCapabilities(capabilities);
        }
    }

    // Should be locked by mLock before accessing
    private  <T extends IInterface> UpdateMapper getUpdateMapper(int phoneId, Class<T> type) {
        UpdateMapper mapper = mFeatures.stream()
                .filter((c) -> ((c.phoneId == phoneId) && (c.type == type)))
                .findFirst().orElse(null);
        if (mapper == null) {
            mapper = new UpdateMapper(phoneId, type);
            mFeatures.add(mapper);
        }
        return mapper;
    }

    private void logVerboseLineLocked(int phoneId, String log) {
        if (!Log.isLoggable(TAG, Log.VERBOSE)) return;
        final String phoneIdPrefix = "[" + phoneId + "] ";
        Log.v(TAG, phoneIdPrefix + log);
        mLocalLog.log(phoneIdPrefix + log);
    }

    private void logInfoLineLocked(int phoneId, String log) {
        final String phoneIdPrefix = "[" + phoneId + "] ";
        Log.i(TAG, phoneIdPrefix + log);
        mLocalLog.log(phoneIdPrefix + log);
    }
}
