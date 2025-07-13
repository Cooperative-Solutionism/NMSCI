package com.cooperativesolutionism.nmsci.serializer;

import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class IntToHexSerializer extends JsonSerializer<Integer> {

    @Override
    public void serialize(Integer value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(ByteArrayUtil.bytesToHex(ByteArrayUtil.intToBytes(value)));
    }

}
