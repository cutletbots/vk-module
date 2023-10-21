package ru.blc.cutlet.vk;

import com.google.common.base.Preconditions;

public class AccessToken {

    private final String value;
    private final AccessTokenType type;

    public AccessToken(String value, AccessTokenType type) {
        Preconditions.checkArgument(value != null && !value.isBlank(), "Token value can not be empty or null");
        this.value = value;
        Preconditions.checkNotNull(type, "Token type can not be null");
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public AccessTokenType getType() {
        return type;
    }

    public enum AccessTokenType {
        SERVICE, GROUP, USER
    }
}
