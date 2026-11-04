package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.RoleDTO;
import com.clinic.clinicmanager.DTO.UserDTO;
import com.clinic.clinicmanager.repo.RoleRepo;
import com.clinic.clinicmanager.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepo roleRepo;

    @Override
    public List<RoleDTO> getAllRoles() {
        return roleRepo.findAll().stream()
                .map(RoleDTO::new)
                .collect(Collectors.toList());
    }
}
