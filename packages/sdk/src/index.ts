/**
 * Typings for the Moud runtime SDK.
 *
 * The SDK is split between **server** and **client** execution contexts. Every type below
 * carries an availability note in its documentation so you immediately know where it can be used:
 *
 * - {@linkcode ServerOnly} types are exposed exclusively to server-side scripts running in the
 *   Minestom-based runtime.
 * - {@linkcode ClientOnly} types are exported by the Fabric client mod and are only available when
 *   a script executes on the client.
 * - {@linkcode Shared} types can safely be referenced from both environments.
 *
 */

/**
 * Marker interface used in documentation to indicate that a type is server-only.
 * @remarks Server-only.
 */
export interface ServerOnly {
    /** @internal */
    readonly __brand: 'server';
}

/**
 * Marker interface used in documentation to indicate that a type is client-only.
 * @remarks Client-only.
 */
export interface ClientOnly {
    /** @internal */
    readonly __brand: 'client';
}

/** Marker interface used in documentation to indicate that a type is shared by client and server. */
export interface Shared {
    /** @internal */
    readonly __brand: 'shared';
}

// --- Core & Global Types -------------------------------------------------

declare global {
    /**
     * The main entry point to all server-side Moud APIs.
     *
     * @remarks Available in {@link ServerOnly server scripts}.
     */
    const api: import('./index').MoudAPI;

    const Moud: import('./index').MoudAPI & {
        audio: import('./index').ClientAudioAPI;
        gamepad: import('./index').GamepadAPI;
        [key: string]: any;
    };

    /**
     * A secure console API for logging messages to the server console.
     * Mirrors the standard browser console API.
     *
     * @remarks Available in {@link ServerOnly server scripts}.
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

    /** Exposes math helpers in both the client and server runtime. */
    namespace MoudMath {
        const Vector3: {
            new(x: number, y: number, z: number): import('./index').Vector3;
            /** A vector with all components set to 0. */
            zero(): import('./index').Vector3;
            /** A vector with all components set to 1. */
            one(): import('./index').Vector3;
            /** A vector pointing up (0, 1, 0). */
            up(): import('./index').Vector3;
            /** A vector pointing down (0, -1, 0). */
            down(): import('./index').Vector3;
            /** A vector pointing left (-1, 0, 0). */
            left(): import('./index').Vector3;
            /** A vector pointing right (1, 0, 0). */
            right(): import('./index').Vector3;
            /** A vector pointing forward (0, 0, 1). */
            forward(): import('./index').Vector3;
            /** A vector pointing backward (0, 0, -1). */
            backward(): import('./index').Vector3;
            /** Linearly interpolates between two vectors. */
            lerp(start: import('./index').Vector3, end: import('./index').Vector3, t: number): import('./index').Vector3;
        };

        const Quaternion: {
            new(x: number, y: number, z: number, w: number): import('./index').Quaternion;
            /** Returns an identity quaternion (no rotation). */
            identity(): import('./index').Quaternion;
            /** Creates a quaternion from Euler angles in degrees. */
            fromEuler(pitch: number, yaw: number, roll: number): import('./index').Quaternion;
            /** Creates a quaternion representing rotation around an axis by the given angle in degrees. */
            fromAxisAngle(axis: import('./index').Vector3, angle: number): import('./index').Quaternion;
            /** Creates a quaternion that rotates from one vector to another. */
            fromToRotation(from: import('./index').Vector3, to: import('./index').Vector3): import('./index').Quaternion;
            /** Creates a quaternion with the specified forward and up directions. */
            lookRotation(forward: import('./index').Vector3, up: import('./index').Vector3): import('./index').Quaternion;
        };

        const Matrix4: {
            new(): import('./index').Matrix4;
            new(values: number[]): import('./index').Matrix4;
            new(other: import('./index').Matrix4): import('./index').Matrix4;
            /** Returns a 4x4 identity matrix. */
            identity(): import('./index').Matrix4;
            /** Creates a translation matrix from the given translation vector. */
            translation(translation: import('./index').Vector3): import('./index').Matrix4;
            /** Creates a rotation matrix from the given quaternion. */
            rotation(rotation: import('./index').Quaternion): import('./index').Matrix4;
            /** Creates a scale matrix from the given scale vector. */
            scaling(scale: import('./index').Vector3): import('./index').Matrix4;
            /** Creates a transformation matrix combining translation, rotation, and scale. */
            trs(translation: import('./index').Vector3, rotation: import('./index').Quaternion, scale: import('./index').Vector3): import('./index').Matrix4;
            /** Creates a perspective projection matrix. */
            perspective(fov: number, aspect: number, near: number, far: number): import('./index').Matrix4;
            /** Creates an orthographic projection matrix. */
            orthographic(left: number, right: number, bottom: number, top: number, near: number, far: number): import('./index').Matrix4;
            /** Creates a view matrix looking from eye position towards target with specified up vector. */
            lookAt(eye: import('./index').Vector3, target: import('./index').Vector3, up: import('./index').Vector3): import('./index').Matrix4;
        };

        const Transform: {
            new(): import('./index').Transform;
            new(position: import('./index').Vector3, rotation: import('./index').Quaternion, scale: import('./index').Vector3): import('./index').Transform;
            new(other: import('./index').Transform): import('./index').Transform;
            /** Returns an identity transform with zero position, identity rotation, and unit scale. */
            identity(): import('./index').Transform;
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
            distancePointToLine(point: import('./index').Vector3, lineStart: import('./index').Vector3, lineEnd: import('./index').Vector3): number;
            /** Finds the closest point on a line segment to a given point. */
            closestPointOnLine(point: import('./index').Vector3, lineStart: import('./index').Vector3, lineEnd: import('./index').Vector3): import('./index').Vector3;
            /** Calculates the signed distance from a point to a plane. */
            distancePointToPlane(point: import('./index').Vector3, planePoint: import('./index').Vector3, planeNormal: import('./index').Vector3): number;
            /** Projects a point onto a plane. */
            projectPointOnPlane(point: import('./index').Vector3, planePoint: import('./index').Vector3, planeNormal: import('./index').Vector3): import('./index').Vector3;
            /** Tests if a ray intersects with a plane. */
            rayPlaneIntersection(rayOrigin: import('./index').Vector3, rayDirection: import('./index').Vector3, planePoint: import('./index').Vector3, planeNormal: import('./index').Vector3): boolean;
            /** Calculates the intersection point between a ray and a plane. */
            rayPlaneIntersectionPoint(rayOrigin: import('./index').Vector3, rayDirection: import('./index').Vector3, planePoint: import('./index').Vector3, planeNormal: import('./index').Vector3): import('./index').Vector3;
            /** Tests if two spheres intersect. */
            sphereIntersection(center1: import('./index').Vector3, radius1: number, center2: import('./index').Vector3, radius2: number): boolean;
            /** Tests if a point is inside a sphere. */
            pointInSphere(point: import('./index').Vector3, sphereCenter: import('./index').Vector3, sphereRadius: number): boolean;
            /** Tests if a point is inside an axis-aligned bounding box. */
            pointInBox(point: import('./index').Vector3, boxMin: import('./index').Vector3, boxMax: import('./index').Vector3): boolean;
            /** Calculates barycentric coordinates of a point relative to a triangle. */
            barycentric(point: import('./index').Vector3, a: import('./index').Vector3, b: import('./index').Vector3, c: import('./index').Vector3): import('./index').Vector3;
            /** Tests if a point is inside a triangle. */
            pointInTriangle(point: import('./index').Vector3, a: import('./index').Vector3, b: import('./index').Vector3, c: import('./index').Vector3): boolean;
            /** Calculates the normal vector of a triangle. */
            triangleNormal(a: import('./index').Vector3, b: import('./index').Vector3, c: import('./index').Vector3): import('./index').Vector3;
            /** Calculates the area of a triangle. */
            triangleArea(a: import('./index').Vector3, b: import('./index').Vector3, c: import('./index').Vector3): number;
            /** Reflects a vector off a surface with the given normal. */
            reflect(incident: import('./index').Vector3, normal: import('./index').Vector3): import('./index').Vector3;
            /** Refracts a vector through a surface with the given normal and refractive index. */
            refract(incident: import('./index').Vector3, normal: import('./index').Vector3, eta: number): import('./index').Vector3;
            /** Calculates the Fresnel reflection coefficient. */
            fresnel(incident: import('./index').Vector3, normal: import('./index').Vector3, n1: number, n2: number): number;
            /** Evaluates a cubic Bézier curve at parameter t. */
            bezierCubic(p0: import('./index').Vector3, p1: import('./index').Vector3, p2: import('./index').Vector3, p3: import('./index').Vector3, t: number): import('./index').Vector3;
            /** Evaluates a quadratic Bézier curve at parameter t. */
            bezierQuadratic(p0: import('./index').Vector3, p1: import('./index').Vector3, p2: import('./index').Vector3, t: number): import('./index').Vector3;
            /** Evaluates a Catmull-Rom spline at parameter t. */
            catmullRom(p0: import('./index').Vector3, p1: import('./index').Vector3, p2: import('./index').Vector3, p3: import('./index').Vector3, t: number): import('./index').Vector3;
            /** Calculates the signed volume of a tetrahedron. */
            signedVolumeOfTetrahedron(a: import('./index').Vector3, b: import('./index').Vector3, c: import('./index').Vector3, d: import('./index').Vector3): number;
            /** Tests if two points are on the same side of a line. */
            sameSide(p1: import('./index').Vector3, p2: import('./index').Vector3, a: import('./index').Vector3, b: import('./index').Vector3): boolean;
            /** Calculates the circumcenter of a triangle. */
            circumcenter(a: import('./index').Vector3, b: import('./index').Vector3, c: import('./index').Vector3): import('./index').Vector3;
            /** Calculates the circumradius of a triangle. */
            circumradius(a: import('./index').Vector3, b: import('./index').Vector3, c: import('./index').Vector3): number;
            /** Calculates the incenter of a triangle. */
            incenter(a: import('./index').Vector3, b: import('./index').Vector3, c: import('./index').Vector3): import('./index').Vector3;
            /** Calculates the inradius of a triangle. */
            inradius(a: import('./index').Vector3, b: import('./index').Vector3, c: import('./index').Vector3): number;
            /** Generates uniformly distributed points on a sphere surface. */
            generateSpherePoints(count: number): Array<import('./index').Vector3>;
            /** Converts spherical coordinates to Cartesian coordinates. */
            sphericalToCartesian(radius: number, theta: number, phi: number): import('./index').Vector3;
            /** Converts Cartesian coordinates to spherical coordinates. */
            cartesianToSpherical(point: import('./index').Vector3): import('./index').Vector3;
            /** Converts cylindrical coordinates to Cartesian coordinates. */
            cylindricalToCartesian(radius: number, theta: number, height: number): import('./index').Vector3;
            /** Converts Cartesian coordinates to cylindrical coordinates. */
            cartesianToCylindrical(point: import('./index').Vector3): import('./index').Vector3;
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


    // Re-exported type aliases for ambient usage
    type Vector3 = import('./index').Vector3;
    type Quaternion = import('./index').Quaternion;
    type Matrix4 = import('./index').Matrix4;
    type Transform = import('./index').Transform;
    type MoudAPI = import('./index').MoudAPI;
    type RaycastOptions = import('./index').RaycastOptions;
    type RaycastResult = import('./index').RaycastResult;
    type Player = import('./index').Player;
    type Server = import('./index').Server;
    type PlayerModelOptions = import('./index').PlayerModelOptions;
    type TextOptions = import('./index').TextOptions;
    type World = import('./index').World;
    type LightingAPI = import('./index').LightingAPI;
    type AsyncManager = import('./index').AsyncManager;
    type PlayerClient = import('./index').PlayerClient;
    type CameraStateOptions = import('./index').CameraStateOptions;
    type CameraTransitionOptions = import('./index').CameraTransitionOptions;
    type CameraLockOptions = import('./index').CameraLockOptions;
    type CameraLock = import('./index').CameraLock;
    type UIVisibilityOptions = import('./index').UIVisibilityOptions;
    type PlayerUI = import('./index').PlayerUI;
    type Cursor = import('./index').Cursor;
    type InterpolationOptions = import('./index').InterpolationOptions;
    type PartConfigOptions = import('./index').PartConfigOptions;
    type PlayerAnimation = import('./index').PlayerAnimation;
    type CancellableEvent = import('./index').CancellableEvent;
    type ChatEvent = import('./index').ChatEvent;
    type BlockEvent = import('./index').BlockEvent;
    type PlayerMoveEvent = import('./index').PlayerMoveEvent;
    type PlayerLeaveEvent = import('./index').PlayerLeaveEvent;
    type EntityInteractionEvent = import('./index').EntityInteractionEvent;
    type PlayerModel = import('./index').PlayerModel;
    type Text = import('./index').Text;
    type SharedValueApi = import('./index').SharedValueApi;
    type SharedStore = import('./index').SharedStore;
    type Asset = import('./index').Asset;
    type CameraService = import('./index').CameraService;
    type Client = import('./index').Client;
    type ClientSharedApi = import('./index').ClientSharedApi;
    type Command = import('./index').Command;
    type ConsoleAPI = import('./index').ConsoleAPI;
    type CursorService = import('./index').CursorService;
    type EventService = import('./index').EventService;
    type InputService = import('./index').InputService;
    type NetworkService = import('./index').NetworkService;
    type PlayerWindow = import('./index').PlayerWindow;
    type RenderingService = import('./index').RenderingService;
    type ScriptingAPI = import('./index').ScriptingAPI;
    type UIComponent = import('./index').UIComponent;
    type UIContainer = import('./index').UIContainer;
    type UIImage = import('./index').UIImage;
    type UIInput = import('./index').UIInput;
    type UIService = import('./index').UIService;
    type ZoneAPI = import('./index').ZoneAPI;
}

/**
 * Represents a 3D vector with x, y, and z components.
 * @remarks Shared between client and server scripts.
 */
export interface Vector3 {
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
 * @remarks Shared between client and server scripts.
 */
export interface Quaternion {
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

/**
 * Represents a 4x4 transformation matrix.
 * @remarks Shared between client and server scripts.
 */
export interface Matrix4 {
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

/**
 * Represents a position, rotation and scale tuple.
 * @remarks Shared between client and server scripts.
 */
export interface Transform {
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
 * @remarks Server-only.
 */
export interface MoudAPI {
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
     * Registers a handler that will automatically unregister itself after firing once.
     * @param eventName The event identifier.
     * @param callback The handler to invoke once.
     */
    once(eventName: string, callback: (...args: unknown[]) => void): void;
    /**
     * Removes a previously registered handler for an event.
     * @param eventName The event identifier that was passed to {@link on} or {@link once}.
     * @param callback The original handler function.
     */
    off(eventName: string, callback: (...args: unknown[]) => void): void;
    /**
     * Registers a callback that fires *only once* when the server finishes its initial startup.
     * This event does not fire on hot reloads, making it ideal for persistent, one-time setup.
     */
    on(eventName: 'server.load', callback: () => void): void;
    /**
     * Server-wide helpers for broadcasting messages, enumerating players, and running commands.
     * @remarks Mirrors {@link ServerProxy} on the backend.
     */
    readonly server: Server;
    /**
     * The default world proxy used for block manipulation and entity spawning.
     */
    readonly world: World;
    /**
     * API for creating and updating dynamic lights.
     */
    readonly lighting: LightingAPI;
    /**
     * Server-side trigger zone helpers used by the scene editor and scripts.
     */
    readonly zones: ZoneAPI;
    /**
     * Mathematical helper utilities that mirror the server-side `MathProxy`.
     */
    readonly math: Math;
    /**
     * Runtime command registration API.
     */
    readonly commands: Command;
    /**
     * Asset loader that reads packaged datapack resources.
     */
    readonly assets: Asset;
    /**
     * Asynchronous task runner shared with {@link AsyncManager}.
     */
    readonly async: AsyncManager;

    /**
     * @deprecated Use the {@link server} property instead.
     */
    getServer(): Server;
    /**
     * @deprecated Use the {@link world} property instead.
     */
    getWorld(): World;
    /**
     * @deprecated Use the {@link lighting} property instead.
     */
    getLighting(): LightingAPI;
    /**
     * @deprecated Use the {@link async} property instead.
     */
    getAsync(): AsyncManager;
}

/**
 * Options for performing a world raycast.
 * @remarks Server-only.
 */
export interface RaycastOptions {
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
 * @remarks Server-only.
 */
export interface RaycastResult {
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
 * @remarks Server-only.
 */
export interface Player {
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
     * Checks whether the player is standing near a ledge (no support ahead).
     * Considers both blocks and model colliders on the server.
     * @param forwardDistance How far ahead to probe from the player's feet, in blocks. Defaults to 0.6.
     * @param dropThreshold Maximum vertical gap to still count as support. Defaults to 0.75 blocks.
     */
    isAtEdge(forwardDistance?: number, dropThreshold?: number): boolean;

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
    readonly camera: CameraLock;

    /**
     * Accesses the API for showing or hiding elements of the vanilla Minecraft HUD.
     * @returns The PlayerUI API proxy.
     */
    readonly ui: PlayerUI;

    /**
     * Accesses the API for controlling the player's 3D world cursor.
     * @returns The Cursor API proxy.
     */
    readonly cursor: Cursor;

    /**
     * Accesses the API for controlling and overriding player model animations and parts.
     * @returns The PlayerAnimation API proxy.
     */
    readonly animation: PlayerAnimation;

    /** @returns The audio interface for this player's client. */
    getAudio(): PlayerAudio;

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

export interface PlayerAudio {
    play(options: SoundPlayOptions): void;
    update(options: SoundUpdateOptions): void;
    stop(options: SoundStopOptions): void;
    startMicrophone(options?: MicrophoneStartOptions): void;
    stopMicrophone(): void;
    isMicrophoneActive(): boolean;
    getMicrophoneSession(): MicrophoneSession | null;
}

export type SoundCategory =
    | 'master'
    | 'music'
    | 'record'
    | 'weather'
    | 'block'
    | 'hostile'
    | 'neutral'
    | 'player'
    | 'ambient'
    | 'voice';

export interface SoundPlayOptions {
    id: string;
    sound: string;
    category?: SoundCategory;
    volume?: number;
    pitch?: number;
    fadeInMs?: number;
    fadeOutMs?: number;
    loop?: boolean;
    positional?: boolean;
    position?: Vector3 | [number, number, number];
    maxDistance?: number;
    pitchRamp?: {
        pitch: number;
        durationMs: number;
        easing?: 'linear' | 'ease_in' | 'ease_out' | 'ease_in_out';
    };
    crossFadeGroup?: string;
    crossFadeMs?: number;
}

export interface SoundUpdateOptions extends Partial<Omit<SoundPlayOptions, 'id'>> {
    id: string;
}

export interface SoundStopOptions {
    id: string;
    fadeOutMs?: number;
    immediate?: boolean;
}

export interface MicrophoneStartOptions {
    sessionId?: string;
    sampleRate?: number;
}

export interface MicrophoneSession {
    sessionId: string;
    active: boolean;
    state?: string | null;
    timestamp: number;
    sampleRate: number;
    channels: number;
    chunkBase64?: string;
}

export interface ClientMicrophoneAPI {
    start(options?: MicrophoneStartOptions): void;
    stop(): void;
    isActive(): boolean;
}

export interface ClientAudioAPI {
    play(options: SoundPlayOptions): void;
    update(options: SoundUpdateOptions): void;
    stop(options: SoundStopOptions): void;
    getMicrophone(): ClientMicrophoneAPI;
}

export interface GamepadAPI {
    isConnected(index: number): boolean;
    getState(index: number): GamepadSnapshot | null;
    onChange(callback: (snapshot: GamepadSnapshot) => void): string;
    removeListener(listenerId: string): void;
}

export interface GamepadSnapshot {
    readonly index: number;
    readonly name: string | null;
    readonly axes: ReadonlyArray<number>;
    readonly buttons: ReadonlyArray<boolean>;
    readonly connected: boolean;
    readonly timestamp: number;
}

/**
 * API for managing server-level operations.
 */
export interface Server {
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
 * @remarks Server-only.
 */
export interface PlayerModelOptions {
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
 * @remarks Server-only.
 */
export interface TextOptions {
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
 * @remarks Server-only.
 */
export interface World {
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
     * @returns The current in-game time (0-24000) for this world.
     */
    getTime(): number;
    /**
     * Sets the current time of day. Values follow vanilla semantics (0=sunrise, 6000=noon).
     */
    setTime(time: number): void;
    /**
     * @returns The rate at which time advances. Default is 1.
     */
    getTimeRate(): number;
    /**
     * Adjusts how quickly time advances. Set to 0 to freeze time.
     */
    setTimeRate(rate: number): void;
    /**
     * @returns The number of ticks between client time-sync packets.
     */
    getTimeSynchronizationTicks(): number;
    /**
     * Changes how often clients are synchronized with the server time. Use 0 to disable sync.
     */
    setTimeSynchronizationTicks(ticks: number): void;

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
 * @remarks Server-only.
 */
export interface LightingAPI {
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
 * @remarks Server-only.
 */
export interface AsyncManager {
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
 * @remarks Server-only — bridges to the connected client.
 */
export interface PlayerClient {
    /**
     * Sends a custom event to this specific player's client script.
     * The client script must have a corresponding `moudAPI.network.on('eventName', ...)` handler.
     * @param eventName The name of the event to trigger on the client.
     * @param data The data payload for the event. Must be serializable (e.g., strings, numbers, booleans, nested objects/arrays).
     */
    send(eventName: string, data: any): void;
}

/**
 * Describes the state of the camera (position, rotation, field of view).
 * Used for the `snapTo` and `transitionTo` methods.
 * @remarks Client-only.
 */
export interface CameraStateOptions {
    /** The target position of the camera in the world. */
    position?: Vector3 | CameraVector;
    /** The target horizontal orientation (yaw) in degrees. */
    yaw?: number;
    /** The target vertical orientation (pitch) in degrees. */
    pitch?: number;
    /** The target tilt (roll) in degrees. */
    roll?: number;
    /** The target field of view (FOV). */
    fov?: number;
}

/**
 * Options for a smooth camera transition to a new state.
 * @remarks Client-only.
 */
export interface CameraTransitionOptions extends CameraStateOptions {
    /** The duration of the transition in milliseconds. Default: 1000. */
    duration?: number;
    /** A JavaScript function defining the animation curve (e.g., t => t * t for an ease-in). */
    easing?: (t: number) => number;
}



/**
 * Options for locking the player's camera.
 * @remarks Server-only.
 */
export interface CameraLockOptions {
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
export interface CameraLock {
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
 * @remarks Server-only.
 */
export interface UIVisibilityOptions {
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
 * @remarks Server-only.
 */
export interface PlayerUI {
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
 * @remarks Server-only.
 */
export interface Cursor {
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
 * @remarks Server-only.
 */
export interface InterpolationOptions {
    /** If false, the change will be instant. Defaults to true. */
    enabled?: boolean;
    /** The duration of the interpolation in milliseconds. Defaults to 150. */
    duration?: number;
    /** The easing function to use for the animation curve. */
    easing?: 'linear' | 'ease_in' | 'ease_out' | 'ease_in_out' | 'bounce';
}

/**
 * Options for modifying a player model's part.
 * @remarks Server-only.
 */
export interface PartConfigOptions {
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
 * @remarks Server-only.
 */
export interface PlayerAnimation {
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
 * @remarks Server-only.
 */
export interface CancellableEvent {
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
 * @remarks Server-only.
 */
export interface ChatEvent extends CancellableEvent {
    /** @returns The player who sent the message. */
    getPlayer(): Player;
    /** @returns The content of the chat message. */
    getMessage(): string;
}

/**
 * Fired when a player breaks or places a block.
 * @remarks Server-only.
 */
export interface BlockEvent extends CancellableEvent {
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
 * @remarks Server-only.
 */
export interface PlayerMoveEvent extends CancellableEvent {
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
 * @remarks Server-only.
 */
export interface PlayerLeaveEvent {
    /** @returns The name of the player who left. */
    getName(): string;
    /** @returns The UUID of the player who left. */
    getUuid(): string;
}

/**
 * Fired when a player's cursor interacts with a scripted entity.
 * @remarks Server-only.
 */
export interface EntityInteractionEvent {
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
export interface PlayerModel {
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
export interface Text {
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
 * @remarks Server-only.
 */
export interface SharedValueApi {
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
 * @remarks Server-only for values synchronised to the owning client.
 */
export interface SharedStore {
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
export {};


// --- Proxy & Service interfaces ------------------------------------------------

/**
 * Tools for loading server-managed assets that are bundled with the current datapack.
 * @remarks Server-only.
 */
export interface Asset {
    /**
     * Loads a shader asset from disk and exposes the compiled source.
     * @param path Relative path inside the datapack's `assets` folder.
     */
    loadShader(path: string): ShaderAsset;

    /**
     * Loads an image asset and exposes the raw binary data.
     * @param path Relative path inside the datapack's `assets` folder.
     */
    loadTexture(path: string): TextureAsset;

    /**
     * Loads a JSON or text asset.
     * @param path Relative path inside the datapack's `assets` folder.
     */
    loadData(path: string): DataAsset;
}

/** Describes a shader that has been loaded through {@link Asset.loadShader}. */
export interface ShaderAsset {
    /** @returns The identifier that uniquely represents this asset. */
    getId(): string;
    /** @returns The GLSL shader source code. */
    getCode(): string;
}

/** Describes a texture that has been loaded through {@link Asset.loadTexture}. */
export interface TextureAsset {
    /** @returns The identifier that uniquely represents this asset. */
    getId(): string;
    /** @returns The raw byte data for the texture. */
    getData(): number[];
}

/** Describes a text or JSON asset that was loaded by {@link Asset.loadData}. */
export interface DataAsset {
    /** @returns The identifier that uniquely represents this asset. */
    getId(): string;
    /** @returns The raw contents of the asset. */
    getContent(): string;
}

/**
 * Options used when animating the camera to a new pose.
 * @remarks Client-only.
 */
export interface CameraTransitionOptions {
    /** Target world-space position for the camera. */
    position?: CameraVector;
    /** Target yaw in degrees. */
    yaw?: number;
    /** Target pitch in degrees. */
    pitch?: number;
    /** Target roll in degrees. */
    roll?: number;
    /** Target field of view in degrees. */
    fov?: number;
    /** Duration of the animation in milliseconds. Defaults to `1000`. */
    duration?: number;
    /**
     * Optional easing function that receives a progress value between `0` and `1`
     * and must return the eased progress.
     */
    easing?: (progress: number) => number;
}

/** Vector coordinates accepted by {@link CameraService} transition helpers. */
export interface CameraVector {
    x: number;
    y: number;
    z: number;
}

/**
 * Controls the in-game camera from client scripts.
 * @remarks Client-only.
 */
export interface CameraService {
    /** Enables the custom camera controller and captures the current pose. */
    enableCustomCamera(): void;
    /** Disables the custom camera controller and restores the previous perspective. */
    disableCustomCamera(): void;
    /**
     * Smoothly interpolates the camera towards the supplied pose.
     * @param options Coordinates and orientation values to animate towards.
     */
    transitionTo(options: CameraTransitionOptions): void;
    /**
     * Instantly applies a new camera pose. Any `duration` or `easing` values are ignored.
     * @param options Coordinates and orientation values to apply immediately.
     */
    snapTo(options: CameraTransitionOptions): void;
    /** @returns `true` when the custom camera is active. */
    isCustomCameraActive(): boolean;
    /** @returns The player's current X coordinate. */
    getPlayerX(): number;
    /** @returns The player's eye-level Y coordinate. */
    getPlayerY(): number;
    /** @returns The player's current Z coordinate. */
    getPlayerZ(): number;
    /** @returns The player's yaw in degrees. */
    getPlayerYaw(): number;
    /** @returns The player's pitch in degrees. */
    getPlayerPitch(): number;
    /**
     * Utility helper that creates a {@link Vector3} usable with other APIs.
     * @param x X component.
     * @param y Y component.
     * @param z Z component.
     */
    createVector3(x: number, y: number, z: number): Vector3;
    /** @returns `true` if the game is currently rendering in a third-person perspective. */
    isThirdPerson(): boolean;
    /**
     * Switches between first and third person perspectives.
     * @param thirdPerson Set to `true` to force third person.
     */
    setThirdPerson(thirdPerson: boolean): void;
    /** @returns The player's current field of view in degrees. */
    getFov(): number;
}

/**
 * Sends custom network events to an individual client from a server script.
 * @remarks Server-only.
 */
export interface Client {
    /**
     * Sends an event to the connected client mod.
     * @param eventName Unique name of the event.
     * @param data Payload that will be JSON-serialised before sending.
     */
    send<T = unknown>(eventName: string, data?: T): void;
}

/**
 * Entry point for managing client-side shared values from a client script.
 * @remarks Client-only.
 */
export interface ClientSharedApi {
    /**
     * Returns a handle to a shared value store synchronised with the server.
     * @param storeName Name of the store to access.
     */
    getStore(storeName: string): ClientSharedStore;
}

/**
 * Represents a client-side cache of shared values synchronised with the server.
 * @remarks Client-only.
 */
export interface ClientSharedStore {
    /**
     * Reads the cached value for a key.
     * @param key Name of the value to read.
     */
    get<T = unknown>(key: string): T | undefined;
    /** @returns `true` if the cache currently holds a value for the key. */
    has(key: string): boolean;
    /**
     * Requests that the server updates a key. The change is queued immediately on the client.
     * @param key Name of the value to change.
     * @param value New value to send to the server.
     * @returns `true` if the client is allowed to modify the key.
     */
    set(key: string, value: unknown): boolean;
    /**
     * Subscribes to changes on any key in the store.
     * @param event Must be `"change"`.
     * @param callback Invoked with `(key, newValue, previousValue)`.
     */
    on(event: 'change', callback: (key: string, newValue: unknown, previousValue: unknown) => void): void;
    /**
     * Subscribes to changes for a specific key.
     * @param key Name of the value to monitor.
     * @param callback Invoked with `(newValue, previousValue)`.
     */
    onChange(key: string, callback: (newValue: unknown, previousValue: unknown) => void): void;
    /** @returns `true` if the client has permission to change the key optimistically. */
    canModify(key: string): boolean;
}

/**
 * Registers commands that players can execute in chat.
 * @remarks Server-only.
 */
export interface Command {
    /**
     * Registers a command without aliases.
     * @param name Primary literal used to execute the command.
     * @param callback Invoked when a player runs the command.
     */
    register(name: string, callback: (player: Player) => void): void;
    /**
     * Registers a command with additional aliases.
     * @param name Primary literal used to execute the command.
     * @param aliases Additional names that trigger the same command.
     * @param callback Invoked when a player runs the command.
     */
    registerWithAliases(name: string, aliases: string[], callback: (player: Player) => void): void;
}

/**
 * Logging helper that mirrors the browser `console` API.
 * @remarks Server-only.
 */
export interface ConsoleAPI {
    /** Writes an informational message to the server log. */
    log(...args: unknown[]): void;
    /** Writes a warning to the server log. */
    warn(...args: unknown[]): void;
    /** Writes an error to the server log. */
    error(...args: unknown[]): void;
    /** Writes a debug message to the server log. */
    debug(...args: unknown[]): void;
}

/**
 * Manually shows or hides the system cursor while the client mod is running.
 * @remarks Client-only.
 */
export interface CursorService {
    /** Unlocks the cursor and shows it on screen. */
    show(): void;
    /** Locks and hides the cursor. */
    hide(): void;
    /** Toggles the cursor visibility. */
    toggle(): void;
    /** @returns `true` if the cursor is currently visible. */
    isVisible(): boolean;
}

/**
 * Client-side event bus used by the scripting runtime.
 * @remarks Client-only.
 */
export interface EventService {
    /**
     * Registers a handler for a client-side event.
     * @param eventName Identifier of the event.
     * @param callback Executed when the event fires.
     */
    on(eventName: string, callback: (...args: unknown[]) => void): void;
    /**
     * Immediately dispatches an event to all registered handlers.
     * @param eventName Identifier of the event.
     * @param args Arguments forwarded to the handlers.
     */
    dispatch(eventName: string, ...args: unknown[]): void;
}

/**
 * Provides realtime access to keyboard and mouse input.
 * @remarks Client-only.
 */
export interface InputService {
    /** @returns `true` if the GLFW key code is currently pressed. */
    isKeyPressed(keyCode: number): boolean;
    /** @returns `true` if the translation key (e.g. `key.keyboard.w`) is pressed. */
    isKeyPressed(keyName: string): boolean;
    /** @returns `true` if the specified mouse button is pressed. */
    isMouseButtonPressed(button: number): boolean;
    /** @returns The raw mouse X position relative to the window. */
    getMouseX(): number;
    /** @returns The raw mouse Y position relative to the window. */
    getMouseY(): number;
    /** @returns The horizontal delta since the last frame. */
    getMouseDeltaX(): number;
    /** @returns The vertical delta since the last frame. */
    getMouseDeltaY(): number;
    /**
     * Registers a callback fired when the given translation key changes state.
     * @param keyName Translation key, for example `key.keyboard.w`.
     * @param callback Receives `true` when pressed and `false` when released.
     */
    onKey(keyName: string, callback: (pressed: boolean) => void): void;
    /**
     * Registers a callback fired when the given mouse button changes state.
     * @param buttonName Human readable button name, e.g. `left`, `right`.
     * @param callback Receives `true` when pressed and `false` when released.
     */
    onMouseButton(buttonName: string, callback: (pressed: boolean) => void): void;
    /**
     * Registers a callback fired every time the mouse moves.
     * @param callback Receives the X and Y delta for the current frame.
     */
    onMouseMove(callback: (deltaX: number, deltaY: number) => void): void;
    /**
     * Registers a callback fired when the scroll wheel moves.
     * @param callback Receives the scroll delta.
     */
    onScroll(callback: (delta: number) => void): void;
    /** @returns `true` if the player is moving forward. */
    isMovingForward(): boolean;
    /** @returns `true` if the player is moving backward. */
    isMovingBackward(): boolean;
    /** @returns `true` if the player is strafing left. */
    isStrafingLeft(): boolean;
    /** @returns `true` if the player is strafing right. */
    isStrafingRight(): boolean;
    /** @returns `true` if the jump key is pressed. */
    isJumping(): boolean;
    /** @returns `true` if the player is sprinting. */
    isSprinting(): boolean;
    /** @returns `true` if the player character is on the ground. */
    isOnGround(): boolean;
    /** @returns `true` if any movement key is currently active. */
    isMoving(): boolean;
    /**
     * Locks or unlocks the mouse cursor.
     * @param locked `true` to lock, `false` to unlock.
     */
    lockMouse(locked: boolean): void;
    /** @returns `true` if the mouse cursor is locked. */
    isMouseLocked(): boolean;
    /** @returns The current mouse sensitivity value. */
    getMouseSensitivity(): number;
    /**
     * Updates the Minecraft mouse sensitivity.
     * @param sensitivity New sensitivity value in the same range as the in-game slider.
     */
    setMouseSensitivity(sensitivity: number): void;
}

/**
 * Common mathematical helpers mirrored from the server implementation.
 * @remarks Server-only.
 */
export interface MathUtils {
    clamp(value: number, min: number, max: number): number;
    lerp(a: number, b: number, t: number): number;
    atan2(y: number, x: number): number;
    sin(radians: number): number;
    cos(radians: number): number;
    tan(radians: number): number;
    asin(value: number): number;
    acos(value: number): number;
    atan(value: number): number;
    sqrt(value: number): number;
    abs(value: number): number;
    min(a: number, b: number): number;
    max(a: number, b: number): number;
    floor(value: number): number;
    ceil(value: number): number;
    round(value: number): number;
    toRadians(degrees: number): number;
    toDegrees(radians: number): number;
    /** Mathematical constant π. */
    readonly PI: number;
    /** Conversion factor from degrees to radians. */
    readonly DEG_TO_RAD: number;
    /** Conversion factor from radians to degrees. */
    readonly RAD_TO_DEG: number;
}

/**
 * Geometry helper functions mirrored from the server implementation.
 * @remarks Server-only.
 */
export interface GeometryUtils {
    /**
     * Calculates the shortest distance between a point and a line segment.
     */
    distancePointToLine(point: Vector3, lineStart: Vector3, lineEnd: Vector3): number;
    /**
     * Finds the closest point on a line segment to a reference point.
     */
    closestPointOnLine(point: Vector3, lineStart: Vector3, lineEnd: Vector3): Vector3;
    /** @returns `true` if two spheres overlap. */
    sphereIntersection(center1: Vector3, radius1: number, center2: Vector3, radius2: number): boolean;
}

/**
 * Combined mathematical helpers exposed through `api.math`.
 * @remarks Server-only.
 */
export interface Math extends MathUtils {
    /** Direct access to the full set of math utility functions. */
    readonly utils: MathUtils;
    /** Geometry-specific helpers. */
    readonly geometry: GeometryUtils;

    /** Creates a new vector. */
    vector3(x?: number, y?: number, z?: number): Vector3;
    /** Creates a quaternion from components or returns the identity when omitted. */
    quaternion(x?: number, y?: number, z?: number, w?: number): Quaternion;
    /** Creates a quaternion from Euler angles (degrees). */
    quaternionFromEuler(pitch: number, yaw: number, roll: number): Quaternion;
    /** Creates a quaternion from an axis and angle (degrees). */
    quaternionFromAxisAngle(axis: Vector3, angle: number): Quaternion;

    /** Creates an identity matrix. */
    matrix4(): Matrix4;
    /** Alias for {@link matrix4}. */
    matrix4Identity(): Matrix4;
    /** Creates a translation matrix. */
    matrix4Translation(translation: Vector3): Matrix4;
    /** Creates a rotation matrix. */
    matrix4Rotation(rotation: Quaternion): Matrix4;
    /** Creates a scaling matrix. */
    matrix4Scaling(scale: Vector3): Matrix4;
    /** Creates a TRS matrix. */
    matrix4TRS(translation: Vector3, rotation: Quaternion, scale: Vector3): Matrix4;
    /** Creates a perspective projection matrix. */
    matrix4Perspective(fov: number, aspect: number, near: number, far: number): Matrix4;
    /** Creates an orthographic projection matrix. */
    matrix4Orthographic(left: number, right: number, bottom: number, top: number, near: number, far: number): Matrix4;
    /** Creates a view matrix that looks from `eye` towards `target`. */
    matrix4LookAt(eye: Vector3, target: Vector3, up: Vector3): Matrix4;

    /** Creates an identity transform or one from position, rotation and scale. */
    transform(position?: Vector3, rotation?: Quaternion, scale?: Vector3): Transform;

    /** @returns A zero vector. */
    getVector3Zero(): Vector3;
    /** @returns A vector of ones. */
    getVector3One(): Vector3;
    /** @returns A unit vector pointing up. */
    getVector3Up(): Vector3;
    /** @returns A unit vector pointing down. */
    getVector3Down(): Vector3;
    /** @returns A unit vector pointing left. */
    getVector3Left(): Vector3;
    /** @returns A unit vector pointing right. */
    getVector3Right(): Vector3;
    /** @returns A unit vector pointing forwards. */
    getVector3Forward(): Vector3;
    /** @returns A unit vector pointing backwards. */
    getVector3Backward(): Vector3;
    /** @returns The identity quaternion. */
    getQuaternionIdentity(): Quaternion;
    /** @returns Mathematical constant π. */
    getPI(): number;
    /** @returns Mathematical constant τ (2π). */
    getTWO_PI(): number;
    /** @returns Mathematical constant π/2. */
    getHALF_PI(): number;
    /** @returns Degrees-to-radians conversion factor. */
    getDEG_TO_RAD(): number;
    /** @returns Radians-to-degrees conversion factor. */
    getRAD_TO_DEG(): number;
    /** @returns Machine epsilon used for float comparisons. */
    getEPSILON(): number;
}

/**
 * Sends custom network packets from the client mod to the server.
 * @remarks Client-only.
 */
export interface NetworkService {
    /**
     * Sends a custom event to the server runtime.
     * @param eventName Identifier of the event.
     * @param data Payload that will be serialised to JSON.
     */
    sendToServer<T = unknown>(eventName: string, data?: T): void;
    /**
     * Registers a handler for events sent from the server.
     * @param eventName Identifier of the event.
     * @param callback Receives the deserialised payload.
     */
    on(eventName: string, callback: (payload: unknown) => void): void;
}

/**
 * Animates the player's OS window (borderless move/resize effects).
 * @remarks Server-only.
 */
export interface PlayerWindow {
    /**
     * Tweens the window to a new position/size.
     * @param options Target window properties.
     */
    transitionTo(options: PlayerWindowTransition): void;
    /**
     * Plays a sequence of transitions one after another.
     * @param steps Ordered list of transitions to apply.
     */
    playSequence(steps: PlayerWindowSequenceStep[]): void;
    /**
     * Updates the native window title.
     * @param title Text to show in the title bar.
     */
    setTitle(title: string): void;
    /**
     * Enables or disables the window border.
     * @param borderless `true` to remove the border.
     */
    setBorderless(borderless: boolean): void;
    /** Maximises the window. */
    maximize(): void;
    /** Minimises the window. */
    minimize(): void;
    /** Restores the window to its normal state. */
    restore(): void;
}

/** Options accepted by {@link PlayerWindow.transitionTo}. */
export interface PlayerWindowTransition {
    x?: number;
    y?: number;
    width?: number;
    height?: number;
    /** Duration in milliseconds, defaults to `500`. */
    duration?: number;
    /** Client-side easing function identifier, defaults to `"ease-out-quad"`. */
    easing?: string;
}

/** Describes a single step inside {@link PlayerWindow.playSequence}. */
export interface PlayerWindowSequenceStep extends PlayerWindowTransition {
    /** Optional borderless toggle for this step. */
    borderless?: boolean;
    /** Optional title update for this step. */
    title?: string;
}

/**
 * Rendering helpers that integrate with Foundry Veil.
 * @remarks Client-only.
 */
export interface RenderingService {
    /**
     * Schedules a callback to run during the next render tick.
     * @param callback Receives the frame time in milliseconds.
     * @returns An identifier that can be used with {@link cancelAnimationFrame}.
     */
    requestAnimationFrame(callback: (deltaMs: number) => void): string;
    /**
     * Cancels a callback registered with {@link requestAnimationFrame}.
     * @param id Identifier returned from {@link requestAnimationFrame}.
     */
    cancelAnimationFrame(id: string): void;
    /**
     * Creates (or retrieves) a render type backed by a custom shader.
     * @param options Definition of the render pipeline.
     * @returns Identifier used with Minecraft's rendering APIs.
     */
    createRenderType(options: RenderTypeOptions): string;
    /**
     * Queues a uniform update for a shader created via {@link createRenderType}.
     * @param shaderId Identifier returned by {@link createRenderType}.
     * @param uniformName Name of the uniform inside the shader.
     * @param value Number or boolean value to assign.
     */
    setShaderUniform(shaderId: string, uniformName: string, value: number | boolean): void;
}

/**
 * Definition of a custom render pipeline used by {@link RenderingService.createRenderType}.
 * @remarks Client-only.
 */
export interface RenderTypeOptions {
    /** Identifier of the shader program to bind. */
    shader: string;
    /** Optional textures that should be bound when rendering. */
    textures?: string[];
    /** Transparency mode, defaults to `"opaque"`. */
    transparency?: string;
    /** Whether back-face culling is enabled. Defaults to `true`. */
    cull?: boolean;
    /** Whether light-mapping is enabled. Defaults to `false`. */
    lightmap?: boolean;
    /** Whether depth testing is enabled. Defaults to `true`. */
    depthTest?: boolean;
}

/**
 * Root server-side API exposed via `globalThis.api`.
 * @remarks Server-only.
 */
export interface ScriptingAPI {
    /**
     * Registers a global event listener.
     * @param eventName Event identifier.
     * @param callback Handler invoked when the event fires.
     */
    on(eventName: string, callback: (...args: unknown[]) => void): void;
    /** @returns The asynchronous task manager used to schedule background work. */
    getAsync(): AsyncManager;
}

/**
 * Base interface for all UI components created by {@link UIService}.
 * @remarks Client-only.
 */
export interface UIComponent {
    /** Shows the component as an overlay. */
    showAsOverlay(): this;
    /** Hides the component overlay. */
    hideOverlay(): this;
    /** @returns The X coordinate of the component. */
    getX(): number;
    /** @returns The Y coordinate of the component. */
    getY(): number;
    /** @returns The width of the component. */
    getWidth(): number;
    /** @returns The height of the component. */
    getHeight(): number;
    /** Sets the X coordinate. */
    setX(x: number): this;
    /** Sets the Y coordinate. */
    setY(y: number): this;
    /** Sets the width. */
    setWidth(width: number): this;
    /** Sets the height. */
    setHeight(height: number): this;
    /** @returns The unique identifier of the component. */
    getComponentId(): string;
    /** Overrides the component identifier. */
    setComponentId(id: string): this;
    /** Sets the display text rendered inside the component. */
    setText(text: string): this;
    /** @returns The text currently rendered inside the component. */
    getText(): string;
    /** Moves the component. */
    setPos(x: number, y: number): this;
    /** Resizes the component. */
    setSize(width: number, height: number): this;
    /** Sets the background colour (ARGB hex). */
    setBackgroundColor(color: string): this;
    /** @returns The background colour (ARGB hex). */
    getBackgroundColor(): string;
    /** Sets the text colour (ARGB hex). */
    setTextColor(color: string): this;
    /** @returns The text colour (ARGB hex). */
    getTextColor(): string;
    /** Configures the border width and colour. */
    setBorder(width: number, color: string): this;
    /** @returns The current border width in pixels. */
    getBorderWidth(): number;
    /** @returns The current border colour (ARGB hex). */
    getBorderColor(): string;
    /** Sets the overall opacity (0–1). */
    setOpacity(opacity: number): this;
    /** @returns The current opacity (0–1). */
    getOpacity(): number;
    /** Sets the text alignment inside the component. */
    setTextAlign(alignment: 'left' | 'center' | 'right'): this;
    /** @returns The current text alignment. */
    getTextAlign(): 'left' | 'center' | 'right';
    /** Sets the component padding (top, right, bottom, left). */
    setPadding(top: number, right: number, bottom: number, left: number): this;
    /** @returns The top padding in pixels. */
    getPaddingTop(): number;
    /** @returns The right padding in pixels. */
    getPaddingRight(): number;
    /** @returns The bottom padding in pixels. */
    getPaddingBottom(): number;
    /** @returns The left padding in pixels. */
    getPaddingLeft(): number;
    /** Adds a child component. */
    appendChild<T extends UIComponent>(child: T): this;
    /** Removes a child component. */
    removeChild(child: UIComponent): this;
    /** @returns A snapshot of the current children. */
    getChildren(): UIComponent[];
    /** Makes the component visible. */
    show(): this;
    /** Hides the component. */
    hide(): this;
    /** @returns `true` if the component is visible. */
    isVisible(): boolean;
    /**
     * Registers a callback for click events.
     * @param callback Receives the component, mouse X/Y and the pressed button.
     */
    onClick(callback: (component: UIComponent, mouseX: number, mouseY: number, button: number) => void): this;
    /**
     * Registers a callback for hover events.
     * @param callback Receives the component and raw hover event data.
     */
    onHover(callback: (component: UIComponent, ...args: unknown[]) => void): this;
    /**
     * Registers a callback fired when the component gains focus.
     * @param callback Receives the focused component.
     */
    onFocus(callback: (component: UIComponent) => void): this;
    /**
     * Registers a callback fired when the component loses focus.
     * @param callback Receives the blurred component.
     */
    onBlur(callback: (component: UIComponent) => void): this;
}

/** Text label component returned by {@link UIService.createText}. */
export interface UIText extends UIComponent {}

/** Button component returned by {@link UIService.createButton}. */
export interface UIButton extends UIComponent {}

/**
 * Flex-style layout container.
 * @remarks Client-only.
 */
export interface UIContainer extends UIComponent {
    /** Adds a child element and recomputes the layout. */
    appendChild<T extends UIComponent>(child: T): this;
    /** Sets the flex direction: `row` or `column`. */
    setFlexDirection(direction: 'row' | 'column'): this;
    /** @returns The current flex direction. */
    getFlexDirection(): 'row' | 'column';
    /** Sets the justify-content rule. */
    setJustifyContent(value: 'flex-start' | 'center' | 'flex-end' | 'space-between' | 'space-around'): this;
    /** @returns The current justify-content rule. */
    getJustifyContent(): 'flex-start' | 'center' | 'flex-end' | 'space-between' | 'space-around';
    /** Sets the align-items rule. */
    setAlignItems(value: 'stretch' | 'flex-start' | 'center' | 'flex-end'): this;
    /** @returns The current align-items rule. */
    getAlignItems(): 'stretch' | 'flex-start' | 'center' | 'flex-end';
    /** Sets the gap between children in pixels. */
    setGap(gap: number): this;
    /** @returns The gap between children in pixels. */
    getGap(): number;
    /**
     * Enables or disables automatic resizing to fit the content.
     * When `true` the container expands to the size of its children.
     */
    setAutoResize(autoResize: boolean): this;
}

/**
 * Image component returned by {@link UIService.createImage}.
 * @remarks Client-only.
 */
export interface UIImage extends UIComponent {
    /** Updates the image source, for example `"minecraft:textures/gui/widgets.png"`. */
    setSource(source: string): this;
    /** @returns The currently displayed image source. */
    getSource(): string;
}

/**
 * Text input component returned by {@link UIService.createInput}.
 * @remarks Client-only.
 */
export interface UIInput extends UIComponent {
    /** @returns The current input value. */
    getValue(): string;
    /** Sets the current value. */
    setValue(value: string): this;
    /** @returns The placeholder shown when the input is empty. */
    getPlaceholder(): string;
    /**
     * Registers a callback fired whenever the value changes.
     * @param callback Receives the input component, new value and old value.
     */
    onChange(callback: (input: UIInput, newValue: string, previousValue: string) => void): this;
    /**
     * Registers a callback fired when the user submits the input (presses Enter).
     * @param callback Receives the input component and the submitted value.
     */
    onSubmit(callback: (input: UIInput, value: string) => void): this;
}

/**
 * Creates UI overlay components from client scripts.
 * @remarks Client-only.
 */
export interface UIService {
    /** @returns The scaled screen width. */
    getScreenWidth(): number;
    /** @returns The scaled screen height. */
    getScreenHeight(): number;
    /** @returns The scaled mouse X coordinate. */
    getMouseX(): number;
    /** @returns The scaled mouse Y coordinate. */
    getMouseY(): number;
    /**
     * Measures the rendered width of text using the Minecraft font renderer.
     * @param text Text to measure.
     */
    getTextWidth(text: string): number;
    /**
     * Creates a text label component.
     * @param content Text to display.
     */
    createText(content: string): UIText;
    /**
     * Creates a button component.
     * @param label Text to display inside the button.
     */
    createButton(label: string): UIButton;
    /**
     * Creates an input component.
     * @param placeholder Placeholder text shown when empty.
     */
    createInput(placeholder: string): UIInput;
    /** Creates a flex layout container. */
    createContainer(): UIContainer;
    /**
     * Registers a callback invoked when the screen size changes.
     * @param callback Receives the new width and height.
     */
    onResize(callback: (width: number, height: number) => void): void;
    /**
     * Creates an image component.
     * @param source Texture identifier to display.
     */
    createImage(source: string): UIImage;
}

/**
 * Options supported by {@link ZoneAPI.create}.
 * @remarks Server-only.
 */
export interface ZoneOptions {
    /** Callback fired when a player enters the zone. */
    onEnter?: (player: Player, zoneId: string) => void;
    /** Callback fired when a player leaves the zone. */
    onLeave?: (player: Player, zoneId: string) => void;
}

/**
 * Server-side manager for 3D trigger zones.
 * @remarks Server-only.
 */
export interface ZoneAPI {
    /**
     * Creates (or replaces) a zone bounded by two corners.
     * @param id Unique identifier for the zone.
     * @param corner1 First corner of the axis-aligned bounding box.
     * @param corner2 Opposite corner of the axis-aligned bounding box.
     * @param options Optional enter/leave callbacks.
     */
    create(id: string, corner1: Vector3, corner2: Vector3, options?: ZoneOptions): void;
    /**
     * Removes a zone by its identifier.
     * @param id Unique identifier of the zone to remove.
     */
    remove(id: string): void;
}
