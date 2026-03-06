({
  tool: true,
  actions: {
    "Reset Player Transform": (api) => {
      const node = api.node();
      api.set(node.id, "x", "0");
      api.set(node.id, "y", "-64");
      api.set(node.id, "z", "0");
      api.set(node.id, "ry", "0");
    }
  },

  _enter_tree(api) {
    this.vx = 0.0;
    this.vy = 0.0;
    this.vz = 0.0;
    this.onFloor = true;
    this.cameraId = 0;
  },

  _physics_process(api, dt) {
    const input = api.input();
    if (!input) {
      return;
    }

    const yaw = input.yawDeg();
    const pitch = input.pitchDeg();

    api.setNumber("ry", yaw);
    if (!this.cameraId) {
      this.cameraId = api.find("./Camera3D");
    }
    if (this.cameraId) {
      api.setNumber(this.cameraId, "rx", pitch);
    }

    let moveX = input.moveX();
    let moveZ = input.moveZ();
    const len = Math.hypot(moveX, moveZ);
    if (len > 1e-6 && len > 1.0) {
      moveX /= len;
      moveZ /= len;
    }

    const speed = api.getNumber("speed", 6.0);
    const effectiveSpeed = input.sprint() ? speed * 1.5 : speed;

    const yawRad = yaw * Math.PI / 180.0;
    const cos = Math.cos(yawRad);
    const sin = Math.sin(yawRad);

    const targetVx = (-moveZ * sin + moveX * cos) * effectiveSpeed;
    const targetVz = (moveZ * cos + moveX * sin) * effectiveSpeed;

    const isMoving = Math.abs(moveX) > 1e-4 || Math.abs(moveZ) > 1e-4;
    const accel = this.onFloor
      ? (isMoving ? 40.0 : 50.0)
      : (isMoving ? 10.0 : 5.0);

    const moveToward = (cur, target, maxDelta) => {
      const diff = target - cur;
      if (Math.abs(diff) <= maxDelta) return target;
      return cur + Math.sign(diff) * maxDelta;
    };

    this.vx = moveToward(this.vx, targetVx, accel * dt);
    this.vz = moveToward(this.vz, targetVz, accel * dt);

    if (this.onFloor && input.jump()) {
      this.vy = 10.0;
    }
    this.vy -= 30.0 * dt;

    let x = api.getNumber("x", 0.0);
    let y = api.getNumber("y", -64.0);
    let z = api.getNumber("z", 0.0);

    x += this.vx * dt;
    y += this.vy * dt;
    z += this.vz * dt;

    const floorY = -64.0;
    this.onFloor = false;
    if (y <= floorY) {
      y = floorY;
      if (this.vy < 0.0) {
        this.vy = 0.0;
      }
      this.onFloor = true;
    }

    api.setNumber("x", x);
    api.setNumber("y", y);
    api.setNumber("z", z);
  }
})

