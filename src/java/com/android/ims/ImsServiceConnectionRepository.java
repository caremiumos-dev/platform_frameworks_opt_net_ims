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
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsRegistration;
import android.util.ArrayMap;
import android.util.LocalLog;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

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
 * A singleton intra-process repository of ImsFeature connections  made available by an ImsService
 * once it has been successfully bound.
 *
 * Provides the ability for listeners to register callbacks and the repository notify registered
 * listeners when a connection has been created/removed for a specific connection type.
 */
public class ImsServiceConnectionRepository {

    private static final String TAG = "ImsServiceConnRepo";

    /**
     * Callback interface to be used by registrants to listen to when a connection is available or
     * unavailable
     * @param <T> The Connection being listened to.
     */
    public interface Callback<T extends IInterface> {
        /**
         * A new connection has become available for the requested type. Discard any previous
         * connections that were available.
         * @param connection The connection that has become available.
         */
        void onConnectionAvailable(FeatureContainer<T> connection);

        /**
         * The previously available connection has become unavailable for usage. The
         * connection should be discarded.
         */
        void onConnectionUnavailable();
    }

    public static class FeatureContainer<T extends IInterface> {
        public final T imsFeature;
        public final IImsConfig imsConfig;
        public final IImsRegistration imsRegistration;

        public FeatureContainer(@NonNull T iFace, @NonNull IImsConfig iConfig,
                @NonNull IImsRegistration iReg) {
            imsFeature = iFace;
            imsConfig = iConfig;
            imsRegistration = iReg;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FeatureContainer<?> that = (FeatureContainer<?>) o;
            return imsFeature.equals(that.imsFeature) &&
                    imsConfig.equals(that.imsConfig) &&
                    imsRegistration.equals(that.imsRegistration);
        }

        @Override
        public int hashCode() {
            return Objects.hash(imsFeature, imsConfig, imsRegistration);
        }

        @Override
        public String toString() {
            return "FeatureContainer{" +
                    "imsFeature=" + imsFeature +
                    ", imsConfig=" + imsConfig +
                    ", imsRegistration=" + imsRegistration +
                    '}';
        }
    }

    private static class ListenerContainer<T extends IInterface> {
        public final int phoneId;
        private final Callback<T> mCallback;
        private final Executor mExecutor;


        public ListenerContainer(int p, @NonNull Callback<T> c, @NonNull Executor e) {
            phoneId = p;
            mCallback = c;
            mExecutor = e;
        }

        public void notifyCallback(FeatureContainer<T> connection) {
            if (connection == null) {
                mExecutor.execute(mCallback::onConnectionUnavailable);
            }
            else {
                mExecutor.execute(() -> mCallback.onConnectionAvailable(connection));
            }
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

    private static ImsServiceConnectionRepository mInstance;

    /**
     * @return The ImsService connection repository instance for this process.
     */
    public static ImsServiceConnectionRepository getInstance() {
        if (mInstance == null) {
            synchronized (ImsServiceConnectionRepository.class) {
                if (mInstance == null) {
                    mInstance = new ImsServiceConnectionRepository();
                }
            }
        }
        return mInstance;
    }

    private final Object mLock;
    @GuardedBy(value="mLock")
    // Array of phoneIds containing a map of FeatureContainer types to the respective
    // FeatureContainer holding the information.
    private final SparseArray<Map<Class<?>,
            FeatureContainer<? extends IInterface>>> mConnectionsByPhoneId;
    @GuardedBy(value="mLock")
    // A map of FeatureContainer types to the respective list of listeners listening for changes to
    // that type.
    private final Map<Class<?>, List<ListenerContainer<? extends IInterface>>> mListeners;
    @GuardedBy(value="mLock")
    private final LocalLog mLocalLog = new LocalLog(50 /*lines*/);

    /**
     * Not to be instantiated directly, use {@link #getInstance()} instead.
     */
    @VisibleForTesting
    public ImsServiceConnectionRepository() {
        mLock = new Object();
        mConnectionsByPhoneId = new SparseArray<>();
        mListeners = new HashMap<>();
        logInfoLineLocked(-1, "ImsServiceConnectionRepository - created");
    }


    /**
     * Get the requested connection now if it exists. This method is blocking and may block if
     * waiting for access to the requested interface.
     * @param phoneId The phone ID that the connection is related to.
     * @param type The {@link Class<T>} type that the callback will be registered for.
     * @param <T> The Interface Type.
     * @return
     */
    public <T extends IInterface> Optional<FeatureContainer<T>> getIfExists(
            int phoneId, @NonNull Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("this method does not accept null arguments");
        }
        synchronized (mLock) {
            FeatureContainer<T> conn = filterForType(getConnectionMapLocked(phoneId), type);
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
            @NonNull Class<T> type, @NonNull Callback<T> callback, @NonNull Executor executor) {
        if (type == null || callback == null || executor == null) {
            throw new IllegalArgumentException("this method does not accept null arguments");
        }
        ListenerContainer<T> container = new ListenerContainer<>(phoneId, callback, executor);
        FeatureContainer<T> conn;
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
        if (conn != null) {
            container.notifyCallback(conn);
        }
    }

    /**
     * Unregister for updates to a previously registered callback.
     *
     * @param callback The callback to unregister.
     * @param <T> The Interface Type.
     */
    public <T extends IInterface> void unregisterForConnectionUpdates(
            @NonNull Callback<T> callback) {
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
     * Update the connection associated with a specific connection type.
     * @param phoneId The phone ID associated with this connection.
     * @param type The class of the connection.
     * @param newConnection The new connection, either an {@link IInterface} or {@code null} if
     *                      the connection is no longer available.
     * @param <T> The connection class type.
     */
    public <T extends IInterface> void updateConnection(int phoneId, @NonNull Class<T> type,
            @Nullable FeatureContainer<T> newConnection) {
        if (type == null) {
            throw new IllegalArgumentException("The type must be non-null");
        }
        List<ListenerContainer<T>> callbacks = null;
        synchronized (mLock) {
            Map<Class<?>, FeatureContainer<? extends IInterface>> connMap =
                    getConnectionMapLocked(phoneId);
            FeatureContainer<T> oldConnection = filterForType(connMap, type);
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
     * Prints the dump of log events that have occurred on this repository.
     */
    public void dump(PrintWriter printWriter) {
        mLocalLog.dump(printWriter);
    }

    // Should be locked by mLock before accessing
    private <T extends IInterface> List<ListenerContainer<T>> filterListenersLocked(
            int phoneId, Class<T> type) {
        List<ListenerContainer<? extends IInterface>> containers = getListenerListLocked(type);
        List<ListenerContainer<T>> filteredContainers = new ArrayList<>();
        for (ListenerContainer<? extends IInterface> c : containers) {
            if (c.phoneId == phoneId) {
                filteredContainers.add((ListenerContainer<T>) c);
            }
        }
        return filteredContainers;
    }

    // Should be locked by mLock before accessing
    private Map<Class<?>, FeatureContainer<? extends IInterface>> getConnectionMapLocked(
            int phoneId) {
        Map<Class<?>, FeatureContainer<? extends IInterface>> connectionMap =
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

    private <T extends IInterface, U> FeatureContainer<T> filterForType(
            Map<Class<?>, U> map, Class<T> type) {
        U value = null;
        for (Class<?> t : map.keySet()) {
            if (t == type) {
                value = map.get(t);
                break;
            }
        }
        if (value == null) return null;
        return (FeatureContainer<T>) value;
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
