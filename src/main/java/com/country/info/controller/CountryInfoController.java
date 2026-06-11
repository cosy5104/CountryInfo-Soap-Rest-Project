package com.country.info.controller;

import com.country.info.controller.CountryDtos.*;
import com.country.info.model.CountryInfo;
import com.country.info.model.Language;
import com.country.info.service.CountryInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API for Country Info operations.
 *
 * POST   /api/countries          – fetch from SOAP and persist
 * GET    /api/countries          – list all
 * GET    /api/countries/{id}     – get by ID
 * PUT    /api/countries/{id}     – update
 * DELETE /api/countries/{id}     – delete
 */
@RestController
@RequestMapping("/api/countries")
@RequiredArgsConstructor
@Slf4j
public class CountryInfoController {

    private final CountryInfoService countryInfoService;

    // ---------------------------------------------------------------
    // POST /api/countries  –  receive name, call SOAP, persist
    // ---------------------------------------------------------------
    @PostMapping
    public ResponseEntity<ApiResponse<CountryInfo>> fetchAndSave(
            @Valid @RequestBody CountryNameRequest request) {

        log.info("Received POST /api/countries with name='{}'", request.getName());
        CountryInfo saved = countryInfoService.fetchAndSaveCountryInfo(request.getName());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Country info fetched and saved successfully", saved));
    }

    // ---------------------------------------------------------------
    // GET /api/countries  –  fetch all
    // ---------------------------------------------------------------
    @GetMapping
    public ResponseEntity<ApiResponse<List<CountryInfo>>> getAll() {
        List<CountryInfo> countries = countryInfoService.findAll();
        return ResponseEntity.ok(ApiResponse.ok(countries));
    }

    // ---------------------------------------------------------------
    // GET /api/countries/{id}  –  fetch by ID
    // ---------------------------------------------------------------
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CountryInfo>> getById(@PathVariable Long id) {
        CountryInfo country = countryInfoService.findById(id);
        return ResponseEntity.ok(ApiResponse.ok(country));
    }

    // ---------------------------------------------------------------
    // PUT /api/countries/{id}  –  update
    // ---------------------------------------------------------------
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CountryInfo>> update(
            @PathVariable Long id,
            @RequestBody CountryUpdateRequest request) {

        CountryInfo patch = mapUpdateRequestToEntity(request);
        CountryInfo updated = countryInfoService.update(id, patch);
        return ResponseEntity.ok(ApiResponse.ok("Country info updated successfully", updated));
    }

    // ---------------------------------------------------------------
    // DELETE /api/countries/{id}  –  delete
    // ---------------------------------------------------------------
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        countryInfoService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Country info deleted successfully", null));
    }

    // ---------------------------------------------------------------
    // Exception handlers
    // ---------------------------------------------------------------
    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            jakarta.persistence.EntityNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred: " + ex.getMessage()));
    }

    // ---------------------------------------------------------------
    // Private mapper
    // ---------------------------------------------------------------
    private CountryInfo mapUpdateRequestToEntity(CountryUpdateRequest req) {
        CountryInfo c = new CountryInfo();
        c.setName(req.getName());
        c.setIsoCode(req.getIsoCode());
        c.setCapitalCity(req.getCapitalCity());
        c.setPhoneCode(req.getPhoneCode());
        c.setContinentCode(req.getContinentCode());
        c.setCurrencyIsoCode(req.getCurrencyIsoCode());
        c.setCountryFlag(req.getCountryFlag());

        if (req.getLanguages() != null) {
            List<Language> langs = req.getLanguages().stream().map(dto -> {
                Language l = new Language();
                l.setIsoCode(dto.getIsoCode());
                l.setName(dto.getName());
                return l;
            }).collect(Collectors.toList());
            c.setLanguages(langs);
        }

        return c;
    }
}
