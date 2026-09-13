    function distanceTo(self, other: Instance): number
    function distanceSqTo(self, other: Instance): number
    function canSee(self, other: Instance, range: number?): boolean
    function isGrounded(self): boolean
    function isInWater(self): boolean
    function isMoving(self): boolean
    function lookDirection(self): Vector3
    function facing(self, other: Instance, maxAngle: number?): boolean
    function walkTo(self, point: Vector3, options: PathOptions?): boolean
    function follow(self, target: Instance, distance: number?): ()
    function stopWalking(self): ()
    function isWalking(self): boolean
    function jump(self): ()
    function lookAt(self, point: Vector3): ()
    function face(self, direction: Vector3): ()
