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

package com.android.ims.rcs.uce.presence.publish;

import android.util.Log;

import com.android.ims.rcs.uce.util.UceUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The helper class to manage the publish request parameters.
 */
class PublishProcessorState {

    private static final String LOG_TAG = "PublishProcessorState";

    // Default throttle for publication in milliseconds
    private static final long PUBLISH_THROTTLE_MS = 60000L;

    // The maximum number of the publication retries.
    private static final int PUBLISH_MAXIMUM_NUM_RETRIES = 3;

    private long mTaskId;
    private volatile boolean mIsPublishing;
    private volatile boolean mPendingRequest;
    private int mRetryCount;
    private Instant mAllowedTimestamp;

    private final Object mLock = new Object();

    PublishProcessorState() {
    }

    // Generate a unique task Id for this request.
    long generatePublishTaskId() {
        synchronized (mLock) {
            mTaskId = UceUtils.generateTaskId();
            return mTaskId;
        }
    }

    long getTaskId() {
        synchronized (mLock) {
            return mTaskId;
        }
    }

    void setPublishingFlag(boolean flag) {
        mIsPublishing = flag;
    }

    boolean isPublishingNow() {
        return mIsPublishing;
    }

    void setPendingRequest(boolean pendingRequest) {
        mPendingRequest = pendingRequest;
    }

    boolean hasPendingRequest() {
        return mPendingRequest;
    }

    // Check if it has reached the maximum retry count.
    boolean isReachMaximumRetries() {
       synchronized (mLock) {
           return (mRetryCount > PUBLISH_MAXIMUM_NUM_RETRIES) ? true : false;
       }
    }

    // Check if the current timestamp is allowed to request publish.
    boolean isCurrentTimeAllowed() {
        synchronized (mLock) {
            Instant now = Instant.now();
            return (now.isAfter(mAllowedTimestamp)) ? true : false;
        }
    }

    // Get the delay time to allow to execute the publish request.
    long getDelayTimeToAllowPublish() {
        synchronized (mLock) {
            // Setup the delay to the time which publish request is allowed to execute.
            long delayTime = ChronoUnit.MILLIS.between(Instant.now(), mAllowedTimestamp);
            if (delayTime < 0) {
                delayTime = 0L;
            }
            return delayTime;
        }
    }

    void resetRetryCount() {
        synchronized (mLock) {
            mRetryCount = 0;
            // Adjust the allowed timestamp of the request
            adjustAllowedPublishTimestamp(0L);
        }
    }

    void increaseRetryCount() {
        synchronized (mLock) {
            mRetryCount++;
            int count = (mRetryCount > PUBLISH_MAXIMUM_NUM_RETRIES) ?
                    PUBLISH_MAXIMUM_NUM_RETRIES : mRetryCount;
            long delay = count * PUBLISH_THROTTLE_MS;
            adjustAllowedPublishTimestamp(delay);
        }
    }

    // Adjust the timestamp to allow request PUBLISH with the specific delay time
    private void adjustAllowedPublishTimestamp(long delay) {
        synchronized (mLock) {
            Log.v(LOG_TAG, "adjustAllowedPublishTimestamp: delay=" + delay);
            if (mAllowedTimestamp == null) {
                // For the first time initialization.
                mAllowedTimestamp = Instant.now();
            } else if (delay == 0) {
                // The retry count is reset
                mAllowedTimestamp = Instant.now();
            } else if (delay > 0) {
                mAllowedTimestamp = Instant.now().plus(Duration.ofMillis(delay));
            }

            Log.v(LOG_TAG, "The new allowed publish timestamp: " + mAllowedTimestamp.toString());
        }
    }
}
