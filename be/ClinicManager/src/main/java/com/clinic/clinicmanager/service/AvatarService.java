package com.clinic.clinicmanager.service;

import java.util.List;

public interface AvatarService {

    String findFirstFreeAvatar();

    List<String> getAvatarsTakenByOthers(Long userId);
}
