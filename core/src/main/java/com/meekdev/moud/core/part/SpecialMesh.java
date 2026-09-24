package com.meekdev.moud.core.part;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;

public class SpecialMesh extends Instance {

    public MeshType meshType = MeshType.BRICK;

    @Prop(asset = true) public String meshId = "";

    @Prop(asset = true) public String textureId = "";

    public Vector3 scale = Vector3.ONE;

    public Vector3 offset = Vector3.ZERO;

    public Color vertexColor = Color.WHITE;
}
