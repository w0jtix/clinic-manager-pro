package com.clinic.clinicmanager.utils;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXParseException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SecureXmlTest {

    private static Document parse(String xml) throws Exception {
        return SecureXml.newDocumentBuilderFactory().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldParsePlainXml() throws Exception {
        Document doc = parse("<Faktura><Fa><P_2>FV/1</P_2></Fa></Faktura>");

        assertEquals("FV/1", doc.getElementsByTagName("P_2").item(0).getTextContent());
    }

    @Test
    void shouldRejectDoctypeWithExternalEntity() {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE Faktura [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <Faktura><P_2>&xxe;</P_2></Faktura>
                """;

        assertThrows(SAXParseException.class, () -> parse(xml));
    }

    @Test
    void shouldRejectInternalEntityExpansion() {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE Faktura [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;">]>
                <Faktura>&b;</Faktura>
                """;

        assertThrows(SAXParseException.class, () -> parse(xml));
    }
}
