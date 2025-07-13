package com.cooperativesolutionism.nmsci.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class HexSerializer extends JsonSerializer<byte[]> {

    @Override
    public void serialize(byte[] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (byte b : value) {
            sb.append(String.format("%02x", b));
        }
        gen.writeString(sb.toString());
    }

}
