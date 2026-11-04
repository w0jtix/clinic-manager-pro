package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.impl.AvatarServiceImpl;
import com.clinic.clinicmanager.utils.AvatarCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvatarServiceImplTest {

    @Mock UserRepo userRepo;

    @InjectMocks
    AvatarServiceImpl avatarService;

    @Test
    void findFirstFreeAvatar_shouldReturnLowestFreeNumber_whenGapInTakenAvatars() {
        when(userRepo.findAllTakenAvatars()).thenReturn(List.of("avatar1.png", "avatar2.png", "avatar4.png"));

        assertEquals("avatar3.png", avatarService.findFirstFreeAvatar());
    }

    @Test
    void findFirstFreeAvatar_shouldReturnAvatar1_whenNothingTaken() {
        when(userRepo.findAllTakenAvatars()).thenReturn(List.of());

        assertEquals("avatar1.png", avatarService.findFirstFreeAvatar());
    }

    @Test
    void findFirstFreeAvatar_shouldCompareNumbersNotText_whenAvatar1To9Taken() {
        List<String> taken = new ArrayList<>();
        for (int number = 1; number <= 9; number++) {
            taken.add("avatar" + number + ".png");
        }
        when(userRepo.findAllTakenAvatars()).thenReturn(taken);

        assertEquals("avatar10.png", avatarService.findFirstFreeAvatar());
    }

    @Test
    void findFirstFreeAvatar_shouldIgnoreValuesOutsideCatalog() {
        when(userRepo.findAllTakenAvatars()).thenReturn(List.of("avatar1.png", "legacy.png", "avatar21.png"));

        assertEquals("avatar2.png", avatarService.findFirstFreeAvatar());
    }

    @Test
    void findFirstFreeAvatar_shouldReturnNull_whenAllAvatarsTaken() {
        when(userRepo.findAllTakenAvatars()).thenReturn(AvatarCatalog.AVATARS);

        assertNull(avatarService.findFirstFreeAvatar());
    }

    @Test
    void getAvatarsTakenByOthers_shouldReturnRepoResultForGivenUser() {
        when(userRepo.findAvatarsTakenByOthers(3L)).thenReturn(List.of("avatar2.png", "avatar7.png"));

        assertEquals(List.of("avatar2.png", "avatar7.png"), avatarService.getAvatarsTakenByOthers(3L));
    }

    @Test
    void catalog_shouldContainExactlyAvatar1ToAvatar20InOrder() {
        assertEquals(20, AvatarCatalog.AVATARS.size());
        assertEquals("avatar1.png", AvatarCatalog.AVATARS.getFirst());
        assertEquals("avatar20.png", AvatarCatalog.AVATARS.getLast());
        assertTrue(AvatarCatalog.isValid("avatar15.png"));
        assertFalse(AvatarCatalog.isValid("avatar21.png"));
        assertFalse(AvatarCatalog.isValid("../avatar1.png"));
        assertFalse(AvatarCatalog.isValid(null));
    }
}
