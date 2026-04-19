package org.astropanty.net;

import java.io.Serializable;

public class InputPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    public int playerId;
    public int shipType;
    public boolean forward;
    public boolean left;
    public boolean right;
    public boolean shoot;

    public InputPayload(int playerId, int shipType, boolean forward, boolean left, boolean right, boolean shoot) {
        this.playerId = playerId;
        this.shipType = shipType;
        this.forward = forward;
        this.left = left;
        this.right = right;
        this.shoot = shoot;
    }
}
