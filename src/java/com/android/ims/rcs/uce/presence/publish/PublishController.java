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

import android.annotation.NonNull;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsUceAdapter.PublishState;
import android.telephony.ims.aidl.IRcsUcePublishStateCallback;

import com.android.ims.rcs.uce.ControllerBase;

/**
 * The interface related to the PUBLISH request.
 */
public interface PublishController extends ControllerBase {
    /**
     * The type of triggering the publish request.
     */
    enum PUBLISH_TRIGGER_TYPE {
        REQUEST_REGULAR_PUBLISH,
        REQUEST_FROM_SERVICE,
        REQUEST_FAILED_RETRY,
        TTY_PREFERRED_CHANGED,
        AIRPLANE_MODE_CHANGED,
        MOBILE_DATA_CHANGED,
        VT_SETTING_CHANGED,
        MMTEL_REGISTERED,
        MMTEL_UNREGISTERED,
        MMTEL_CAPABILITY_CHANGED,
        PROVISIONING_CHANGED
    }

    /**
     * Receive the callback from the sub-components which interact with PublishController.
     */
    interface PublishControllerCallback {
        /**
         * Request publish from local.
         */
        void requestPublishFromInternal(PUBLISH_TRIGGER_TYPE type, long delay);

        /**
         * Update the publish request result.
         */
        void updatePublishRequestResult(int publishState);
    }

    /**
     * Retrieve the RCS UCE Publish state.
     */
    @PublishState int getUcePublishState();

    /**
     * Notify that the device's capabilities have been unpublished from the network.
     */
    void onUnpublish();

    /**
     * Retrieve the device's capabilities.
     */
    RcsContactUceCapability getDeviceCapabilities();

    /**
     * Publish the device's capabilities to the Presence server.
     */
    void requestPublishCapabilitiesFromService();

    /**
     * Register a {@link PublishStateCallback} to listen to the published state changed.
     */
    void registerPublishStateCallback(@NonNull IRcsUcePublishStateCallback c);

    /**
     * Removes an existing {@link PublishStateCallback}.
     */
    void unregisterPublishStateCallback(@NonNull IRcsUcePublishStateCallback c);
}
