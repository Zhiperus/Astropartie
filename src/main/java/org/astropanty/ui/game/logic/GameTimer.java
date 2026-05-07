package org.astropanty.ui.game.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.astropanty.ui.game.entities.PowerUp;
import org.astropanty.ui.game.entities.Projectile;
import org.astropanty.ui.game.entities.Ship;
import org.astropanty.ui.game.entities.Wall;
import org.astropanty.ui.game.screens.GameProper;
import org.astropanty.ui.game.screens.WinningScreen;
import org.astropanty.ui.navigation.ScreenController;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;

/**
 * GameTimer class is responsible for managing the game loop.
 * It handles animations, player controls, and in-game events like shooting and collisions.
 */
public class GameTimer extends AnimationTimer {
    private final GraphicsContext gc; // Graphics context for rendering game objects
    private final Scene scene;       // The game scene
    private final Ship player1Ship;  // Player 1's ship
    private final Ship player2Ship;  // Player 2's ship
    private final Thread player1Thread; // Thread for player 1's ship logic
    private final Thread player2Thread; // Thread for player 2's ship logic
    private final List<Wall> walls; // list of wall entities for selected map
    private final List<PowerUp> powerUps = new ArrayList<>();
    private final Map<Ship, List<ActivePowerUp>> activePowerUps = new HashMap<>();
    private final List<double[]> hitmarks = new ArrayList<>(); // [x, y, timeAlive]

    private boolean gameOver = false;
    private boolean explosionActive = false;
    private double explosionTimer = 0;
    private static final double EXPLOSION_DURATION = 1.8;
    private Ship losingShip;
    private Ship winningShip;
    private String pendingWinner;
    private double explosionCX, explosionCY;
    private final List<double[]> explosionParticles = new ArrayList<>();
    private final Set<KeyCode> activeKeys = new HashSet<>(); // Tracks currently pressed keys
    private final long startTime;
    private long lastNanoTime;

    private final ScreenController screenController;
    private final Runnable navigateToMenu;

    /**
     * Constructor for initializing the GameTimer with necessary dependencies.
     */
    public GameTimer(GraphicsContext gc, Scene scene, Ship player1Ship, Ship player2Ship, List<Wall> walls, Runnable navigateToMenu, ScreenController screenController) {
        this.startTime = System.nanoTime(); // Store the start time in nanoseconds
        this.gc = gc;
        this.scene = scene;
        this.player1Ship = player1Ship;
        this.player2Ship = player2Ship;
        this.walls = walls;

        // Threads for concurrent ship logic (e.g., AI or complex calculations)
        this.player1Thread = new Thread(player1Ship);
        this.player2Thread = new Thread(player2Ship);

        // Initialize key press and release event handlers
        this.setupKeyHandlers();

        this.screenController = screenController;
        this.navigateToMenu = navigateToMenu;
    }

    /**
     * Check for collisions between ships and walls.
     *
     * @param ship The ship to check.
     */
    private void checkShipCollision(Ship ship) {
        for (Wall wall : walls) {
            Rectangle2D shipBounds = new Rectangle2D(ship.getXPos(), ship.getYPos(), ship.getWidth(), ship.getHeight());
            if (wall.checkCollision(shipBounds)) {
                System.out.println(ship.getShipName() + " collided with " + wall.getName());
                adjustShipPosition(ship, wall);
            }
        }
    }

    /**
     * Adjusts the ship's position to prevent it from moving into the wall.
     *
     * @param ship The ship to adjust.
     * @param wall The wall it collided with.
     */
    private void adjustShipPosition(Ship ship, Wall wall) {
        Rectangle2D shipBounds = new Rectangle2D(ship.getXPos(), ship.getYPos(), ship.getWidth(), ship.getHeight());
        Rectangle2D wallBounds = wall.getBounds();
        if (shipBounds.intersects(wallBounds)) {
            if (shipBounds.getMinX() < wallBounds.getMinX()) {
                ship.setXPos(wallBounds.getMinX() - ship.getWidth());
            } else if (shipBounds.getMaxX() > wallBounds.getMaxX()) {
                ship.setXPos(wallBounds.getMaxX());
            }
            
            if (shipBounds.getMinY() < wallBounds.getMinY()) {
                ship.setYPos(wallBounds.getMinY() - ship.getHeight());
            } else if (shipBounds.getMaxY() > wallBounds.getMaxY()) {
                ship.setYPos(wallBounds.getMaxY());
            }
        }
    }

    /**
     * Check for collisions between projectiles and walls.
     */
    private void checkProjectileCollsions(Ship shooter){
        Iterator<Projectile> iterator = shooter.getBullets().iterator();
        while (iterator.hasNext()){
            Projectile projectile = iterator.next();
            for (Wall wall: walls){
                if (wall.checkCollision(projectile.hitbox)){
                    System.out.println("Projectile hit " + wall.getName());
                    projectile.stop();
                    iterator.remove();
                    break;
                }
            }
        }
    }

    private void checkWallCollsions(){
        Iterator<PowerUp> iterator = powerUps.iterator();
        while (iterator.hasNext()){
            PowerUp powerUp = iterator.next();
            for (Wall wall: walls){
                if (wall.checkCollision(powerUp.getHitbox())){
                    iterator.remove();
                    break;
                }
            }
        }
    }

    /**
     * Render all walls.
     */
    private void renderWalls(){
        for (Wall wall : walls){
            wall.render(gc);
        }
    }

    private void spawnPowerUp() {
        if (Math.random() < 0.005) { // 0.5% chance per frame
             // Define buffer distance from edges (e.g., 50 pixels)
            int buffer = 50;
            
            // Randomize the spawn position, making sure it's not too close to the borders
            double x = buffer + Math.random() * (GameProper.WINDOW_WIDTH - 2 * buffer); // Ensure x is within the inner area
            double y = buffer + Math.random() * (GameProper.WINDOW_HEIGHT - 2 * buffer); // Ensure y is within the inner area

    
            PowerUp.PowerUpType[] types = PowerUp.PowerUpType.values();
            PowerUp.PowerUpType randomType = types[(int) (Math.random() * types.length)];
    
            Image icon;
            switch (randomType) {
                case SPEED:
                    icon = new Image(getClass().getResource("/org/astropanty/speed_icon.png").toExternalForm(),50, 50, false ,false);
                    break;
                case DAMAGE:
                    icon = new Image(getClass().getResource("/org/astropanty/damage_icon.png").toExternalForm(),50, 50, false ,false);
                    break;
                case HEALTH:
                    icon = new Image(getClass().getResource("/org/astropanty/health_icon.png").toExternalForm(),50, 50, false ,false);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + randomType);
            }
    
            PowerUp powerUp = new PowerUp(randomType, x, y, icon); // 10 seconds duration
            powerUps.add(powerUp);
        }
    }

    private void checkPowerUpCollisions(Ship ship) {
        Iterator<PowerUp> iterator = powerUps.iterator();
        while (iterator.hasNext()) {
            PowerUp powerUp = iterator.next();
            powerUp.render(gc);

            if (powerUp.isActive() && ship.hitbox.intersects(powerUp.getHitbox())) { // Match the size used in `draw()`
                applyPowerUp(ship, powerUp);
                iterator.remove(); // Remove collected power-up
            }
        }
    }

    private void applyPowerUp(Ship ship, PowerUp powerUp) {
        powerUp.applyTo(ship); // Apply the immediate effect
        activePowerUps.computeIfAbsent(ship, k -> new ArrayList<>())
                      .add(new ActivePowerUp(powerUp.getType(), 3)); // 3 seconds duration
    }

    private void updatePowerUps(double deltaTime) {
        Iterator<PowerUp> iterator = powerUps.iterator();
        while (iterator.hasNext()) {
            PowerUp powerUp = iterator.next();
            powerUp.update(deltaTime); // Update the power-up's remaining time
    
            // Remove the power-up if it is no longer active
            if (!powerUp.isActive()) {
                iterator.remove();
            }
        }
    }

    private void updateActivePowerUps(double deltaTime) {
        Iterator<Map.Entry<Ship, List<ActivePowerUp>>> shipIterator = activePowerUps.entrySet().iterator();
    
        while (shipIterator.hasNext()) {
            Map.Entry<Ship, List<ActivePowerUp>> entry = shipIterator.next();
            Ship ship = entry.getKey();
            List<ActivePowerUp> powerUps = entry.getValue();
    
            Iterator<ActivePowerUp> powerUpIterator = powerUps.iterator();
            while (powerUpIterator.hasNext()) {
                ActivePowerUp activePowerUp = powerUpIterator.next();
                activePowerUp.reduceTime(deltaTime);
    
                // If the power-up duration is over, reset the ship's stats
                if (activePowerUp.getRemainingTime() <= 0) {
                    resetPowerUpEffect(ship, activePowerUp.getType());
                    powerUpIterator.remove();
                }
            }
    
            // Remove ship entry if no active power-ups remain
            if (powerUps.isEmpty()) {
                shipIterator.remove();
            }
        }
    }

    private void resetPowerUpEffect(Ship ship, PowerUp.PowerUpType type) {
        switch (type) {
            case SPEED:
                ship.resetSpeed();
                break;
            case DAMAGE:
                ship.resetDamage();
                break;
            case HEALTH:
                // No reset needed; health boosts are permanent 
                break;
        }
    }
    
    /**
     * Starts the race by launching threads for player ship logic.
     */
    public void startRace() {
        this.player1Thread.start();
        this.player2Thread.start();
    }

    /**
     * Configures key press and key release event listeners.
     */
    private void setupKeyHandlers() {
        scene.setOnKeyPressed(e -> activeKeys.add(e.getCode())); // Track pressed keys
        scene.setOnKeyReleased(e -> activeKeys.remove(e.getCode())); // Remove released keys
    }

    /**
     * Handles ship movement based on specified controls.
     */
    private void moveShip(Ship ship, KeyCode forward, KeyCode left, KeyCode right) {
        if (activeKeys.contains(forward)) {
            ship.forward(); // Move forward
        }
        if (activeKeys.contains(right)) {
            ship.rotateRight(); // Rotate right
        }
        if (activeKeys.contains(left)) {
            ship.rotateLeft(); // Rotate left
        }
    }

    /**
     * Handles shooting logic for a ship, including cooldowns and collisions.
     */
    private void handleShooting(Ship shooter, Ship target, KeyCode shootKey) {
        long currentTime = System.nanoTime();
        final long COOLDOWN_PERIOD = 100_000_000L;  // Cooldown between shots (100ms)
        final long RELOAD_PERIOD = 3_000_000_000L; // Reload time (3 seconds)

        List<Projectile> projectiles = shooter.getBullets();
        int bulletsLeft = shooter.bulletsLeft;
        long lastShootTime = shooter.lastShot;

        // Handle reload logic
        if (bulletsLeft == 0 && currentTime - lastShootTime >= RELOAD_PERIOD) {
            shooter.bulletsLeft = 5; // Reset bullet count after reload
        }

        // Handle shooting logic
        if (activeKeys.contains(shootKey) && currentTime - lastShootTime >= COOLDOWN_PERIOD && bulletsLeft > 0) {
            shooter.shoot(); // Fire a projectile
            shooter.bulletsLeft--; // Decrement bullet count
            shooter.lastShot = currentTime; // Update last shot time
        }

        // Render and check for projectile collisions
        Iterator<Projectile> iterator = projectiles.iterator();
        while (iterator.hasNext()) {
            Projectile projectile = iterator.next();
            projectile.render(gc);

            // Check for collision with the target ship
            if (projectile.hitbox.intersects(target.hitbox)) {
                System.out.println("Hit " + target.getShipName() + " by " + projectile.getName());
                target.minusHealth(shooter.getBulletDamage());
                target.triggerHitFlash();
                hitmarks.add(new double[]{projectile.getXPos() + 5, projectile.getYPos() + 5, 0.0});
                projectile.stop();
            }

            // Remove projectile if it is no longer active
            if (!projectile.isPlaying()) {
                iterator.remove();
            }
        }
    }

    public void checkWinner() {
        if (gameOver) return;
        if (!this.player1Thread.isAlive() || !this.player2Thread.isAlive() || (System.nanoTime() - this.startTime) / 1_000_000_000 > 119) {
            gameOver = true;
            player1Ship.stop();
            player2Ship.stop();
            player1Thread.interrupt();
            player2Thread.interrupt();

            if (player1Ship.getHealth() > player2Ship.getHealth()) {
                triggerExplosion(player2Ship, player1Ship, player1Ship.getShipName());
            } else if (player2Ship.getHealth() > player1Ship.getHealth()) {
                triggerExplosion(player1Ship, player2Ship, player2Ship.getShipName());
            } else {
                navigateToWinnerScreen("Draw!");
            }
        }
    }

    private void triggerExplosion(Ship loser, Ship winner, String winnerName) {
        this.losingShip = loser;
        this.winningShip = winner;
        this.pendingWinner = winnerName;
        this.explosionTimer = 0;
        this.explosionCX = loser.getXPos() + loser.getWidth() / 2;
        this.explosionCY = loser.getYPos() + loser.getHeight() / 2;
        this.explosionActive = true;

        for (int i = 0; i < 30; i++) {
            double angle = Math.random() * 360;
            double speed = 40 + Math.random() * 140;
            double size = 3 + Math.random() * 9;
            double vx = Math.sin(Math.toRadians(angle)) * speed;
            double vy = -Math.cos(Math.toRadians(angle)) * speed;
            explosionParticles.add(new double[]{explosionCX, explosionCY, vx, vy, size, 1.0});
        }
    }

    private void renderExplosion(double deltaTime) {
        explosionTimer += deltaTime;
        double t = explosionTimer;

        if (winningShip != null) winningShip.render(gc);

        // Losing ship flickers for the first 0.35s then vanishes
        if (t < 0.35 && (int)(t / 0.055) % 2 == 0) {
            losingShip.render(gc);
        }

        // Three staggered shockwave rings
        renderShockwave(t, 0.0, 75);
        renderShockwave(t, 0.13, 58);
        renderShockwave(t, 0.27, 44);

        // Particles
        for (double[] p : explosionParticles) {
            p[0] += p[2] * deltaTime;
            p[1] += p[3] * deltaTime;
            p[5] = Math.max(0, p[5] - deltaTime * (t < 1.0 ? 0.35 : 1.4));
            if (p[5] > 0) {
                gc.save();
                gc.setFill(Color.color(1.0, Math.max(0, 0.55 - t * 0.3), 0.0, p[5]));
                gc.fillOval(p[0] - p[4] / 2, p[1] - p[4] / 2, p[4], p[4]);
                gc.restore();
            }
        }

        // Winner text fades in after 0.6s
        if (t > 0.6) {
            double alpha = Math.min(1.0, (t - 0.6) / 0.45);
            String text = pendingWinner + " Wins!";
            gc.save();
            gc.setGlobalAlpha(alpha);
            gc.setFont(Font.font("Orbitron", 40));
            gc.setFill(Color.GOLD);
            gc.fillText(text, GameProper.WINDOW_WIDTH / 2.0 - text.length() * 10, GameProper.WINDOW_HEIGHT / 2.0);
            gc.restore();
        }

        if (t >= EXPLOSION_DURATION) {
            navigateToWinnerScreen(pendingWinner);
        }
    }

    private void renderShockwave(double t, double delay, double maxRadius) {
        double age = t - delay;
        if (age <= 0) return;
        double duration = 0.55;
        if (age > duration) return;
        double progress = age / duration;
        double radius = progress * maxRadius;
        double alpha = 1.0 - progress;
        gc.save();
        gc.setStroke(Color.color(1.0, Math.max(0, 0.65 - progress * 0.5), 0.0, alpha));
        gc.setLineWidth(3.5 * (1.0 - progress * 0.6) + 1);
        gc.strokeOval(explosionCX - radius, explosionCY - radius, radius * 2, radius * 2);
        if (progress < 0.25) {
            gc.setFill(Color.color(1.0, 1.0, 0.8, (1.0 - progress / 0.25) * 0.45));
            gc.fillOval(explosionCX - radius * 0.7, explosionCY - radius * 0.7, radius * 1.4, radius * 1.4);
        }
        gc.restore();
    }

    private void navigateToWinnerScreen(String winner) {
        this.stop();
        Platform.runLater(() -> {
            if (!winner.equals("Draw!"))
                screenController.navigate(new WinningScreen(scene, navigateToMenu, winner,
                    player1Ship.getShipName().equals(winner) ? player1Ship.getImage() : player2Ship.getImage(), null));
            else
                screenController.navigate(new WinningScreen(scene, navigateToMenu, winner, player1Ship.getImage(), player2Ship.getImage()));
        });
    }

    private void renderHitmarks(double deltaTime) {
        Iterator<double[]> it = hitmarks.iterator();
        while (it.hasNext()) {
            double[] hm = it.next();
            hm[2] += deltaTime;
            double duration = 0.35;
            if (hm[2] >= duration) {
                it.remove();
                continue;
            }
            double progress = hm[2] / duration;
            double radius = 6 + progress * 22;
            double alpha = 1.0 - progress;
            gc.save();
            gc.setStroke(Color.color(1.0, 0.85, 0.1, alpha));
            gc.setLineWidth(2.5);
            gc.strokeOval(hm[0] - radius, hm[1] - radius, radius * 2, radius * 2);
            double tick = 5 * (1.0 - progress);
            gc.setStroke(Color.color(1.0, 1.0, 1.0, alpha));
            gc.setLineWidth(1.5);
            gc.strokeLine(hm[0] - tick, hm[1], hm[0] + tick, hm[1]);
            gc.strokeLine(hm[0], hm[1] - tick, hm[0], hm[1] + tick);
            gc.restore();
        }
    }

    private void renderBulletUI(Ship shooter, double startX, double y) {
        final long RELOAD_PERIOD = 3_000_000_000L;
        final double ICON_W = 7;
        final double ICON_H = 15;
        final double GAP = 5;
        int bulletsLeft = shooter.bulletsLeft;

        if (bulletsLeft == 0) {
            long elapsed = System.nanoTime() - shooter.lastShot;
            double progress = Math.min(1.0, (double) elapsed / RELOAD_PERIOD);
            double cx = startX + (5 * ICON_W + 4 * GAP) / 2;
            double cy = y + ICON_H / 2;
            double r = 12;
            gc.save();
            gc.setStroke(Color.color(0.25, 0.25, 0.25, 0.9));
            gc.setLineWidth(3.5);
            gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
            gc.setStroke(Color.color(0.95, 0.75, 0.15, 1.0));
            gc.setLineWidth(3.5);
            gc.strokeArc(cx - r, cy - r, r * 2, r * 2, 90, -progress * 360, ArcType.OPEN);
            gc.restore();
        } else {
            for (int i = 0; i < 5; i++) {
                double x = startX + i * (ICON_W + GAP);
                gc.save();
                if (i < bulletsLeft) {
                    gc.setFill(Color.color(0.95, 0.9, 0.35, 1.0));
                    gc.setStroke(Color.color(1.0, 1.0, 0.6, 0.6));
                    gc.setLineWidth(1);
                } else {
                    gc.setFill(Color.color(0.25, 0.25, 0.25, 0.7));
                    gc.setStroke(Color.color(0.4, 0.4, 0.4, 0.4));
                    gc.setLineWidth(1);
                }
                gc.fillRoundRect(x, y, ICON_W, ICON_H, 3, 3);
                gc.strokeRoundRect(x, y, ICON_W, ICON_H, 3, 3);
                gc.setFill(i < bulletsLeft ? Color.color(1.0, 1.0, 0.7, 1.0) : Color.color(0.35, 0.35, 0.35, 0.7));
                gc.fillRoundRect(x + 1, y, ICON_W - 2, 5, 2, 2);
                gc.restore();
            }
        }
    }

     /**
     * Renders a health bar for a given ship.
     */
    private void renderHealthBar(Ship ship, double x, double y, double width) {
        gc.setStroke(Color.WHITE);
        gc.strokeRect(x, y, width, 32);
        gc.setFill(Color.RED);
        gc.fillRect(x, y + 1, ship.getHealth(), 30);
    }

    /**
     * The main game loop, called on every frame.
     */
    @Override
    public void handle(long currentNanoTime) {
        gc.setFont(Font.font("Orbitron", 20));
        long currentSecond = (System.nanoTime() - this.startTime) / 1_000_000_000; // Calculate elapsed seconds
        double deltaTime = (currentNanoTime - lastNanoTime) / 1_000_000_000.0; // Time in seconds
        lastNanoTime = currentNanoTime;

        // Clear the canvas for rendering
        gc.clearRect(0, 0, GameProper.WINDOW_WIDTH, GameProper.WINDOW_HEIGHT);

        // Render walls
        renderWalls();

        if (explosionActive) {
            renderExplosion(deltaTime);
            renderHealthBar(player1Ship, 20, 20, 100);
            renderHealthBar(player2Ship, GameProper.WINDOW_WIDTH - 120, 20, 100);
            return;
        }

        // Spawn power-ups
        spawnPowerUp();

        updatePowerUps(deltaTime);

        // Check collisions with power-ups
        checkPowerUpCollisions(player1Ship);
        checkPowerUpCollisions(player2Ship);
        checkWallCollsions();

        // Update active power-ups
        updateActivePowerUps(deltaTime);

        // Handle ship collision with walls
        checkShipCollision(player1Ship);
        checkShipCollision(player2Ship);

        // Handle projectile collision with walls
        checkProjectileCollsions(player1Ship);
        checkProjectileCollsions(player2Ship);

        // Handle Player 1 controls
        moveShip(player1Ship, KeyCode.W, KeyCode.A, KeyCode.D);
        handleShooting(player1Ship, player2Ship, KeyCode.SPACE);

        // Handle Player 2 controls
        moveShip(player2Ship, KeyCode.UP, KeyCode.LEFT, KeyCode.RIGHT);
        handleShooting(player2Ship, player1Ship, KeyCode.ENTER);

        // Render ships (with hit flash)
        player1Ship.decrementHitFlash(deltaTime);
        player2Ship.decrementHitFlash(deltaTime);
        player1Ship.render(gc);
        player2Ship.render(gc);

        renderHitmarks(deltaTime);

        // Render health bars
        renderHealthBar(player1Ship, 20, 20, 100);
        renderHealthBar(player2Ship, GameProper.WINDOW_WIDTH - 120, 20, 100);

        // Render bullet UI (icons + reload arc)
        renderBulletUI(player1Ship, 20, 500);
        renderBulletUI(player2Ship, GameProper.WINDOW_WIDTH - 60, 500);

        gc.strokeText(currentSecond / 60 + " : " + ((currentSecond % 59 < 10) ? "0" : "") + currentSecond % 59, (GameProper.WINDOW_WIDTH / 2) - 22, 45); // Time display

        checkWinner();
    }
}
