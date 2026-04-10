package com.stockstream.core.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stockstream.core.exception.DeserializationException;
import com.stockstream.core.model.Tick;

public final class TickDeserializer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private TickDeserializer() {}

    public static Tick deserialize(byte[] data) {
        try {
            return MAPPER.readValue(data, Tick.class);
        } catch (Exception e) {
            throw new DeserializationException(
                    "Failed to deserialize tick from " + data.length + " bytes", e);
        }
    }
}
