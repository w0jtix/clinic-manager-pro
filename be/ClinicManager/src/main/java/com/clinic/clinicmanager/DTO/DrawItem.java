package com.clinic.clinicmanager.DTO;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DrawStrokeItem.class, name = "stroke"),
        @JsonSubTypes.Type(value = DrawTextItem.class, name = "text"),
        @JsonSubTypes.Type(value = DrawMarkerItem.class, name = "marker")
})
public sealed interface DrawItem permits DrawStrokeItem, DrawTextItem, DrawMarkerItem {
}
