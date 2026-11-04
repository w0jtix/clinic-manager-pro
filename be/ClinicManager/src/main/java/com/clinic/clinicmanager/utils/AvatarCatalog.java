package com.clinic.clinicmanager.utils;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@UtilityClass
public class AvatarCatalog {

    public static final int AVATAR_COUNT = 20;
    public static final String AVATAR_INVALID = "Nieprawidłowy avatar.";
    public static final String AVATAR_TAKEN = "Ten avatar jest już zajęty.";
    public static final String UNIQUE_INDEX = "uq_users_avatar";

    public static final List<String> AVATARS = IntStream.rangeClosed(1, AVATAR_COUNT)
            .mapToObj(number -> "avatar" + number + ".png")
            .toList();

    public static boolean isValid(String avatar) {
        return avatar != null && AVATARS.contains(avatar);
    }

    public static String firstFree(Collection<String> taken) {
        Set<String> takenSet = new HashSet<>(taken);
        return AVATARS.stream()
                .filter(avatar -> !takenSet.contains(avatar))
                .findFirst()
                .orElse(null);
    }
}
