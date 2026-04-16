package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.UserDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import com.clinic.clinicmanager.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(WebSecurityConfig.class)
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    UserService userService;

    @MockBean
    UserDetailsServiceImpl userDetailsService;

    @MockBean
    AuthEntryPointJwt authEntryPointJwt;

    @MockBean
    JwtUtils jwtUtils;

    @MockBean
    TokenBlacklistService tokenBlacklistService;

    private static UserDetailsImpl userWith(Long id, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());
        return new UserDetailsImpl(id, "testuser", "pass", null, null, authorities);
    }

    private UserDTO buildUserDTO(Long id) {
        UserDTO dto = new UserDTO();
        dto.setId(id);
        dto.setUsername("testuser");
        return dto;
    }

    @Test
    void getAllUsers_shouldReturn200_whenAdminAndUsersExist() throws Exception {
        List<UserDTO> users = List.of(buildUserDTO(1L), buildUserDTO(2L));
        when(userService.getAllUsers()).thenReturn(users);

        mockMvc.perform(get("/api/user/all")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAllUsers_shouldReturn204_whenAdminAndNoUsers() throws Exception {
        when(userService.getAllUsers()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/user/all")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void getAllUsers_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/user/all")
                        .with(user(userWith(1L, "USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCurrentUser_shouldReturn200_withOwnData() throws Exception {
        UserDTO userDTO = buildUserDTO(1L);
        when(userService.getUserById(1L)).thenReturn(userDTO);

        mockMvc.perform(get("/api/user/me")
                        .with(user(userWith(1L, "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void getUserById_shouldReturn200_whenAdmin() throws Exception {
        UserDTO userDTO = buildUserDTO(2L);
        when(userService.getUserById(2L)).thenReturn(userDTO);

        mockMvc.perform(get("/api/user/2")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void getUserById_shouldReturn404_whenNotFound() throws Exception {
        when(userService.getUserById(99L))
                .thenThrow(new ResourceNotFoundException("User not found with ID: 99"));

        mockMvc.perform(get("/api/user/99")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserById_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/user/1")
                        .with(user(userWith(1L, "USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserByEmployeeId_shouldReturn200_whenFound() throws Exception {
        UserDTO userDTO = buildUserDTO(1L);
        when(userService.getUserByEmployeeId(5L)).thenReturn(userDTO);

        mockMvc.perform(get("/api/user/employee/5")
                        .with(user(userWith(1L, "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getUserByEmployeeId_shouldReturn404_whenNotFound() throws Exception {
        when(userService.getUserByEmployeeId(99L))
                .thenThrow(new ResourceNotFoundException("User not found with ID: 99"));

        mockMvc.perform(get("/api/user/employee/99")
                        .with(user(userWith(1L,  "USER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateUser_shouldReturn200_whenUserUpdatesOwnProfile() throws Exception {
        UserDTO inputDTO = buildUserDTO(null);
        UserDTO savedDTO = buildUserDTO(1L);
        when(userService.updateUser(eq(1L), any(UserDTO.class))).thenReturn(savedDTO);

        mockMvc.perform(put("/api/user/1")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateUser_shouldReturn403_whenUserUpdatesOtherProfile() throws Exception {
        mockMvc.perform(put("/api/user/2")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUserDTO(null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_shouldReturn200_whenAdminUpdatesAnyProfile() throws Exception {
        UserDTO inputDTO = buildUserDTO(null);
        UserDTO savedDTO = buildUserDTO(2L);
        when(userService.updateUser(eq(2L), any(UserDTO.class))).thenReturn(savedDTO);

        mockMvc.perform(put("/api/user/2")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }
}
