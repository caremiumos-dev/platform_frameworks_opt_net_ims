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

import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.telephony.ims.RcsContactPresenceTuple;
import android.telephony.ims.RcsContactPresenceTuple.ServiceCapabilities;
import android.telephony.ims.RcsContactUceCapability;
import android.telephony.ims.RcsContactUceCapability.PresenceBuilder;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.ims.ImsTestBase;

import com.android.ims.rcs.uce.presence.pidfparser.pidf.Presence;
import java.io.Reader;
import java.io.StringReader;
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

        android.util.Log.d("JCF", "pidfResult=" + pidfResult);

        final XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);

        // Convert to Reader
        Reader reader = new StringReader(pidfResult);
        parser.setInput(reader);

        int nextType = parser.next();
        String namespace = parser.getNamespace();  // urn:ietf:params:xml:ns:pidf
        String name = parser.getName();        // presence
        String entity = parser.getAttributeValue(XmlPullParser.NO_NAMESPACE, "entity");

        android.util.Log.d("JCF", "start: nextType=" + nextType + ", namespace=" + namespace
                + ", name=" + name + ", entity=" + entity);

        Presence presence = new Presence(presenceBuilder.build());
        presence.parse(parser);

        // while(nextType != XmlPullParser.END_TAG) {
        //     nextType = parser.next();
        //     namespace = parser.getNamespace();
        //     name = parser.getName();
        //     String text = null;
        //     if (nextType == XmlPullParser.TEXT) {
        //         text = parser.getText();
        //     }
        //     Log.d("JCF", "parse: nextType=" + nextType + ", namespace=" + namespace
        //             + ", name=" + name + ", text=" + text);
        // }




        // String nextText = parser.nextText();
        // android.util.Log.d("JCF", "nextText=" + nextText);
        //
        // int eventType1 = parser.nextTag();    // 2 start tag
        //
        // String name1 = parser.getName();     // tuple
        //
        // android.util.Log.d("JCF", "entity=" + entity + ", startTag1=" + startTag1 + ", eventType=" + eventType1 + ", namespace1=" + namespace1 + ", name1=" + name1);

        reader.close();
    }
}
