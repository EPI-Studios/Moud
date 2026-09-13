    function shake(self, trauma: number): ()
    function kick(self, pitch: number, yaw: number, roll: number, seconds: number): ()
    function fovPunch(self, degrees: number, seconds: number): ()
    function clearEffects(self): ()
    function worldToScreen(self, world: Vector3): Vector3?
    function screenToRay(self, x: number, y: number): (Vector3, Vector3)
