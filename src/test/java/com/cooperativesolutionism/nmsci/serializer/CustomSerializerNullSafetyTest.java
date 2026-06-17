package com.cooperativesolutionism.nmsci.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class CustomSerializerNullSafetyTest {

    @Test
    void bytesSerializerWritesJsonNullForNullValue() throws IOException {
        JsonGenerator jsonGenerator = mock(JsonGenerator.class);

        new BytesToHexSerializer().serialize(null, jsonGenerator, null);

        verify(jsonGenerator).writeNull();
        verifyNoMoreInteractions(jsonGenerator);
    }

    @Test
    void intSerializerWritesJsonNullForNullValue() throws IOException {
        JsonGenerator jsonGenerator = mock(JsonGenerator.class);

        new IntToHexSerializer().serialize(null, jsonGenerator, null);

        verify(jsonGenerator).writeNull();
        verifyNoMoreInteractions(jsonGenerator);
    }

    @Test
    void identifiableSerializerWritesJsonNullForNullValue() throws IOException {
        JsonGenerator jsonGenerator = mock(JsonGenerator.class);

        new IdentifiableToStringSerializer().serialize(null, jsonGenerator, null);

        verify(jsonGenerator).writeNull();
        verifyNoMoreInteractions(jsonGenerator);
    }
}
