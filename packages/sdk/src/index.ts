/**
 * Moud Engine - Server-Side API Definitions
 *
 * This file provides TypeScript definitions for the Moud server-side scripting environment.
 *
 */

// --- Core & Global Types ---

declare global {
    /**
     * The main entry point to all server-side Moud APIs.
     */
    const api: MoudAPI;

    /**
     * A secure console API for logging messages to the server console.
     * Mirrors the standard browser console API.
     */
    const console: {
        /** Logs informational messages. */
        log(...args: any[]): void;
        /** Logs warning messages. */
        warn(...args: any[]): void;
        /** Logs error messages. */
        error(...args: any[]): void;
        /** Logs messages only when the server is in a debug-like state. */
        debug(...args: any[]): void;
    };

    namespace MoudMath {
        const Vector3: {
            new(x: number, y: number, z: number): Vector3;
            /** A vector with all components set to 0. */
            zero(): Vector3;
            /** A vector with all components set to 1. */
            one(): Vector3;
            /** A vector pointing up (0, 1, 0). */
            up(): Vector3;
            /** A vector pointing down (0, -1, 0). */
            down(): Vector3;
            /** A vector pointing left (-1, 0, 0). */
            left(): Vector3;
            /** A vector pointing right (1, 0, 0). */
            right(): Vector3;
            /** A vector pointing forward (0, 0, 1). */
            forward(): Vector3;
            /** A vector pointing backward (0, 0, -1). */
            backward(): Vector3;
            /** Linearly interpolates between two vectors. */
            lerp(start: Vector3, end: Vector3, t: number): Vector3;
        };

        const Quaternion: {
            new(x: number, y: number, z: number, w: number): Quaternion;
            /** Returns an identity quaternion (no rotation). */
            identity(): Quaternion;
            /** Creates a quaternion from Euler angles in degrees. */
            fromEuler(pitch: number, yaw: number, roll: number): Quaternion;
            /** Creates a quaternion representing rotation around an axis by the given angle in degrees. */
            fromAxisAngle(axis: Vector3, angle: number): Quaternion;
            /** Creates a quaternion that rotates from one vector to another. */
            fromToRotation(from: Vector3, to: Vector3): Quaternion;
            /** Creates a quaternion with the specified forward and up directions. */
            lookRotation(forward: Vector3, up: Vector3): Quaternion;
        };

        const Matrix4: {
            new(): Matrix4;
            new(values: number[]): Matrix4;
            new(other: Matrix4): Matrix4;
            /** Returns a 4x4 identity matrix. */
            identity(): Matrix4;
            /** Creates a translation matrix from the given translation vector. */
            translation(translation: Vector3): Matrix4;
            /** Creates a rotation matrix from the given quaternion. */
            rotation(rotation: Quaternion): Matrix4;
            /** Creates a scale matrix from the given scale vector. */
            scaling(scale: Vector3): Matrix4;
            /** Creates a transformation matrix combining translation, rotation, and scale. */
            trs(translation: Vector3, rotation: Quaternion, scale: Vector3): Matrix4;
            /** Creates a perspective projection matrix. */
            perspective(fov: number, aspect: number, near: number, far: number): Matrix4;
            /** Creates an orthographic projection matrix. */
            orthographic(left: number, right: number, bottom: number, top: number, near: number, far: number): Matrix4;
            /** Creates a view matrix looking from eye position towards target with specified up vector. */
            lookAt(eye: Vector3, target: Vector3, up: Vector3): Matrix4;
        };

        const Transform: {
            new(): Transform;
            new(position: Vector3, rotation: Quaternion, scale: Vector3): Transform;
            new(other: Transform): Transform;
            /** Returns an identity transform with zero position, identity rotation, and unit scale. */
            identity(): Transform;
        };

        const MathUtils: {
            /** Small value for floating point comparisons (1e-6). */
            readonly EPSILON: number;
            /** Mathematical constant π. */
            readonly PI: number;
            /** Mathematical constant 2π. */
            readonly TWO_PI: number;
            /** Mathematical constant π/2. */
            readonly HALF_PI: number;
            /** Conversion factor from degrees to radians. */
            readonly DEG_TO_RAD: number;
            /** Conversion factor from radians to degrees. */
            readonly RAD_TO_DEG: number;
            /** Clamps a value between a minimum and maximum value. */
            clamp(value: number, min: number, max: number): number;
            /** Linearly interpolates between two values. */
            lerp(a: number, b: number, t: number): number;
            /** Returns the parameter that produces the interpolant value within the range [a, b]. */
            inverseLerp(a: number, b: number, value: number): number;
            /** Smoothly interpolates between two values using Hermite interpolation. */
            smoothstep(edge0: number, edge1: number, x: number): number;
            /** Smoothly interpolates between two values using a smoother curve than smoothstep. */
            smootherstep(edge0: number, edge1: number, x: number): number;
            /** Maps a value from one range to another. */
            map(value: number, inMin: number, inMax: number, outMin: number, outMax: number): number;
            /** Wraps a value within a specified range. */
            wrap(value: number, min: number, max: number): number;
            /** Creates a ping-pong effect with the given length. */
            pingPong(t: number, length: number): number;
            /** Repeats a value within a given length. */
            repeat(t: number, length: number): number;
            /** Calculates the shortest difference between two angles. */
            deltaAngle(current: number, target: number): number;
            /** Linearly interpolates between two angles, taking the shortest path. */
            lerpAngle(a: number, b: number, t: number): number;
            /** Moves a value towards a target by a maximum delta amount. */
            moveTowards(current: number, target: number, maxDelta: number): number;
            /** Moves an angle towards a target angle by a maximum delta amount. */
            moveTowardsAngle(current: number, target: number, maxDelta: number): number;
            /** Smoothly damps a value towards a target using velocity. */
            smoothDamp(current: number, target: number, currentVelocity: number, smoothTime: number, maxSpeed: number, deltaTime: number): number;
            /** Returns the sign of a number (-1, 0, or 1). */
            sign(value: number): number;
            /** Returns the fractional part of a number. */
            fract(value: number): number;
            /** Raises a number to the power of another number. */
            pow(base: number, exponent: number): number;
            /** Returns the square root of a number. */
            sqrt(value: number): number;
            /** Returns the sine of an angle (in radians). */
            sin(radians: number): number;
            /** Returns the cosine of an angle (in radians). */
            cos(radians: number): number;
            /** Returns the tangent of an angle (in radians). */
            tan(radians: number): number;
            /** Returns the arc sine of a number. */
            asin(value: number): number;
            /** Returns the arc cosine of a number. */
            acos(value: number): number;
            /** Returns the arc tangent of a number. */
            atan(value: number): number;
            /** Returns the arc tangent of y/x, handling quadrants correctly. */
            atan2(y: number, x: number): number;
            /** Returns the largest integer less than or equal to the given number. */
            floor(value: number): number;
            /** Returns the smallest integer greater than or equal to the given number. */
            ceil(value: number): number;
            /** Returns the nearest integer to the given number. */
            round(value: number): number;
            /** Returns the absolute value of a number. */
            abs(value: number): number;
            /** Returns the smaller of two numbers. */
            min(a: number, b: number): number;
            /** Returns the larger of two numbers. */
            max(a: number, b: number): number;
            /** Checks if two numbers are approximately equal using EPSILON. */
            approximately(a: number, b: number): boolean;
            /** Checks if two numbers are approximately equal using a custom tolerance. */
            approximately(a: number, b: number, tolerance: number): boolean;
            /** Converts degrees to radians. */
            toRadians(degrees: number): number;
            /** Converts radians to degrees. */
            toDegrees(radians: number): number;
            /** Returns a random number between 0 and 1. */
            random(): number;
            /** Returns a random number between min and max. */
            random(min: number, max: number): number;
            /** Returns a random integer between min and max (inclusive). */
            randomInt(min: number, max: number): number;
        };

        const GeometryUtils: {
            /** Calculates the distance from a point to a line segment. */
            distancePointToLine(point: Vector3, lineStart: Vector3, lineEnd: Vector3): number;
            /** Finds the closest point on a line segment to a given point. */
            closestPointOnLine(point: Vector3, lineStart: Vector3, lineEnd: Vector3): Vector3;
            /** Calculates the signed distance from a point to a plane. */
            distancePointToPlane(point: Vector3, planePoint: Vector3, planeNormal: Vector3): number;
            /** Projects a point onto a plane. */
            projectPointOnPlane(point: Vector3, planePoint: Vector3, planeNormal: Vector3): Vector3;
            /** Tests if a ray intersects with a plane. */
            rayPlaneIntersection(rayOrigin: Vector3, rayDirection: Vector3, planePoint: Vector3, planeNormal: Vector3): boolean;
            /** Calculates the intersection point between a ray and a plane. */
            rayPlaneIntersectionPoint(rayOrigin: Vector3, rayDirection: Vector3, planePoint: Vector3, planeNormal: Vector3): Vector3;
            /** Tests if two spheres intersect. */
            sphereIntersection(center1: Vector3, radius1: number, center2: Vector3, radius2: number): boolean;
            /** Tests if a point is inside a sphere. */
            pointInSphere(point: Vector3, sphereCenter: Vector3, sphereRadius: number): boolean;
            /** Tests if a point is inside an axis-aligned bounding box. */
            pointInBox(point: Vector3, boxMin: Vector3, boxMax: Vector3): boolean;
            /** Calculates barycentric coordinates of a point relative to a triangle. */
            barycentric(point: Vector3, a: Vector3, b: Vector3, c: Vector3): Vector3;
            /** Tests if a point is inside a triangle. */
            pointInTriangle(point: Vector3, a: Vector3, b: Vector3, c: Vector3): boolean;
            /** Calculates the normal vector of a triangle. */
            triangleNormal(a: Vector3, b: Vector3, c: Vector3): Vector3;
            /** Calculates the area of a triangle. */
            triangleArea(a: Vector3, b: Vector3, c: Vector3): number;
            /** Reflects a vector off a surface with the given normal. */
            reflect(incident: Vector3, normal: Vector3): Vector3;
            /** Refracts a vector through a surface with the given normal and refractive index. */
            refract(incident: Vector3, normal: Vector3, eta: number): Vector3;
            /** Calculates the Fresnel reflection coefficient. */
            fresnel(incident: Vector3, normal: Vector3, n1: number, n2: number): number;
            /** Evaluates a cubic Bézier curve at parameter t. */
            bezierCubic(p0: Vector3, p1: Vector3, p2: Vector3, p3: Vector3, t: number): Vector3;
            /** Evaluates a quadratic Bézier curve at parameter t. */
            bezierQuadratic(p0: Vector3, p1: Vector3, p2: Vector3, t: number): Vector3;
            /** Evaluates a Catmull-Rom spline at parameter t. */
            catmullRom(p0: Vector3, p1: Vector3, p2: Vector3, p3: Vector3, t: number): Vector3;
            /** Calculates the signed volume of a tetrahedron. */
            signedVolumeOfTetrahedron(a: Vector3, b: Vector3, c: Vector3, d: Vector3): number;
            /** Tests if two points are on the same side of a line. */
            sameSide(p1: Vector3, p2: Vector3, a: Vector3, b: Vector3): boolean;
            /** Calculates the circumcenter of a triangle. */
            circumcenter(a: Vector3, b: Vector3, c: Vector3): Vector3;
            /** Calculates the circumradius of a triangle. */
            circumradius(a: Vector3, b: Vector3, c: Vector3): number;
            /** Calculates the incenter of a triangle. */
            incenter(a: Vector3, b: Vector3, c: Vector3): Vector3;
            /** Calculates the inradius of a triangle. */
            inradius(a: Vector3, b: Vector3, c: Vector3): number;
            /** Generates uniformly distributed points on a sphere surface. */
            generateSpherePoints(count: number): Vector3[];
            /** Converts spherical coordinates to Cartesian coordinates. */
            sphericalToCartesian(radius: number, theta: number, phi: number): Vector3;
            /** Converts Cartesian coordinates to spherical coordinates. */
            cartesianToSpherical(point: Vector3): Vector3;
            /** Converts cylindrical coordinates to Cartesian coordinates. */
            cylindricalToCartesian(radius: number, theta: number, height: number): Vector3;
            /** Converts Cartesian coordinates to cylindrical coordinates. */
            cartesianToCylindrical(point: Vector3): Vector3;
        };

        const Conversion: {
            /** Converts any object to a double value. Handles Number and String types. */
            toDouble(obj: any): number;
            /** Converts any object to a float value. */
            toFloat(obj: any): number;
            /** Converts any object to a long value. Handles Number and String types. */
            toLong(obj: any): number;
        };
    }

    /**
     * Represents a 3D vector with x, y, and z components.
     */
    interface Vector3 {
        readonly x: number;
        readonly y: number;
        readonly z: number;
        /** Adds two vectors component-wise. */
        add(other: Vector3): Vector3;
        /** Subtracts one vector from another component-wise. */
        subtract(other: Vector3): Vector3;
        /** Multiplies the vector by a scalar value. */
        multiply(scalar: number): Vector3;
        /** Multiplies two vectors component-wise. */
        multiply(other: Vector3): Vector3;
        /** Divides the vector by a scalar value. */
        divide(scalar: number): Vector3;
        /** Divides two vectors component-wise. */
        divide(other: Vector3): Vector3;
        /** Returns the negated vector. */
        negate(): Vector3;
        /** Calculates the dot product with another vector. */
        dot(other: Vector3): number;
        /** Calculates the cross product with another vector. */
        cross(other: Vector3): Vector3;
        /** Returns the magnitude (length) of the vector. */
        length(): number;
        /** Returns the squared magnitude of the vector (faster than length()). */
        lengthSquared(): number;
        /** Returns a unit vector in the same direction. */
        normalize(): Vector3;
        /** Calculates the distance to another vector. */
        distance(other: Vector3): number;
        /** Calculates the squared distance to another vector (faster than distance()). */
        distanceSquared(other: Vector3): number;
        /** Linearly interpolates between this vector and the target. */
        lerp(target: Vector3, t: number): Vector3;
        /** Spherically interpolates between this vector and the target. */
        slerp(target: Vector3, t: number): Vector3;
        /** Reflects this vector off a surface with the given normal. */
        reflect(normal: Vector3): Vector3;
        /** Projects this vector onto another vector. */
        project(onto: Vector3): Vector3;
        /** Returns the rejection of this vector from another vector. */
        reject(onto: Vector3): Vector3;
        /** Calculates the angle between this vector and another in radians. */
        angle(other: Vector3): number;
        /** Rotates this vector around an axis by the given angle in degrees. */
        rotateAroundAxis(axis: Vector3, angle: number): Vector3;
        /** Returns a vector with absolute values of all components. */
        abs(): Vector3;
        /** Returns a vector with the minimum components from both vectors. */
        min(other: Vector3): Vector3;
        /** Returns a vector with the maximum components from both vectors. */
        max(other: Vector3): Vector3;
        /** Clamps all components between the corresponding components of min and max vectors. */
        clamp(min: Vector3, max: Vector3): Vector3;
        /** Checks if two vectors are approximately equal within the given tolerance. */
        equals(other: Vector3, tolerance: number): boolean;
        /** Returns a string representation of the vector. */
        toString(): string;
    }

    /**
     * Represents a rotation in 3D space using quaternions.
     */
    interface Quaternion {
        readonly x: number;
        readonly y: number;
        readonly z: number;
        readonly w: number;
        /** Multiplies this quaternion with another quaternion. */
        multiply(other: Quaternion): Quaternion;
        /** Adds two quaternions component-wise. */
        add(other: Quaternion): Quaternion;
        /** Subtracts one quaternion from another component-wise. */
        subtract(other: Quaternion): Quaternion;
        /** Scales the quaternion by a scalar value. */
        scale(scalar: number): Quaternion;
        /** Calculates the dot product with another quaternion. */
        dot(other: Quaternion): number;
        /** Returns the magnitude of the quaternion. */
        length(): number;
        /** Returns the squared magnitude of the quaternion. */
        lengthSquared(): number;
        /** Returns a normalized unit quaternion. */
        normalize(): Quaternion;
        /** Returns the conjugate of the quaternion. */
        conjugate(): Quaternion;
        /** Returns the inverse of the quaternion. */
        inverse(): Quaternion;
        /** Rotates a point using this quaternion. */
        rotate(point: Vector3): Vector3;
        /** Converts the quaternion to Euler angles in degrees. */
        toEuler(): Vector3;
        /** Gets the rotation axis of the quaternion. */
        getAxis(): Vector3;
        /** Gets the rotation angle in degrees. */
        getAngle(): number;
        /** Spherically interpolates between this quaternion and the target. */
        slerp(target: Quaternion, t: number): Quaternion;
        /** Linearly interpolates between this quaternion and the target. */
        lerp(target: Quaternion, t: number): Quaternion;
        /** Calculates the angle between this quaternion and another in degrees. */
        angleTo(target: Quaternion): number;
        /** Checks if two quaternions are approximately equal within the given tolerance. */
        equals(other: Quaternion, tolerance: number): boolean;
        /** Returns a string representation of the quaternion. */
        toString(): string;
    }

    interface Matrix4 {
        readonly m: number[];
        multiply(other: Matrix4): Matrix4;
        transformPoint(point: Vector3): Vector3;
        transformDirection(direction: Vector3): Vector3;
        transpose(): Matrix4;
        determinant(): number;
        inverse(): Matrix4;
        getTranslation(): Vector3;
        getRotation(): Quaternion;
        getScale(): Vector3;
        get(index: number): number;
        set(index: number, value: number): void;
        toArray(): number[];
        toString(): string;
    }

    interface Transform {
        position: Vector3;
        rotation: Quaternion;
        scale: Vector3;
        toMatrix(): Matrix4;
        multiply(other: Transform): Transform;
        transformPoint(point: Vector3): Vector3;
        transformDirection(direction: Vector3): Vector3;
        inverseTransformPoint(point: Vector3): Vector3;
        inverseTransformDirection(direction: Vector3): Vector3;
        inverse(): Transform;
        lerp(target: Transform, t: number): Transform;
        slerp(target: Transform, t: number): Transform;
        getForward(): Vector3;
        getRight(): Vector3;
        getUp(): Vector3;
        lookAt(target: Vector3, up: Vector3): void;
        translate(translation: Vector3): void;
        rotate(rotation: Quaternion): void;
        rotateAround(point: Vector3, axis: Vector3, angle: number): void;
        toString(): string;
    }
// --- Main API Interfaces ---

/**
 * The root interface for all server-side Moud APIs.
 */
interface MoudAPI {
    /**
     * Registers a callback function to be executed when a specific game event occurs.
     * @param eventName The name of the event (e.g., 'player.join', 'player.chat').
     * @param callback The function to execute when the event is triggered.
     */
    on(eventName: 'player.join', callback: (player: Player) => void): void;
    on(eventName: 'player.leave', callback: (event: PlayerLeaveEvent) => void): void;
    on(eventName: 'player.chat', callback: (event: ChatEvent) => void): void;
    on(eventName: 'player.move', callback: (event: PlayerMoveEvent) => void): void;
    on(eventName: 'player.click', callback: (player: Player, data: { button: 0 | 1 | 2 }) => void): void;
    on(eventName: 'block.break', callback: (event: BlockEvent) => void): void;
    on(eventName: 'block.place', callback: (event: BlockEvent) => void): void;
    on(eventName: 'entity.interact', callback: (event: EntityInteractionEvent) => void): void;
    /**
     * Registers a handler for a custom event sent from a client script.
     * @param eventName The custom name of the event.
     * @param callback A function that receives the player who sent the event and the data payload.
     */
    on(eventName: string, callback: (player: Player, data: any) => void): void;

    /**
     * Accesses the server-wide API for managing players and broadcasting messages.
     * @returns The Server API proxy.
     */
    getServer(): Server;

    /**
     * Accesses the main world/instance API for block manipulation and entity spawning.
     * @returns The World API proxy.
     */
    getWorld(): World;

    /**
     * Accesses the lighting API for creating and managing dynamic lights.
     * @returns The Lighting API proxy.
     */
    getLighting(): LightingAPI;

    /**
     * Accesses the asynchronous task manager for running long-running, non-blocking operations.
     * @returns The AsyncManager API proxy.
     */
    getAsync(): AsyncManager;
}

/**
 * API for managing server-level operations.
 */
interface Server {
    /**
     * Sends a message to every online player.
     * @param message The message to broadcast.
     */
    broadcast(message: string): void;

    /**
     * Gets the current number of online players.
     * @returns The number of players.
     */
    getPlayerCount(): number;

    /**
     * Gets an array of all currently online players.
     * @returns An array of Player objects.
     */
    getPlayers(): Player[];
}

/**
 * Options for creating a player model.
 */
interface PlayerModelOptions {
    /** The initial position of the model in the world. */
    position: Vector3;
    /** The URL of the skin to apply to the model. */
    skinUrl?: string;
}

/**
 * Options for creating world text.
 */
interface TextOptions {
    /** The initial position of the text in the world. */
    position: Vector3;
    /** The content of the text. */
    content?: string;
    /** How the text should face the player. 'fixed', 'vertical', 'horizontal', 'center'. */
    billboard?: 'fixed' | 'vertical' | 'horizontal' | 'center';
}


/**
 * API for managing the main game world.
 */
interface World {
    /**
     * Sets the world generator to a flat grass plain.
     * @returns The World object for chaining.
     */
    setFlatGenerator(): this;

    /**
     * Sets the world generator to a completely empty void.
     * @returns The World object for chaining.
     */
    setVoidGenerator(): this;

    /**
     * Sets the default spawn point for new players.
     * @returns The World object for chaining.
     */
    setSpawn(x: number, y: number, z: number): this;

    /**
     * Gets the namespaced ID of a block at a specific coordinate.
     * @returns The block ID (e.g., 'minecraft:stone').
     */
    getBlock(x: number, y: number, z: number): string;

    /**
     * Places a block at a specific coordinate.
     * @param blockId The namespaced ID of the block to place.
     */
    setBlock(x: number, y: number, z: number, blockId: string): void;

    /**
     * Creates a static, non-player entity that looks like a player.
     * Can have a custom skin and play animations.
     * @param options The configuration for the player model.
     * @returns A PlayerModel object to control the model.
     */
    createPlayerModel(options: PlayerModelOptions): PlayerModel;

    /**
     * Creates floating text in the world.
     * @param options The configuration for the text.
     * @returns A Text object to control the text display.
     */
    createText(options: TextOptions): Text;

    /**
     * Spawns a new entity in the world that is controlled by a script object.
     * The script object must have an `onTick()` method.
     * @param entityType The namespaced ID of the entity (e.g., 'minecraft:zombie').
     * @param jsInstance The script object with an `onTick` method to control the entity.
     */
    spawnScriptedEntity(entityType: string, x: number, y: number, z: number, jsInstance: { onTick: () => void }): any;
}

/**
 * API for creating and managing dynamic lights in the world.
 */
interface LightingAPI {
    /**
     * Creates a new point light (emits light in all directions).
     * @param lightId A unique numeric ID for this light.
     * @param position The world position of the light.
     * @param color A Vector3 representing RGB values (0-1).
     * @param radius The effective radius of the light.
     * @param brightness The intensity of the light.
     */
    createPointLight(lightId: number, position: Vector3, color: Vector3, radius: number, brightness: number): void;

    /**
     * Creates a new area light (a rectangular light source).
     * @param lightId A unique numeric ID for this light.
     * @param position The world position of the light.
     * @param direction The direction the light is pointing.
     * @param color A Vector3 representing RGB values (0-1).
     * @param width The width of the light rectangle.
     * @param height The height of the light rectangle.
     * @param brightness The intensity of the light.
     */
    createAreaLight(lightId: number, position: Vector3, direction: Vector3, color: Vector3, width: number, height: number, brightness: number): void;

    /**
     * Updates the properties of an existing light.
     * @param lightId The ID of the light to update.
     * @param properties An object containing the properties to change (e.g., { position: new Vector3(...) }).
     */
    updateLight(lightId: number, properties: { [key: string]: any }): void;

    /**
     * Removes a light from the world.
     * @param lightId The ID of the light to remove.
     */
    removeLight(lightId: number): void;
}

/**
 * API for running tasks on a separate thread to avoid lagging the server.
 */
interface AsyncManager {
    /**
     * Submits a task to be executed on a worker thread.
     * @param task A function containing the long-running computation.
     * @returns A Promise that resolves with the return value of the task.
     */
    submit<T>(task: () => T): Promise<T>;

    /**
     * Schedules a task to be run on the main server thread on the next tick.
     * Useful for applying results from an async task (e.g., setting blocks).
     * @param task The function to execute on the main thread.
     */
    runOnServerThread(task: () => void): void;
}


/**
 * Represents a player in the game and provides methods to interact with them.
 */
interface Player {
    /** @returns The player's Minecraft username. */
    getName(): string;

    /** @returns The player's unique UUID as a string. */
    getUuid(): string;

    /**
     * Sends a chat message directly to this player.
     * @param message The message to send, supporting standard Minecraft color codes.
     */
    sendMessage(message: string): void;

    /**
     * Disconnects the player from the server.
     * @param reason The message displayed to the player upon being kicked.
     */
    kick(reason: string): void;

    /** @returns True if the player is still connected to the server. */
    isOnline(): boolean;

    /**
     * Accesses the client-side API for this player, allowing you to send custom events
     * to their client script.
     * @returns The PlayerClient API proxy.
     */
    getClient(): PlayerClient;

    /** @returns The current world position of the player as a Vector3. */
    getPosition(): Vector3;

    /** @returns A normalized Vector3 representing the direction the player's body is facing. */
    getDirection(): Vector3;

    /** @returns A normalized Vector3 representing the direction the player's camera is looking. */
    getCameraDirection(): Vector3;

    /**
     * Instantly moves the player to a new position in the world.
     */
    teleport(x: number, y: number, z: number): void;

    /**
     * Accesses the API for synchronizing key-value data with the player's client.
     * @returns The SharedValue API proxy for this player.
     */
    getShared(): SharedValueApi;

    /**
     * Accesses the API for controlling the player's camera.
     * @returns The CameraLock API proxy.
     */
    getCamera(): CameraLock;

    /**
     * Accesses the API for showing or hiding elements of the vanilla Minecraft HUD.
     * @returns The PlayerUI API proxy.
     */
    getUi(): PlayerUI;

    /**
     * Accesses the API for controlling the player's 3D world cursor.
     * @returns The Cursor API proxy.
     */
    getCursor(): Cursor;

    /**
     * Accesses the API for controlling and overriding player model animations and parts.
     * @returns The PlayerAnimation API proxy.
     */
    getAnimation(): PlayerAnimation;
}

/**
 * Represents the client-side of a player connection, for sending client-only events.
 */
interface PlayerClient {
    /**
     * Sends a custom event to this specific player's client script.
     * The client script must have a corresponding `moudAPI.network.on('eventName', ...)` handler.
     * @param eventName The name of the event to trigger on the client.
     * @param data The data payload for the event. Must be serializable (e.g., strings, numbers, booleans, nested objects/arrays).
     */
    send(eventName: string, data: any): void;
}

/**
 * Options for locking the player's camera.
 */
interface CameraLockOptions {
    /** The yaw (horizontal rotation) of the camera. */
    yaw?: number;
    /** The pitch (vertical rotation) of the camera. */
    pitch?: number;
    /** The roll (tilt) of the camera. */
    roll?: number;
    /** If true, the camera will smoothly transition to the new position/rotation. */
    smooth?: boolean;
    /** The speed of the smooth transition. */
    speed?: number;
    /** If true, disables the vanilla view bobbing effect. Defaults to true. */
    disableViewBobbing?: boolean;
    /** If true, hides the player's hand and held item. Defaults to true. */
    disableHandMovement?: boolean;
}

/**
 * API for controlling a player's camera, allowing for cinematic sequences.
 */
interface CameraLock {
    /**
     * Shakes the player's camera.
     * @param intensity The strength of the shake.
     * @param durationMs The duration of the shake in milliseconds.
     */
    shake(intensity: number, durationMs: number): void;

    /**
     * Locks the player's camera at a specific position and rotation, making them invisible.
     * This is the primary method for starting a cinematic sequence.
     * @param position The world position to place the camera.
     * @param options Optional settings for the camera's rotation and behavior.
     */
    lock(position: Vector3, options?: CameraLockOptions): void;

    /**
     * Updates the position of an already locked camera.
     * @param newPosition The new world position for the camera.
     */
    setPosition(newPosition: Vector3): void;

    /**
     * Updates the rotation of an already locked camera.
     * @param rotation An object specifying yaw, pitch, or roll.
     */
    setRotation(rotation: { yaw?: number; pitch?: number; roll?: number }): void;

    /**
     * Releases the player's camera from a locked state, returning control to the player.
     */
    release(): void;

    /**
     * Smoothly moves a locked camera from its current position to a new target over a duration.
     * @param targetPosition The destination position.
     * @param targetRotation The destination rotation.
     * @param durationMs The time in milliseconds for the transition.
     */
    smoothTransitionTo(targetPosition: Vector3, targetRotation: { yaw?: number; pitch?: number; roll?: number }, durationMs: number): void;

    /** Resets the player's cursor direction, which can affect camera direction in some modes. */
    resetCursorRotation(): void;

    /** Immediately stops any active smooth transition or animation. */
    stopAnimation(): void;

    /** @returns True if the camera is currently locked. */
    isLocked(): boolean;

    /** @returns The current position of the locked camera, or a zero vector if not locked. */
    getPosition(): Vector3;

    /** @returns The current rotation (yaw, pitch, roll) of the locked camera as a Vector3. */
    getRotation(): Vector3;
}

/**
 * Options for showing or hiding multiple HUD elements at once.
 */
interface UIVisibilityOptions {
    hotbar?: boolean;
    hand?: boolean;
    experience?: boolean;
    health?: boolean;
    food?: boolean;
    crosshair?: boolean;
    chat?: boolean;
    playerList?: boolean;
    scoreboard?: boolean;
}

/**
 * API for controlling the visibility of the vanilla Minecraft HUD for a player.
 */
interface PlayerUI {
    /** Hides all vanilla HUD elements. */
    hide(): void;
    /** Hides a specific set of HUD elements. */
    hide(options: UIVisibilityOptions): void;
    /** Shows all vanilla HUD elements. */
    show(): void;
    /** Shows a specific set of HUD elements. */
    show(options: UIVisibilityOptions): void;

    hideHotbar(): void;
    showHotbar(): void;
    hideHealth(): void;
    showHealth(): void;
    hideFood(): void;
    showFood(): void;
    hideExperience(): void;
    showExperience(): void;
    hideHand(): void;
    showHand(): void;
    hideCrosshair(): void;
    showCrosshair(): void;
    hideChat(): void;
    showChat(): void;
    hidePlayerList(): void;
    showPlayerList(): void;
    hideScoreboard(): void;
    showScoreboard(): void;
}

/**
 * API for controlling a player's 3D world cursor.
 */
interface Cursor {
    /** @returns The current 3D world position of the cursor. */
    getPosition(): Vector3;

    /** @returns The surface normal of the block the cursor is hitting. */
    getNormal(): Vector3;

    /** @returns True if the cursor is currently intersecting with a block. */
    isHittingBlock(): boolean;

    /**
     * Sets the rendering mode of the cursor.
     * @param mode 'THREE_DIMENSIONAL' follows surfaces, 'TWO_DIMENSIONAL' stays flat relative to the camera.
     */
    setMode(mode: 'THREE_DIMENSIONAL' | 'TWO_DIMENSIONAL'): void;

    /**
     * Sets the global visibility of the cursor for all other players.
     */
    setVisible(visible: boolean): void;

    /** Sets a texture for the cursor. Expects a resource location string. */
    setTexture(texturePath: string): void;

    /** Sets the color tint of the cursor. */
    setColor(r: number, g: number, b: number): void;

    /** Sets the scale of the cursor. */
    setScale(scale: number): void;

    /** Makes the cursor visible only to a specific list of players. */
    setVisibleTo(players: Player | Player[]): void;

    /** Hides the cursor from a specific list of players. */
    hideFrom(players: Player | Player[]): void;

    /** Resets visibility so the cursor is visible to everyone (if globally visible). */
    setVisibleToAll(): void;
}

type PlayerPartName = 'head' | 'body' | 'right_arm' | 'left_arm' | 'right_leg' | 'left_leg';

/**
 * Options for configuring the interpolation of part movements.
 */
interface InterpolationOptions {
    /** If false, the change will be instant. Defaults to true. */
    enabled?: boolean;
    /** The duration of the interpolation in milliseconds. Defaults to 150. */
    duration?: number;
    /** The easing function to use for the animation curve. */
    easing?: 'linear' | 'ease_in' | 'ease_out' | 'ease_in_out' | 'bounce';
}

/**
 * Options for modifying a player model's part.
 */
interface PartConfigOptions {
    position?: Vector3;
    rotation?: Vector3;
    scale?: Vector3;
    visible?: boolean;
    /** If true, this modification will override any running animation on this part. */
    overrideAnimation?: boolean;
    /** Settings for smoothly transitioning to the new configuration. */
    interpolation?: InterpolationOptions;
}

/**
 * API for controlling player model animations and individual parts.
 */
interface PlayerAnimation {
    /**
     * Modifies the transform of a specific part of the player model (e.g., the head or arms).
     * @param partName The name of the part to modify.
     * @param options The transformations to apply.
     */
    setPartConfig(partName: PlayerPartName, options: PartConfigOptions): void;

    /**
     * A utility function to easily make both arms point towards a world position.
     * @param targetPosition The position in the world to point at.
     * @param options Optional interpolation settings.
     */
    pointToPosition(targetPosition: Vector3, options?: { interpolation?: InterpolationOptions }): void;

    /**
     * Resets all part modifications back to their default state.
     */
    resetAllParts(): void;
}

// --- Event-Related Interfaces ---

/**
 * Base interface for events that can be cancelled.
 */
interface CancellableEvent {
    /**
     * Prevents the event from proceeding with its default vanilla behavior.
     * For example, cancelling a 'player.chat' event will prevent the message from appearing in chat.
     */
    cancel(): void;

    /**
     * @returns True if the event has been cancelled.
     */
    isCancelled(): boolean;
}

/**
 * Fired when a player attempts to send a message in chat.
 */
interface ChatEvent extends CancellableEvent {
    /** @returns The player who sent the message. */
    getPlayer(): Player;
    /** @returns The content of the chat message. */
    getMessage(): string;
}

/**
 * Fired when a player breaks or places a block.
 */
interface BlockEvent extends CancellableEvent {
    /** @returns The player who interacted with the block. */
    getPlayer(): Player;
    /** @returns The world position of the block. */
    getBlockPosition(): Vector3;
    /** @returns The namespaced ID of the block (e.g., 'minecraft:stone'). */
    getBlockType(): string;
    /** @returns The numeric state ID of the block. */
    getBlockStateId(): number;
    /** @returns 'break' or 'place'. */
    getEventType(): 'break' | 'place';
    /** @returns True if this was a block break event. */
    isBreakEvent(): boolean;
    /** @returns True if this was a block place event. */
    isPlaceEvent(): boolean;
}

/**
 * Fired when a player moves in the world.
 */
interface PlayerMoveEvent extends CancellableEvent {
    /** @returns The player who moved. */
    getPlayer(): Player;
    /** @returns The player's new position. */
    getNewPosition(): Vector3;
    /** @returns The player's position before this movement occurred. */
    getOldPosition(): Vector3;
    /** @returns The distance the player moved. */
    getDistance(): number;
    /** @returns True if the player moved from one block coordinate to another. */
    hasChangedBlock(): boolean;
}

/**
 * Fired when a player disconnects from the server.
 * Note: The Player object associated with this event may be offline.
 */
interface PlayerLeaveEvent {
    /** @returns The name of the player who left. */
    getName(): string;
    /** @returns The UUID of the player who left. */
    getUuid(): string;
}

/**
 * Fired when a player's cursor interacts with a scripted entity.
 */
interface EntityInteractionEvent {
    /** @returns The player whose cursor interacted with the entity. */
    getPlayer(): Player;
    /** @returns The namespaced ID of the entity type (e.g., 'minecraft:zombie'). */
    getEntityType(): string;
    /** @returns The unique UUID of the entity instance. */
    getEntityUuid(): string;
    /** @returns The type of interaction that occurred. */
    getInteractionType(): 'hover_enter' | 'hover_exit' | 'click';
    /** @returns The world position of the entity. */
    getEntityX(): number;
    getEntityY(): number;
    getEntityZ(): number;

    /** @returns True if the cursor just started hovering over the entity. */
    isHoverEnter(): boolean;
    /** @returns True if the cursor just stopped hovering over the entity. */
    isHoverExit(): boolean;
    /** @returns True if the player clicked on the entity. */
    isClick(): boolean;
}

// --- World Object Interfaces ---

/**
 * Represents a client-side rendered, non-player entity that looks like a player.
 * Can be used for NPCs, ghosts, placeholders, etc.
 */
interface PlayerModel {
    /** @returns The current world position of the model. */
    getPosition(): Vector3;

    /**
     * Sets the world position of the model.
     */
    setPosition(position: Vector3): void;

    /**
     * Sets the rotation of the model.
     * @param rotation An object containing yaw and/or pitch in degrees.
     */
    setRotation(rotation: { yaw?: number; pitch?: number }): void;

    /**
     * Changes the skin of the model.
     * @param skinUrl The URL of the new skin texture.
     */
    setSkin(skinUrl: string): void;

    /**
     * Plays an animation on the model. The animation must be loaded on the client.
     * @param animationName The namespaced ID of the animation to play (e.g., 'moud:wave').
     */
    playAnimation(animationName: string): void;

    /**
     * Removes the model from the world for all players.
     */
    remove(): void;

    /**
     * Registers a callback function to be executed when a player clicks on this model.
     * @param callback The function to execute, receives the player who clicked and click details.
     */
    onClick(callback: (player: Player, clickData: { button: number; mouseX: number; mouseY: number }) => void): void;
}

/**
 * Represents floating text in the world.
 */
interface Text {
    /**
     * Sets the content of the text display.
     */
    setText(newContent: string): void;

    /**
     * Sets the world position of the text.
     */
    setPosition(newPosition: Vector3): void;

    /**
     * Sets the color of the text using RGB values.
     * @param color An object with r, g, b properties (0-255).
     */
    setColor(color: { r: number; g: number; b: number }): void;
    /**
     * Sets the color of the text using RGB values.
     * @param r Red component (0-255).
     * @param g Green component (0-255).
     * @param b Blue component (0-255).
     */
    setColor(r: number, g: number, b: number): void;

    /**
     * Removes the text from the world.
     */
    remove(): void;

    /**
     * @returns The current world position of the text.
     */
    getPosition(): Vector3;
}

// --- Shared Value Synchronization API ---

/**
 * Entry point to the Shared Values API for a specific player.
 * Allows for creating named data stores that are synchronized with the client.
 */
interface SharedValueApi {
    /**
     * Gets or creates a named data store for this player. Stores are player-specific.
     * @param storeName A unique name for the store (e.g., 'inventory', 'playerStats', 'uiState').
     * @returns The SharedStore proxy for manipulating data.
     */
    getStore(storeName: string): SharedStore;
}

/**
 * Represents a key-value data store that is synchronized between the server and a single client.
 * This is the primary mechanism for managing client-side state from the server.
 */
interface SharedStore {
    /**
     * Sets a value in the store. The change will be automatically synchronized to the client.
     * @param key The unique identifier for the data within this store.
     * @param value The value to store. Must be serializable (e.g., numbers, strings, booleans, nested objects/arrays without functions or complex classes).
     * @param syncMode Defines how the update is sent.
     *        - 'batched' (default): Groups multiple changes in a single tick into one packet. Most efficient.
     *        - 'immediate': Sends a packet instantly for this specific change. Use for high-priority updates.
     * @param permission Defines who can modify the value.
     *        - 'hybrid' (default): Both server and client can modify the value.
     *        - 'server_only': Only the server can modify the value. Client receives updates but cannot change it.
     *        - 'client_readonly': Same as 'server_only'.
     */
    set(key: string, value: any, syncMode?: 'batched' | 'immediate', permission?: 'hybrid' | 'server_only' | 'client_readonly'): void;

    /**
     * Gets a value from the store. This reads the server's local copy of the data.
     * @param key The key of the data to retrieve.
     * @returns The value associated with the key, or undefined if it doesn't exist.
     */
    get<T = any>(key: string): T | undefined;

    /**
     * Checks if a key exists in the store.
     * @returns True if the key has a value (including null).
     */
    has(key: string): boolean;

    /**
     * Removes a key-value pair from the store and synchronizes the removal to the client.
     */
    remove(key: string): void;

    /**
     * Registers a callback that fires when any value in this store is changed,
     * either by the server or an update from the client.
     * @param event Must be the string 'change'.
     * @param callback A function that receives the key, the new value, and the old value.
     */
    on(event: 'change', callback: (key: string, newValue: any, oldValue: any) => void): void;

    /**
     * Registers a callback that fires only when a specific key's value changes.
     * @param key The specific key to watch for changes.
     * @param callback A function that receives the new value and the old value.
     */
    onChange(key: string, callback: (newValue: any, oldValue: any) => void): void;
}
}
export {};
