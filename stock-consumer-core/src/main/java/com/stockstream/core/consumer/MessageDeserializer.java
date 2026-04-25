package com.stockstream.core.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stockstream.core.exception.DeserializationException;

public final class MessageDeserializer<T> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Class<T> clazz;

    public MessageDeserializer(Class<T> clazz) {
        this.clazz = clazz;
    }

    public T deserialize(byte[] data) {
        try {
            return MAPPER.readValue(data, this.clazz);
        } catch (Exception e) {
            throw new DeserializationException(
                    "Failed to deserialize " + clazz.getSimpleName() + " from " + data.length + " bytes", e);
        }
    }
}
