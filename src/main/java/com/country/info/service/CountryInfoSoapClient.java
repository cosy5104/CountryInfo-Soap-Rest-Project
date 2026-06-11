package com.country.info.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.soap.client.core.SoapActionCallback;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.soap.SoapMessage;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;

/**
 * SOAP client that communicates with the CountryInfoService WSDL.
 * Uses raw XML building to avoid WSDL code-gen at runtime,
 * making the project compile without network access to the WSDL.
 */
@Service
@Slf4j
public class CountryInfoSoapClient extends WebServiceGatewaySupport {

    private static final String NAMESPACE_URI =
            "http://www.oorsprong.org/websamples.countryinfo";

    @Value("${soap.wsdl.url}")
    private String soapEndpointUrl;

    /**
     * Calls CountryISOCode SOAP operation.
     * Request body: <sCountryName>Tanzania</sCountryName>
     * Returns: ISO code string, e.g. "TZ"
     */
    public String getCountryIsoCode(String countryName) {

        String request =
                "<web:CountryISOCode xmlns:web=\"" + NAMESPACE_URI + "\">" +
                        "<web:sCountryName>" + countryName + "</web:sCountryName>" +
                        "</web:CountryISOCode>";

        String response = sendSoapRequest(request, NAMESPACE_URI + "/CountryISOCode");

        return extractTagValue(response, "CountryISOCodeResult");
    }

    /**
     * Calls FullCountryInfo SOAP operation.
     * Request body: <sCountryISOCode>TZ</sCountryISOCode>
     * Returns: raw XML of tCountryInfo
     */
    public String getFullCountryInfoXml(String isoCode) {

        String request =
                "<web:FullCountryInfo xmlns:web=\"" + NAMESPACE_URI + "\">" +
                        "<web:sCountryISOCode>" + isoCode + "</web:sCountryISOCode>" +
                        "</web:FullCountryInfo>";

        return sendSoapRequest(request, NAMESPACE_URI + "/FullCountryInfo");
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------
private String sendSoapRequest(String xml, String soapAction) {

    try {
        WebServiceTemplate template = getWebServiceTemplate();

        StringWriter writer = new StringWriter();

        Source request = new javax.xml.transform.stream.StreamSource(
                new java.io.StringReader(xml)
        );

        template.sendSourceAndReceiveToResult(
                soapEndpointUrl,
                request,
                message -> ((SoapMessage) message).setSoapAction(soapAction),
                new javax.xml.transform.stream.StreamResult(writer)
        );

        return writer.toString();

    } catch (Exception e) {
        throw new RuntimeException("SOAP call failed: " + e.getMessage(), e);
    }
}

    /**
     * Extracts inner text of the first occurrence of <tagName>...</tagName>.
     */
    public String extractTagValue(String xml, String tagName) {

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));

            NodeList nodes = doc.getElementsByTagNameNS("*", tagName);

            if (nodes.getLength() > 0) {
                return nodes.item(0).getTextContent();
            }

            log.warn("Tag {} not found in SOAP response", tagName);
            return null;

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse SOAP response", e);
        }
    }

    private String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
    }
}
