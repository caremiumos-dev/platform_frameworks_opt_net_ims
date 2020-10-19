package com.android.ims.rcs.uce.presence.pidfparser.pidf;

import android.text.TextUtils;

import com.android.ims.rcs.uce.presence.pidfparser.ElementBase;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

public class Timestamp extends ElementBase {
    /** The name of this element */
    public static final String ELEMENT_NAME = "timestamp";

    private String mTimestamp;

    public Timestamp() {
    }

    public Timestamp(String timestamp) {
        mTimestamp = timestamp;
    }

    @Override
    protected String initNamespace() {
        return PidfConstant.NAMESPACE;
    }

    @Override
    protected String initElementName() {
        return ELEMENT_NAME;
    }

    public String getValue() {
        return mTimestamp;
    }

    @Override
    public void serialize(XmlSerializer serializer) throws IOException {
        if (mTimestamp == null) {
            return;
        }
        final String namespace = getNamespace();
        final String element = getElementName();
        serializer.startTag(namespace, element);
        serializer.text(mTimestamp);
        serializer.endTag(namespace, element);
    }

    @Override
    public void parse(XmlPullParser parser) throws IOException, XmlPullParserException {
        String namespace = parser.getNamespace();
        String name = parser.getName();

        if (!verifyParsingElement(namespace, name)) {
            throw new XmlPullParserException("Incorrect element: " + namespace + ", " + name);
        }

        // Move to the next event to get the value.
        int eventType = parser.next();

        // Get the value if the event type is text.
        if (eventType == XmlPullParser.TEXT) {
            String timestamp = parser.getText();
            if (!TextUtils.isEmpty(timestamp)) {
                mTimestamp = timestamp;
            }
        }

        // Move to the end tag.
        moveToElementEndTag(parser, eventType);
    }
}
