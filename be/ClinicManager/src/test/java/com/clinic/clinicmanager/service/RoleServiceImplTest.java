package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.RoleDTO;
import com.clinic.clinicmanager.model.Role;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.RoleRepo;
import com.clinic.clinicmanager.service.impl.RoleServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock RoleRepo roleRepo;

    @InjectMocks
    RoleServiceImpl roleService;

    @Test
    void getAllRoles_shouldReturnMappedList() {
        Role user = Role.builder().id(1L).name(RoleType.ROLE_USER).build();
        Role admin = Role.builder().id(2L).name(RoleType.ROLE_ADMIN).build();
        when(roleRepo.findAll()).thenReturn(List.of(user, admin));

        List<RoleDTO> result = roleService.getAllRoles();

        assertEquals(2, result.size());
        assertEquals(RoleType.ROLE_USER, result.get(0).getName());
        assertEquals(RoleType.ROLE_ADMIN, result.get(1).getName());
    }

    @Test
    void getAllRoles_shouldReturnEmptyList_whenNoRoles() {
        when(roleRepo.findAll()).thenReturn(List.of());

        List<RoleDTO> result = roleService.getAllRoles();

        assertTrue(result.isEmpty());
    }
}
