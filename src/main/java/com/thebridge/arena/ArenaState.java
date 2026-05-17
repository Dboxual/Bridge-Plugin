package com.thebridge.arena;

public enum ArenaState {
    DISABLED,   // Not available — setup incomplete or manually disabled
    WAITING,    // Accepting players in the lobby
    STARTING,   // Pre-game countdown running
    IN_GAME,    // Match in progress
    RESETTING   // Schematic restore in progress
}
