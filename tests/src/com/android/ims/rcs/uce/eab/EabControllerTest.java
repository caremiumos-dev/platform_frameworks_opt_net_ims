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

package com.android.ims.rcs.uce.eab;

import static android.telephony.ims.RcsContactUceCapability.REQUEST_RESULT_FOUND;
import static android.telephony.ims.RcsContactUceCapability.SOURCE_TYPE_NETWORK;

import static com.android.ims.rcs.uce.eab.EabProvider.CONTACT_URI;

import static java.time.temporal.ChronoUnit.DAYS;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactUceCapability;

import android.test.mock.MockContentResolver;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class EabControllerTest extends ImsTestBase {
    EabProviderTestable mEabProviderTestable = new EabProviderTestable();
    private int mSubId = 1;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockContentResolver mockContentResolver =
                (MockContentResolver) mContext.getContentResolver();
        mEabProviderTestable.initializeForTesting(mContext);
        mockContentResolver.addProvider(EabProvider.AUTHORITY, mEabProviderTestable);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testGetAvailability() {
        ContentValues data = new ContentValues();
        data.put(EabProvider.ContactColumns.NAME, "Android");
        data.put(EabProvider.ContactColumns.PHONE_NUMBER, "1234567");
        data.put(EabProvider.ContactColumns.RAW_CONTACT_ID, 1);
        mContext.getContentResolver().insert(CONTACT_URI, data);

        Uri contactUri = Uri.parse("1234567");
        List<RcsContactUceCapability> contactList = new ArrayList<>();
        RcsContactUceCapability.PresenceBuilder builder =
                new RcsContactUceCapability.PresenceBuilder(
                        contactUri, SOURCE_TYPE_NETWORK, REQUEST_RESULT_FOUND);
        RcsContactPresenceTuple.ServiceCapabilities.Builder serviceCapabilitiesBuilder =
                new RcsContactPresenceTuple.ServiceCapabilities.Builder(true, false);
        RcsContactPresenceTuple rcsContactPresenceTuple =
                new RcsContactPresenceTuple.Builder("status", "serviceId", "version")
                        .addDescription("description")
                        .addContactUri(contactUri)
                        .addServiceCapabilities(serviceCapabilitiesBuilder.build())
                        .addTimeStamp(String.valueOf(Instant.now().plus(1, DAYS).toEpochMilli()))
                        .build();
        builder.addCapabilityTuple(rcsContactPresenceTuple);

        EabController eabController = createEabController();
        contactList.add(builder.build());
        eabController.saveCapabilities(contactList);

        Assert.assertEquals(1,
                eabController.getAvailability(contactUri).getContactCapabilities().size());
    }

    @Test
    @SmallTest
    public void testGetCapability() {
        ContentValues data = new ContentValues();
        data.put(EabProvider.ContactColumns.NAME, "Android");
        data.put(EabProvider.ContactColumns.PHONE_NUMBER, "1234567");
        data.put(EabProvider.ContactColumns.RAW_CONTACT_ID, 1);
        mContext.getContentResolver().insert(CONTACT_URI, data);

        Uri contactUri = Uri.parse("1234567");
        List<RcsContactUceCapability> contactList = new ArrayList<>();
        RcsContactUceCapability.PresenceBuilder builder =
                new RcsContactUceCapability.PresenceBuilder(
                        contactUri, SOURCE_TYPE_NETWORK, REQUEST_RESULT_FOUND);
        RcsContactPresenceTuple.ServiceCapabilities.Builder serviceCapabilitiesBuilder =
                new RcsContactPresenceTuple.ServiceCapabilities.Builder(true, false);
        RcsContactPresenceTuple rcsContactPresenceTuple =
                new RcsContactPresenceTuple.Builder("status", "serviceId", "version")
                        .addDescription("description")
                        .addContactUri(contactUri)
                        .addServiceCapabilities(serviceCapabilitiesBuilder.build())
                        .addTimeStamp(String.valueOf(Instant.now().plus(1, DAYS).toEpochMilli()))
                        .build();
        builder.addCapabilityTuple(rcsContactPresenceTuple);

        EabController eabController = createEabController();
        contactList.add(builder.build());
        eabController.saveCapabilities(contactList);

        List<Uri> uri = new ArrayList<>();
        uri.add(contactUri);
        Assert.assertEquals(0,
                eabController.getCapabilities(uri).getExpiredContacts().size());
        Assert.assertEquals(1,
                eabController.getCapabilities(uri).getContactCapabilities().size());

    }

    private EabControllerImpl createEabController() {
        EabControllerImpl eabController = new EabControllerImpl(mContext, mSubId);
        return eabController;
    }
}
