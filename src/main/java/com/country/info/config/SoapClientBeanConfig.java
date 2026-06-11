package com.country.info.config;

import com.country.info.service.CountryInfoSoapClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;

@Configuration
public class SoapClientBeanConfig {

    @Autowired
    private Jaxb2Marshaller marshaller;

    @Bean
    public CountryInfoSoapClient countryInfoSoapClient() {
        CountryInfoSoapClient client = new CountryInfoSoapClient();
        client.setMarshaller(marshaller);
        client.setUnmarshaller(marshaller);
        return client;
    }
}
