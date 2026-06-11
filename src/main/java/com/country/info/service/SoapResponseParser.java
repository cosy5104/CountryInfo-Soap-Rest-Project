package com.country.info.service;

import com.country.info.model.CountryInfo;
import com.country.info.model.Language;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the XML response from the FullCountryInfo SOAP operation
 * and maps it to CountryInfo / Language domain objects.
 *
 * Expected XML structure (simplified):
 * <FullCountryInfoResult>
 *   <sISOCode>TZ</sISOCode>
 *   <sName>Tanzania</sName>
 *   <sCapitalCity>Dodoma</sCapitalCity>
 *   <sPhoneCode>255</sPhoneCode>
 *   <sContinentCode>AF</sContinentCode>
 *   <sCurrencyISOCode>TZS</sCurrencyISOCode>
 *   <sCountryFlag>http://...</sCountryFlag>
 *   <Languages>
 *     <tLanguage>
 *       <sISOCode>SW</sISOCode>
 *       <sName>Swahili</sName>
 *     </tLanguage>
 *   </Languages>
 * </FullCountryInfoResult>
 */
@Component
@Slf4j
public class SoapResponseParser {

    public CountryInfo parseFullCountryInfo(String responseXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(responseXml)));

            CountryInfo countryInfo = new CountryInfo();

            countryInfo.setIsoCode(getTextContent(doc, "sISOCode"));
            countryInfo.setName(getTextContent(doc, "sName"));
            countryInfo.setCapitalCity(getTextContent(doc, "sCapitalCity"));
            countryInfo.setPhoneCode(getTextContent(doc, "sPhoneCode"));
            countryInfo.setContinentCode(getTextContent(doc, "sContinentCode"));
            countryInfo.setCurrencyIsoCode(getTextContent(doc, "sCurrencyISOCode"));
            countryInfo.setCountryFlag(getTextContent(doc, "sCountryFlag"));

            // Parse languages
            NodeList languageNodes = doc.getElementsByTagNameNS("*", "tLanguage");
            if (languageNodes.getLength() == 0) {
                // Fallback: without namespace
                languageNodes = doc.getElementsByTagName("tLanguage");
            }

            List<Language> languages = new ArrayList<>();
            for (int i = 0; i < languageNodes.getLength(); i++) {
                Element langElement = (Element) languageNodes.item(i);
                Language language = new Language();
                language.setIsoCode(getChildText(langElement, "sISOCode"));
                language.setName(getChildText(langElement, "sName"));
                language.setCountryInfo(countryInfo);
                languages.add(language);
            }
            countryInfo.setLanguages(languages);

            log.info("Parsed country: {} (ISO: {}) with {} language(s)",
                     countryInfo.getName(), countryInfo.getIsoCode(), languages.size());

            return countryInfo;

        } catch (Exception e) {
            log.error("Failed to parse FullCountryInfo XML response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse SOAP response: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------
    // Private XML helpers
    // ---------------------------------------------------------------

    private String getTextContent(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagNameNS("*", tagName);
        if (nodes.getLength() == 0) {
            nodes = doc.getElementsByTagName(tagName);
        }
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }

    private String getChildText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", tagName);
        if (nodes.getLength() == 0) {
            nodes = parent.getElementsByTagName(tagName);
        }
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }
}
