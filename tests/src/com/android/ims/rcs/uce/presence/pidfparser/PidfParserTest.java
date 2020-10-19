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

package com.android.ims.rcs.uce.presence.pidfparser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactPresenceTuple.ServiceCapabilities;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsContactUceCapability.PresenceBuilder;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

@RunWith(AndroidJUnit4.class)
public class PidfParserTest extends ImsTestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testConvertToPidf() throws Exception {
        Uri contact = Uri.fromParts("sip", "test", null);
        ServiceCapabilities.Builder servCapsBuilder = new ServiceCapabilities.Builder(true, true);
        servCapsBuilder.addSupportedDuplexMode(ServiceCapabilities.DUPLEX_MODE_FULL);

        RcsContactPresenceTuple.Builder tupleBuilder = new RcsContactPresenceTuple.Builder(
            RcsContactPresenceTuple.TUPLE_BASIC_STATUS_OPEN,
            RcsContactPresenceTuple.SERVICE_ID_MMTEL, "1.0");
        tupleBuilder.addContactUri(contact).addServiceCapabilities(servCapsBuilder.build());

        PresenceBuilder presenceBuilder = new PresenceBuilder(contact,
                RcsContactUceCapability.SOURCE_TYPE_CACHED,
                RcsContactUceCapability.REQUEST_RESULT_FOUND);
        presenceBuilder.addCapabilityTuple(tupleBuilder.build());

        final String pidfResult = PidfParser.convertToPidf(presenceBuilder.build());

        String audioSupported = "<caps:audio>true</caps:audio>";
        String videoSupported = "<caps:video>true</caps:video>";
        assertTrue(pidfResult.contains(audioSupported));
        assertTrue(pidfResult.contains(videoSupported));
    }

    @Test
    @SmallTest
    public void testRcsContactUceCapabilityConversation() throws Exception {
        String pidf = getPidfData();

        RcsContactUceCapability capabilities = PidfParser.convertToRcsContactUceCapability(pidf);

        assertNotNull(capabilities);
        assertEquals(Uri.fromParts("sip", "test", null), capabilities.getContactUri());

        List<RcsContactPresenceTuple> presenceTupleList = capabilities.getPresenceTuples();
        assertNotNull(presenceTupleList);
        assertEquals(2, presenceTupleList.size());

        RcsContactPresenceTuple presenceTuple1 = presenceTupleList.get(0);
        assertEquals("service_id_01", presenceTuple1.getServiceId());
        assertEquals("1.0", presenceTuple1.getServiceVersion());
        assertEquals("description_test1", presenceTuple1.getServiceDescription());
        assertEquals(Uri.fromParts("sip", "test", null), presenceTuple1.getContactUri());
        assertTrue(presenceTuple1.getServiceCapabilities().isAudioCapable());
        assertTrue(presenceTuple1.getServiceCapabilities().isVideoCapable());

        RcsContactPresenceTuple presenceTuple2 = presenceTupleList.get(1);
        assertEquals("service_id_02", presenceTuple2.getServiceId());
        assertEquals("1.0", presenceTuple2.getServiceVersion());
        assertEquals("description_test2", presenceTuple2.getServiceDescription());
        assertEquals(Uri.fromParts("sip", "test", null), presenceTuple2.getContactUri());
        assertTrue(presenceTuple2.getServiceCapabilities().isAudioCapable());
        assertFalse(presenceTuple2.getServiceCapabilities().isVideoCapable());
    }

    private String getPidfData() {
        StringBuilder pidfBuilder = new StringBuilder();
        pidfBuilder.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
                .append("<presence entity=\"sip:test\"")
                .append(" xmlns=\"urn:ietf:params:xml:ns:pidf\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
                .append(" xmlns:op=\"urn:oma:xml:prs:pidf:oma-pres\"")
                .append(" xmlns:caps=\"urn:ietf:params:xml:ns:pidf:caps\">")
                // tuple 1
                .append("<tuple id=\"tid0\"><status><basic>open</basic></status>")
                .append("<op:service-description>")
                .append("<op:service-id>service_id_01</op:service-id>")
                .append("<op:version>1.0</op:version>")
                .append("<op:description>description_test1</op:description>")
                .append("</op:service-description>")
                // support audio
                .append("<caps:servcaps>")
                .append("<caps:audio>true</caps:audio>")
                // support audio
                .append("<caps:video>true</caps:video>")
                .append("</caps:servcaps>")
                .append("<contact>sip:test</contact>")
                .append("</tuple>")
                // tuple 2
                .append("<tuple id=\"tid1\"><status><basic>open</basic></status>")
                .append("<op:service-description>")
                .append("<op:service-id>service_id_02</op:service-id>")
                .append("<op:version>1.0</op:version>")
                .append("<op:description>description_test2</op:description>")
                .append("</op:service-description>")
                // support audio
                .append("<caps:servcaps>")
                .append("<caps:audio>true</caps:audio>")
                // NOT support audio
                .append("<caps:video>false</caps:video>")
                .append("</caps:servcaps>")
                .append("<contact>sip:test</contact>")
                .append("</tuple></presence>");

        return pidfBuilder.toString();
    }
}
