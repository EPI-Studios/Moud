(function () {
    'use strict';

    var NESTED_PROP_KEYS = {
        position: { x: 'x', y: 'y', z: 'z' },
        rotation: { x: 'rx', y: 'ry', z: 'rz' },
        scale:    { x: 'sx', y: 'sy', z: 'sz' }
    };

    var INTERNAL_PROPS = {
        __api: true, __id: true, __nodeType: true, __userProps: true,
        __target: true, constructor: true
    };

    function getMoud(target) {
        if (!target.constructor.__moud) target.constructor.__moud = {};
        return target.constructor.__moud;
    }

    function makeVec3Proxy(api, nodeId, keys) {
        return new Proxy({}, {
            get: function (_, axis) {
                if (Object.prototype.hasOwnProperty.call(keys, axis))
                    return api.getNumber(nodeId, keys[axis], 0);
                return undefined;
            },
            set: function (_, axis, value) {
                if (Object.prototype.hasOwnProperty.call(keys, axis)) {
                    api.setNumber(nodeId, keys[axis], value);
                    return true;
                }
                return false;
            }
        });
    }

    function wrapNodeRef(api, nodeId) {
        if (nodeId == null) return null;
        var id = typeof nodeId === 'object' ? Number(nodeId) : nodeId;
        if (!id || id <= 0) return null;

        if (__typeClassMap === null) buildTypeClassMap();

        var nodeType = '';
        try { nodeType = api.typeOf(id) || ''; } catch (_) { nodeType = ''; }
        var Cls = __typeClassMap[nodeType] || Node;

        var inst = new Cls();
        inst.__api = api;
        inst.__id = id;
        inst.__nodeType = nodeType;
        inst.__userProps = {};
        return inst;
    }

    function engineGet(api, nodeId, nodeType, userProps, prop) {
        var typeSchema = (typeof __moudTypes !== 'undefined' && nodeType) ? __moudTypes[nodeType] : null;
        var propType = typeSchema ? typeSchema[prop] : null;
        if (propType) {
            if (propType === 'float' || propType === 'int') return api.getNumber(nodeId, prop, 0);
            if (propType === 'bool') return api.get(nodeId, prop) === 'true';
            return api.get(nodeId, prop);
        }
        var userProp = userProps ? userProps[prop] : null;
        if (userProp != null) {
            if (userProp.isNumber) return api.getNumber(nodeId, prop, userProp.defaultValue);
            if (userProp.isBool) return api.get(nodeId, prop) === 'true';
            return api.get(nodeId, prop) != null ? api.get(nodeId, prop) : userProp.defaultValue;
        }
        return undefined;
    }

    function engineSet(api, nodeId, nodeType, userProps, prop, value) {
        var typeSchema = (typeof __moudTypes !== 'undefined' && nodeType) ? __moudTypes[nodeType] : null;
        var propType = typeSchema ? typeSchema[prop] : null;
        if (propType) {
            if (propType === 'float' || propType === 'int') { api.setNumber(nodeId, prop, value); return true; }
            if (propType === 'bool') { api.set(nodeId, prop, value ? 'true' : 'false'); return true; }
            api.set(nodeId, prop, String(value));
            return true;
        }
        if (userProps && prop in userProps) {
            var userProp = userProps[prop];
            if (userProp.isNumber) api.setNumber(nodeId, prop, value);
            else if (userProp.isBool) api.set(nodeId, prop, value ? 'true' : 'false');
            else api.set(nodeId, prop, String(value));
            return true;
        }
        if (typeof value === 'number') api.setNumber(nodeId, prop, value);
        else if (typeof value === 'boolean') api.set(nodeId, prop, value ? 'true' : 'false');
        else api.set(nodeId, prop, String(value));
        return true;
    }

    var nodeProxyHandler = {
        get: function (target, prop, receiver) {
            if (Object.prototype.hasOwnProperty.call(INTERNAL_PROPS, prop) ||
                    prop in target) {
                return Reflect.get(target, prop, receiver);
            }
            if (Object.prototype.hasOwnProperty.call(NESTED_PROP_KEYS, prop)) {
                if (!target.__api) return undefined;
                return makeVec3Proxy(target.__api, target.__id, NESTED_PROP_KEYS[prop]);
            }
            if (!target.__api) return undefined;
            return engineGet(target.__api, target.__id, target.__nodeType, target.__userProps, prop);
        },

        set: function (target, prop, value) {
            if (!target.__api) {
                Reflect.set(target, prop, value);
                return true;
            }
            if (Object.prototype.hasOwnProperty.call(INTERNAL_PROPS, prop)) {
                target[prop] = value;
                return true;
            }
            if (Object.prototype.hasOwnProperty.call(NESTED_PROP_KEYS, prop) &&
                    typeof value === 'object' && value !== null) {
                var keys = NESTED_PROP_KEYS[prop];
                for (var axis in keys) {
                    if (Object.prototype.hasOwnProperty.call(value, axis))
                        target.__api.setNumber(target.__id, keys[axis], value[axis]);
                }
                return true;
            }
            return engineSet(target.__api, target.__id, target.__nodeType, target.__userProps, prop, value);
        }
    };

    function Node() {
        this.__api = null;
        this.__id = 0;
        this.__nodeType = null;
        this.__userProps = {};
        this.__target = this;
        return new Proxy(this, nodeProxyHandler);
    }

    Node.prototype._enter_tree = function (api) {
        var rawTarget = this.__target;
        rawTarget.__api = api;
        rawTarget.__id = api.id();
        rawTarget.__nodeType = api.type();
        rawTarget.__userProps = {};

        var moud = this.constructor.__moud || {};

        var signals = moud.signals;
        if (signals) {
            for (var i = 0; i < signals.length; i++) {
                api.connect(rawTarget.__id, signals[i].signal, rawTarget.__id, signals[i].method);
            }
        }

        var properties = moud.properties;
        if (properties) {
            for (var j = 0; j < properties.length; j++) {
                var key = properties[j];
                var rawDefault = rawTarget[key];
                var isNumber = typeof rawDefault === 'number';
                var isBool = typeof rawDefault === 'boolean';
                rawTarget.__userProps[key] = { isNumber: isNumber, isBool: isBool, defaultValue: rawDefault };
            }
        }

        globalThis.__currentApi = api;
        globalThis.__currentNodeId = rawTarget.__id;

        var h = moud.enterTree;
        if (h) this[h]();
    };

    Node.prototype._ready = function (api) {
        var h = (this.constructor.__moud || {}).ready;
        if (h) this[h]();
    };

    Node.prototype._process = function (api, dt) {
        var h = (this.constructor.__moud || {}).process;
        if (h) this[h](dt);
    };

    Node.prototype._physics_process = function (api, dt) {
        var h = (this.constructor.__moud || {}).physicsProcess;
        if (h) this[h](dt);
    };

    Node.prototype._input = function (api, event) {
        var h = (this.constructor.__moud || {}).input;
        if (h) this[h](event);
    };

    Node.prototype._exit_tree = function (api) {
        var h = (this.constructor.__moud || {}).exitTree;
        if (h) this[h]();
        globalThis.__currentApi = null;
        globalThis.__currentNodeId = 0;
    };

    Object.defineProperty(Node.prototype, 'name', { get: function () { return this.__api.name(); } });
    Object.defineProperty(Node.prototype, 'type', { get: function () { return this.__api.type(); } });

    Node.prototype.find = function (path) { return wrapNodeRef(this.__api, this.__api.find(path)); };
    Node.prototype.getChildren = function () {
        var ids = this.__api.getChildren(this.__id);
        var result = [];
        for (var i = 0; i < ids.length; i++) result.push(wrapNodeRef(this.__api, ids[i]));
        return result;
    };
    Node.prototype.exists = function () { return this.__api.exists(this.__id); };
    Node.prototype.rename = function (n) { this.__api.rename(this.__id, n); };
    Node.prototype.reparent = function (parent) { this.__api.reparent(this.__id, parent.__id); };
    Node.prototype.free = function () { this.__api.free(this.__id); };
    Node.prototype.flush = function () { this.__api.flush(); };
    Node.prototype.createChild = function (name, type) {
        return wrapNodeRef(this.__api, this.__api.createRuntime(this.__id, name, type));
    };

    Node.prototype.connect = function (opts) {
        this.__api.connect(this.__id, opts.signal, opts.target.__id, opts.handler);
    };
    Node.prototype.disconnect = function (opts) {
        this.__api.disconnect(this.__id, opts.signal, opts.target.__id, opts.handler);
    };
    Node.prototype.emit = function (signal, a1, a2, a3) {
        this.__api.emit_signal(signal, a1, a2, a3);
    };

    Node.prototype.tween = function (opts) {
        this.__api.tween(this.__id, opts.property, opts.to, opts.duration);
        if (opts.onComplete) this.__api.after(opts.duration, opts.onComplete);
    };

    Node.prototype.getProperty = function (key) { return this.__api.get(this.__id, key); };
    Node.prototype.setProperty = function (key, value) {
        if (typeof value === 'number') this.__api.setNumber(this.__id, key, value);
        else this.__api.set(this.__id, key, String(value));
    };
    Node.prototype.removeProperty = function (key) { this.__api.remove(this.__id, key); };

    Node.prototype.getBodyVelocity = function () {
        var v = this.__api.getBodyVelocity(this.__id);
        return { x: v[0], y: v[1], z: v[2] };
    };
    Node.prototype.applyForce = function (v) { this.__api.applyForce(this.__id, v.x, v.y, v.z); };
    Node.prototype.applyImpulse = function (v) { this.__api.applyImpulse(this.__id, v.x, v.y, v.z); };
    Node.prototype.setLinearVelocity = function (v) { this.__api.setLinearVelocity(this.__id, v.x, v.y, v.z); };
    Node.prototype.getCollisionEvents = function () { return this.__api.getCollisionEvents(); };

    Node.prototype.getInput = function () { return this.__api.getInput(); };

    Node.prototype.follow = function (opts) {
        var off = opts.offset || { x: 0, y: 0, z: 0 };
        this.__api.setFollowCamera(off.x, off.y, off.z, opts.pitch || 0, opts.roll || 0);
    };
    Node.prototype.scriptable = function (opts) {
        var pos = opts.position || { x: 0, y: 0, z: 0 };
        this.__api.setScriptCamera(pos.x, pos.y, pos.z, opts.yaw || 0, opts.pitch || 0, opts.roll || 0);
    };
    Node.prototype.scene = function () { this.__api.setSceneCurrentCamera(this.__id); };
    Node.prototype.reset = function () { this.__api.resetCamera(); };

    Node.prototype.setUniform = function (name) {
        var args = Array.prototype.slice.call(arguments, 1);
        this.__api.setUniform.apply(this.__api, [this.__id, name].concat(args));
    };
    Node.prototype.setInstances = function (data) { this.__api.setInstances(this.__id, data.__buffer); };

    function extend(Parent) {
        var Child = function () { return Parent.apply(this, arguments) || this; };
        Child.prototype = Object.create(Parent.prototype);
        Child.prototype.constructor = Child;
        return Child;
    }

    // ==========================================================================
    // AUTO-GENERATED: node class hierarchy — injected by MoudRuntimeShim
    // ==========================================================================
    // __MOUD_CLASS_HIERARCHY__

    // PlayerAttachment augmentations — runtime helpers for player-bound nodes
    Object.defineProperty(PlayerAttachment.prototype, 'playerName', {
        get: function () { return this.getProperty('player_name'); },
        enumerable: true
    });

    PlayerAttachment.prototype.setAnchor = function (anchorNode) {
        var anchorId = anchorNode && anchorNode.__id;
        if (anchorId && anchorId > 0) {
            this.setProperty('anchor_node_id', String(anchorId));
        } else {
            this.removeProperty('anchor_node_id');
        }
    };

    PlayerAttachment.prototype.clearAnchor = function () {
        this.removeProperty('anchor_node_id');
    };

    PlayerAttachment.prototype.setVelocity = function (vx, vy, vz) {
        this.__api.playerSetVelocity(this.getProperty('target'), vx || 0, vy || 0, vz || 0);
    };

    PlayerAttachment.prototype.addVelocity = function (vx, vy, vz) {
        this.__api.playerAddVelocity(this.getProperty('target'), vx || 0, vy || 0, vz || 0);
    };

    PlayerAttachment.prototype.getVelocity = function () {
        var v = this.__api.playerGetVelocity(this.getProperty('target'));
        return { x: v[0], y: v[1], z: v[2] };
    };

    // Decorators
    function process()       { return function (target, key) { getMoud(target).process = key; }; }
    function ready()         { return function (target, key) { getMoud(target).ready = key; }; }
    function enterTree()     { return function (target, key) { getMoud(target).enterTree = key; }; }
    function exitTree()      { return function (target, key) { getMoud(target).exitTree = key; }; }
    function physicsProcess(){ return function (target, key) { getMoud(target).physicsProcess = key; }; }
    function input()         { return function (target, key) { getMoud(target).input = key; }; }

    function signal(name) {
        return function (target, key) {
            var m = getMoud(target);
            if (!m.signals) m.signals = [];
            m.signals.push({ signal: name, method: key });
        };
    }

    function property(target, key) {
        var m = getMoud(target);
        if (!m.properties) m.properties = [];
        m.properties.push(key);
    }

    function emits() {
        return function (target) { };
    }

    // InstanceData
    function InstanceData(count) {
        this.__buffer = new Float32Array(count * 13);
        this.__count = count;
    }

    InstanceData.prototype.set = function (index, opts) {
        var off = index * 13;
        var b = this.__buffer;
        var pos = opts.position || { x: 0, y: 0, z: 0 };
        var rot = opts.rotation || { x: 0, y: 0, z: 0, w: 1 };
        var sc  = opts.scale    || { x: 1, y: 1, z: 1 };
        var col = opts.color    || { r: 1, g: 1, b: 1, a: 1 };
        b[off]      = pos.x; b[off + 1]  = pos.y; b[off + 2]  = pos.z;
        b[off + 3]  = rot.x; b[off + 4]  = rot.y; b[off + 5]  = rot.z; b[off + 6] = rot.w;
        b[off + 7]  = sc.x;  b[off + 8]  = sc.y;  b[off + 9]  = sc.z;
        b[off + 10] = col.r; b[off + 11] = col.g; b[off + 12] = col.b;
    };

    // Math
    function Vec3(x, y, z) { this.x = x || 0; this.y = y || 0; this.z = z || 0; }
    Vec3.prototype.add       = function (v) { return new Vec3(this.x + v.x, this.y + v.y, this.z + v.z); };
    Vec3.prototype.sub       = function (v) { return new Vec3(this.x - v.x, this.y - v.y, this.z - v.z); };
    Vec3.prototype.scale     = function (s) { return new Vec3(this.x * s, this.y * s, this.z * s); };
    Vec3.prototype.length    = function () { return Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z); };
    Vec3.prototype.normalize = function () {
        var l = this.length(); if (l === 0) return new Vec3(0, 0, 0);
        return new Vec3(this.x / l, this.y / l, this.z / l);
    };

    Vec3.add      = function (a, b) { return new Vec3(a.x + b.x, a.y + b.y, a.z + b.z); };
    Vec3.sub      = function (a, b) { return new Vec3(a.x - b.x, a.y - b.y, a.z - b.z); };
    Vec3.dot      = function (a, b) { return a.x*b.x + a.y*b.y + a.z*b.z; };
    Vec3.distance = function (a, b) {
        var dx = a.x-b.x, dy = a.y-b.y, dz = a.z-b.z;
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    };
    Vec3.forward  = function (yawDeg) {
        var r = yawDeg * Math.PI / 180;
        return new Vec3(-Math.sin(r), 0, -Math.cos(r));
    };
    Vec3.up    = function () { return new Vec3(0, 1, 0); };
    Vec3.right = function () { return new Vec3(1, 0, 0); };
    Vec3.down  = function () { return new Vec3(0, -1, 0); };

    function lerp(a, b, t)      { return a + (b - a) * t; }
    function clamp(v, min, max) { return v < min ? min : v > max ? max : v; }
    function randf(min, max)    { return min + Math.random() * (max - min); }
    function randi(min, max)    { return Math.floor(min + Math.random() * (max - min + 1)); }

    // Timers
    function after(seconds, callback) {
        if (!globalThis.__currentApi) throw new Error('after() called outside of a node lifecycle');
        return globalThis.__currentApi.after(seconds, callback);
    }

    // Physics queries
    function raycast(origin, direction, maxDistance) {
        if (!globalThis.__currentApi) return null;
        var hit = globalThis.__currentApi.raycast(
            origin.x, origin.y, origin.z,
            direction.x, direction.y, direction.z,
            maxDistance
        );
        if (!hit) return null;
        return {
            point:    { x: hit.x(),    y: hit.y(),    z: hit.z() },
            normal:   { x: hit.nx(),   y: hit.ny(),   z: hit.nz() },
            distance: hit.distance(),
            bodyId:   wrapNodeRef(globalThis.__currentApi, hit.bodyId())
        };
    }

    function overlapSphere(center, radius) {
        if (!globalThis.__currentApi) return [];
        var ids = globalThis.__currentApi.overlapSphere(center.x, center.y, center.z, radius);
        var result = [];
        for (var i = 0; i < ids.length; i++) result.push(wrapNodeRef(globalThis.__currentApi, ids[i]));
        return result;
    }

    // Players
    function getPlayers() {
        if (!globalThis.__currentApi) return [];
        return globalThis.__currentApi.getPlayers();
    }

    function teleportPlayer(opts) {
        if (!globalThis.__currentApi) return;
        var pos = opts.position || { x: 0, y: 0, z: 0 };
        if (opts.yaw !== undefined || opts.pitch !== undefined) {
            globalThis.__currentApi.teleportPlayer(
                opts.uuid, pos.x, pos.y, pos.z, opts.yaw || 0, opts.pitch || 0
            );
        } else {
            globalThis.__currentApi.teleportPlayer(opts.uuid, pos.x, pos.y, pos.z);
        }
    }

    // Cursor
    function setCursorMode(enabled) {
        if (globalThis.__currentApi) globalThis.__currentApi.setCursorMode(!!enabled);
    }

    function isCursorModeEnabled() {
        return !!(globalThis.__currentApi && globalThis.__currentApi.isCursorModeEnabled());
    }

    function setOsCursorVisible(visible) {
        if (globalThis.__currentApi) globalThis.__currentApi.setOsCursorVisible(!!visible);
    }

    function isOsCursorVisible() {
        return !(globalThis.__currentApi && !globalThis.__currentApi.isOsCursorVisible());
    }

    function getCursorPosition() {
        if (!globalThis.__currentApi) return { x: 0, y: 0 };
        var pos = globalThis.__currentApi.getCursorPosition();
        return { x: pos[0], y: pos[1] };
    }

    // Scene
    function loadScene(sceneId) {
        if (globalThis.__currentApi) globalThis.__currentApi.loadScene(sceneId);
    }

    function instantiate(scenePath, parentNode) {
        if (!globalThis.__currentApi) return null;
        var parentId = parentNode ? parentNode.__id : 0;
        return wrapNodeRef(globalThis.__currentApi,
            globalThis.__currentApi.instantiate(scenePath, parentId));
    }

    function getRoot() {
        if (!globalThis.__currentApi) return null;
        return wrapNodeRef(globalThis.__currentApi, globalThis.__currentApi.getRootId());
    }

    function findNodesByType(nodeType) {
        if (!globalThis.__currentApi) return [];
        var ids = globalThis.__currentApi.findNodesByType(nodeType);
        var api = globalThis.__currentApi;
        if (__typeClassMap === null) buildTypeClassMap();
        var Cls = __typeClassMap[nodeType] || Node;
        var result = [];
        for (var i = 0; i < ids.length; i++) {
            var id = typeof ids[i] === 'object' ? Number(ids[i]) : ids[i];
            if (!id || id <= 0) continue;
            var inst = new Cls();
            inst.__api = api;
            inst.__id = id;
            inst.__nodeType = nodeType;
            inst.__userProps = {};
            result.push(inst);
        }
        return result;
    }

    // Static enums (not registry-driven)
    var Shape = { Box: 'box', Sphere: 'sphere', Capsule: 'capsule' };

    var InputAction = {
        Jump: 'jump', Sprint: 'sprint',
        MoveLeft: 'move_left', MoveRight: 'move_right',
        MoveForward: 'move_forward', MoveBack: 'move_back'
    };

    // ==========================================================================
    // AUTO-GENERATED: type map, NodeType enum, and module exports
    // injected by MoudRuntimeShim from the NodeTypeRegistry
    // ==========================================================================
    // __MOUD_TYPE_MAP__

})();
