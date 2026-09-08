package com.meekdev.moud.script.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.vm.Vm;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypesTest {

    private final String declared = Types.declare(Classes.registry());

    @Test
    void everyRegisteredClassIsDeclaredOnce() {
        for (ClassDef<?> def : Classes.registry().all()) {
            String header = "declare class " + def.name() + " extends ";
            assertEquals(1, count(declared, header), def.name() + " is declared once");
        }
    }

    @Test
    void everyPropertyOfEveryClassIsDeclaredWithItsType() {
        assertTrue(declared.contains("declare class Part extends Spatial\n"));
        // a class is its public fields, so these are the fields of Part sorted by name
        assertTrue(declared.contains("    anchored: boolean\n"));
        assertTrue(declared.contains("    collides: boolean\n"));
        assertTrue(declared.contains("    color: Color\n"));
        assertTrue(declared.contains("    size: Vector3\n"));
        assertTrue(declared.contains("    transparency: number\n"));
    }

    @Test
    void anInheritedPropertyIsDeclaredOnlyOnTheClassThatIntroducesIt() {
        // cframe belongs to Spatial, and Part extends it, so declaring it on both would be an error
        assertTrue(block("Spatial").contains("cframe: CFrame"));
        assertFalse(block("Part").contains("cframe: CFrame"));

        // the two Proxies serves off cframe without them being properties, on the same class
        assertTrue(block("Spatial").contains("position: Vector3"));
        assertTrue(block("Spatial").contains("worldCframe: CFrame"));
        assertFalse(block("Part").contains("worldCframe: CFrame"));
    }

    @Test
    void aClassWithNoParentExtendsInstance() {
        assertTrue(declared.contains("declare class Folder extends Instance\n"));
        assertTrue(declared.contains("declare class Spatial extends Instance\n"));
    }

    @Test
    void nothingIsDeclaredThatTheBindingDoesNotProvide() {
        // quat has no constructor, only a type reachable through cframe.rotation
        assertFalse(declared.contains("declare function quat("));
        // the design sketches these, and they are not bound yet
        assertFalse(declared.contains("isServer"));
        assertFalse(declared.contains("declare function remote("));
    }

    @Test
    void everyPropertyOfEveryClassReachesTheDeclarations() {
        for (ClassDef<?> def : Classes.registry().all()) {
            for (PropertyDef property : def.properties()) {
                assertTrue(declared.contains("    " + property.name() + ": "),
                        def.name() + "." + property.name() + " reached the declarations");
            }
        }
    }

    // a class that extends one the registry never declared would leave the file referring to a
    // type an editor cannot resolve
    @Test
    void everyClassExtendsSomethingTheFileDeclares() {
        for (ClassDef<?> def : Classes.registry().all()) {
            ClassDef<?> parent = def.parent();
            if (parent == null) continue;
            assertTrue(declared.indexOf("declare class " + parent.name() + " extends ")
                            < declared.indexOf("declare class " + def.name() + " extends "),
                    parent.name() + " is declared before " + def.name());
        }
    }

    // properties come from the registry and cannot drift, methods are written by hand in both
    // places. this is what stops one being added without the other
    @Test
    void everyInstanceMethodIsDeclared() {
        try (Vm vm = new Vm()) {
            vm.bind(Instances.createRoot(new InstanceTree(), Classes.SPATIAL, "World"),
                    Classes.registry());
            Set<String> methods = Proxies.methodNames();
            assertFalse(methods.isEmpty(), "the binding registered its methods");
            for (String name : methods) {
                assertTrue(block("Instance").contains("function " + name + "(self"),
                        name + " is declared on Instance");
            }
        }
    }

    private String block(String className) {
        int at = declared.indexOf("declare class " + className + "\n");
        if (at < 0) at = declared.indexOf("declare class " + className + " extends ");
        assertTrue(at >= 0, className + " is declared");
        return declared.substring(at, declared.indexOf("\nend\n", at));
    }

    @Test
    void itIsWrittenWhereAnEditorLooksForIt(@TempDir Path place) throws Exception {
        Path file = Types.write(place, Classes.registry());
        assertEquals(place.resolve(".moud").resolve("types.d.luau"), file);
        assertEquals(declared, Files.readString(file));
    }

    private static int count(String text, String needle) {
        int found = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) found++;
        return found;
    }
}
