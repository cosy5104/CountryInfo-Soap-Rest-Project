package com.country.info;

import com.country.info.model.CountryInfo;
import com.country.info.model.Language;
import com.country.info.repository.CountryInfoRepository;
import com.country.info.service.CountryInfoService;
import com.country.info.service.CountryInfoSoapClient;
import com.country.info.service.SoapResponseParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CountryInfoServiceTest {

    @Mock
    private CountryInfoRepository repository;

    @Mock
    private CountryInfoSoapClient soapClient;

    @Mock
    private SoapResponseParser parser;

    @InjectMocks
    private CountryInfoService service;

    private CountryInfo sampleCountry;

    @BeforeEach
    void setUp() {
        sampleCountry = CountryInfo.builder()
                .id(1L)
                .isoCode("TZ")
                .name("Tanzania")
                .capitalCity("Dodoma")
                .phoneCode("255")
                .continentCode("AF")
                .currencyIsoCode("TZS")
                .languages(List.of(
                        Language.builder().isoCode("SW").name("Swahili").build()
                ))
                .build();
    }

    // ── Sentence-case tests ────────────────────────────────────────

    @Test
    void toSentenceCase_lowercase() {
        assertThat(service.toSentenceCase("kenya")).isEqualTo("Kenya");
    }

    @Test
    void toSentenceCase_uppercase() {
        assertThat(service.toSentenceCase("KENYA")).isEqualTo("Kenya");
    }

    @Test
    void toSentenceCase_multiWord() {
        assertThat(service.toSentenceCase("new zealand")).isEqualTo("New Zealand");
    }

    @Test
    void toSentenceCase_nullInput() {
        assertThat(service.toSentenceCase(null)).isNull();
    }

    // ── fetchAndSaveCountryInfo tests ──────────────────────────────

    @Test
    void fetchAndSave_newCountry_savesSuccessfully() {
        when(soapClient.getCountryIsoCode("Tanzania")).thenReturn("TZ");
        when(soapClient.getFullCountryInfoXml("TZ")).thenReturn("<xml/>");
        when(parser.parseFullCountryInfo("<xml/>")).thenReturn(sampleCountry);
        when(repository.findByIsoCode("TZ")).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(sampleCountry);

        CountryInfo result = service.fetchAndSaveCountryInfo("tanzania");

        assertThat(result.getIsoCode()).isEqualTo("TZ");
        assertThat(result.getName()).isEqualTo("Tanzania");
        verify(repository).save(sampleCountry);
    }

//    @Test
//    void fetchAndSave_existingCountry_updatesSuccessfully() {
//        when(soapClient.getCountryIsoCode("Tanzania")).thenReturn("TZ");
//        when(soapClient.getFullCountryInfoXml("TZ")).thenReturn("<xml/>");
//        when(parser.parseFullCountryInfo("<xml/>")).thenReturn(sampleCountry);
//        when(repository.findByIsoCode("TZ")).thenReturn(Optional.of(sampleCountry));
//        when(repository.save(any())).thenReturn(sampleCountry);
//
//        CountryInfo result = service.fetchAndSaveCountryInfo("TANZANIA");
//
//        assertThat(result).isNotNull();
//        verify(repository).save(sampleCountry);
//    }

    @Test
    void fetchAndSave_isoCodeNotFound_throwsException() {
        when(soapClient.getCountryIsoCode(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.fetchAndSaveCountryInfo("Unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO code");
    }

    // ── CRUD tests ─────────────────────────────────────────────────

    @Test
    void findById_exists_returnsCountry() {
        when(repository.findByIdWithLanguages(1L)).thenReturn(Optional.of(sampleCountry));
        assertThat(service.findById(1L).getIsoCode()).isEqualTo("TZ");
    }

    @Test
    void findById_notFound_throwsException() {
        when(repository.findByIdWithLanguages(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    @Test
    void delete_exists_deletesSuccessfully() {
        when(repository.existsById(1L)).thenReturn(true);
        service.delete(1L);
        verify(repository).deleteById(1L);
    }

    @Test
    void delete_notFound_throwsException() {
        when(repository.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }
}
