package com.meekdev.moud.core.part;

import com.meekdev.moud.core.clazz.Prop;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Color;

public class MaterialOverride extends Instance {

    public String material = "";

    public Color color = Color.WHITE;

    @Prop(asset = true, group = "Texture") public String texture = "";

    @Prop(asset = true, group = "PBR") public String normalMap = "";

    @Prop(asset = true, group = "PBR") public String roughnessMap = "";

    @Prop(asset = true, group = "PBR") public String metallicMap = "";

    @Prop(asset = true, group = "PBR") public String occlusionMap = "";

    @Prop(asset = true, group = "Emission") public String emissionMap = "";

    @Prop(min = -1, max = 1, group = "PBR") public double roughness = -1;

    @Prop(min = -1, max = 1, group = "PBR") public double metallic = -1;

    @Prop(group = "Emission") public Color emission = Color.BLACK;

    @Prop(min = 0, group = "Emission") public double emissionStrength = 1;

    @Prop(min = 0, max = 1) public double transparency;

    public boolean doubleSided;

    @Prop(group = "Texture") public TextureFilter filter = TextureFilter.DEFAULT;

    @Prop(group = "Texture") public double offsetU;

    @Prop(group = "Texture") public double offsetV;

    @Prop(group = "Texture") public double repeatU = 1;

    @Prop(group = "Texture") public double repeatV = 1;
}
