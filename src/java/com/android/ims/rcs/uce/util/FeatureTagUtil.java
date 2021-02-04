/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.ims.rcs.uce.util;

import android.net.Uri;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsContactUceCapability.OptionsBuilder;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TODO
 */
public class FeatureTagUtil {
    private static final String FEATURE_TAG_MMTEL =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel\"";

    private static final String FEATURE_VIDEO = "video";

    /**
     * Add the MMTEL feature tag to the given RcsContactUceCapability OPTIONS builder.
     * @param optionsBuilder The OptionsBuilder to add the mmtel feature tags
     * @param audioSupport If the audio capability is supported
     * @param videoSupport If the video capability is supported
     */
    public static void addMmTelFeatureTags(final OptionsBuilder optionsBuilder,
            boolean audioSupport, boolean videoSupport) {
        StringBuilder builder = new StringBuilder();
        if (audioSupport) {
            builder.append(FEATURE_TAG_MMTEL);
            if (videoSupport) {
                builder.append(": ").append(FEATURE_VIDEO);
            }
        }
        String mmtelFeature = builder.toString();
        if (!TextUtils.isEmpty(mmtelFeature)) {
            optionsBuilder.addFeatureTag(mmtelFeature);
        }
    }

    /**
     * Convert the given feature tag list to the RcsContactUceCapabilities.
     */
    public static RcsContactUceCapability getContactCapability(Uri contact,
            List<String> featureTags) {
        OptionsBuilder builder = new OptionsBuilder(contact);
        builder.setRequestResult(RcsContactUceCapability.REQUEST_RESULT_FOUND);
        featureTags.forEach(feature -> builder.addFeatureTag(feature));
        return builder.build();
        // TODO
        // boolean audioSupport = false;
        // boolean videoSupport = false;
        //
        // Optional<String> mmtelFeatureTag = featureTags.stream()
        //         .filter(Objects::nonNull)
        //         .filter(featureTag -> featureTag.equalsIgnoreCase(FEATURE_TAG_MMTEL))
        //         .findFirst();
        //
        // if (mmtelFeatureTag.isPresent()) {
        //     audioSupport = true;
        //     videoSupport = mmtelFeatureTag
        //             .filter(feature -> feature.contains(FEATURE_VIDEO))
        //             .findAny()
        //             .isPresent();
        // }
    }
}
