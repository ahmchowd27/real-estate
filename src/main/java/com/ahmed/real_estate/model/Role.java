

package com.ahmed.real_estate.model;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {
    User, Agent;

    @JsonCreator
    public static Role fromString(String value) {
        for (Role role : Role.values()) {
            if (role.name().equalsIgnoreCase(value)) { // Case insensitive comparison
                return role;
            }
        }
        throw new IllegalArgumentException("Invalid role: " + value);
    }
}

