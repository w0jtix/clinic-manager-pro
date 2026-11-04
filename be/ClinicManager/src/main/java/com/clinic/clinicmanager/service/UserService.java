package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.UserAssignmentDTO;
import com.clinic.clinicmanager.DTO.UserAvatarDTO;
import com.clinic.clinicmanager.DTO.UserDTO;
import com.clinic.clinicmanager.DTO.UserPreferencesDTO;

import java.util.List;

public interface UserService {

    List<UserDTO> getAllUsers();

    List<UserAssignmentDTO> getAllUserAssignments();

    UserDTO getUserById(Long id);

    UserAssignmentDTO getUserAssignmentById(Long id);

    UserAvatarDTO getUserAvatarByEmployeeId(Long employeeId);

    UserDTO updateUser(Long id, UserDTO user);

    UserPreferencesDTO getMyPreferences(Long userId);

    UserPreferencesDTO updateMyPreferences(Long userId, UserPreferencesDTO preferences);

}
