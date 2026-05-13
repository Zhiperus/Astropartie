package org.astropanty.net;

import java.io.Serializable;

public class GameStatePayload implements Serializable {
    private static final long serialVersionUID = 1L;

    public double[] shipXs = new double[4];
    public double[] shipYs = new double[4];
    public double[] rotations = new double[4];
    public int[] healths = new int[4];
    public int[] bulletCounts = new int[4];
    public int[] shipTypes = new int[4];
    public boolean[] connected = new boolean[4];

    public double[] projXs = new double[20];
    public double[] projYs = new double[20];
    public boolean[] projActive = new boolean[20];

    // Chat and identity
    public String[] playerNames = new String[4];
    public String[] recentChats = new String[5];

    // Game state machine
    // 0=WAITING_LOBBY, 1=COUNTDOWN, 2=PLAYING, 3=GAME_OVER
    public int gamePhase = 0;
    public long phaseTimeRemaining = 0; // millis remaining in current phase
    public int winnerId = -1;           // -1 = no winner / draw

    public GameStatePayload() {
        // Initialize to safe defaults
        for (int i = 0; i < 4; i++) {
            healths[i] = 100;
            bulletCounts[i] = 5;
        }
    }
}
