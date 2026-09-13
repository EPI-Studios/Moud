type TweenInfo = { time: number?, easing: string?, direction: string?, repeats: number?,
    reverses: boolean?, delay: number? }

declare class Tween
    completed: StepSignal
    function pause(self): ()
    function resume(self): ()
    function cancel(self): ()
    function state(self): string
end

type RayHit = { part: Instance, position: Vector3, distance: number, normal: Vector3 }
type QueryOptions = { exclude: { Instance }?, include: { Instance }?, respectCollides: boolean?, collisionGroup: string?, tag: string?, className: string?, limit: number?, sorted: boolean?,
    ignoreBlocks: boolean? }

declare class Instance
    name: string
    className: string
    parent: Instance?
    changed: ChangedSignal
    childAdded: InstanceSignal
    destroying: InstanceSignal
    function add(self, className: string, properties: { [string]: any }?): Instance
    function addAll(self, className: string, properties: { { [string]: any } }): number
    function children(self): { Instance }
    function find(self, name: string): Instance?
    function isA(self, className: string): boolean
    function raycast(self, from: Vector3, direction: Vector3, range: number?, options: QueryOptions?): (Instance?, Vector3?, number?, Vector3?, string?)
    function spherecast(self, from: Vector3, radius: number, direction: Vector3, range: number?, options: QueryOptions?): (Instance?, Vector3?, number?, Vector3?)
    function blockcast(self, frame: CFrame, size: Vector3, direction: Vector3, range: number?, options: QueryOptions?): (Instance?, Vector3?, number?, Vector3?)
    function partsInBox(self, frame: CFrame, size: Vector3, options: QueryOptions?): { Instance }
    function partsInRadius(self, position: Vector3, radius: number, options: QueryOptions?): { Instance }
    function partsInPart(self, part: Instance, options: QueryOptions?): { Instance }
    function raycastAll(self, from: Vector3, direction: Vector3, range: number?, options: QueryOptions?): { RayHit }
    function raycastMany(self, rays: { { any } }, options: QueryOptions?): { RayHit | false }
    function nearestPart(self, position: Vector3, radius: number, options: QueryOptions?): (Instance?, number?)
    function nearestTagged(self, position: Vector3, tag: string, radius: number?): (Instance?, number?)
    function partsAlongRay(self, from: Vector3, direction: Vector3, range: number?, options: QueryOptions?): { RayHit }
    function capsulecast(self, from: Vector3, to: Vector3, radius: number, options: QueryOptions?): (Instance?, Vector3?, number?, Vector3?)
    function sweep(self, part: Instance, direction: Vector3, distance: number, options: QueryOptions?): (Instance?, Vector3?, number?, Vector3?)
    function partsAtPoint(self, position: Vector3, options: QueryOptions?): { Instance }
    function boundsOf(self, instances: { Instance }): (CFrame?, Vector3?)
    function groundAt(self, x: number, z: number, from: number?, options: QueryOptions?): (number?, Vector3?)
    function surfaceNormal(self, position: Vector3, options: QueryOptions?): Vector3?
    function heightmap(self, from: Vector3, to: Vector3, step: number?, options: QueryOptions?): { { number } }
    function findFreeSpot(self, near: Vector3, size: Vector3, radius: number?): Vector3?
    function findFirstDescendant(self, name: string): Instance?
    function descendants(self, className: string?): { Instance }
    function childrenOfClass(self, className: string): { Instance }
    function firstAncestorOfClass(self, className: string): Instance?
    function firstAncestor(self, name: string): Instance?
    function isDescendantOf(self, other: Instance): boolean
    function byTag(self, tag: string): { Instance }
    function values(self): { [string]: any }
    function clone(self, parent: Instance?): Instance?
    function query(self, selector: string): { Instance }
    function queryFirst(self, selector: string): Instance?
    function waitForChild(self, name: string, timeout: number?): Instance?
    function onChild(self, name: string, fn: (child: Instance) -> ()): Connection
    function destroy(self): ()
    function setOwner(self, to: Instance?): ()
    function addTag(self, tag: string): ()
    function removeTag(self, tag: string): ()
    function hasTag(self, tag: string): boolean
    function getTags(self): { string }
    function tween(self, goals: { [string]: any }, info: TweenInfo?): Tween
    function fireServer(self, ...: any): ()
    function fireClient(self, to: Instance, ...: any): ()
    function fireAllClients(self, ...: any): ()
end

