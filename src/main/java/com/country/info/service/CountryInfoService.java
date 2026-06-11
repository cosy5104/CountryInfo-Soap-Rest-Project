package com.country.info.service;

import com.country.info.model.CountryInfo;
import com.country.info.model.Language;
import com.country.info.repository.CountryInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CountryInfoService {

    private final CountryInfoRepository countryInfoRepository;
    private final CountryInfoSoapClient soapClient;
    private final SoapResponseParser responseParser;

    // ---------------------------------------------------------------
    // Core SOAP → DB flow
    // ---------------------------------------------------------------

    /**
     * Converts a raw country name to sentence case,
     * calls the SOAP API to get ISO code, fetches full info,
     * persists and returns the result.
     */
    public CountryInfo fetchAndSaveCountryInfo(String rawCountryName) {
        // Step 1: Sentence-case the name
        String countryName = toSentenceCase(rawCountryName);
        log.info("Processing country: {}", countryName);

        // Step 2: Get ISO code via SOAP
        String isoCode = soapClient.getCountryIsoCode(countryName);
        if (isoCode == null || isoCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Could not find ISO code for country: " + countryName);
        }
        log.info("ISO code for {}: {}", countryName, isoCode);

        // Step 3: Get full country info via SOAP
        String fullInfoXml = soapClient.getFullCountryInfoXml(isoCode);
        CountryInfo countryInfo = responseParser.parseFullCountryInfo(fullInfoXml);

        // Step 4: Persist (upsert by ISO code)
        Optional<CountryInfo> existing = countryInfoRepository.findByIsoCode(isoCode);
        if (existing.isPresent()) {
            CountryInfo toUpdate = existing.get();
            mergeCountryInfo(toUpdate, countryInfo);
            return countryInfoRepository.save(toUpdate);
        }

        return countryInfoRepository.save(countryInfo);
    }

    // ---------------------------------------------------------------
    // CRUD operations
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CountryInfo> findAll() {
        return countryInfoRepository.findAllWithLanguages();
    }

    @Transactional(readOnly = true)
    public CountryInfo findById(Long id) {
        return countryInfoRepository.findByIdWithLanguages(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "CountryInfo not found with id: " + id));
    }

    public CountryInfo update(Long id, CountryInfo updated) {

        CountryInfo existing = findById(id);

        if (updated.getName() != null)
            existing.setName(updated.getName());

        if (updated.getIsoCode() != null)
            existing.setIsoCode(updated.getIsoCode());

        if (updated.getCapitalCity() != null)
            existing.setCapitalCity(updated.getCapitalCity());

        if (updated.getPhoneCode() != null)
            existing.setPhoneCode(updated.getPhoneCode());

        if (updated.getContinentCode() != null)
            existing.setContinentCode(updated.getContinentCode());

        if (updated.getCurrencyIsoCode() != null)
            existing.setCurrencyIsoCode(updated.getCurrencyIsoCode());

        if (updated.getCountryFlag() != null)
            existing.setCountryFlag(updated.getCountryFlag());

        // ============================
        // FIXED LANGUAGE HANDLING
        // ============================
        if (updated.getLanguages() != null && !updated.getLanguages().isEmpty()) {

            // SAFE REPLACEMENT (NO clear())
            existing.getLanguages(); // ONLY works if mutable list

            for (Language lang : updated.getLanguages()) {

                // IMPORTANT: maintain relationship
                lang.setCountryInfo(existing);

                existing.getLanguages().add(lang);
            }
        }

        return countryInfoRepository.save(existing);
    }

    public void delete(Long id) {
        if (!countryInfoRepository.existsById(id)) {
            throw new jakarta.persistence.EntityNotFoundException(
                    "CountryInfo not found with id: " + id);
        }
        countryInfoRepository.deleteById(id);
    }

    // ---------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------

    /**
     * Converts any string to Sentence Case:
     *   "kenya"     → "Kenya"
     *   "KENYA"     → "Kenya"
     *   "new zealand" → "New Zealand"
     */
    public String toSentenceCase(String input) {
        if (input == null || input.isBlank()) return input;
        String trimmed = input.trim();
        String[] words = trimmed.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void mergeCountryInfo(CountryInfo target, CountryInfo source) {
        target.setName(source.getName());
        target.setCapitalCity(source.getCapitalCity());
        target.setPhoneCode(source.getPhoneCode());
        target.setContinentCode(source.getContinentCode());
        target.setCurrencyIsoCode(source.getCurrencyIsoCode());
        target.setCountryFlag(source.getCountryFlag());
        target.getLanguages().clear();
        source.getLanguages().forEach(target::addLanguage);
    }
}
