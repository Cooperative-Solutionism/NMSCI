package com.cooperativesolutionism.nmsci.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BooleanDtoJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sliceResponseKeepsBooleanFieldNames() {
        SliceResponseDTO<String> response = new SliceResponseDTO<>();
        response.setContent(List.of("item"));
        response.setHasNext(true);
        response.setHasPrevious(false);

        JsonNode json = objectMapper.valueToTree(response);

        assertTrue(json.has("hasNext"));
        assertTrue(json.get("hasNext").asBoolean());
        assertTrue(json.has("hasPrevious"));
        assertFalse(json.get("hasPrevious").asBoolean());
        assertFalse(json.has("next"));
        assertFalse(json.has("previous"));
    }

    @Test
    void lockedMessageResponseKeepsLockedFieldName() {
        LockedMessageResponseDTO<String> response = new LockedMessageResponseDTO<>(true, "locked-message");

        JsonNode json = objectMapper.valueToTree(response);

        assertTrue(json.has("locked"));
        assertTrue(json.get("locked").asBoolean());
        assertFalse(json.has("isLocked"));
    }

    @Test
    void flowNodeListItemKeepsBooleanFieldNames() {
        FlowNodeListItemDTO response = new FlowNodeListItemDTO(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                new byte[]{1, 2, 3},
                true,
                true,
                false,
                true
        );

        JsonNode json = objectMapper.valueToTree(response);

        assertTrue(json.has("registered"));
        assertTrue(json.get("registered").asBoolean());
        assertTrue(json.has("authorized"));
        assertTrue(json.get("authorized").asBoolean());
        assertTrue(json.has("locked"));
        assertFalse(json.get("locked").asBoolean());
        assertTrue(json.has("currentCentralPubkeyAuthorized"));
        assertTrue(json.get("currentCentralPubkeyAuthorized").asBoolean());
        assertFalse(json.has("isLocked"));
        assertFalse(json.has("isCurrentCentralPubkeyAuthorized"));
    }

    @Test
    void systemStatusKeepsCurrentCentralPubkeyLockedFieldName() {
        SystemStatusDTO response = SystemStatusDTO.from(null, 3L, 7L, 10_000L, true);

        JsonNode json = objectMapper.valueToTree(response);

        assertTrue(json.has("currentCentralPubkeyLocked"));
        assertTrue(json.get("currentCentralPubkeyLocked").asBoolean());
        assertFalse(json.has("isCurrentCentralPubkeyLocked"));
    }
}
