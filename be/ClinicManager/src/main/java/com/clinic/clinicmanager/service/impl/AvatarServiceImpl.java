package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.AvatarService;
import com.clinic.clinicmanager.utils.AvatarCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AvatarServiceImpl implements AvatarService {

    private final UserRepo userRepo;

    @Override
    public String findFirstFreeAvatar() {
        return AvatarCatalog.firstFree(userRepo.findAllTakenAvatars());
    }

    @Override
    public List<String> getAvatarsTakenByOthers(Long userId) {
        return userRepo.findAvatarsTakenByOthers(userId);
    }
}
