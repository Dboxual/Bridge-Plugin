package com.thebridge.match;

import net.kyori.adventure.text.format.NamedTextColor;

public enum Team {
    RED("Red", "§c", NamedTextColor.RED),
    BLUE("Blue", "§9", NamedTextColor.BLUE);

    public final String display;
    public final String colorCode;
    public final NamedTextColor color;

    Team(String display, String colorCode, NamedTextColor color) {
        this.display = display;
        this.colorCode = colorCode;
        this.color = color;
    }
}
