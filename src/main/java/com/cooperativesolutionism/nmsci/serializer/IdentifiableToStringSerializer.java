package com.cooperativesolutionism.nmsci.serializer;

import com.cooperativesolutionism.nmsci.model.Identifiable;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class IdentifiableToStringSerializer extends JsonSerializer<Identifiable> {

    @Override
    public void serialize(Identifiable value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(value.getId().toString());
    }

}
