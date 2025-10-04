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
     * Registers a callback that fires *only once* when the server finishes its initial startup.
     * This event does not fire on hot reloads, making it ideal for persistent, one-time setup.
     */
    on(eventName: 'server.load', callback: () => void): void;
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
 * Options for performing a world raycast.
 */
interface RaycastOptions {
    /** The starting point of the ray. */
    origin: Vector3;
    /** A normalized vector indicating the direction of the ray. */
    direction: Vector3;
    /** The maximum distance the ray should travel. Defaults to 100. */
    maxDistance?: number;
    /** A specific player to ignore during the raycast. Essential for first-person casting. */
    ignorePlayer?: Player;
}

/**
 * The result of a world raycast operation.
 */
interface RaycastResult {
    /** True if the raycast hit a block or an entity. */
    readonly didHit: boolean;
    /** The world position where the raycast hit, or where it ended if it missed. */
    readonly position: Vector3;
    /** The surface normal of the block face that was hit, or null if an entity was hit or it missed. */
    readonly normal: Vector3 | null;
    /** The entity that was hit, or null. */
    readonly entity: Player | null; // Note: Can be other entity types in the future
    /** The namespaced ID of the block that was hit (e.g., 'minecraft:stone'), or null. */
    readonly blockType: string | null;
    /** The distance from the origin to the hit point. */
    readonly distance: number;
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

    /** @returns A normalized Vector3 representing the direction the player's head is facing. Works for all clients (vanilla and modded). */
    getDirection(): Vector3;

    /** @returns A normalized Vector3 representing the direction the player's camera is looking. Requires the Moud client mod for full accuracy. */
    getCameraDirection(): Vector3;

    /** @returns A Vector3 where x is yaw and y is pitch, representing the player's head rotation. */
    getHeadRotation(): Vector3;

    /** @returns The player's yaw (horizontal rotation) in degrees. */
    getYaw(): number;

    /** @returns The player's pitch (vertical rotation) in degrees. */
    getPitch(): number;

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

    // --- Added missing movement state methods ---

    /** @returns True if the player is moving on the ground but not sprinting or sneaking. */
    isWalking(): boolean;

    /** @returns True if the player is sprinting. */
    isRunning(): boolean;

    /** @returns True if the player is sneaking. */
    isSneaking(): boolean;

    /** @returns True if the player is currently in the act of jumping. */
    isJumping(): boolean;

    /** @returns True if the player is on solid ground. */
    isOnGround(): boolean;

    /** @returns The player's current movement type as a string. Requires Moud client mod. */
    getMovementType(): 'standing' | 'walking' | 'sneaking' | 'sprinting' | 'unknown';

    /** @returns The player's current movement direction as a string (e.g., 'north', 'southwest', 'none'). Requires Moud client mod. */
    getMovementDirection(): string;

    /** @returns The player's current movement speed as reported by the client. Requires Moud client mod. */
    getMovementSpeed(): number;

    /**
     * Accesses the API for controlling player model animations and individual parts.
     * @returns The PlayerAnimation API proxy.
    */
    getAnimation(): PlayerAnimation;

     /**
     * Makes the player invisible to others, but still physically present.
     */
     setVanished(vanished: boolean): void;
    /**
     * Checks if the player is actively moving (walking, sprinting, or sneaking).
     * @returns `true` if the player is moving, `false` otherwise.
     */
    isMoving(): boolean;

    /**
     * Modifies the transform (position, rotation, scale) of a specific player model part.
     * @param partName The name of the part to modify ('head', 'body', 'right_arm', etc.).
     * @param options The transformations and settings to apply.
     */
    setPartConfig(partName: PlayerPartName, options: PartConfigOptions): void;

    /**
     * Defines the global interpolation settings for this player's part animations.
     * @param settings The interpolation settings to apply to subsequent transformations.
     */
    setInterpolationSettings(settings: InterpolationOptions): void;

    /**
     * Plays a specific animation on the player's model.
     * @param animationId The ID of the animation to play (e.g., 'moud:wave').
     */
    playAnimation(animationId: string): void;

    /**
     * Makes the player's arms point towards a world position.
     * @param targetPosition The world position to point at.
     * @param options Optional interpolation settings for a smooth transition.
     */
    pointToPosition(targetPosition: Vector3, options?: { interpolation?: InterpolationOptions }): void;

    /**
     * Configures the visibility of the player's model parts in first-person view.
     * @param config An object specifying which parts to show or hide.
     */
    setFirstPersonConfig(config: {
        showRightArm?: boolean;
        showLeftArm?: boolean;
        showRightItem?: boolean;
        showLeftItem?: boolean;
        showArmor?: boolean;
    }): void;

    /**
     * Resets all part modifications made by `setPartConfig` back to their default state.
     */
    resetAllParts(): void;
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

    /**
     * Attempts to find an online player by their exact username.
     * @param name The case-sensitive Minecraft username to search for.
     * @returns The player proxy if the player is online, otherwise null.
     */
    getPlayer(name: string): Player | null;

    /**
     * Attempts to find an online player by UUID string.
     * @param uuid The UUID of the player (with hyphens).
     * @returns The player proxy if the player is online, otherwise null.
     */
    getPlayerByUuid(uuid: string): Player | null;

    /**
     * Checks whether a player with the given username is currently connected.
     * @param name The username to search for.
     * @returns True if the player is online, otherwise false.
     */
    hasPlayer(name: string): boolean;

    /**
     * Retrieves the usernames of all currently online players.
     * @returns An array of usernames in login order.
     */
    getPlayerNames(): string[];

    /**
     * Executes a console command on the server.
     * @param command The full command string to execute.
     */
    runCommand(command: string): void;

    /**
     * Sends an action bar message to every online player.
     * @param message The message to display above the hotbar.
     */
    broadcastActionBar(message: string): void;
}

/**
 * Options for creating a player model.
 */
interface PlayerModelOptions {
    /** The initial position of the model in the world. */
    position: Vector3;
    /** The URL of the skin to apply to the model. */
    skinUrl?: string;

    /**
     * Makes the model walk towards a target destination.
     * The model will automatically rotate to face the target.
     * @param target The world position (Vector3) to walk to.
     * @param options Optional settings, like speed.
     */
    walkTo(target: Vector3, options?: { speed?: number }): void;

    /**
     * Immediately stops the model's current walking movement.
     * The model will transition to its idle animation.
     */
    stopWalking(): void;

    /**
     * Checks if the model is currently executing a `walkTo` command.
     * @returns `true` if the model is walking, `false` otherwise.
     */
    isWalking(): boolean;

    /**
     * Changes the skin of the model.
     * @param skinUrl The URL of the new skin texture.
     */
    setSkin(skinUrl: string): void;

    /**
     * Plays an animation, smoothly fading from the current animation.
     * @param animationName The ID of the animation to play.
     * @param durationMs The duration of the fade transition in milliseconds.
     */
    playAnimationWithFade(animationName: string, durationMs: number): void;

    /**
     * Plays an animation on the model, instantly overriding the current one.
     * @param animationName The ID of the animation to play (e.g., 'moud:idle', 'moud:walk').
     */
    playAnimation(animationName: string): void;

    /**
     * Removes the model from the world for all players.
     */
    remove(): void;

    /**
     * Registers a callback function to execute when a player clicks on this model.
     * @param callback The function to execute. It receives the player who clicked and click details.
     */
    onClick(callback: (player: Player, clickData: { button: number; mouseX: number; mouseY: number }) => void): void;
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
    /**
     * Creates an invisible, clickable hitbox for this text.
     * If width and height are not provided, the hitbox is automatically sized based on the text content.
     * @param width The width of the hitbox in blocks.
     * @param height The height of the hitbox in blocks.
     */
    enableHitbox(width?: number, height?: number): void;

    /**
     * Removes the clickable hitbox from this text.
     */
    disableHitbox(): void;

    /**
     * Removes the text display (and its hitbox, if any) from the world.
     */
    remove(): void;

    /**
     * Gets the current world position of the text.
     * @returns The position as a Vector3.
     */
    getPosition(): Vector3;

    /**
     * Gets the unique UUID of the interaction entity (hitbox).
     * This is useful for identifying the text in an 'entity.interact' event.
     * @returns The UUID string of the hitbox, or null if no hitbox is enabled.
     */
    getInteractionUuid(): string | null;
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
     /**
       * Performs a raycast through the world to find the first block or entity it intersects with.
       * @param options The configuration for the raycast.
       * @returns A RaycastResult object with detailed information about the hit.
     */
     raycast(options: RaycastOptions): RaycastResult;
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
    /**
     * Activates the custom camera mode for the player.
     */
    enableCustomCamera(): void;

    /**
     * Deactivates the custom camera mode and returns control to the player.
     * The player's view will snap back to their character's perspective.
     */
    disableCustomCamera(): void;

    /**
     * Smoothly moves the camera from its current state to a new target state over a specified duration.
     * This method only works when the custom camera is enabled.
     * @param options The target state and animation settings for the transition.
     */
    transitionTo(options: CameraTransitionOptions): void;

    /**
     * Instantly teleports the camera to a new state.
     * This method only works when the custom camera is enabled.
     * @param options The target state for the camera.
     */
    snapTo(options: CameraStateOptions): void;

    /**
     * Checks if the custom camera mode is currently active for the player.
     * @returns `true` if the custom camera is enabled, `false` otherwise.
     */
    isCustomCameraActive(): boolean;
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
    isHotbarHidden(...args: any[]): boolean;
    isHealthHidden(...args: any[]): boolean;
    isFoodHidden(...args: any[]): boolean;
    isExperienceHidden(...args: any[]): boolean;
    isHandHidden(...args: any[]): boolean;
    isCrosshairHidden(...args: any[]): boolean;
    isChatHidden(...args: any[]): boolean;
    isPlayerListHidden(...args: any[]): boolean;
    isScoreboardHidden(...args: any[]): boolean;
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

    projectOntoBlock(...args: any[]): void;

    getScale(...args: any[]): number;

    getTexture(...args: any[]): string;

    getColor(...args: any[]): Vector3;
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

    /**
     * Auto-generated from Java method 'cancel'.
     * Please specify parameters and update return type if necessary.
     */
    cancel(...args: any[]): void;
    /**
     * Auto-generated from Java method 'isCancelled'.
     * Please specify parameters and update return type if necessary.
     */
    isCancelled(...args: any[]): boolean;
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

    /**
     * Auto-generated from Java method 'cancel'.
     * Please specify parameters and update return type if necessary.
     */
    cancel(...args: any[]): void;
    /**
     * Auto-generated from Java method 'isCancelled'.
     * Please specify parameters and update return type if necessary.
     */
    isCancelled(...args: any[]): boolean;
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

    /**
     * Auto-generated from Java method 'cancel'.
     * Please specify parameters and update return type if necessary.
     */
    cancel(...args: any[]): void;
    /**
     * Auto-generated from Java method 'isCancelled'.
     * Please specify parameters and update return type if necessary.
     */
    isCancelled(...args: any[]): boolean;
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

    /**
     * Auto-generated from Java method 'getPlayer'.
     * Please specify parameters and update return type if necessary.
     */
    getPlayer(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getEntityType'.
     * Please specify parameters and update return type if necessary.
     */
    getEntityType(...args: any[]): string;
    /**
     * Auto-generated from Java method 'getEntityUuid'.
     * Please specify parameters and update return type if necessary.
     */
    getEntityUuid(...args: any[]): string;
    /**
     * Auto-generated from Java method 'getInteractionType'.
     * Please specify parameters and update return type if necessary.
     */
    getInteractionType(...args: any[]): string;
    /**
     * Auto-generated from Java method 'getEntityX'.
     * Please specify parameters and update return type if necessary.
     */
    getEntityX(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getEntityY'.
     * Please specify parameters and update return type if necessary.
     */
    getEntityY(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getEntityZ'.
     * Please specify parameters and update return type if necessary.
     */
    getEntityZ(...args: any[]): number;
    /**
     * Auto-generated from Java method 'isHoverEnter'.
     * Please specify parameters and update return type if necessary.
     */
    isHoverEnter(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'isHoverExit'.
     * Please specify parameters and update return type if necessary.
     */
    isHoverExit(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'isClick'.
     * Please specify parameters and update return type if necessary.
     */
    isClick(...args: any[]): boolean;
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

    /**
     * Auto-generated from Java method 'set'.
     * Please specify parameters and update return type if necessary.
     */
    set(...args: any[]): void;
    /**
     * Auto-generated from Java method 'get'.
     * Please specify parameters and update return type if necessary.
     */
    get(...args: any[]): any;
    /**
     * Auto-generated from Java method 'has'.
     * Please specify parameters and update return type if necessary.
     */
    has(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'remove'.
     * Please specify parameters and update return type if necessary.
     */
    remove(...args: any[]): void;
    /**
     * Auto-generated from Java method 'on'.
     * Please specify parameters and update return type if necessary.
     */
    on(...args: any[]): void;
    /**
     * Auto-generated from Java method 'onChange'.
     * Please specify parameters and update return type if necessary.
     */
    onChange(...args: any[]): void;
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


// --- Auto-generated interfaces ---

/**
 * Auto-generated for Java class `AssetProxy`.
 */
declare interface Asset {
    /**
     * Auto-generated from Java method 'loadShader'.
     * Please specify parameters and update return type if necessary.
     */
    loadShader(...args: any[]): any;
    /**
     * Auto-generated from Java method 'loadTexture'.
     * Please specify parameters and update return type if necessary.
     */
    loadTexture(...args: any[]): any;
    /**
     * Auto-generated from Java method 'loadData'.
     * Please specify parameters and update return type if necessary.
     */
    loadData(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getId'.
     * Please specify parameters and update return type if necessary.
     */
    getId(...args: any[]): string;
    /**
     * Auto-generated from Java method 'getCode'.
     * Please specify parameters and update return type if necessary.
     */
    getCode(...args: any[]): string;
    /**
     * Auto-generated from Java method 'getData'.
     * Please specify parameters and update return type if necessary.
     */
    getData(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getContent'.
     * Please specify parameters and update return type if necessary.
     */
    getContent(...args: any[]): string;
}

/**
 * Auto-generated for Java class `CameraService`.
 */
declare interface CameraService {
    /**
     * Auto-generated from Java method 'enableCustomCamera'.
     * Please specify parameters and update return type if necessary.
     */
    enableCustomCamera(...args: any[]): void;
    /**
     * Auto-generated from Java method 'disableCustomCamera'.
     * Please specify parameters and update return type if necessary.
     */
    disableCustomCamera(...args: any[]): void;
    /**
     * Auto-generated from Java method 'transitionTo'.
     * Please specify parameters and update return type if necessary.
     */
    transitionTo(...args: any[]): void;
    /**
     * Auto-generated from Java method 'snapTo'.
     * Please specify parameters and update return type if necessary.
     */
    snapTo(...args: any[]): void;
    /**
     * Auto-generated from Java method 'isCustomCameraActive'.
     * Please specify parameters and update return type if necessary.
     */
    isCustomCameraActive(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'getPlayerX'.
     * Please specify parameters and update return type if necessary.
     */
    getPlayerX(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getPlayerY'.
     * Please specify parameters and update return type if necessary.
     */
    getPlayerY(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getPlayerZ'.
     * Please specify parameters and update return type if necessary.
     */
    getPlayerZ(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getPlayerYaw'.
     * Please specify parameters and update return type if necessary.
     */
    getPlayerYaw(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getPlayerPitch'.
     * Please specify parameters and update return type if necessary.
     */
    getPlayerPitch(...args: any[]): number;
    /**
     * Auto-generated from Java method 'createVector3'.
     * Please specify parameters and update return type if necessary.
     */
    createVector3(...args: any[]): Vector3;
    /**
     * Auto-generated from Java method 'isThirdPerson'.
     * Please specify parameters and update return type if necessary.
     */
    isThirdPerson(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'setThirdPerson'.
     * Please specify parameters and update return type if necessary.
     */
    setThirdPerson(...args: any[]): void;
    /**
     * Auto-generated from Java method 'getFov'.
     * Please specify parameters and update return type if necessary.
     */
    getFov(...args: any[]): number;
}

/**
 * Auto-generated for Java class `ClientProxy`.
 */
declare interface Client {
    /**
     * Auto-generated from Java method 'send'.
     * Please specify parameters and update return type if necessary.
     */
    send(...args: any[]): void;
}

/**
 * Auto-generated for Java class `ClientSharedApiProxy`.
 */
declare interface ClientSharedApi {
    /**
     * Auto-generated from Java method 'getStore'.
     * Please specify parameters and update return type if necessary.
     */
    getStore(...args: any[]): any;
    /**
     * Auto-generated from Java method 'get'.
     * Please specify parameters and update return type if necessary.
     */
    get(...args: any[]): any;
    /**
     * Auto-generated from Java method 'has'.
     * Please specify parameters and update return type if necessary.
     */
    has(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'set'.
     * Please specify parameters and update return type if necessary.
     */
    set(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'on'.
     * Please specify parameters and update return type if necessary.
     */
    on(...args: any[]): void;
    /**
     * Auto-generated from Java method 'onChange'.
     * Please specify parameters and update return type if necessary.
     */
    onChange(...args: any[]): void;
    /**
     * Auto-generated from Java method 'canModify'.
     * Please specify parameters and update return type if necessary.
     */
    canModify(...args: any[]): boolean;
}

/**
 * Auto-generated for Java class `CommandProxy`.
 */
declare interface Command {
    /**
     * Auto-generated from Java method 'register'.
     * Please specify parameters and update return type if necessary.
     */
    register(...args: any[]): void;
    /**
     * Auto-generated from Java method 'registerWithAliases'.
     * Please specify parameters and update return type if necessary.
     */
    registerWithAliases(...args: any[]): void;
}

/**
 * Auto-generated for Java class `ConsoleAPI`.
 */
declare interface ConsoleAPI {
    /**
     * Auto-generated from Java method 'log'.
     * Please specify parameters and update return type if necessary.
     */
    log(...args: any[]): void;
    /**
     * Auto-generated from Java method 'warn'.
     * Please specify parameters and update return type if necessary.
     */
    warn(...args: any[]): void;
    /**
     * Auto-generated from Java method 'error'.
     * Please specify parameters and update return type if necessary.
     */
    error(...args: any[]): void;
    /**
     * Auto-generated from Java method 'debug'.
     * Please specify parameters and update return type if necessary.
     */
    debug(...args: any[]): void;
}

/**
 * Auto-generated for Java class `CursorService`.
 */
declare interface CursorService {
    /**
     * Auto-generated from Java method 'show'.
     * Please specify parameters and update return type if necessary.
     */
    show(...args: any[]): void;
    /**
     * Auto-generated from Java method 'hide'.
     * Please specify parameters and update return type if necessary.
     */
    hide(...args: any[]): void;
    /**
     * Auto-generated from Java method 'toggle'.
     * Please specify parameters and update return type if necessary.
     */
    toggle(...args: any[]): void;
    /**
     * Auto-generated from Java method 'isVisible'.
     * Please specify parameters and update return type if necessary.
     */
    isVisible(...args: any[]): boolean;
}

/**
 * Auto-generated for Java class `EventService`.
 */
declare interface EventService {
    /**
     * Auto-generated from Java method 'on'.
     * Please specify parameters and update return type if necessary.
     */
    on(...args: any[]): void;
    /**
     * Auto-generated from Java method 'dispatch'.
     * Please specify parameters and update return type if necessary.
     */
    dispatch(...args: any[]): void;
}

/**
 * Auto-generated for Java class `InputService`.
 */
declare interface InputService {
    /**
     * Auto-generated from Java method 'isKeyPressed'.
     * Please specify parameters and update return type if necessary.
     */
    isKeyPressed(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'isMouseButtonPressed'.
     * Please specify parameters and update return type if necessary.
     */
    isMouseButtonPressed(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'getMouseX'.
     * Please specify parameters and update return type if necessary.
     */
    getMouseX(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getMouseY'.
     * Please specify parameters and update return type if necessary.
     */
    getMouseY(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getMouseDeltaX'.
     * Please specify parameters and update return type if necessary.
     */
    getMouseDeltaX(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getMouseDeltaY'.
     * Please specify parameters and update return type if necessary.
     */
    getMouseDeltaY(...args: any[]): number;
    /**
     * Auto-generated from Java method 'onKey'.
     * Please specify parameters and update return type if necessary.
     */
    onKey(...args: any[]): void;
    /**
     * Auto-generated from Java method 'onMouseButton'.
     * Please specify parameters and update return type if necessary.
     */
    onMouseButton(...args: any[]): void;
    /**
     * Auto-generated from Java method 'onMouseMove'.
     * Please specify parameters and update return type if necessary.
     */
    onMouseMove(...args: any[]): void;
    /**
     * Auto-generated from Java method 'onScroll'.
     * Please specify parameters and update return type if necessary.
     */
    onScroll(...args: any[]): void;
    /**
     * Auto-generated from Java method 'isMovingForward'.
     * Please specify parameters and update return type if necessary.
     */
    isMovingForward(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'isMovingBackward'.
     * Please specify parameters and update return type if necessary.
     */
    isMovingBackward(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'isStrafingLeft'.
     * Please specify parameters and update return type if necessary.
     */
    isStrafingLeft(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'isStrafingRight'.
     * Please specify parameters and update return type if necessary.
     */
    isStrafingRight(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'isJumping'.
     * Please specify parameters and update return type if necessary.
     */
    isJumping(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'isSprinting'.
     * Please specify parameters and update return type if necessary.
     */
    isSprinting(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'isOnGround'.
     * Please specify parameters and update return type if necessary.
     */
    isOnGround(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'isMoving'.
     * Please specify parameters and update return type if necessary.
     */
    isMoving(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'lockMouse'.
     * Please specify parameters and update return type if necessary.
     */
    lockMouse(...args: any[]): void;
    /**
     * Auto-generated from Java method 'isMouseLocked'.
     * Please specify parameters and update return type if necessary.
     */
    isMouseLocked(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'getMouseSensitivity'.
     * Please specify parameters and update return type if necessary.
     */
    getMouseSensitivity(...args: any[]): number;
    /**
     * Auto-generated from Java method 'setMouseSensitivity'.
     * Please specify parameters and update return type if necessary.
     */
    setMouseSensitivity(...args: any[]): void;
}

/**
 * Auto-generated for Java class `MathProxy`.
 */
declare interface Math {
    /**
     * Auto-generated from Java method 'clamp'.
     * Please specify parameters and update return type if necessary.
     */
    clamp(...args: any[]): number;
    /**
     * Auto-generated from Java method 'lerp'.
     * Please specify parameters and update return type if necessary.
     */
    lerp(...args: any[]): number;
    /**
     * Auto-generated from Java method 'atan2'.
     * Please specify parameters and update return type if necessary.
     */
    atan2(...args: any[]): number;
    /**
     * Auto-generated from Java method 'sin'.
     * Please specify parameters and update return type if necessary.
     */
    sin(...args: any[]): number;
    /**
     * Auto-generated from Java method 'cos'.
     * Please specify parameters and update return type if necessary.
     */
    cos(...args: any[]): number;
    /**
     * Auto-generated from Java method 'tan'.
     * Please specify parameters and update return type if necessary.
     */
    tan(...args: any[]): number;
    /**
     * Auto-generated from Java method 'asin'.
     * Please specify parameters and update return type if necessary.
     */
    asin(...args: any[]): number;
    /**
     * Auto-generated from Java method 'acos'.
     * Please specify parameters and update return type if necessary.
     */
    acos(...args: any[]): number;
    /**
     * Auto-generated from Java method 'atan'.
     * Please specify parameters and update return type if necessary.
     */
    atan(...args: any[]): number;
    /**
     * Auto-generated from Java method 'sqrt'.
     * Please specify parameters and update return type if necessary.
     */
    sqrt(...args: any[]): number;
    /**
     * Auto-generated from Java method 'abs'.
     * Please specify parameters and update return type if necessary.
     */
    abs(...args: any[]): number;
    /**
     * Auto-generated from Java method 'min'.
     * Please specify parameters and update return type if necessary.
     */
    min(...args: any[]): number;
    /**
     * Auto-generated from Java method 'max'.
     * Please specify parameters and update return type if necessary.
     */
    max(...args: any[]): number;
    /**
     * Auto-generated from Java method 'floor'.
     * Please specify parameters and update return type if necessary.
     */
    floor(...args: any[]): number;
    /**
     * Auto-generated from Java method 'ceil'.
     * Please specify parameters and update return type if necessary.
     */
    ceil(...args: any[]): number;
    /**
     * Auto-generated from Java method 'round'.
     * Please specify parameters and update return type if necessary.
     */
    round(...args: any[]): number;
    /**
     * Auto-generated from Java method 'toRadians'.
     * Please specify parameters and update return type if necessary.
     */
    toRadians(...args: any[]): number;
    /**
     * Auto-generated from Java method 'toDegrees'.
     * Please specify parameters and update return type if necessary.
     */
    toDegrees(...args: any[]): number;
    /**
     * Auto-generated from Java method 'vector3'.
     * Please specify parameters and update return type if necessary.
     */
    vector3(...args: any[]): Vector3;
    /**
     * Auto-generated from Java method 'quaternion'.
     * Please specify parameters and update return type if necessary.
     */
    quaternion(...args: any[]): Quaternion;
    /**
     * Auto-generated from Java method 'quaternionFromEuler'.
     * Please specify parameters and update return type if necessary.
     */
    quaternionFromEuler(...args: any[]): Quaternion;
    /**
     * Auto-generated from Java method 'quaternionFromAxisAngle'.
     * Please specify parameters and update return type if necessary.
     */
    quaternionFromAxisAngle(...args: any[]): Quaternion;
    /**
     * Auto-generated from Java method 'matrix4'.
     * Please specify parameters and update return type if necessary.
     */
    matrix4(...args: any[]): any;
    /**
     * Auto-generated from Java method 'matrix4Identity'.
     * Please specify parameters and update return type if necessary.
     */
    matrix4Identity(...args: any[]): any;
    /**
     * Auto-generated from Java method 'matrix4Translation'.
     * Please specify parameters and update return type if necessary.
     */
    matrix4Translation(...args: any[]): any;
    /**
     * Auto-generated from Java method 'matrix4Rotation'.
     * Please specify parameters and update return type if necessary.
     */
    matrix4Rotation(...args: any[]): any;
    /**
     * Auto-generated from Java method 'matrix4Scaling'.
     * Please specify parameters and update return type if necessary.
     */
    matrix4Scaling(...args: any[]): any;
    /**
     * Auto-generated from Java method 'matrix4TRS'.
     * Please specify parameters and update return type if necessary.
     */
    matrix4TRS(...args: any[]): any;
    /**
     * Auto-generated from Java method 'matrix4Perspective'.
     * Please specify parameters and update return type if necessary.
     */
    matrix4Perspective(...args: any[]): any;
    /**
     * Auto-generated from Java method 'matrix4Orthographic'.
     * Please specify parameters and update return type if necessary.
     */
    matrix4Orthographic(...args: any[]): any;
    /**
     * Auto-generated from Java method 'matrix4LookAt'.
     * Please specify parameters and update return type if necessary.
     */
    matrix4LookAt(...args: any[]): any;
    /**
     * Auto-generated from Java method 'transform'.
     * Please specify parameters and update return type if necessary.
     */
    transform(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getVector3Zero'.
     * Please specify parameters and update return type if necessary.
     */
    getVector3Zero(...args: any[]): Vector3;
    /**
     * Auto-generated from Java method 'getVector3One'.
     * Please specify parameters and update return type if necessary.
     */
    getVector3One(...args: any[]): Vector3;
    /**
     * Auto-generated from Java method 'getVector3Up'.
     * Please specify parameters and update return type if necessary.
     */
    getVector3Up(...args: any[]): Vector3;
    /**
     * Auto-generated from Java method 'getVector3Down'.
     * Please specify parameters and update return type if necessary.
     */
    getVector3Down(...args: any[]): Vector3;
    /**
     * Auto-generated from Java method 'getVector3Left'.
     * Please specify parameters and update return type if necessary.
     */
    getVector3Left(...args: any[]): Vector3;
    /**
     * Auto-generated from Java method 'getVector3Right'.
     * Please specify parameters and update return type if necessary.
     */
    getVector3Right(...args: any[]): Vector3;
    /**
     * Auto-generated from Java method 'getVector3Forward'.
     * Please specify parameters and update return type if necessary.
     */
    getVector3Forward(...args: any[]): Vector3;
    /**
     * Auto-generated from Java method 'getVector3Backward'.
     * Please specify parameters and update return type if necessary.
     */
    getVector3Backward(...args: any[]): Vector3;
    /**
     * Auto-generated from Java method 'getQuaternionIdentity'.
     * Please specify parameters and update return type if necessary.
     */
    getQuaternionIdentity(...args: any[]): Quaternion;
    /**
     * Auto-generated from Java method 'getPI'.
     * Please specify parameters and update return type if necessary.
     */
    getPI(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getTWO_PI'.
     * Please specify parameters and update return type if necessary.
     */
    getTWO_PI(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getHALF_PI'.
     * Please specify parameters and update return type if necessary.
     */
    getHALF_PI(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getDEG_TO_RAD'.
     * Please specify parameters and update return type if necessary.
     */
    getDEG_TO_RAD(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getRAD_TO_DEG'.
     * Please specify parameters and update return type if necessary.
     */
    getRAD_TO_DEG(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getEPSILON'.
     * Please specify parameters and update return type if necessary.
     */
    getEPSILON(...args: any[]): number;
    /**
     * Auto-generated from Java method 'distancePointToLine'.
     * Please specify parameters and update return type if necessary.
     */
    distancePointToLine(...args: any[]): number;
    /**
     * Auto-generated from Java method 'closestPointOnLine'.
     * Please specify parameters and update return type if necessary.
     */
    closestPointOnLine(...args: any[]): Vector3;
    /**
     * Auto-generated from Java method 'sphereIntersection'.
     * Please specify parameters and update return type if necessary.
     */
    sphereIntersection(...args: any[]): boolean;
}

/**
 * Auto-generated for Java class `NetworkService`.
 */
declare interface NetworkService {
    /**
     * Auto-generated from Java method 'sendToServer'.
     * Please specify parameters and update return type if necessary.
     */
    sendToServer(...args: any[]): void;
    /**
     * Auto-generated from Java method 'on'.
     * Please specify parameters and update return type if necessary.
     */
    on(...args: any[]): void;
}

/**
 * Auto-generated for Java class `PlayerWindowProxy`.
 */
declare interface PlayerWindow {
    /**
     * Auto-generated from Java method 'transitionTo'.
     * Please specify parameters and update return type if necessary.
     */
    transitionTo(...args: any[]): void;
    /**
     * Auto-generated from Java method 'playSequence'.
     * Please specify parameters and update return type if necessary.
     */
    playSequence(...args: any[]): void;
    /**
     * Auto-generated from Java method 'setTitle'.
     * Please specify parameters and update return type if necessary.
     */
    setTitle(...args: any[]): void;
    /**
     * Auto-generated from Java method 'setBorderless'.
     * Please specify parameters and update return type if necessary.
     */
    setBorderless(...args: any[]): void;
    /**
     * Auto-generated from Java method 'maximize'.
     * Please specify parameters and update return type if necessary.
     */
    maximize(...args: any[]): void;
    /**
     * Auto-generated from Java method 'minimize'.
     * Please specify parameters and update return type if necessary.
     */
    minimize(...args: any[]): void;
    /**
     * Auto-generated from Java method 'restore'.
     * Please specify parameters and update return type if necessary.
     */
    restore(...args: any[]): void;
}

/**
 * Auto-generated for Java class `RenderingService`.
 */
declare interface RenderingService {
    /**
     * Auto-generated from Java method 'requestAnimationFrame'.
     * Please specify parameters and update return type if necessary.
     */
    requestAnimationFrame(...args: any[]): string;
    /**
     * Auto-generated from Java method 'cancelAnimationFrame'.
     * Please specify parameters and update return type if necessary.
     */
    cancelAnimationFrame(...args: any[]): void;
    /**
     * Auto-generated from Java method 'createRenderType'.
     * Please specify parameters and update return type if necessary.
     */
    createRenderType(...args: any[]): string;
    /**
     * Auto-generated from Java method 'setShaderUniform'.
     * Please specify parameters and update return type if necessary.
     */
    setShaderUniform(...args: any[]): void;
}

/**
 * Auto-generated for Java class `ScriptingAPI`.
 */
declare interface ScriptingAPI {
    /**
     * Auto-generated from Java method 'on'.
     * Please specify parameters and update return type if necessary.
     */
    on(...args: any[]): void;
    /**
     * Auto-generated from Java method 'getAsync'.
     * Please specify parameters and update return type if necessary.
     */
    getAsync(...args: any[]): any;
}

/**
 * Auto-generated for Java class `UIComponent`.
 */
declare interface UIComponent {
    /**
     * Auto-generated from Java method 'showAsOverlay'.
     * Please specify parameters and update return type if necessary.
     */
    showAsOverlay(...args: any[]): any;
    /**
     * Auto-generated from Java method 'hideOverlay'.
     * Please specify parameters and update return type if necessary.
     */
    hideOverlay(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getX'.
     * Please specify parameters and update return type if necessary.
     */
    getX(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getY'.
     * Please specify parameters and update return type if necessary.
     */
    getY(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getWidth'.
     * Please specify parameters and update return type if necessary.
     */
    getWidth(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getHeight'.
     * Please specify parameters and update return type if necessary.
     */
    getHeight(...args: any[]): number;
    /**
     * Auto-generated from Java method 'setX'.
     * Please specify parameters and update return type if necessary.
     */
    setX(...args: any[]): any;
    /**
     * Auto-generated from Java method 'setY'.
     * Please specify parameters and update return type if necessary.
     */
    setY(...args: any[]): any;
    /**
     * Auto-generated from Java method 'setWidth'.
     * Please specify parameters and update return type if necessary.
     */
    setWidth(...args: any[]): any;
    /**
     * Auto-generated from Java method 'setHeight'.
     * Please specify parameters and update return type if necessary.
     */
    setHeight(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getComponentId'.
     * Please specify parameters and update return type if necessary.
     */
    getComponentId(...args: any[]): string;
    /**
     * Auto-generated from Java method 'setComponentId'.
     * Please specify parameters and update return type if necessary.
     */
    setComponentId(...args: any[]): any;
    /**
     * Auto-generated from Java method 'setText'.
     * Please specify parameters and update return type if necessary.
     */
    setText(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getText'.
     * Please specify parameters and update return type if necessary.
     */
    getText(...args: any[]): string;
    /**
     * Auto-generated from Java method 'setPos'.
     * Please specify parameters and update return type if necessary.
     */
    setPos(...args: any[]): any;
    /**
     * Auto-generated from Java method 'setSize'.
     * Please specify parameters and update return type if necessary.
     */
    setSize(...args: any[]): any;
    /**
     * Auto-generated from Java method 'setBackgroundColor'.
     * Please specify parameters and update return type if necessary.
     */
    setBackgroundColor(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getBackgroundColor'.
     * Please specify parameters and update return type if necessary.
     */
    getBackgroundColor(...args: any[]): string;
    /**
     * Auto-generated from Java method 'setTextColor'.
     * Please specify parameters and update return type if necessary.
     */
    setTextColor(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getTextColor'.
     * Please specify parameters and update return type if necessary.
     */
    getTextColor(...args: any[]): string;
    /**
     * Auto-generated from Java method 'setBorder'.
     * Please specify parameters and update return type if necessary.
     */
    setBorder(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getBorderWidth'.
     * Please specify parameters and update return type if necessary.
     */
    getBorderWidth(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getBorderColor'.
     * Please specify parameters and update return type if necessary.
     */
    getBorderColor(...args: any[]): string;
    /**
     * Auto-generated from Java method 'setOpacity'.
     * Please specify parameters and update return type if necessary.
     */
    setOpacity(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getOpacity'.
     * Please specify parameters and update return type if necessary.
     */
    getOpacity(...args: any[]): number;
    /**
     * Auto-generated from Java method 'setTextAlign'.
     * Please specify parameters and update return type if necessary.
     */
    setTextAlign(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getTextAlign'.
     * Please specify parameters and update return type if necessary.
     */
    getTextAlign(...args: any[]): string;
    /**
     * Auto-generated from Java method 'setPadding'.
     * Please specify parameters and update return type if necessary.
     */
    setPadding(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getPaddingTop'.
     * Please specify parameters and update return type if necessary.
     */
    getPaddingTop(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getPaddingRight'.
     * Please specify parameters and update return type if necessary.
     */
    getPaddingRight(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getPaddingBottom'.
     * Please specify parameters and update return type if necessary.
     */
    getPaddingBottom(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getPaddingLeft'.
     * Please specify parameters and update return type if necessary.
     */
    getPaddingLeft(...args: any[]): number;
    /**
     * Auto-generated from Java method 'appendChild'.
     * Please specify parameters and update return type if necessary.
     */
    appendChild(...args: any[]): any;
    /**
     * Auto-generated from Java method 'removeChild'.
     * Please specify parameters and update return type if necessary.
     */
    removeChild(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getChildren'.
     * Please specify parameters and update return type if necessary.
     */
    getChildren(...args: any[]): any;
    /**
     * Auto-generated from Java method 'show'.
     * Please specify parameters and update return type if necessary.
     */
    show(...args: any[]): any;
    /**
     * Auto-generated from Java method 'hide'.
     * Please specify parameters and update return type if necessary.
     */
    hide(...args: any[]): any;
    /**
     * Auto-generated from Java method 'isVisible'.
     * Please specify parameters and update return type if necessary.
     */
    isVisible(...args: any[]): boolean;
    /**
     * Auto-generated from Java method 'onClick'.
     * Please specify parameters and update return type if necessary.
     */
    onClick(...args: any[]): any;
    /**
     * Auto-generated from Java method 'onHover'.
     * Please specify parameters and update return type if necessary.
     */
    onHover(...args: any[]): any;
    /**
     * Auto-generated from Java method 'onFocus'.
     * Please specify parameters and update return type if necessary.
     */
    onFocus(...args: any[]): any;
    /**
     * Auto-generated from Java method 'onBlur'.
     * Please specify parameters and update return type if necessary.
     */
    onBlur(...args: any[]): any;
}

/**
 * Auto-generated for Java class `UIContainer`.
 */
declare interface UIContainer {
    /**
     * Auto-generated from Java method 'appendChild'.
     * Please specify parameters and update return type if necessary.
     */
    appendChild(...args: any[]): any;
    /**
     * Auto-generated from Java method 'setFlexDirection'.
     * Please specify parameters and update return type if necessary.
     */
    setFlexDirection(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getFlexDirection'.
     * Please specify parameters and update return type if necessary.
     */
    getFlexDirection(...args: any[]): string;
    /**
     * Auto-generated from Java method 'setJustifyContent'.
     * Please specify parameters and update return type if necessary.
     */
    setJustifyContent(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getJustifyContent'.
     * Please specify parameters and update return type if necessary.
     */
    getJustifyContent(...args: any[]): string;
    /**
     * Auto-generated from Java method 'setAlignItems'.
     * Please specify parameters and update return type if necessary.
     */
    setAlignItems(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getAlignItems'.
     * Please specify parameters and update return type if necessary.
     */
    getAlignItems(...args: any[]): string;
    /**
     * Auto-generated from Java method 'setGap'.
     * Please specify parameters and update return type if necessary.
     */
    setGap(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getGap'.
     * Please specify parameters and update return type if necessary.
     */
    getGap(...args: any[]): number;
    /**
     * Auto-generated from Java method 'setAutoResize'.
     * Please specify parameters and update return type if necessary.
     */
    setAutoResize(...args: any[]): any;
}

/**
 * Auto-generated for Java class `UIImage`.
 */
declare interface UIImage {
    /**
     * Auto-generated from Java method 'setSource'.
     * Please specify parameters and update return type if necessary.
     */
    setSource(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getSource'.
     * Please specify parameters and update return type if necessary.
     */
    getSource(...args: any[]): string;
}

/**
 * Auto-generated for Java class `UIInput`.
 */
declare interface UIInput {
    /**
     * Auto-generated from Java method 'getValue'.
     * Please specify parameters and update return type if necessary.
     */
    getValue(...args: any[]): string;
    /**
     * Auto-generated from Java method 'setValue'.
     * Please specify parameters and update return type if necessary.
     */
    setValue(...args: any[]): any;
    /**
     * Auto-generated from Java method 'getPlaceholder'.
     * Please specify parameters and update return type if necessary.
     */
    getPlaceholder(...args: any[]): string;
    /**
     * Auto-generated from Java method 'onChange'.
     * Please specify parameters and update return type if necessary.
     */
    onChange(...args: any[]): any;
    /**
     * Auto-generated from Java method 'onSubmit'.
     * Please specify parameters and update return type if necessary.
     */
    onSubmit(...args: any[]): any;
}

/**
 * Auto-generated for Java class `UIService`.
 */
declare interface UIService {
    /**
     * Auto-generated from Java method 'getScreenWidth'.
     * Please specify parameters and update return type if necessary.
     */
    getScreenWidth(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getScreenHeight'.
     * Please specify parameters and update return type if necessary.
     */
    getScreenHeight(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getMouseX'.
     * Please specify parameters and update return type if necessary.
     */
    getMouseX(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getMouseY'.
     * Please specify parameters and update return type if necessary.
     */
    getMouseY(...args: any[]): number;
    /**
     * Auto-generated from Java method 'getTextWidth'.
     * Please specify parameters and update return type if necessary.
     */
    getTextWidth(...args: any[]): number;
    /**
     * Auto-generated from Java method 'createText'.
     * Please specify parameters and update return type if necessary.
     */
    createText(...args: any[]): any;
    /**
     * Auto-generated from Java method 'createButton'.
     * Please specify parameters and update return type if necessary.
     */
    createButton(...args: any[]): any;
    /**
     * Auto-generated from Java method 'createInput'.
     * Please specify parameters and update return type if necessary.
     */
    createInput(...args: any[]): any;
    /**
     * Auto-generated from Java method 'createContainer'.
     * Please specify parameters and update return type if necessary.
     */
    createContainer(...args: any[]): any;
    /**
     * Auto-generated from Java method 'onResize'.
     * Please specify parameters and update return type if necessary.
     */
    onResize(...args: any[]): void;
    /**
     * Auto-generated from Java method 'createImage'.
     * Please specify parameters and update return type if necessary.
     */
    createImage(...args: any[]): any;
}

/**
 * Auto-generated for Java class `ZoneAPIProxy`.
 */
declare interface ZoneAPI {
    /**
     * Auto-generated from Java method 'create'.
     * Please specify parameters and update return type if necessary.
     */
    create(...args: any[]): void;
    /**
     * Auto-generated from Java method 'remove'.
     * Please specify parameters and update return type if necessary.
     */
    remove(...args: any[]): void;
}
