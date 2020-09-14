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

/**
 * The interface to define the device status changed.
 */
public interface DeviceStatusChangedCallback {

    /**
     * Notify the TTY preferred mode is changed.
     */
    void onTtyPreferredModeChanged();

    /**
     * Notify the airplane mode is changed.
     */
    void onAirplaneModeChanged();

    /**
     * Notify the mobile data setting is changed.
     */
    void onMobileDataChanged();

    /**
     * Notify the VT setting is changed.
     */
    void onVtSettingChanged();

    /**
     * Notify the IMS is registered.
     */
    void onImsMmtelRegistered();

    /**
     * Notify the IMS is unregistered.
     */
    void onImsMmtelUnregistered();

    /**
     * Notify the IMS MMTEL capabilities is changed.
     */
    void onFeatureCapabilityChanged();

    /**
     * Notify the provisioning is changed.
     */
    void onProvisioningChanged();
}
