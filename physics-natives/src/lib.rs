use std::ffi::c_void;
use std::sync::Mutex;

use rapier3d::control::{EffectiveCharacterMovement, KinematicCharacterController};
use rapier3d::parry::query::ShapeCastOptions;
use rapier3d::pipeline::{ActiveEvents, EventHandler};
use rapier3d::prelude::*;

#[no_mangle]
pub extern "C" fn rapier_version() -> u32 {
    0x00_01_00_00
}

#[repr(C)]
#[derive(Copy, Clone)]
struct ContactEventRow {
    a: i64,
    b: i64,
    px: f32,
    py: f32,
    pz: f32,
    nx: f32,
    ny: f32,
    nz: f32,
    impulse: f32,
}

#[repr(C)]
#[derive(Copy, Clone)]
struct AreaEventRow {
    area: i64,
    other: i64,
    entered: i32,
    _pad: i32,
}

struct CollectingEventHandler {
    collisions: Mutex<Vec<CollisionEvent>>,
}

impl CollectingEventHandler {
    fn new() -> Self {
        Self {
            collisions: Mutex::new(Vec::new()),
        }
    }
}

impl EventHandler for CollectingEventHandler {
    fn handle_collision_event(
        &self,
        _bodies: &RigidBodySet,
        _colliders: &ColliderSet,
        event: CollisionEvent,
        _contact_pair: Option<&ContactPair>,
    ) {
        if let Ok(mut v) = self.collisions.lock() {
            v.push(event);
        }
    }

    fn handle_contact_force_event(
        &self,
        _dt: Real,
        _bodies: &RigidBodySet,
        _colliders: &ColliderSet,
        _contact_pair: &ContactPair,
        _total_force_magnitude: Real,
    ) {
    }
}

pub struct World {
    pipeline: PhysicsPipeline,
    gravity: Vector<Real>,
    integration_parameters: IntegrationParameters,
    islands: IslandManager,
    broad_phase: DefaultBroadPhase,
    narrow_phase: NarrowPhase,
    bodies: RigidBodySet,
    colliders: ColliderSet,
    impulse_joints: ImpulseJointSet,
    multibody_joints: MultibodyJointSet,
    ccd_solver: CCDSolver,
    query_pipeline: QueryPipeline,
    physics_hooks: (),
    events: CollectingEventHandler,
    pending_contacts: Vec<ContactEventRow>,
    pending_areas: Vec<AreaEventRow>,
}

impl World {
    fn new() -> Self {
        Self {
            pipeline: PhysicsPipeline::new(),
            gravity: vector![0.0, -9.81, 0.0],
            integration_parameters: IntegrationParameters::default(),
            islands: IslandManager::new(),
            broad_phase: DefaultBroadPhase::new(),
            narrow_phase: NarrowPhase::new(),
            bodies: RigidBodySet::new(),
            colliders: ColliderSet::new(),
            impulse_joints: ImpulseJointSet::new(),
            multibody_joints: MultibodyJointSet::new(),
            ccd_solver: CCDSolver::new(),
            query_pipeline: QueryPipeline::new(),
            physics_hooks: (),
            events: CollectingEventHandler::new(),
            pending_contacts: Vec::new(),
            pending_areas: Vec::new(),
        }
    }
}

const HANDLE_VALID_BIT: u64 = 1u64 << 63;

fn pack_body(handle: RigidBodyHandle) -> u64 {
    let (idx, gen) = handle.0.into_raw_parts();
    HANDLE_VALID_BIT | (((gen as u64) & 0x7FFF_FFFF) << 32) | (idx as u64 & 0xFFFF_FFFF)
}

fn unpack_body(packed: u64) -> RigidBodyHandle {
    let idx = (packed & 0xFFFF_FFFF) as u32;
    let gen = ((packed >> 32) & 0x7FFF_FFFF) as u32;
    RigidBodyHandle(rapier3d::data::Index::from_raw_parts(idx, gen))
}

fn pack_joint(handle: ImpulseJointHandle) -> u64 {
    let (idx, gen) = handle.0.into_raw_parts();
    HANDLE_VALID_BIT | (((gen as u64) & 0x7FFF_FFFF) << 32) | (idx as u64 & 0xFFFF_FFFF)
}

fn unpack_joint(packed: u64) -> ImpulseJointHandle {
    let idx = (packed & 0xFFFF_FFFF) as u32;
    let gen = ((packed >> 32) & 0x7FFF_FFFF) as u32;
    ImpulseJointHandle(rapier3d::data::Index::from_raw_parts(idx, gen))
}

fn make_groups(group: i32, mask: i32) -> InteractionGroups {
    InteractionGroups::new(
        Group::from_bits_truncate(group as u32),
        Group::from_bits_truncate(mask as u32),
    )
}

fn iso(x: f32, y: f32, z: f32, qx: f32, qy: f32, qz: f32, qw: f32) -> Isometry<Real> {
    use nalgebra::{Quaternion, Translation3, Unit};
    let q = Unit::new_normalize(Quaternion::new(qw, qx, qy, qz));
    Isometry::from_parts(Translation3::new(x, y, z), q)
}

#[no_mangle]
pub extern "C" fn rapier_world_new() -> *mut c_void {
    Box::into_raw(Box::new(World::new())) as *mut c_void
}

#[no_mangle]
pub extern "C" fn rapier_world_drop(world: *mut c_void) {
    if world.is_null() {
        return;
    }
    unsafe {
        let _ = Box::from_raw(world as *mut World);
    }
}

#[no_mangle]
pub extern "C" fn rapier_world_step(world: *mut c_void, dt: f32) {
    if world.is_null() {
        return;
    }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    w.integration_parameters.dt = dt;

    if let Ok(mut v) = w.events.collisions.lock() {
        v.clear();
    }

    w.pipeline.step(
        &w.gravity,
        &w.integration_parameters,
        &mut w.islands,
        &mut w.broad_phase,
        &mut w.narrow_phase,
        &mut w.bodies,
        &mut w.colliders,
        &mut w.impulse_joints,
        &mut w.multibody_joints,
        &mut w.ccd_solver,
        Some(&mut w.query_pipeline),
        &w.physics_hooks,
        &w.events,
    );

    let drained: Vec<CollisionEvent> = match w.events.collisions.lock() {
        Ok(mut v) => std::mem::take(&mut *v),
        Err(_) => Vec::new(),
    };

    for ev in drained {
        let c1 = ev.collider1();
        let c2 = ev.collider2();
        let body1 = w.colliders.get(c1).and_then(|c| c.parent()).map(pack_body).unwrap_or(0) as i64;
        let body2 = w.colliders.get(c2).and_then(|c| c.parent()).map(pack_body).unwrap_or(0) as i64;

        if ev.sensor() {
            w.pending_areas.push(AreaEventRow {
                area: body1,
                other: body2,
                entered: if ev.started() { 1 } else { 0 },
                _pad: 0,
            });
        } else {
            let mut row = ContactEventRow {
                a: body1,
                b: body2,
                px: 0.0,
                py: 0.0,
                pz: 0.0,
                nx: 0.0,
                ny: 0.0,
                nz: 0.0,
                impulse: 0.0,
            };
            if ev.started() {
                if let Some(pair) = w.narrow_phase.contact_pair(c1, c2) {
                    let (impulse, normal) = pair.max_impulse();
                    row.impulse = impulse;
                    row.nx = normal.x;
                    row.ny = normal.y;
                    row.nz = normal.z;
                    if let Some(m) = pair.manifolds.first() {
                        if let Some(p) = m.points.first() {
                            if let Some(co) = w.colliders.get(c1) {
                                let p_world = co.position() * p.local_p1;
                                row.px = p_world.x;
                                row.py = p_world.y;
                                row.pz = p_world.z;
                            }
                        }
                    }
                }
            }
            w.pending_contacts.push(row);
        }
    }
}

#[no_mangle]
pub extern "C" fn rapier_world_set_gravity(world: *mut c_void, gx: f32, gy: f32, gz: f32) {
    if world.is_null() {
        return;
    }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    w.gravity = vector![gx, gy, gz];
}

static SHAPE_POOL: Mutex<Vec<Option<SharedShape>>> = Mutex::new(Vec::new());

fn pool_store(shape: SharedShape) -> u64 {
    let mut pool = SHAPE_POOL.lock().unwrap();
    if pool.is_empty() {
        pool.push(None);
    }
    for (i, slot) in pool.iter_mut().enumerate().skip(1) {
        if slot.is_none() {
            *slot = Some(shape);
            return i as u64;
        }
    }
    let idx = pool.len() as u64;
    pool.push(Some(shape));
    idx
}

fn pool_get(handle: u64) -> Option<SharedShape> {
    if handle == 0 {
        return None;
    }
    let pool = SHAPE_POOL.lock().unwrap();
    pool.get(handle as usize).and_then(|s| s.clone())
}

fn pool_drop(handle: u64) {
    if handle == 0 {
        return;
    }
    let mut pool = SHAPE_POOL.lock().unwrap();
    if let Some(slot) = pool.get_mut(handle as usize) {
        *slot = None;
    }
}

#[no_mangle]
pub extern "C" fn rapier_shape_box(hx: f32, hy: f32, hz: f32) -> u64 {
    pool_store(SharedShape::cuboid(hx, hy, hz))
}

#[no_mangle]
pub extern "C" fn rapier_shape_sphere(r: f32) -> u64 {
    pool_store(SharedShape::ball(r))
}

#[no_mangle]
pub extern "C" fn rapier_shape_capsule(r: f32, half_height: f32) -> u64 {
    pool_store(SharedShape::capsule_y(half_height, r))
}

#[no_mangle]
pub extern "C" fn rapier_shape_trimesh(
    verts: *const f32,
    vlen: i32,
    idx: *const i32,
    ilen: i32,
) -> u64 {
    if verts.is_null() || idx.is_null() || vlen <= 0 || ilen <= 0 {
        return 0;
    }
    if vlen % 3 != 0 || ilen % 3 != 0 {
        return 0;
    }
    let verts_slice = unsafe { std::slice::from_raw_parts(verts, vlen as usize) };
    let idx_slice = unsafe { std::slice::from_raw_parts(idx, ilen as usize) };
    let points: Vec<Point<Real>> = verts_slice
        .chunks_exact(3)
        .map(|c| Point::new(c[0], c[1], c[2]))
        .collect();
    let triangles: Vec<[u32; 3]> = idx_slice
        .chunks_exact(3)
        .map(|c| [c[0] as u32, c[1] as u32, c[2] as u32])
        .collect();
    pool_store(SharedShape::trimesh(points, triangles))
}

#[no_mangle]
pub extern "C" fn rapier_shape_drop(shape: u64) {
    pool_drop(shape);
}

fn add_body_with(
    w: &mut World,
    builder: RigidBodyBuilder,
    shape: u64,
    group: i32,
    mask: i32,
    sensor: bool,
) -> u64 {
    let s = match pool_get(shape) {
        Some(s) => s,
        None => return 0,
    };
    let rb = builder.build();
    let handle = w.bodies.insert(rb);
    let mut col = ColliderBuilder::new(s)
        .collision_groups(make_groups(group, mask))
        .active_events(ActiveEvents::COLLISION_EVENTS);
    if sensor {
        col = col.sensor(true);
    }
    w.colliders.insert_with_parent(col.build(), handle, &mut w.bodies);
    pack_body(handle)
}

#[no_mangle]
pub extern "C" fn rapier_body_add_static(
    world: *mut c_void,
    shape: u64,
    x: f32, y: f32, z: f32,
    qx: f32, qy: f32, qz: f32, qw: f32,
    group: i32, mask: i32,
) -> u64 {
    if world.is_null() { return 0; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    let builder = RigidBodyBuilder::fixed().position(iso(x, y, z, qx, qy, qz, qw));
    add_body_with(w, builder, shape, group, mask, false)
}

#[no_mangle]
pub extern "C" fn rapier_body_add_dynamic(
    world: *mut c_void,
    shape: u64,
    x: f32, y: f32, z: f32,
    qx: f32, qy: f32, qz: f32, qw: f32,
    group: i32, mask: i32,
    mass: f32,
    gscale: f32,
    lin_damp: f32,
    ang_damp: f32,
    ccd: i32,
) -> u64 {
    if world.is_null() { return 0; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    let builder = RigidBodyBuilder::dynamic()
        .position(iso(x, y, z, qx, qy, qz, qw))
        .gravity_scale(gscale)
        .linear_damping(lin_damp)
        .angular_damping(ang_damp)
        .ccd_enabled(ccd != 0)
        .additional_mass(mass);
    add_body_with(w, builder, shape, group, mask, false)
}

#[no_mangle]
pub extern "C" fn rapier_body_add_kinematic(
    world: *mut c_void,
    shape: u64,
    x: f32, y: f32, z: f32,
    qx: f32, qy: f32, qz: f32, qw: f32,
    group: i32, mask: i32,
) -> u64 {
    if world.is_null() { return 0; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    let builder = RigidBodyBuilder::kinematic_position_based().position(iso(x, y, z, qx, qy, qz, qw));
    add_body_with(w, builder, shape, group, mask, false)
}

#[no_mangle]
pub extern "C" fn rapier_body_add_area(
    world: *mut c_void,
    shape: u64,
    x: f32, y: f32, z: f32,
    qx: f32, qy: f32, qz: f32, qw: f32,
    group: i32, mask: i32,
) -> u64 {
    if world.is_null() { return 0; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    let builder = RigidBodyBuilder::fixed().position(iso(x, y, z, qx, qy, qz, qw));
    add_body_with(w, builder, shape, group, mask, true)
}

#[no_mangle]
pub extern "C" fn rapier_body_remove(world: *mut c_void, body: u64) {
    if world.is_null() || body == 0 { return; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    let h = unpack_body(body);
    let _ = w.bodies.remove(
        h,
        &mut w.islands,
        &mut w.colliders,
        &mut w.impulse_joints,
        &mut w.multibody_joints,
        true,
    );
}

#[no_mangle]
pub extern "C" fn rapier_body_set_xform(
    world: *mut c_void,
    body: u64,
    x: f32, y: f32, z: f32,
    qx: f32, qy: f32, qz: f32, qw: f32,
) {
    if world.is_null() { return; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    if let Some(rb) = w.bodies.get_mut(unpack_body(body)) {
        rb.set_position(iso(x, y, z, qx, qy, qz, qw), true);
    }
}

#[no_mangle]
pub extern "C" fn rapier_body_get_xform(world: *mut c_void, body: u64, out: *mut f32) {
    if world.is_null() || out.is_null() { return; }
    let w: &World = unsafe { &*(world as *const World) };
    if let Some(rb) = w.bodies.get(unpack_body(body)) {
        let p = rb.position();
        let t = p.translation.vector;
        let q = p.rotation;
        let arr = [t.x, t.y, t.z, q.i, q.j, q.k, q.w];
        unsafe { std::ptr::copy_nonoverlapping(arr.as_ptr(), out, 7); }
    }
}

#[no_mangle]
pub extern "C" fn rapier_body_apply_force(world: *mut c_void, body: u64, fx: f32, fy: f32, fz: f32) {
    if world.is_null() { return; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    if let Some(rb) = w.bodies.get_mut(unpack_body(body)) {
        rb.add_force(vector![fx, fy, fz], true);
    }
}

#[no_mangle]
pub extern "C" fn rapier_body_apply_impulse(world: *mut c_void, body: u64, jx: f32, jy: f32, jz: f32) {
    if world.is_null() { return; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    if let Some(rb) = w.bodies.get_mut(unpack_body(body)) {
        rb.apply_impulse(vector![jx, jy, jz], true);
    }
}

#[no_mangle]
pub extern "C" fn rapier_body_set_linvel(world: *mut c_void, body: u64, vx: f32, vy: f32, vz: f32) {
    if world.is_null() { return; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    if let Some(rb) = w.bodies.get_mut(unpack_body(body)) {
        rb.set_linvel(vector![vx, vy, vz], true);
    }
}

#[no_mangle]
pub extern "C" fn rapier_body_get_linvel(world: *mut c_void, body: u64, out: *mut f32) {
    if world.is_null() || out.is_null() { return; }
    let w: &World = unsafe { &*(world as *const World) };
    if let Some(rb) = w.bodies.get(unpack_body(body)) {
        let v = rb.linvel();
        let arr = [v.x, v.y, v.z];
        unsafe { std::ptr::copy_nonoverlapping(arr.as_ptr(), out, 3); }
    }
}

#[no_mangle]
pub extern "C" fn rapier_body_sleep(world: *mut c_void, body: u64) {
    if world.is_null() { return; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    if let Some(rb) = w.bodies.get_mut(unpack_body(body)) {
        rb.sleep();
    }
}

#[no_mangle]
pub extern "C" fn rapier_body_wake(world: *mut c_void, body: u64) {
    if world.is_null() { return; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    if let Some(rb) = w.bodies.get_mut(unpack_body(body)) {
        rb.wake_up(true);
    }
}

#[no_mangle]
pub extern "C" fn rapier_joint_add_fixed(
    world: *mut c_void,
    body_a: u64,
    body_b: u64,
    ax: f32, ay: f32, az: f32,
    aqx: f32, aqy: f32, aqz: f32, aqw: f32,
    bx: f32, by: f32, bz: f32,
    bqx: f32, bqy: f32, bqz: f32, bqw: f32,
    contacts_enabled: i32,
) -> u64 {
    if world.is_null() || body_a == 0 || body_b == 0 { return 0; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    let joint = FixedJointBuilder::new()
        .local_frame1(iso(ax, ay, az, aqx, aqy, aqz, aqw))
        .local_frame2(iso(bx, by, bz, bqx, bqy, bqz, bqw))
        .contacts_enabled(contacts_enabled != 0)
        .build();
    let handle = w.impulse_joints.insert(unpack_body(body_a), unpack_body(body_b), joint, true);
    pack_joint(handle)
}

#[no_mangle]
pub extern "C" fn rapier_joint_add_spherical(
    world: *mut c_void,
    body_a: u64,
    body_b: u64,
    ax: f32, ay: f32, az: f32,
    bx: f32, by: f32, bz: f32,
    contacts_enabled: i32,
) -> u64 {
    if world.is_null() || body_a == 0 || body_b == 0 { return 0; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    let joint = SphericalJointBuilder::new()
        .local_anchor1(Point::new(ax, ay, az))
        .local_anchor2(Point::new(bx, by, bz))
        .contacts_enabled(contacts_enabled != 0)
        .build();
    let handle = w.impulse_joints.insert(unpack_body(body_a), unpack_body(body_b), joint, true);
    pack_joint(handle)
}

#[no_mangle]
pub extern "C" fn rapier_joint_remove(world: *mut c_void, joint: u64) {
    if world.is_null() || joint == 0 { return; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    w.impulse_joints.remove(unpack_joint(joint), true);
}

unsafe fn write_hit(out: *mut u8, p: Point<Real>, n: Vector<Real>, distance: f32, body_id: i64) {
    let arr_f = [p.x, p.y, p.z, n.x, n.y, n.z, distance];
    std::ptr::copy_nonoverlapping(arr_f.as_ptr() as *const u8, out, 7 * 4);
    std::ptr::copy_nonoverlapping(
        (&body_id as *const i64) as *const u8,
        out.add(7 * 4),
        8,
    );
}

#[no_mangle]
pub extern "C" fn rapier_query_raycast(
    world: *mut c_void,
    ox: f32, oy: f32, oz: f32,
    dx: f32, dy: f32, dz: f32,
    max_dist: f32,
    mask: i32,
    out: *mut u8,
) -> bool {
    if world.is_null() { return false; }
    let w: &World = unsafe { &*(world as *const World) };
    let ray = Ray::new(Point::new(ox, oy, oz), vector![dx, dy, dz]);
    let filter = QueryFilter::new().groups(InteractionGroups::new(
        Group::all(),
        Group::from_bits_truncate(mask as u32),
    ));
    if let Some((handle, hit)) = w.query_pipeline.cast_ray_and_get_normal(
        &w.bodies,
        &w.colliders,
        &ray,
        max_dist,
        true,
        filter,
    ) {
        let p = ray.point_at(hit.time_of_impact);
        let body_id = w
            .colliders
            .get(handle)
            .and_then(|c| c.parent())
            .map(pack_body)
            .unwrap_or(0) as i64;
        unsafe { write_hit(out, p, hit.normal, hit.time_of_impact, body_id); }
        true
    } else {
        false
    }
}

#[no_mangle]
pub extern "C" fn rapier_query_shape_cast(
    world: *mut c_void,
    shape: u64,
    x: f32, y: f32, z: f32,
    qx: f32, qy: f32, qz: f32, qw: f32,
    dx: f32, dy: f32, dz: f32,
    max_dist: f32,
    mask: i32,
    out: *mut u8,
) -> bool {
    if world.is_null() { return false; }
    let w: &World = unsafe { &*(world as *const World) };
    let s = match pool_get(shape) {
        Some(s) => s,
        None => return false,
    };
    let pos = iso(x, y, z, qx, qy, qz, qw);
    let vel = vector![dx, dy, dz];
    let mut options = ShapeCastOptions::default();
    options.max_time_of_impact = max_dist;
    options.stop_at_penetration = true;
    let filter = QueryFilter::new().groups(InteractionGroups::new(
        Group::all(),
        Group::from_bits_truncate(mask as u32),
    ));
    if let Some((handle, hit)) = w.query_pipeline.cast_shape(
        &w.bodies,
        &w.colliders,
        &pos,
        &vel,
        s.as_ref(),
        options,
        filter,
    ) {
        let body_id = w
            .colliders
            .get(handle)
            .and_then(|c| c.parent())
            .map(pack_body)
            .unwrap_or(0) as i64;
        let witness = hit.witness1;
        unsafe { write_hit(out, witness, *hit.normal1, hit.time_of_impact, body_id); }
        true
    } else {
        false
    }
}

#[no_mangle]
pub extern "C" fn rapier_query_overlap(
    world: *mut c_void,
    shape: u64,
    x: f32, y: f32, z: f32,
    qx: f32, qy: f32, qz: f32, qw: f32,
    mask: i32,
    out: *mut i64,
    max_results: i32,
) -> i32 {
    if world.is_null() || out.is_null() || max_results <= 0 { return 0; }
    let w: &World = unsafe { &*(world as *const World) };
    let s = match pool_get(shape) {
        Some(s) => s,
        None => return 0,
    };
    let pos = iso(x, y, z, qx, qy, qz, qw);
    let filter = QueryFilter::new().groups(InteractionGroups::new(
        Group::all(),
        Group::from_bits_truncate(mask as u32),
    ));
    let cap = max_results as usize;
    let out_slice: &mut [i64] = unsafe { std::slice::from_raw_parts_mut(out, cap) };
    let mut count: usize = 0;
    w.query_pipeline.intersections_with_shape(
        &w.bodies,
        &w.colliders,
        &pos,
        s.as_ref(),
        filter,
        |handle| {
            if count >= cap {
                return false;
            }
            let body_id = w
                .colliders
                .get(handle)
                .and_then(|c| c.parent())
                .map(pack_body)
                .unwrap_or(0) as i64;
            out_slice[count] = body_id;
            count += 1;
            true
        },
    );
    count as i32
}

pub struct CharCtl {
    inner: KinematicCharacterController,
}

#[no_mangle]
pub extern "C" fn rapier_charctl_new(_world: *mut c_void) -> *mut c_void {
    use rapier3d::control::{CharacterAutostep, CharacterLength};
    let mut inner = KinematicCharacterController::default();
    inner.offset = CharacterLength::Absolute(0.05);
    inner.up = Vector::y_axis();
    inner.slide = true;
    inner.max_slope_climb_angle = std::f32::consts::FRAC_PI_4 + 0.0175;
    inner.min_slope_slide_angle = std::f32::consts::FRAC_PI_4 + 0.0175;
    inner.autostep = Some(CharacterAutostep {
        max_height: CharacterLength::Absolute(0.55),
        min_width:  CharacterLength::Absolute(0.16),
        include_dynamic_bodies: false,
    });
    inner.snap_to_ground = Some(CharacterLength::Absolute(0.5));
    Box::into_raw(Box::new(CharCtl { inner })) as *mut c_void
}

#[no_mangle]
pub extern "C" fn rapier_charctl_drop(ctl: *mut c_void) {
    if ctl.is_null() { return; }
    unsafe { let _ = Box::from_raw(ctl as *mut CharCtl); }
}

#[no_mangle]
pub extern "C" fn rapier_charctl_move(
    ctl: *mut c_void,
    world: *mut c_void,
    body: u64,
    dx: f32, dy: f32, dz: f32,
    dt: f32,
    out: *mut u8,
) {
    if ctl.is_null() || world.is_null() || out.is_null() { return; }
    let c: &CharCtl = unsafe { &*(ctl as *const CharCtl) };
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    let h = unpack_body(body);
    let (shape_clone, pos, _collider_handle) = {
        let rb = match w.bodies.get(h) {
            Some(rb) => rb,
            None => return,
        };
        let collider_handle = match rb.colliders().first() {
            Some(c) => *c,
            None => return,
        };
        let collider = match w.colliders.get(collider_handle) {
            Some(c) => c,
            None => return,
        };
        (collider.shared_shape().clone(), *rb.position(), collider_handle)
    };
    let filter = QueryFilter::new().exclude_rigid_body(h);
    let movement: EffectiveCharacterMovement = c.inner.move_shape(
        dt,
        &w.bodies,
        &w.colliders,
        &w.query_pipeline,
        shape_clone.as_ref(),
        &pos,
        vector![dx, dy, dz],
        filter,
        |_| {},
    );
    let arr_f = [movement.translation.x, movement.translation.y, movement.translation.z];
    let grounded: i32 = if movement.grounded { 1 } else { 0 };
    unsafe {
        std::ptr::copy_nonoverlapping(arr_f.as_ptr() as *const u8, out, 12);
        std::ptr::copy_nonoverlapping((&grounded as *const i32) as *const u8, out.add(12), 4);
    }
}

#[no_mangle]
pub extern "C" fn rapier_charctl_move_shape(
    ctl: *mut c_void,
    world: *mut c_void,
    shape: u64,
    px: f32, py: f32, pz: f32,
    qx: f32, qy: f32, qz: f32, qw: f32,
    dx: f32, dy: f32, dz: f32,
    dt: f32,
    out: *mut u8,
) {
    if ctl.is_null() || world.is_null() || out.is_null() { return; }
    let shape_clone = match pool_get(shape) {
        Some(s) => s,
        None => {
            let zero = [0f32, 0f32, 0f32];
            let g: i32 = 0;
            unsafe {
                std::ptr::copy_nonoverlapping(zero.as_ptr() as *const u8, out, 12);
                std::ptr::copy_nonoverlapping((&g as *const i32) as *const u8, out.add(12), 4);
            }
            return;
        }
    };
    let c: &CharCtl = unsafe { &*(ctl as *const CharCtl) };
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    let translation = nalgebra::Translation3::new(px, py, pz);
    let rotation = nalgebra::UnitQuaternion::from_quaternion(
        nalgebra::Quaternion::new(qw, qx, qy, qz),
    );
    let pos = Isometry::from_parts(translation, rotation);
    let filter = QueryFilter::new();
    let movement: EffectiveCharacterMovement = c.inner.move_shape(
        dt,
        &w.bodies,
        &w.colliders,
        &w.query_pipeline,
        shape_clone.as_ref(),
        &pos,
        vector![dx, dy, dz],
        filter,
        |_| {},
    );
    let arr_f = [movement.translation.x, movement.translation.y, movement.translation.z];
    let grounded: i32 = if movement.grounded { 1 } else { 0 };
    unsafe {
        std::ptr::copy_nonoverlapping(arr_f.as_ptr() as *const u8, out, 12);
        std::ptr::copy_nonoverlapping((&grounded as *const i32) as *const u8, out.add(12), 4);
    }
}

#[no_mangle]
pub extern "C" fn rapier_events_drain_contacts(
    world: *mut c_void,
    out: *mut u8,
    max_events: i32,
) -> i32 {
    if world.is_null() || out.is_null() || max_events <= 0 { return 0; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    let n = std::cmp::min(max_events as usize, w.pending_contacts.len());
    const ROW: usize = 44;
    for i in 0..n {
        let row = w.pending_contacts[i];
        let dst = unsafe { out.add(i * ROW) };
        unsafe {
            std::ptr::copy_nonoverlapping((&row.a as *const i64) as *const u8, dst, 8);
            std::ptr::copy_nonoverlapping((&row.b as *const i64) as *const u8, dst.add(8), 8);
            let floats = [row.px, row.py, row.pz, row.nx, row.ny, row.nz, row.impulse];
            std::ptr::copy_nonoverlapping(floats.as_ptr() as *const u8, dst.add(16), 7 * 4);
        }
    }
    w.pending_contacts.drain(..n);
    n as i32
}

#[no_mangle]
pub extern "C" fn rapier_events_drain_areas(
    world: *mut c_void,
    out: *mut u8,
    max_events: i32,
) -> i32 {
    if world.is_null() || out.is_null() || max_events <= 0 { return 0; }
    let w: &mut World = unsafe { &mut *(world as *mut World) };
    let n = std::cmp::min(max_events as usize, w.pending_areas.len());
    const ROW: usize = 24;
    for i in 0..n {
        let row = w.pending_areas[i];
        let dst = unsafe { out.add(i * ROW) };
        unsafe {
            std::ptr::copy_nonoverlapping((&row.area as *const i64) as *const u8, dst, 8);
            std::ptr::copy_nonoverlapping((&row.other as *const i64) as *const u8, dst.add(8), 8);
            std::ptr::copy_nonoverlapping((&row.entered as *const i32) as *const u8, dst.add(16), 4);
            let zero: i32 = 0;
            std::ptr::copy_nonoverlapping((&zero as *const i32) as *const u8, dst.add(20), 4);
        }
    }
    w.pending_areas.drain(..n);
    n as i32
}
