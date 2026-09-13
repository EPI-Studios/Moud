package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.clazz.EventDef;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Remote;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Owners;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.script.api.BlockRef;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

public final class Proxies {

    public static final int TAG = 1;

    private static final String METHODS = "moud.methods";

    private static final Set<String> NAMES = new LinkedHashSet<>();

    private static final Map<String, Set<String>> CLASS_NAMES = new LinkedHashMap<>();

    private static ClassRegistry classes;

    private Proxies() {}

    public static void install(LuaState state, ClassRegistry registry) {
        classes = registry;

        state.newTable();
        state.pushFunction(LuaFunc.wrap(Proxies::index, "Instance.__index"));
        state.rawSetField(-2, "__index");
        state.pushFunction(LuaFunc.wrap(Proxies::newIndex, "Instance.__newindex"));
        state.rawSetField(-2, "__newindex");
        state.pushFunction(LuaFunc.wrap(Proxies::name, "Instance.__tostring"));
        state.rawSetField(-2, "__tostring");
        state.pushFunction(LuaFunc.wrap(Proxies::same, "Instance.__eq"));
        state.rawSetField(-2, "__eq");
        state.setUserDataMetaTable(TAG);

        // shared method table, so __index hands back the same function rather than a new closure
        state.newTable();
        method(state, "fireServer", Proxies::fireServer);
        method(state, "fireClient", Proxies::fireClient);
        method(state, "fireAllClients", Proxies::fireAllClients);
        method(state, "add", Proxies::add);
        method(state, "addAll", Proxies::addAll);
        method(state, "children", Proxies::children);
        method(state, "find", Proxies::find);
        method(state, "isA", Proxies::isA);
        method(state, "destroy", Proxies::destroy);
        method(state, "raycast", Proxies::raycast);
        method(state, "spherecast", s -> QueryMethods.spherecast(s, self(s)));
        method(state, "blockcast", s -> QueryMethods.blockcast(s, self(s)));
        method(state, "partsInBox", s -> QueryMethods.partsInBox(s, self(s)));
        method(state, "partsInRadius", s -> QueryMethods.partsInRadius(s, self(s)));
        method(state, "partsInPart", s -> QueryMethods.partsInPart(s, self(s)));
        method(state, "setOwner", Proxies::setOwner);
        state.rawSetField(LuaState.REGISTRY_INDEX, METHODS);
    }

    private static String methodsOf(ClassDef<?> def) {
        return "moud.methods." + def.name();
    }

    // methods only one class has. the table is per vm like every other registry entry, so a
    // client vm can carry the camera's verbs and a server vm never sees them
    public static void classMethods(LuaState state, ClassDef<?> def,
                                    Map<String, ToIntFunction<LuaState>> methods) {
        state.newTable();
        for (Map.Entry<String, ToIntFunction<LuaState>> entry : methods.entrySet()) {
            CLASS_NAMES.computeIfAbsent(def.name(), name -> new LinkedHashSet<>())
                    .add(entry.getKey());
            state.pushFunction(LuaFunc.wrap(entry.getValue(), def.name() + ":" + entry.getKey()));
            state.rawSetField(-2, entry.getKey());
        }
        state.rawSetField(LuaState.REGISTRY_INDEX, methodsOf(def));
    }

    public static Set<String> methodNames(String className) {
        return Collections.unmodifiableSet(
                CLASS_NAMES.getOrDefault(className, new LinkedHashSet<>()));
    }

    // every method an instance carries passes through here, so the names the type declarations
    // promise are the names that were actually registered rather than a second list to keep
    private static void method(LuaState state, String name, ToIntFunction<LuaState> body) {
        NAMES.add(name);
        state.pushFunction(LuaFunc.wrap(body, "Instance:" + name));
        state.rawSetField(-2, name);
    }

    public static Set<String> methodNames() {
        return Collections.unmodifiableSet(NAMES);
    }

    // a proxy is not cached. caching one per instance pins a jni global ref for every instance
    // that a script ever touches, and building a scene then degrades as the ref table grows.
    // identity is __eq on the instance behind the proxy instead, which is what a place observes
    public static void push(LuaState state, Instance instance) {
        state.newUserDataTaggedWithMetatable(instance, TAG);
    }

    // a channel's three verbs. they sit on the one metatable every instance shares, like every other
    // method here, and say so when they are asked of something that is not a channel
    private static int fireServer(LuaState state) {
        if (!(self(state) instanceof Remote remote)) throw state.error("fireServer is a Remote's");
        return Remotes.fireServer(state, remote);
    }

    private static int fireClient(LuaState state) {
        if (!(self(state) instanceof Remote remote)) throw state.error("fireClient is a Remote's");
        return Remotes.fireClient(state, remote);
    }

    private static int fireAllClients(LuaState state) {
        if (!(self(state) instanceof Remote remote)) {
            throw state.error("fireAllClients is a Remote's");
        }
        return Remotes.fireAllClients(state, remote);
    }

    private static int same(LuaState state) {
        state.pushBoolean(state.toUserDataTagged(1, TAG) == state.toUserDataTagged(2, TAG));
        return 1;
    }

    private static Instance self(LuaState state) {
        Instance instance = (Instance) state.toUserDataTagged(1, TAG);
        if (instance == null) throw state.error("not an instance");
        // a destroyed instance is a bug in the place, not something to paper over
        if (!instance.isAlive()) throw state.error("%s has been destroyed", instance.name());
        return instance;
    }

    private static int index(LuaState state) {
        Instance instance = self(state);
        String key = state.checkString(2);

        switch (key) {
            case "name" -> { state.pushString(instance.name()); return 1; }
            case "className" -> { state.pushString(instance.def().name()); return 1; }
            case "parent" -> {
                Instance parent = instance.parent();
                if (parent == null) state.pushNil(); else push(state, parent);
                return 1;
            }
            case "changed" -> {
                Signals.push(state, InstanceSignals.changed(instance));
                return 1;
            }
            case "childAdded" -> {
                Signals.push(state, InstanceSignals.childAdded(instance));
                return 1;
            }
            case "destroying" -> {
                Signals.push(state, InstanceSignals.destroying(instance));
                return 1;
            }
            default -> { }
        }

        // whatever the class said it tells you about. it comes before properties for the same
        // reason a name cannot be both: a class declares each of them once, in the same place
        EventDef event = instance.def().event(key);
        if (event != null) {
            Signals.push(state, InstanceSignals.of(instance, event));
            return 1;
        }

        if (instance instanceof Spatial spatial) {
            // where the thing actually is, not where it is stated
            //
            // these used to read the local frame, which was honest while nothing was ever reparented
            // without being asked: the field is local, so the shortcut matching the field surprised
            // nobody. that premise is gone -- a body standing on something that moves is hung off it
            // by the engine, and a place that never mentioned a parent would start reading positions
            // in the deck's frame. "where is this" is a world question
            //
            // cframe is still the field and still local. it says so, and worldCframe still composes
            if (key.equals("position")) {
                Values.push(state, Transforms.world(instance).position());
                return 1;
            }
            if (key.equals("rotation")) {
                Values.push(state, Transforms.world(instance).rotation());
                return 1;
            }
            if (key.equals("worldCframe")) {
                Values.push(state, Transforms.world(instance));
                return 1;
            }
        }

        PropertyDef property = instance.def().property(key);
        if (property != null) {
            read(state, instance, property);
            return 1;
        }

        Instance child = instance.child(key);
        if (child != null) {
            push(state, child);
            return 1;
        }

        // a class may carry methods of its own, and a subclass inherits its parent's, so the
        // chain is walked before the table every instance shares
        for (ClassDef<?> def = instance.def(); def != null; def = def.parent()) {
            // most classes register nothing, and indexing the nil that leaves on the stack is a
            // native abort rather than an error a place could see
            if (state.rawGetField(LuaState.REGISTRY_INDEX, methodsOf(def)) != LuaType.TABLE) {
                state.pop(1);
                continue;
            }
            if (state.rawGetField(-1, key) != LuaType.NIL) {
                state.remove(-2);
                return 1;
            }
            state.pop(2);
        }

        state.rawGetField(LuaState.REGISTRY_INDEX, METHODS);
        if (state.rawGetField(-1, key) != LuaType.NIL) {
            state.remove(-2);
            return 1;
        }
        throw state.error("%s has no member '%s'", instance.def().name(), key);
    }

    private static int newIndex(LuaState state) {
        apply(state, self(state), state.checkString(2), 3);
        return 0;
    }

    // world:add("Part", { size = ..., position = ... })
    private static int add(LuaState state) {
        Instance parent = self(state);
        String className = state.checkString(2);
        ClassDef<?> def = classes.require(className);
        Instance created = Instances.create(def, parent, className);

        if (!state.isNoneOrNil(3)) {
            state.pushNil();
            while (state.next(3)) {
                apply(state, created, state.checkString(-2), state.absIndex(-1));
                state.pop(1);
            }
        }
        push(state, created);
        return 1;
    }

    // one crossing for the whole list instead of one per instance. the same argument 15.4 makes
    // for worldgen: the per call cost is a floor, so the api has to describe many at once.
    // it hands back a count rather than the instances, because a proxy each would put the cost
    // straight back. a place that needs them can walk children
    private static int addAll(LuaState state) {
        Instance parent = self(state);
        String className = state.checkString(2);
        ClassDef<?> def = classes.require(className);

        int count = state.len(3);
        for (int n = 1; n <= count; n++) {
            state.rawGetI(3, n);
            int entry = state.absIndex(-1);
            Instance created = Instances.create(def, parent, className);
            state.pushNil();
            while (state.next(entry)) {
                apply(state, created, state.checkString(-2), state.absIndex(-1));
                state.pop(1);
            }
            state.pop(1);
        }
        state.pushInteger(count);
        return 1;
    }

    private static int destroy(LuaState state) {
        Instances.destroy(self(state));
        return 0;
    }

    // an array of proxies, because reaching a child by name is no use to a place that does not
    // know the names. one crossing per child is what a list costs, which is why anything that
    // builds rather than reads belongs in addAll instead (19.4)
    private static int children(LuaState state) {
        List<Instance> children = self(state).children();
        state.createTable(children.size(), 0);
        for (int i = 0; i < children.size(); i++) {
            push(state, children.get(i));
            state.rawSetI(-2, i + 1);
        }
        return 1;
    }

    // nil rather than an error, which is the difference between asking whether a child is there
    // and reaching for one that has to be
    private static int find(LuaState state) {
        Instance child = self(state).child(state.checkString(2));
        if (child == null) state.pushNil(); else push(state, child);
        return 1;
    }

    // what a ray runs into under this instance, or nothing
    //
    // three answers rather than a table: the part, the point and how far. a place that only wants
    // to know which limb takes the first and drops the rest, which is the common case
    //
    // it is a question about the tree, so it answers about the tree. what a body walks into is a
    // different question with a different answer, and it is not asked here
    public static void blocks(LuaState state, BlockRef blocks) {
        QueryMethods.blocks(state, blocks);
    }

    public static void forgetBlocks(LuaState state) {
        QueryMethods.forget(state);
    }

    private static int raycast(LuaState state) {
        return QueryMethods.raycast(state, self(state));
    }

    private static int isA(LuaState state) {
        state.pushBoolean(self(state).isA(classes.require(state.checkString(2))));
        return 1;
    }

    private static int name(LuaState state) {
        state.pushString(self(state).name());
        return 1;
    }

    // the one place a member is written, so :add and assignment can never drift apart
    private static void apply(LuaState state, Instance instance, String key, int value) {
        if (key.equals("name")) {
            Instances.rename(instance, state.checkString(value));
            return;
        }
        // the tree is moved by assignment like everything else. detaching is destroy, so there is
        // one way to remove an instance rather than two that mean different things
        if (key.equals("parent")) {
            Instance parent = (Instance) state.toUserDataTagged(value, TAG);
            if (parent == null) throw state.error("parent wants an instance, use destroy to detach");
            Instances.reparent(instance, parent);
            return;
        }
        if (instance instanceof Spatial spatial) {
            PropertyDef frame = instance.def().property("cframe");
            // put where the world says, whatever it hangs off. the same question in the other
            // direction: a place saying "stand here" means here in the world
            if (key.equals("position")) {
                // the pivot comes back on the way in. a world frame already has it folded out --
                // that is what makes an arm's world frame the middle of its box rather than the
                // shoulder it turns at -- so converting back without refolding it would slide the
                // thing by however far its pivot was moved
                Instances.setObj(instance, frame, Transforms.localFor(instance,
                        Transforms.world(instance).withPosition(Values.vec3(state, value)))
                        .mul(CFrame.at(spatial.pivot)));
                return;
            }
            // turning a thing leaves it where it is. an arm animated by writing the whole frame
            // loses the offset that put it at the shoulder and swings from the floor instead,
            // which is what 6.2.5 means by these being views onto one value rather than state
            //
            // so only the rotation is touched, and only it is converted: the angle asked for is a
            // world angle, and what gets written is that angle expressed against the parent
            if (key.equals("rotation")) {
                Quat local = Transforms.localFor(instance,
                        new CFrame(Vec3.ZERO, rotationOf(state, value))).rotation();
                Instances.setObj(instance, frame, spatial.cframe.withRotation(local));
                return;
            }
            if (key.equals("worldCframe")) {
                Instances.setObj(instance, frame, Transforms.localFor(instance, Values.cframe(state, value)));
                return;
            }
        }

        PropertyDef property = instance.def().property(key);
        if (property == null) throw state.error("%s has no property '%s'", instance.def().name(), key);
        write(state, instance, property, value);
    }

    // thing:setOwner(body) / thing:setOwner(nil)
    //
    // §10.4: ownership can move, and handing a pushed crate to the player pushing it is what makes
    // the push feel instant. the server's to give, because a client that could give itself ownership
    // of anything would give itself ownership of everything
    //
    // it takes the body rather than a player id, because a body is how a player is named everywhere
    // else here. and one owner rather than a list: two clients predicting the same crate is two
    // answers to where it is, which is the thing the engine refuses to have
    private static int setOwner(LuaState state) {
        Instance instance = self(state);
        if (Remotes.onClient(state)) {
            throw state.error("setOwner is the server's. a client cannot give itself a thing");
        }
        if (!(instance instanceof Spatial)) {
            throw state.error("%s has no owner: ownership is about a thing that is somewhere",
                    instance.def().name());
        }
        PropertyDef owner = instance.def().property("owner");
        if (state.isNoneOrNil(2)) {
            Instances.setObj(instance, owner, "");
            return 0;
        }
        Instance who = (Instance) state.toUserDataTagged(2, TAG);
        if (!(who instanceof Character body)) {
            throw state.error("setOwner wants the body of whoever it is for, or nil for the server");
        }
        Instances.setObj(instance, owner, body.owner);
        return 0;
    }

    // §10.1: writing a replicated property on a client without ownership is a luau error, not a
    // silent revert
    //
    // it has to be an error because the alternative is what the previous engine did: the write lands
    // in the client's own copy, looks like it worked, and is overwritten the next time the server says
    // anything about that property -- so the bug is a thing that works until it doesn't, with nothing
    // anywhere naming the moment it stopped
    //
    // three ways past it, and each is a real case rather than a loophole. a local instance does not
    // exist on the other side at all, so nobody else has an opinion about it. a property the class
    // marked as not replicated is the client's by declaration -- how a body is drawn, what you see of
    // your own. and a thing this player owns is theirs to write, which is the whole point of ownership
    private static void allowed(LuaState state, Instance instance, PropertyDef property) {
        if (!property.replicated() || instance.id() < 0) return;
        if (!Remotes.onClient(state)) return;
        String me = Remotes.me(state);
        if (Owners.owns(me, instance)) return;
        String owner = Owners.of(instance);
        throw state.error("%s.%s is the server's to write%s. a client writes what it owns, what is"
                + " local to it, and what its class keeps to itself", instance.def().name(),
                property.name(), owner.isEmpty() ? "" : " -- this one belongs to " + owner);
    }

    // a reference to something destroyed reads as nil rather than as a proxy that errors on
    // touch: a place holding a ref to a part someone removed asked a reasonable question
    private static void ref(LuaState state, Instance target) {
        if (target == null || !target.isAlive()) state.pushNil(); else push(state, target);
    }

    private static Instance refOf(LuaState state, PropertyDef property, int value) {
        if (state.isNoneOrNil(value)) return null;
        Instance target = (Instance) state.toUserDataTagged(value, TAG);
        if (target == null) throw state.error("%s wants an instance or nil", property.name());
        if (!target.isAlive()) throw state.error("%s was handed a destroyed instance", property.name());
        return target;
    }

    // a rotation is a quat, and a cframe is the friendlier way to say one: cframe.angles reads
    // better than any constructor we would offer for the quat itself
    private static Quat rotationOf(LuaState state, int value) {
        Object quat = state.toUserDataTagged(value, Values.QUAT);
        if (quat instanceof Quat q) return q;
        Object frame = state.toUserDataTagged(value, Values.CFRAME);
        if (frame instanceof CFrame cf) return cf.rotation();
        throw state.error("rotation wants a cframe or a quat");
    }

    // the default is an instance of the enum, so it names the type without the class def
    // having to carry one
    private static Enum<?> enumOf(LuaState state, PropertyDef property, int value) {
        try {
            return Enums.parse(property.defaultValue().getClass(), state.checkString(value));
        } catch (IllegalArgumentException e) {
            throw state.error("%s: %s", property.name(), e.getMessage());
        }
    }

    private static void read(LuaState state, Instance instance, PropertyDef property) {
        switch (property.type()) {
            case BOOL -> state.pushBoolean(property.getBool(instance));
            case INT, NUM -> state.pushNumber(property.getNum(instance));
            case STRING -> state.pushString((String) property.getObj(instance));
            case VEC3 -> Values.push(state, (Vec3) property.getObj(instance));
            case COLOR -> Values.push(state, (Color) property.getObj(instance));
            case UDIM2 -> Values.push(state, (UDim2) property.getObj(instance));
            case CFRAME -> Values.push(state, (CFrame) property.getObj(instance));
            case ENUM -> state.pushString(Enums.name((Enum<?>) property.getObj(instance)));
            case REF -> ref(state, (Instance) property.getObj(instance));
            default -> throw state.error("%s is not a value luau can read yet", property.name());
        }
    }

    private static void write(LuaState state, Instance instance, PropertyDef property, int value) {
        allowed(state, instance, property);
        switch (property.type()) {
            case BOOL -> Instances.setBool(instance, property, state.toBoolean(value));
            case INT, NUM -> Instances.setNum(instance, property, state.checkNumber(value));
            case STRING -> Instances.setObj(instance, property, state.checkString(value));
            case VEC3 -> Instances.setObj(instance, property, Values.vec3(state, value));
            case COLOR -> Instances.setObj(instance, property, Values.color(state, value));
            case UDIM2 -> Instances.setObj(instance, property, Values.udim2(state, value));
            case CFRAME -> Instances.setObj(instance, property, Values.cframe(state, value));
            case ENUM -> Instances.setObj(instance, property, enumOf(state, property, value));
            case REF -> Instances.setObj(instance, property, refOf(state, property, value));
            default -> throw state.error("%s is not a value luau can write yet", property.name());
        }
    }
}
