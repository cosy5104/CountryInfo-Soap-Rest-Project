package com.country.info.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "country_info")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountryInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "iso_code", unique = true, length = 10)
    private String isoCode;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "capital_city", length = 150)
    private String capitalCity;

    @Column(name = "phone_code", length = 20)
    private String phoneCode;

    @Column(name = "continent_code", length = 10)
    private String continentCode;

    @Column(name = "currency_iso_code", length = 10)
    private String currencyIsoCode;

    @Column(name = "country_flag", length = 500)
    private String countryFlag;

    @OneToMany(
        mappedBy = "countryInfo",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @JsonManagedReference
    @Builder.Default
    private List<Language> languages = new ArrayList<>();

    // Helper method to add language and maintain bidirectional relationship
    public void addLanguage(Language language) {
        languages.add(language);
        language.setCountryInfo(this);
    }

    public void removeLanguage(Language language) {
        languages.remove(language);
        language.setCountryInfo(null);
    }
}
