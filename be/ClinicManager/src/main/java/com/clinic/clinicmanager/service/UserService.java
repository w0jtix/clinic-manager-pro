package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.UserDTO;

import java.util.List;

public interface UserService {

    List<UserDTO> getAllUsers();

    UserDTO getUserById(Long id);

    UserDTO getUserByEmployeeId(Long employeeId);

    UserDTO updateUser(Long id, UserDTO user);

}
