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

package com.android.ims.rcs.uce;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter;
import android.telephony.ims.aidl.IRcsUceControllerCallback;
import android.util.Log;

import com.android.ims.rcs.uce.UceController.UceControllerCallback;
import com.android.ims.rcs.uce.eab.EabCapabilityResult;
import com.android.ims.rcs.uce.presence.subscribe.SubscribeController;
import com.android.ims.rcs.uce.request.SubscribeRequest;
import com.android.ims.rcs.uce.request.UceRequest;
import com.android.ims.rcs.uce.util.UceUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The interface of managing the capability request and the availability request.
 */
public class UceRequestManager {

    private static final String LOG_TAG = "UceRequestManager";

    /**
     * The interface to receive the request from UceRequest.
     */
    public interface RequestManagerCallback {
        /**
         * Retrieve the contact capabilities from the cache.
         */
        EabCapabilityResult getCapabilitiesFromCache(List<Uri> uriList);

        /**
         * Retrieve the contact availability from the cache.
         */
        EabCapabilityResult getAvailabilityFromCache(Uri uri);

        /**
         * Store the given capabilities to the cache.
         */
        void saveCapabilities(List<RcsContactUceCapability> contactCapabilities);

        /**
         * Notify the request is finish. It only removed the request from the request collections.
         */
        void onRequestFinished(Long taskId);

        /**
         * Notify that the request is failed. It will trigger the callback
         * IRcsUceControllerCallback#onError
         */
        void onRequestFailed(Long taskId);

        /**
         * Notify that the request is success. It will trigger the callback
         * IRcsUceControllerCallback#onComplete
         */
        void onRequestSuccess(Long taskId);

        /**
         * Notify that the capabilities updates. It will trigger the callback
         * IRcsUceControllerCallback#onCapabilitiesReceived
         */
        void onCapabilityUpdate(Long taskId);
    }

    private RequestManagerCallback mRequestMgrCallback = new RequestManagerCallback() {
        @Override
        public EabCapabilityResult getCapabilitiesFromCache(List<Uri> uriList) {
            return mControllerCallback.getCapabilitiesFromCache(uriList);
        }

        @Override
        public EabCapabilityResult getAvailabilityFromCache(Uri uri) {
            return mControllerCallback.getAvailabilityFromCache(uri);
        }

        @Override
        public void saveCapabilities(List<RcsContactUceCapability> contactCapabilities) {
            mControllerCallback.saveCapabilities(contactCapabilities);
        }

        @Override
        public void onRequestFinished(Long taskId) {
            mHandler.sendRequestFinishedMessage(taskId);
        }

        @Override
        public void onRequestFailed(Long taskId) {
            mHandler.sendRequestFailedMessage(taskId);
        }

        @Override
        public void onRequestSuccess(Long taskId) {
            mHandler.sendRequestSuccessMessage(taskId);
        }

        @Override
        public void onCapabilityUpdate(Long taskId) {
            mHandler.sendCapabilitiesUpdateMessage(taskId);
        }
    };

    private final int mSubId;
    private final Context mContext;
    private final UceRequestHandler mHandler;
    private final Map<Long, UceRequest> mRequestCollection;

    private volatile boolean mIsDestroyed;
    private SubscribeController mSubscribeCtrl;
    private UceControllerCallback mControllerCallback;
    private final Object mLock = new Object();

    public UceRequestManager(Context context, int subId, Looper looper, UceControllerCallback c) {
        mSubId = subId;
        mContext = context;
        mControllerCallback = c;

        mHandler = new UceRequestHandler(looper);
        mRequestCollection = new HashMap<>();
        logi("create: " + mSubId);
    }

    /**
     * Set the SubscribeController for requesting capabilities by Subscribe mechanism.
     */
    public void setSubscribeController(SubscribeController controller) {
        mSubscribeCtrl = controller;
    }

    /**
     * Notify the request manager to destroy.
     */
    public void onDestroy() {
        logi("onDestroy: " + mSubId);
        mIsDestroyed = true;
        mHandler.removeAllMessages();
        synchronized (mLock) {
            mRequestCollection.forEach((taskId, request) -> request.onFinish());
            mRequestCollection.clear();
        }
    }

    /**
     * Send a new capability request. It is called by UceController.
     */
    public void sendCapabilityRequest(List<Uri> uriList, IRcsUceControllerCallback callback)
            throws RemoteException {
        if (mIsDestroyed) {
            callback.onError(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return;
        }
        sendRequestInternal(UceRequest.REQUEST_TYPE_CAPABILITY, uriList, callback);
    }

    /**
     * Send a new availability request. It is called by UceController.
     */
    public void sendAvailabilityRequest(Uri uri, IRcsUceControllerCallback callback)
            throws RemoteException {
        if (mIsDestroyed) {
            callback.onError(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return;
        }
        sendRequestInternal(UceRequest.REQUEST_TYPE_AVAILABILITY,
                Collections.singletonList(uri), callback);
    }

    private void sendRequestInternal(@UceRequest.UceRequestType int type, List<Uri> uriList,
            IRcsUceControllerCallback callback) throws RemoteException {
        UceRequest request = null;
        if (UceUtils.isPresenceSupported(mContext, mSubId)) {
            request = new SubscribeRequest(mSubId, type, mRequestMgrCallback, mSubscribeCtrl);
            request.setContactUri(uriList);
            request.setCapabilitiesCallback(callback);

        } else if (UceUtils.isSipOptionsSupported(mContext, mSubId)) {
            // TODO: Implement the OPTIONS request
        }

        if (request == null) {
            logw("sendCapabilityRequest: Neither Presence nor OPTIONS are supported");
            callback.onError(RcsUceAdapter.ERROR_NOT_ENABLED);
            return;
        }

        logd("sendRequestInternal: taskId=" + request.getTaskId() + ", request type=" + type);

        // Add this request to collection for tracking.
        addRequestToCollection(request);

        // send this request to message queue
        mHandler.sendRequestMessage(request.getTaskId());
    }

    private void addRequestToCollection(UceRequest request) {
        synchronized (mLock) {
            mRequestCollection.put(request.getTaskId(), request);
        }
    }

    private UceRequest removeRequestFromCollection(Long taskId) {
        synchronized (mLock) {
            return mRequestCollection.remove(taskId);
        }
    }

    private UceRequest getRequestFromCollection(Long taskId) {
        synchronized (mLock) {
            return mRequestCollection.get(taskId);
        }
    }

    private class UceRequestHandler extends Handler {
        private static final int EVENT_REQUEST_CAPABILITIES = 1;
        private static final int EVENT_REQUEST_FINISHED = 2;
        private static final int EVENT_REQUEST_FAILED = 3;
        private static final int EVENT_REQUEST_SUCCESS = 4;
        private static final int EVENT_CAPABILITIES_UPDATE = 5;

        public UceRequestHandler(Looper looper) {
            super(looper);
        }

        /**
         * Send the capabilities request message.
         */
        public void sendRequestMessage(Long taskId) {
            Message message = mHandler.obtainMessage();
            message.what = EVENT_REQUEST_CAPABILITIES;
            message.obj = taskId;
            sendMessage(message);
        }

        /**
         * Send the task is finished message.
         */
        public void sendRequestFinishedMessage(Long taskId) {
            Message message = mHandler.obtainMessage();
            message.what = EVENT_REQUEST_FINISHED;
            message.obj = taskId;
            sendMessage(message);
        }

        /**
         * Send the task is failed message.
         */
        public void sendRequestFailedMessage(Long taskId) {
            Message message = mHandler.obtainMessage();
            message.what = EVENT_REQUEST_FAILED;
            message.obj = taskId;
            sendMessage(message);
        }

        /**
         * Send the task is success message.
         */
        public void sendRequestSuccessMessage(Long taskId) {
            Message message = mHandler.obtainMessage();
            message.what = EVENT_REQUEST_SUCCESS;
            message.obj = taskId;
            sendMessage(message);
        }

        /**
         * Send the capabilities updated message.
         */
        public void sendCapabilitiesUpdateMessage(Long taskId) {
            Message message = mHandler.obtainMessage();
            message.what = EVENT_CAPABILITIES_UPDATE;
            message.obj = taskId;
            sendMessage(message);
        }

        /**
         * Remove all the messages from the handler
         */
        public void removeAllMessages() {
            removeMessages(EVENT_REQUEST_CAPABILITIES);
            removeMessages(EVENT_REQUEST_FINISHED);
            removeMessages(EVENT_REQUEST_FAILED);
            removeMessages(EVENT_REQUEST_SUCCESS);
            removeMessages(EVENT_CAPABILITIES_UPDATE);
        }

        @Override
        public void handleMessage(Message msg) {
            final Long taskId = (Long) msg.obj;
            logd("handleMessage: what=" + msg.what + ", taskId=" + taskId);

            switch (msg.what) {
                case EVENT_REQUEST_CAPABILITIES: {
                    UceRequest request = getRequestFromCollection(taskId);
                    if (request != null) {
                        request.executeRequest();
                    } else {
                        logw("handleMessage: cannot find request, taskId=" + taskId);
                    }
                    break;
                }
                case EVENT_REQUEST_FINISHED: {
                    // Remove the request from collections
                    UceRequest request = removeRequestFromCollection(taskId);
                    if (request != null) {
                        request.onFinish();
                    } else {
                        logw("handleMessage: cannot find request, taskId=" + taskId);
                    }
                    break;
                }
                case EVENT_REQUEST_FAILED: {
                    // Trigger the error callback and finish this request.
                    UceRequest request = removeRequestFromCollection(taskId);
                    if (request != null) {
                        request.handleRequestFailed(false);
                        request.onFinish();
                    } else {
                        logw("handleMessage: cannot find request, taskId=" + taskId);
                    }
                    break;
                }
                case EVENT_REQUEST_SUCCESS: {
                    // Trigger the success callback and finish this request.
                    UceRequest request = removeRequestFromCollection(taskId);
                    if (request != null) {
                        request.handleRequestCompleted(false);
                        request.onFinish();
                    } else {
                        logw("handleMessage: cannot find request, taskId=" + taskId);
                    }
                    break;
                }
                case EVENT_CAPABILITIES_UPDATE: {
                    UceRequest request = getRequestFromCollection(taskId);
                    if (request != null) {
                        boolean result = request.handleCapabilitiesUpdated();
                        if (!result) {
                            // Remove this request if trigger capabilities callback is failed.
                            removeRequestFromCollection(taskId);
                            request.onFinish();
                        }
                    } else {
                        logw("handleMessage: cannot find request, taskId=" + taskId);
                    }
                    break;
                }
                default:
                    logw("handleMessage: error message");
                    break;
            }
        }
    }

    private void logi(String log) {
        Log.i(LOG_TAG, log);
    }

    private void logd(String log) {
        Log.d(LOG_TAG, getLogPrefix().append(log).toString());
    }

    private void logw(String log) {
        Log.w(LOG_TAG, getLogPrefix().append(log).toString());
    }

    private StringBuilder getLogPrefix() {
        StringBuilder builder = new StringBuilder("[");
        builder.append(mSubId);
        builder.append("] ");
        return builder;
    }
}
