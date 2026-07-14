package com.community.servercore.staff;

public enum StaffRole {
    OWNER("Owner", "servercore.role.owner"),
    ADMIN("Admin", "servercore.role.admin"),
    DEVELOPER("Developer", "servercore.role.dev"),
    SUPPORT("Support", "servercore.role.support"),
    MODERATOR("Moderator", "servercore.role.mod");

    private final String displayName;
    private final String permission;

    StaffRole(String displayName, String permission) {
        this.displayName = displayName;
        this.permission = permission;
    }

    public String displayName() {
        return displayName;
    }

    public String permission() {
        return permission;
    }
}
