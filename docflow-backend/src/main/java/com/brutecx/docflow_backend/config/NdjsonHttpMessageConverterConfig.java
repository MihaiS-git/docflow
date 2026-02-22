package com.brutecx.docflow_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class NdjsonHttpMessageConverterConfig implements WebMvcConfigurer {

    private static final MediaType NDJSON =
            MediaType.parseMediaType("application/x-ndjson");

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                List<MediaType> types = new ArrayList<>(jackson.getSupportedMediaTypes());
                if (!types.contains(NDJSON)) {
                    types.add(NDJSON);
                    jackson.setSupportedMediaTypes(types);
                }
            }
        }
    }
}