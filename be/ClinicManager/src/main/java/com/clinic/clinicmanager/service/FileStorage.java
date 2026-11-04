package com.clinic.clinicmanager.service;

public interface FileStorage {

    void put(String key, byte[] bytes);

    byte[] get(String key);

    void delete(String key);

    boolean exists(String key);
}
