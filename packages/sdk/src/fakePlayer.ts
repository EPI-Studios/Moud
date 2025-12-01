import { Vector3, Quaternion } from './index';

export interface FakePlayerWaypoint {
    position: Vector3;
}

export interface FakePlayerDescriptor {
    id?: number;
    label?: string;
    skinUrl?: string;
    position: Vector3;
    rotation?: Quaternion;
    width?: number;
    height?: number;
    physicsEnabled?: boolean;
    sneaking?: boolean;
    sprinting?: boolean;
    swinging?: boolean;
    usingItem?: boolean;
    path?: FakePlayerWaypoint[];
    pathSpeed?: number;
    pathLoop?: boolean;
    pathPingPong?: boolean;
}

export interface FakePlayerPose {
    sneaking?: boolean;
    sprinting?: boolean;
    swinging?: boolean;
    usingItem?: boolean;
}

export interface FakePlayerPhysics {
    enabled: boolean;
    width?: number;
    height?: number;
}

export interface FakePlayerPath {
    waypoints: FakePlayerWaypoint[];
    speed?: number;
    loop?: boolean;
    pingPong?: boolean;
}

export interface FakePlayerService {
    spawn(descriptor: FakePlayerDescriptor): void;
    remove(id: number): void;
    setPose(id: number, pose: FakePlayerPose): void;
    setPhysics(id: number, physics: FakePlayerPhysics): void;
    setPath(id: number, path: FakePlayerPath): void;
    setSkin(id: number, skinUrl: string): void;
}
