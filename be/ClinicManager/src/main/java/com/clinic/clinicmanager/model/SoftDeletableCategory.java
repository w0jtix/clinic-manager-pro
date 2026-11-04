package com.clinic.clinicmanager.model;

public interface SoftDeletableCategory {

    Long getId();

    String getName();

    void setName(String name);

    String getColor();

    void setColor(String color);

    Boolean getIsDeleted();

    void softDelete();

    void restore();
}
