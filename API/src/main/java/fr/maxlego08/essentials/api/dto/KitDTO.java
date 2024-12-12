package fr.maxlego08.essentials.api.dto;

public record KitDTO(
        String displayName,
        String name,
        long cooldown,
        String actions,
        String items
) { }
