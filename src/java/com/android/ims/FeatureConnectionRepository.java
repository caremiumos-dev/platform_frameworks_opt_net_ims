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
import android.os.IInterface;
import android.os.RemoteException;
import android.telephony.ims.ImsService;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.feature.ImsFeature;
import android.util.ArrayMap;
import android.util.LocalLog;
import android.util.Log;
import android.util.SparseArray;

import com.android.ims.internal.IImsServiceFeatureCallback;
import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Contains an IInterface linking to the appropriate ImsFeature as well as the associated
     * interfaces.
     * @param <T> The ImsFeature Binder Connection.
     */
    public static class Container<T extends IInterface> {

        /**
         * ImsFeature IInterface that is being tracked.
         */
        public final T imsFeature;

        /**
         * IImsConfig interface that should be associated with the ImsFeature.
         */
        public final IImsConfig imsConfig;

        /**
         * IImsRegistration interface that should be associated with this ImsFeature.
         */
        public final IImsRegistration imsRegistration;

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
        public Container(@NonNull T iFace, @NonNull IImsConfig iConfig,
                @NonNull IImsRegistration iReg, long initialCaps) {
            imsFeature = iFace;
            imsConfig = iConfig;
            imsRegistration = iReg;
            mCapabilities = initialCaps;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Container<?> that = (Container<?>) o;
            return imsFeature.equals(that.imsFeature) &&
                    imsConfig.equals(that.imsConfig) &&
                    imsRegistration.equals(that.imsRegistration) &&
                    mCapabilities == that.mCapabilities;
        }

        @Override
        public int hashCode() {
            return Objects.hash(imsFeature, imsConfig, imsRegistration, mCapabilities);
        }

        @Override
        public String toString() {
            return "FeatureContainer{" +
                    "imsFeature=" + imsFeature +
                    ", imsConfig=" + imsConfig +
                    ", imsRegistration=" + imsRegistration +
                    ", capabilities = " + mCapabilities +
                    '}';
        }

        /**
         * Update the capabilties that are associated with the ImsService that this ImsFeature
         * belongs to.
         */
        public void updateCapabilities(@ImsService.ImsServiceCapability long caps) {
            mCapabilities = caps;
        }

        /**
         * @return the capabilties that are associated with the ImsService that this ImsFeature
         * belongs to.
         */
        public @ImsService.ImsServiceCapability long getCapabilities() {
            return mCapabilities;
        }
    }

    /**
     * Internal class representing the listeners that are listening for changes to specific
     * ImsFeature instances.
     * @param <T> The ImsFeature instance.
     */
    private static class ListenerContainer<T extends IInterface> {
        public final int phoneId;
        private final IImsServiceFeatureCallback mCallback;
        private final Executor mExecutor;


        public ListenerContainer(int p, @NonNull IImsServiceFeatureCallback c,
                @NonNull Executor e) {
            phoneId = p;
            mCallback = c;
            mExecutor = e;
        }

        public void notifyCallback(Container<T> connection) {
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
                mExecutor.execute(() -> {
                    try {
                        mCallback.imsFeatureCreated(connection.imsFeature.asBinder(),
                                connection.imsConfig, connection.imsRegistration,
                                connection.getCapabilities());
                    } catch (RemoteException e) {
                        // This listener will eventually be caught and removed during stale checks.
                    }
                });
            }
        }

        public void notifyStateChanged(int status) {
            mExecutor.execute(() -> {
                try {
                    mCallback.imsStatusChanged(status);
                } catch (RemoteException e) {
                    // This listener will eventually be caught and removed during stale checks.
                }
            });
        }

        public void notifyUpdateCapabilties(Container<T> container) {
            mExecutor.execute(() -> {
                try {
                    mCallback.updateCapabilities(container.getCapabilities());
                } catch (RemoteException e) {
                    // This listener will eventually be caught and removed during stale checks.
                }
            });
        }

        public boolean isStale() {
            return mCallback.asBinder().isBinderAlive();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListenerContainer<?> that = (ListenerContainer<?>) o;
            // Do not count executor for equality.
            return phoneId == that.phoneId &&
                    mCallback.equals(that.mCallback);
        }

        @Override
        public int hashCode() {
            // Do not use executor for hash.
            return Objects.hash(phoneId, mCallback);
        }

        @Override
        public String toString() {
            return "ListenerContainer{" + "pId=" + phoneId + ", cb=" + mCallback + '}';
        }
    }

    private final Object mLock;
    @GuardedBy(value="mLock")
    // Array of phoneIds containing a map of FeatureContainer types to the respective
    // FeatureContainer holding the information.
    private final SparseArray<Map<Class<?>,
            Container<? extends IInterface>>> mConnectionsByPhoneId;
    // A map of FeatureContainer types to the respective list of listeners listening for changes to
    // that type.
    @GuardedBy(value="mLock")
    private final Map<Class<?>, List<ListenerContainer<? extends IInterface>>> mListeners;
    @GuardedBy(value="mLock")
    private final LocalLog mLocalLog = new LocalLog(50 /*lines*/);

    public FeatureConnectionRepository() {
        mLock = new Object();
        mConnectionsByPhoneId = new SparseArray<>();
        mListeners = new HashMap<>();
        logInfoLineLocked(-1, "FeatureConnectionRepository - created");
    }


    /**
     * Get the requested connection now if it exists. This method is blocking and may block if
     * waiting for access to the requested interface.
     * @param phoneId The phone ID that the connection is related to.
     * @param type The {@link Class<T>} type that the callback will be registered for.
     * @param <T> The Interface Type.
     * @return
     */
    public <T extends IInterface> Optional<Container<T>> getIfExists(
            int phoneId, @NonNull Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("this method does not accept null arguments");
        }
        synchronized (mLock) {
            Container<T> conn = filterForType(getConnectionMapLocked(phoneId), type);
            return Optional.ofNullable(conn);
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
        ListenerContainer<T> container = new ListenerContainer<>(phoneId, callback, executor);
        Container<T> conn;
        synchronized (mLock) {
            List<ListenerContainer<? extends IInterface>> oldContainers
                    = getListenerListLocked(type);
            if (!oldContainers.contains(container)) {
                logInfoLineLocked(phoneId, "registerForConnectionUpdates - added cb: "
                        + container);
                oldContainers.add(container);
            } else {
                logVerboseLineLocked(phoneId, "registerForConnectionUpdates - cb exists: "
                        + container);
            }
            conn = filterForType(getConnectionMapLocked(phoneId), type);
            logInfoLineLocked(phoneId, "registerForConnectionUpdates - conn  " + conn);
        }

        // Only notify in the case that the Connection is already available.
        if (conn != null) container.notifyCallback(conn);
    }

    /**
     * Unregister for updates to a previously registered callback.
     *
     * @param callback The callback to unregister.
     * @param <T> The Interface Type.
     */
    public <T extends IInterface> void unregisterForConnectionUpdates(
            @NonNull IImsServiceFeatureCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("this method does not accept null arguments");
        }
        synchronized (mLock) {
            List<ListenerContainer<? extends IInterface>> containersToRemove;
            for (List<ListenerContainer<? extends IInterface>> containers : mListeners.values()) {
                containersToRemove = containers.stream().filter(c -> c.mCallback == callback)
                        .collect(Collectors.toList());
                containersToRemove.forEach(c -> logInfoLineLocked(c.phoneId,
                        "unregisterForConnectionUpdates - cb removed: " + c));
                containers.removeAll(containersToRemove);

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
            @Nullable Container<T> newConnection) {
        if (type == null) {
            throw new IllegalArgumentException("The type must be non-null");
        }
        List<ListenerContainer<T>> callbacks = null;
        synchronized (mLock) {
            Map<Class<?>, Container<? extends IInterface>> connMap =
                    getConnectionMapLocked(phoneId);
            Container<T> oldConnection = filterForType(connMap, type);
            if (!Objects.equals(oldConnection, newConnection)) {
                logInfoLineLocked(phoneId, "updateConnection - updated: "  + oldConnection + "-> "
                        + newConnection);
                connMap.put(type, newConnection);
                callbacks = filterListenersLocked(phoneId, type);
                if (callbacks != null) callbacks.forEach(c -> logVerboseLineLocked(phoneId,
                        "updateConnection  - updating callback: " + c));
            } else {
                logInfoLineLocked(phoneId, "updateConnection - update discarded for"
                        + "" + newConnection);
            }
        }
        // Callback will also be null if there was no update to the connections.
        if (callbacks != null) callbacks.forEach(c -> c.notifyCallback(newConnection));
    }

    /**
     * Remove the connection associated with a specific connection type.
     * @param phoneId The phone ID associated with this connection.
     * @param type The class of the connection.
     * @param <T> The connection class type.
     */
    public <T extends IInterface> Container<T> removeConnection(int phoneId,
            @NonNull Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("The type must be non-null");
        }
        List<ListenerContainer<T>> callbacks = null;
       Container<T> oldConnection = null;
        synchronized (mLock) {
            Map<Class<?>, Container<? extends IInterface>> connMap =
                    getConnectionMapLocked(phoneId);
            oldConnection = filterForType(connMap, type);
            logInfoLineLocked(phoneId, "updateConnection - updated: "  + oldConnection +
                    "-> null");
            connMap.remove(type);
            callbacks = filterListenersLocked(phoneId, type);
            if (callbacks != null) callbacks.forEach(c -> logVerboseLineLocked(phoneId,
                    "updateConnection  - updating callback: " + c));
        }
        // Callback will also be null if there was no update to the connections.
        if (callbacks != null) callbacks.forEach(c -> c.notifyCallback(null));
        return oldConnection;
    }

    /**
     * Prints the dump of log events that have occurred on this repository.
     */
    public void dump(PrintWriter printWriter) {
        mLocalLog.dump(printWriter);
    }

    /**
     * Notify listeners that the state of a specific ImsFeature that this repository is tracking has
     * changed.
     * @param phoneId
     * @param type
     * @param status
     * @param <T>
     */
    public <T extends IInterface> void notifyFeatureStateChanged(int phoneId,
            @NonNull Class<T> type, @ImsFeature.ImsState int state) {
        List<ListenerContainer<T>> containers;
        synchronized (mLock) {
            containers = filterListenersLocked(phoneId, type);
        }
        containers.forEach((c) -> c.notifyStateChanged(state));
    }

    public <T extends IInterface> void notifyFeatureCapabilitiesChanged(int phoneId,
            @NonNull Class<T> type, long capabilities) {
        Container<T> feature;
        List<ListenerContainer<T>> containers;
        synchronized (mLock) {
            feature = filterForType(getConnectionMapLocked(phoneId), type);
            if (feature != null) feature.updateCapabilities(capabilities);
            containers = filterListenersLocked(phoneId, type);
        }
        containers.forEach((c) -> c.notifyUpdateCapabilties(feature));
    }

    // Should be locked by mLock before accessing
    private <T extends IInterface> List<ListenerContainer<T>> filterListenersLocked(
            int phoneId, Class<T> type) {
        List<ListenerContainer<? extends IInterface>> containers = getListenerListLocked(type);
        List<ListenerContainer<T>> filteredContainers = new ArrayList<>();
        List<ListenerContainer<T>> staleContainers = new ArrayList<>();
        for (ListenerContainer<? extends IInterface> c : containers) {
            if (c.phoneId == phoneId) {
                filteredContainers.add((ListenerContainer<T>) c);
            }
            if (c.isStale()) {
                staleContainers.add((ListenerContainer<T>) c);
            }
        }
        //Remove now stale/dead listeners.
        containers.removeAll(staleContainers);
        return filteredContainers;
    }

    // Should be locked by mLock before accessing
    private Map<Class<?>, Container<? extends IInterface>> getConnectionMapLocked(
            int phoneId) {
        Map<Class<?>, Container<? extends IInterface>> connectionMap =
                mConnectionsByPhoneId.get(phoneId);
        if (connectionMap == null) {
            connectionMap = new ArrayMap<>();
            mConnectionsByPhoneId.append(phoneId, connectionMap);
        }
        return connectionMap;
    }

    // Should be locked by mLock before accessing
    private List<ListenerContainer<? extends IInterface>> getListenerListLocked(Class<?> c) {
        return mListeners.computeIfAbsent(c, k -> new ArrayList<>());
    }

    private <T extends IInterface, U> Container<T> filterForType(
            Map<Class<?>, U> map, Class<T> type) {
        U value = null;
        for (Class<?> t : map.keySet()) {
            if (t == type) {
                value = map.get(t);
                break;
            }
        }
        if (value == null) return null;
        return (Container<T>) value;
    }

    private void logVerboseLineLocked(int phoneId, String log) {
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
