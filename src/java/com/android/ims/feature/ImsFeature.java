/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.ims.feature;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Base class for all IMS features that are supported by the framework.
 */

public class ImsFeature {

    // Invalid feature value
    public static final int INVALID = -1;
    // ImsFeatures that are defined in the Manifests
    public static final int EMERGENCY_VOICE = 0;
    public static final int VOICE = 1;
    public static final int RCS = 2;
    // Total number of features defined
    public static final int MAX = 3;


    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            flag = true,
            value = {EMERGENCY_VOICE, VOICE, RCS})
    public @interface Feature {}
}
