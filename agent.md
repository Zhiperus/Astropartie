# Astropartie (Astropanty) - Agent Documentation

This repository contains a 2D top-down spaceship arena shooter game implemented in Java. It supports both a local 2-player split-screen co-op mode, and an advanced up to 4-player online multiplayer mode built upon an authoritative server architecture.

## Tech Stack
- **Language**: Java 11+
- **GUI Framework**: JavaFX
- **Networking**: Standard Java Sockets (`java.net`, `java.io`) via basic serialization.
- **Build Tool**: Maven

## Architecture Overview

The codebase cleanly isolates Local Co-Op handling from Multiplayer Network handling to prevent game loop conflicts.

### 1. Main Entry Points
- `src/main/java/org/astropanty/App.java`: The core Application subclass that mounts the JavaFX UI and launches the game client.
- `src/main/java/org/astropanty/server/GameServer.java`: The headless authoritative server entry point. It calculates bullet and movement physics, tracks health and connected states, and broadcasts deterministic layouts to connected network clients.

### 2. UI & Navigation (`org.astropanty.ui.navigation`)
Instead of FXML, the project builds UI trees directly in Java classes.
- **`ScreenController`**: Manages transitioning the `Scene` on the primary stage.
- **`Screen` Interface**: Implemented by views (e.g., `Home.java`, `Menu.java`) returning a JavaFX layout. `Home` splits out flows into `Local Co-op` and `Online Multiplayer`.

### 3. Local Game Flow (`org.astropanty.ui.game`)
Traditional offline handling:
1. **Character Select**: `CharacterSelect.java` maps UI choices for 2 local players.
2. **Map Select**: `MapSelectScreen.java`
3. **Local Loop**: `GameProper.java` initiates `GameTimer.java` threads. The `Ship.java` entities handle movements in local standalone `Runnable` threads asynchronously. 

### 4. Multiplayer Network Flow (`org.astropanty.net` & `org.astropanty.ui.game.logic`)
The online multiplayer strictly enforces a "Dumb Client" architecture:
- **`NetworkCharacterSelect.java`**: Single player selection prior to connecting to a lobby.
- **`GameClient.java`**: Acts purely as a transmission layer running async on the client. It pushes `InputPayload` (W/A/S/D/Shoot/ShipChoice state) to the server 60 times a second.
- **`GameStatePayload.java`**: Contains coordinate arrays (`shipXs`, `shipYs`, `rotations`, `healths`, `projXs`, `projYs`, `projActive`) defining exact screen positions computed globally by the server. 
- **`NetworkGameTimer.java`**: Bypasses local physics loops. It dynamically constructs ships based on connection flags, applying absolute geometries strictly derived from listening to the most recently received `GameStatePayload`.

### 5. Server Subsystem (`org.astropanty.server`)
- **`GameServer.java`**: Handles rigid mathematical computations for up to 4 players and 20 global projectiles. To access accurate map boundaries via existing code (`MapLayouts.java` returns JavaFX elements), the server silently initializes a JavaFX toolkit instance via `Platform.startup` allowing precise `Rectangle2D` intersecting collision physics independently from graphic contexts.
- **`ClientHandler.java`**: A threaded connection socket dedicated to routing `Serializable` payload streams dynamically.

### 6. Data (`org.astropanty.data`)
- **`ShipRepository`**: Stores the static definitions and attributes of all available ships.
- **`MapLayouts`**: Provides lists of `Wall` boundaries serving as collidable terrain.

## Running the Application

### Development/Running the Client
The standard way to launch the game client via the JavaFX Maven plugin:
```bash
mvn clean javafx:run
```

### Running the Live Server
To host the persistent environment so clients can join `Play Online Multiplayer`:
```bash
mvn compile exec:java -Dexec.mainClass="org.astropanty.server.GameServer"
```

## Key Notes for Automated Agents
- **Network Extensibility**: If tasked with modifying physics (for instance implementing varying ship speeds or new powers), any multiplayer adjustments **MUST** be applied inside `GameServer.updatePhysics()` logic, whereas local bounds are applied in `GameTimer.java` loops. Do NOT put business logic inside `NetworkGameTimer`, as it will instantly be desynced and overwritten by the next backend payload.
- **Payload Arrays**: Data transmitted via `GameStatePayload` uses parallel primitives (`double[]`, `boolean[]`) over object-oriented structures to significantly reduce serialization overhead mapping ticks between Server and `GameClient`.
