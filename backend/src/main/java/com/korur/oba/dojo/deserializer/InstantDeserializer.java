package com.korur.oba.dojo.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;

public class InstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        if (value != null && !value.isEmpty()) {
            long epochNanos = Long.parseLong(value);
            return Instant.ofEpochSecond(epochNanos / 1_000_000_000, epochNanos % 1_000_000_000);
        }
        return null;
    }
}