package com.cooperativesolutionism.nmsci.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Slice;

import java.util.List;

public class SliceResponseDTO<T> {

    private List<T> content;
    private int page;
    private int size;
    private int numberOfElements;
    private boolean hasNext;
    private boolean hasPrevious;

    public static <T> SliceResponseDTO<T> from(Slice<T> slice) {
        SliceResponseDTO<T> response = new SliceResponseDTO<>();
        response.setContent(slice.getContent());
        response.setPage(slice.getNumber());
        response.setSize(slice.getSize());
        response.setNumberOfElements(slice.getNumberOfElements());
        response.setHasNext(slice.hasNext());
        response.setHasPrevious(slice.hasPrevious());
        return response;
    }

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getNumberOfElements() {
        return numberOfElements;
    }

    public void setNumberOfElements(int numberOfElements) {
        this.numberOfElements = numberOfElements;
    }

    @JsonProperty("hasNext")
    public boolean getHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }

    @JsonProperty("hasPrevious")
    public boolean getHasPrevious() {
        return hasPrevious;
    }

    public void setHasPrevious(boolean hasPrevious) {
        this.hasPrevious = hasPrevious;
    }
}
