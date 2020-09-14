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
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.net.Uri;
import android.telephony.ims.RcsUceAdapter;
import android.util.Log;
import android.util.LongSparseArray;

import com.android.ims.rcs.uce.UceController.UceControllerCallback;
import com.android.ims.rcs.uce.UceRequestTask.REQUEST_TYPE;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager the UCE Request task.
 */
public class UceRequestTaskManager {

    private static final String LOG_TAG = "UceRequestTaskManager";

    /**
     * The interface to receive the request from UceRequesTask
     */
    public interface TaskManagerCallback {
        /**
         * Send the request capabilities message.
         */
        void sendRequestCapabilitiesMessage(Long taskId, long delay);

        /**
         * Remove the request capabilities messages associated with the task id.
         */
        void removeRequestCapabilitiesMessages(Long taskId);

        /**
         * Send the task is finished message.
         */
        void sendTaskFinishedMessage(Long taskId);

        /**
         * Remove all the messages associated with the task id.
         */
        void removeMessages(Long taskId);
    }

    private static Long sTaskID = 0L;

    private final int mSubId;
    private volatile boolean mIsDestroyed;

    private final Context mContext;
    private final RequestTaskHandler mHandler;

    // Maps task ID -> UceRequestTask.
    private final LongSparseArray<UceRequestTask> mRequestTaskList;

    private final Object mLock = new Object();

    public UceRequestTaskManager(Context context, int subId, Looper looper) {
        mSubId = subId;
        mContext = context;
        mHandler = new RequestTaskHandler(looper);
        mRequestTaskList = new LongSparseArray<>();
        logi("create: " + mSubId);
    }

    /**
     * Notify to destroy the UceRequestTaskManager.
     */
    public void onDestroy() {
        logi("onDestroy: " + mSubId);
        mIsDestroyed = true;
        mHandler.removeAllMessages();
        synchronized (mLock) {
            for(int index = 0, size = mRequestTaskList.size(); index < size; index++) {
                UceRequestTask requestTask = mRequestTaskList.valueAt(index);
                requestTask.release();
            }
            mRequestTaskList.clear();
        }
    }

    /**
     * Create and initialize a capability request task.
     */
    public UceRequestTask newCapabilityRequestTask(UceControllerCallback controller,
            List<Uri> uriList, RcsUceAdapter.CapabilitiesCallback callback) {

        if (mIsDestroyed) {
            callback.onError(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return null;
        }

        UceRequestParam requestParam = new UceRequestParam(mSubId, generateTaskId(),
                REQUEST_TYPE.CAPABILITY, uriList, callback);

        UceRequestTask task = null;
        if (UceSettingUtil.isPresenceSupported(mContext, mSubId)) {
            // TODO: Implement the PresenceRequestTask
        } else if (UceSettingUtil.isSipOptionsSupported(mContext, mSubId)) {
            task = new OptionsRequestTask(controller, requestParam, mTaskManagerCallback);
        } else {
            logw("newCapabilityRequestTask: Neither Presence nor SIP OPTIONS are supported");
            callback.onError(RcsUceAdapter.ERROR_NOT_ENABLED);
        }

        // Add the task to list for tracking.
        if (task != null) {
            addTaskToCollection(task);
        }
        return task;
    }

    /**
     * Create and initialize a availability request task
     */
    public UceRequestTask newAvailabilityRequestTask(UceControllerCallback controller, Uri uri,
            RcsUceAdapter.CapabilitiesCallback callback) {

        if (mIsDestroyed) {
            callback.onError(RcsUceAdapter.ERROR_GENERIC_FAILURE);
            return null;
        }

        UceRequestParam requestParam = new UceRequestParam(mSubId, generateTaskId(),
                REQUEST_TYPE.AVAILABILITY, Arrays.asList(uri), callback);

        UceRequestTask task = null;
        if (UceSettingUtil.isPresenceSupported(mContext, mSubId)) {
            // TODO: Implement the PresenceRequestTask
        } else if (UceSettingUtil.isSipOptionsSupported(mContext, mSubId)) {
            task = new OptionsRequestTask(controller, requestParam, mTaskManagerCallback);
        } else {
            logw("newAvailabilityRequestTask: Neither Presence nor SIP OPTIONS are supported");
            callback.onError(RcsUceAdapter.ERROR_NOT_ENABLED);
        }

        // Add the task to list for tracking.
        if (task != null) {
            addTaskToCollection(task);
        }
        return task;
    }

    private void addTaskToCollection(UceRequestTask task) {
        synchronized (mLock) {
            mRequestTaskList.put(task.getTaskId(), task);
        }
    }

    private void removeTaskFromCollection(long taskId) {
        synchronized (mLock) {
            mRequestTaskList.remove(taskId);
        }
    }

    private TaskManagerCallback mTaskManagerCallback = new TaskManagerCallback() {
        /**
         * Send the message to execute the request.
         */
        @Override
        public void sendRequestCapabilitiesMessage(Long taskId, long delay) {
            mHandler.sendRequestCapabilitiesMessage(taskId, delay);
        }

        /**
         * Send the message to notify the task is finished.
         */
        @Override
        public void sendTaskFinishedMessage(Long taskId) {
            mHandler.sendTaskFinishedMessage(taskId);
        }

        /**
         * Remove all the messages associated with the task id.
         */
        @Override
        public void removeRequestCapabilitiesMessages(Long taskId) {
            mHandler.removeRequestCapabilitiesMessages(taskId);
        }

        /**
         * Remove all the messages associated with the task id.
         */
        @Override
        public void removeMessages(Long taskId) {
            mHandler.removeMessages(taskId);
        }
    };

    private UceRequestTask getUceRequestTask(long taskId) {
        synchronized (mLock) {
            return mRequestTaskList.get(taskId);
        }
    }

    protected class RequestTaskHandler extends Handler {
        private static final int EVENT_REQUEST_CAPABILITIES = 1;
        private static final int EVENT_TASK_FINISHED = 2;

        private final Object mHandlerLock = new Object();

        public RequestTaskHandler(Looper looper) {
            super(looper);
        }

        /**
         * Send the capabilities request message.
         */
        public void sendRequestCapabilitiesMessage(Long taskId, long delay) {
            synchronized (mHandlerLock) {
                if (mIsDestroyed) return;
                logd("sendRequestCapabilitiesMessage: " + taskId + ", delay=" + delay);
                Message message = mHandler.obtainMessage();
                message.what = EVENT_REQUEST_CAPABILITIES;
                message.obj = taskId;
                if (delay <= 0) {
                    sendMessage(message);
                } else {
                    sendMessageDelayed(message, delay);
                }
            }
        }

        /**
         * Send the task is finished message.
         */
        public void sendTaskFinishedMessage(Long taskId) {
            synchronized (mHandlerLock) {
                if (mIsDestroyed) return;
                logd("sendTaskFinishedMessage: " + taskId);
                Message message = mHandler.obtainMessage();
                message.what = EVENT_TASK_FINISHED;
                message.obj = taskId;
                sendMessage(message);
            }
        }

        /**
         * Remove the messages associated with the giving task Id.
         */
        public void removeRequestCapabilitiesMessages(Long taskId) {
            synchronized (mHandlerLock) {
                logd("removeRequestCapabilitiesMessages: taskId=" + taskId);
                removeMessages(EVENT_REQUEST_CAPABILITIES, taskId);
            }
        }

        /**
         * Remove all the messages associated with the task id.
         */
        public void removeMessages(Long taskId) {
            synchronized (mHandlerLock) {
                logd("removeMessages: taskId=" + taskId);
                removeMessages(EVENT_REQUEST_CAPABILITIES, taskId);
                removeMessages(EVENT_TASK_FINISHED, taskId);
            }
        }

        /**
         * Remove all messages from the handler.
         */
        public void removeAllMessages() {
            synchronized (mHandlerLock) {
                logd("removeAllMessages");
                removeMessages(EVENT_REQUEST_CAPABILITIES);
                removeMessages(EVENT_TASK_FINISHED);
            }
        }

        @Override
        public void handleMessage(Message msg) {
            final long taskId = (msg.obj == null) ? -1 : (long) msg.obj;
            logd("handleMessage: " + msg.what + ", taskId=" + taskId);

            switch (msg.what) {
                case EVENT_REQUEST_CAPABILITIES:
                    UceRequestTask requestTask = getUceRequestTask(taskId);
                    if (requestTask == null) {
                        logw("handleMessage: UceRequestTask is null " + taskId);
                        return;
                    }
                    requestTask.executeBehavior();
                    break;
                case EVENT_TASK_FINISHED:
                    removeTaskFromCollection(taskId);
                    break;
            }
        }
    }

    private static synchronized Long generateTaskId() {
        return ++sTaskID;
    }

    private void logi(String log) {
        Log.i(LOG_TAG, log);
    }

    private void logw(String log) {
        Log.w(LOG_TAG, log);
    }

    private void logd(String log) {
        Log.d(LOG_TAG, log);
    }
}
