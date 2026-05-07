package org.astropanty.ui.game.entities;

import java.util.ArrayList;
import java.util.List;

import org.astropanty.ui.game.screens.GameProper;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

/**
 * Represents a player's ship in the game.
 * Handles movement, shooting, and collision-related logic.
 */
public class Ship extends Sprite implements Runnable {
    private final String name;                 // Name of the ship (e.g., player identifier)
    private boolean playing;                   // Indicates if the ship is active
    private int health;                        // Health points of the ship
    private final List<Projectile> projectiles; // List of active projectiles fired by the ship
    private final Image bulletImage;           // Image used for the ship's projectiles

    private int defaultMovementSpeed;
    private int defaultBulletDamage;
    private int bulletSpeed;
    private int bulletDamage;
    public int bulletsLeft; // Number of bullets left before a reload is required
    public long lastShot;   // Timestamp of the last shot fired
    public Rectangle2D hitbox; // Ship's hitbox for collision detection

    private int movementSpeed; // Speed of the ship's forward movement
    private final int ROTATION_SPEED = 3; // Speed of the ship's rotation
    private double hitFlashTimer = 0;
    private static final double HIT_FLASH_DURATION = 0.4;

    /**
     * Constructs a new ship with initial position, name, and sprite image.
     * 
     * @param x         Initial x-coordinate of the ship
     * @param y         Initial y-coordinate of the ship
     * @param name      Name of the ship (e.g., "Player1")
     * @param SHIP_IMAGE Image representing the ship
     */
    public Ship(double x, double y, String name, Image SHIP_IMAGE, int speed, int bulletSpeed, int bulletDamage, String bulletImagePath) {
        super(x, y, SHIP_IMAGE);
        this.playing = true; // Set the ship as active
        this.name = name;
        this.health = 100; // Default health value
        this.movementSpeed = speed;

        this.projectiles = new ArrayList<>(); // Initialize projectile list
        this.bulletSpeed = bulletSpeed;
        this.bulletDamage = bulletDamage;
        this.bulletImage = new Image(
                bulletImagePath,
                10, 10, false, false); // Load the projectile image
        this.bulletsLeft = 5; // Initial bullets

        this.lastShot = 0; // Initialize last shot timestamp
        this.hitbox = new Rectangle2D(this.xPos, this.yPos, this.width, this.height); // Initialize hitbox
    }

    /**
     * Fires a projectile from the ship.
     * Creates a new projectile and starts its logic in a separate thread.
     */
    public void shoot() {
        Projectile bullet = new Projectile(
                "Bullet",
                this.xPos + 13, // Offset to position the bullet correctly
                this.yPos + 13,
                this.rotation,
                this.bulletSpeed,
                this.bulletImage);
        projectiles.add(bullet); // Add the bullet to the active projectiles list

        // Start the projectile's logic in a separate thread
        Thread bulletThread = new Thread(bullet);
        bulletThread.start();
    }

    /**
     * Returns the list of active projectiles fired by the ship.
     * 
     * @return List of active projectiles
     */
    public List<Projectile> getBullets() {
        return projectiles;
    }

    /**
     * Gets the name of the ship.
     * 
     * @return Ship name
     */
    public String getShipName() {
        return this.name;
    }

    /**
     * Gets the current health of the ship.
     * 
     * @return Health value
     */
    public int getHealth() {
        return this.health;
    }

    public int getBulletDamage() {
        return this.bulletDamage;
    }

    public void increaseSpeed(int speed){
        this.defaultMovementSpeed = movementSpeed;
        this.movementSpeed += speed;
    }

    public void increaseDamage(int damage){
        this.defaultBulletDamage = bulletDamage;
        this.bulletDamage += damage;
    }

    
    public void increaseHealth(int health){
        if(this.health + health > 100)
            this.health = 100;
        else
            this.health += health;
    }

    public void resetSpeed(){
        this.movementSpeed = defaultMovementSpeed;
    }

    public void resetDamage(){
        this.bulletDamage = defaultBulletDamage;
    }

    public void triggerHitFlash() {
        this.hitFlashTimer = HIT_FLASH_DURATION;
    }

    public void decrementHitFlash(double delta) {
        if (hitFlashTimer > 0) hitFlashTimer = Math.max(0, hitFlashTimer - delta);
    }

    @Override
    public void render(GraphicsContext gc) {
        super.render(gc);
        if (hitFlashTimer > 0) {
            double alpha = (hitFlashTimer / HIT_FLASH_DURATION) * 0.6;
            gc.save();
            gc.translate(xPos + width / 2, yPos + height / 2);
            gc.rotate(rotation);
            gc.translate(-width / 2, -height / 2);
            gc.setGlobalAlpha(alpha);
            gc.setFill(Color.RED);
            gc.fillRect(0, 0, width, height);
            gc.restore();
        }
    }

    /**
     * Reduces the ship's health by the specified damage amount.
     * 
     * @param damage Amount of damage to subtract from the ship's health
     */
    public void minusHealth(int damage) {
        this.health -= damage;
    }

    /**
     * Stops the ship, marking it as inactive.
     */
    public void stop() {
        this.playing = false;
    }

    /**
     * Rotates the ship to the right based on the defined rotation speed.
     */
    public void rotateRight() {
        this.rotation = (this.rotation + ROTATION_SPEED) % 360;
    }

    /**
     * Rotates the ship to the left based on the defined rotation speed.
     */
    public void rotateLeft() {
        this.rotation = (this.rotation - ROTATION_SPEED + 360) % 360;
    }

    /**
     * Moves the ship forward in the direction of its rotation.
     * Uses trigonometric functions to calculate the new position.
     */
    public void forward() {
        if (playing){
            this.setXPos(this.getXPos() + Math.sin(Math.toRadians(this.getRotation())) * movementSpeed);
            this.setYPos(this.getYPos() - Math.cos(Math.toRadians(this.getRotation())) * movementSpeed);
        }
    }

    /**
     * Starts the ship's logic in a separate thread.
     */
    @Override
    public void run() {
        this.race();
    }

    /**
     * Handles the ship's continuous logic, such as hitbox updates and screen wrapping.
     * Ensures the ship remains within the game window by wrapping its position.
     */
    private void race() {
        while (playing) {
            // Update the hitbox position to match the current coordinates
            this.hitbox = new Rectangle2D(this.xPos, this.yPos, this.width, this.height);

            if(this.health == 0)
                stop();

            // Implement screen wrapping logic
            if (this.xPos > GameProper.WINDOW_WIDTH) {
                this.setXPos(-50); // Wrap to the left
            } else if (this.xPos < -50) {
                this.setXPos(GameProper.WINDOW_WIDTH); // Wrap to the right
            } else if (this.yPos > GameProper.WINDOW_HEIGHT) {
                this.setYPos(-50); // Wrap to the top
            } else if (this.yPos < -50) {
                this.setYPos(GameProper.WINDOW_HEIGHT); // Wrap to the bottom
            }
        }
    }
}
