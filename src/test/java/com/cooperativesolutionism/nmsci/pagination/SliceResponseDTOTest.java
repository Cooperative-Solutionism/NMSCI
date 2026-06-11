package com.cooperativesolutionism.nmsci.pagination;

import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SliceResponseDTOTest {

    @Test
    void buildsResponseMetadataFromSlice() {
        Slice<String> slice = new SliceImpl<>(List.of("first", "second"), PageRequest.of(2, 50), true);

        SliceResponseDTO<String> response = SliceResponseDTO.from(slice);

        assertEquals(List.of("first", "second"), response.getContent());
        assertEquals(2, response.getPage());
        assertEquals(50, response.getSize());
        assertEquals(2, response.getNumberOfElements());
        assertTrue(response.getHasNext());
        assertTrue(response.getHasPrevious());
    }
}
