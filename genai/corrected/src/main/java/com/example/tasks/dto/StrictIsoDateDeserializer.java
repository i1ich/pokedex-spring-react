package com.example.tasks.dto;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * Accepts only a plain ISO calendar date (yyyy-MM-dd). Jackson's default LocalDate deserializer also
 * accepts "2025-06-15T23:30:00Z" and drops the time and zone, which silently moves a deadline.
 */
public class StrictIsoDateDeserializer extends JsonDeserializer<LocalDate> {

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String text = parser.getValueAsString();
        if (text == null) {
            return (LocalDate) context.handleUnexpectedToken(LocalDate.class, parser);
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            return (LocalDate) context.handleWeirdStringValue(LocalDate.class, text,
                    "expected an ISO date (yyyy-MM-dd) without time or zone");
        }
    }
}
