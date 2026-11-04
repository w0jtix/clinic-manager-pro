package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserAvatarDTO {
    private Long id;
    private String avatar;

    public UserAvatarDTO(User user) {
        this.id = user.getId();
        this.avatar = user.getAvatar();
    }
}
