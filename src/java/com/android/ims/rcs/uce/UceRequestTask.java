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

import android.util.Log;

import com.android.ims.rcs.uce.UceController.UceControllerCallback;
import com.android.ims.rcs.uce.UceRequestTaskManager.TaskManagerCallback;
import com.android.internal.annotations.GuardedBy;

import java.util.concurrent.TimeUnit;

/**
 * The abstract class of the UCE request task
 */
public abstract class UceRequestTask {

    private static final String LOG_TAG = "UceRequestTask";

    private static final int MAX_RETRY_COUNT = 3;
    private static final int RETRY_TIME_INTERVAL = 3;  // seconds
        
    enum REQUEST_TYPE {
        CAPABILITY,
        AVAILABILITY
    }

    protected volatile boolean mIsReleasedFlagSet;

    protected UceRequestParam mRequestParam;
    protected UceRequestBehavior mRequestBehavior;
    protected UceRequestResponse mRequestResponse;

    private TaskManagerCallback mTaskManagerProxy;
    protected UceControllerCallback mUceControllerProxy;

    @GuardedBy("mRetryLock")
    private int mRetryCount = 0;
    private final Object mRetryLock = new Object();

    public UceRequestTask(UceControllerCallback controller, UceRequestParam param,
            TaskManagerCallback taskMgrCallback) {
        mIsReleasedFlagSet = false;

        mUceControllerProxy = controller;
        mTaskManagerProxy = taskMgrCallback;
        mRequestParam = param;
        mRequestResponse = new UceRequestResponse();

        defineTaskBehavior();
        initResponseCallback();
    }

    /**
     * Define how the task execute.
     */
    protected abstract void defineTaskBehavior();

    /**
     * Initialize the response callback.
     */
    protected abstract void initResponseCallback();

    protected void release() {
        logd("release");
        mIsReleasedFlagSet = true;
        mTaskManagerProxy.removeMessages(getTaskId());
    }

    protected void setTaskFinish() {
        logd("setTaskFinish");
        mTaskManagerProxy.sendTaskFinishedMessage(getTaskId());
    }

    /**
     * Send the request capabilities messages to execute the task.
     */
    public void executeTask() {
        if (mIsReleasedFlagSet) {
            logd("executeTask: already released");
            return;
        }
        mTaskManagerProxy.sendRequestCapabilitiesMessage(getTaskId(), 0);
    }

    /**
     * Start to execute the task.
     */
    public void executeBehavior() {
        mRequestBehavior.executeBehavior();
    }

    /**
     * Resend the request capability message.
     * @return true If the retry count has not reach the max retry count, false otherwise.
     */
    public boolean retryWithSendingMessage() {
        synchronized (mRetryLock) {
            if (reachMaxRetryCount()) {
                logd("retryWithSendingMessage: reach max retry count");
                return false;
            } else {
                int retryCount = increaseRetryCount();
                long delayed = TimeUnit.SECONDS.toMillis(retryCount * RETRY_TIME_INTERVAL);
                logd("retryWithSendingMessage: retry count=" + retryCount + ", delay=" + delayed);

                mTaskManagerProxy.removeRequestCapabilitiesMessages(getTaskId());
                mTaskManagerProxy.sendRequestCapabilitiesMessage(getTaskId(), delayed);
                return true;
            }
        }
    }

    // Check if the request has reached the max retry count.
    private boolean reachMaxRetryCount() {
        synchronized (mRetryLock) {
            return (mRetryCount > MAX_RETRY_COUNT) ? true : false;
        }
    }

    // Increase the retry count
    private int increaseRetryCount() {
        synchronized (mRetryLock) {
            return ++mRetryCount;
        }
    }

    public int getSubId() {
        return mRequestParam.getSubId();
    }

    public long getTaskId() {
        return mRequestParam.getTaskId();
    }

    protected void logd(String log) {
        Log.d(getLogTag(), getLogPrefix().append(log).toString());
    }

    protected void logi(String log) {
        Log.i(getLogTag(), getLogPrefix().append(log).toString());
    }

    protected void logw(String log) {
        Log.w(getLogTag(), getLogPrefix().append(log).toString());
    }

    protected String getLogTag() {
        return LOG_TAG;
    }

    protected StringBuilder getLogPrefix() {
        StringBuilder builder = new StringBuilder("[");
        builder.append(getSubId()).append(", ").append(getTaskId()).append("]");
        return builder;
    }
}
