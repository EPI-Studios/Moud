#version 330 core

in vec4 vColor;
in vec3 vPos;
in vec2 vLight;
in vec2 vUv;
in float vCutout;
in vec2 vOverlay;
in vec3 vNormal;

uniform sampler2D TextureSampler;
uniform sampler2D LightMap;

out vec4 FragColor;

void main() {
    vec4 skin = texture(TextureSampler, vUv);

    // a cutout is a hat, a sleeve, a plate of armour, a wing: where the sheet left it blank there
    // is nothing to draw, and drawing it anyway is the black box every bad skin renderer puts on
    // somebody's head. the game cuts these out at a tenth and so do we
    if (vCutout > 0.5 && skin.a < 0.1) discard;
    if (skin.a < 0.01) discard;

    // the two lights the game hangs over a level, and the mix it shades every entity with
    //
    // not a block's face table -- top full, bottom half, sides six and eight tenths -- which is what
    // was here first and made a body too dark. and not nothing, which is what replaced it and made a
    // body flat: with only the light map left, every face of every limb came out at the same
    // brightness, so a body read as lit from the inside however the sun stood
    //
    // these directions are fixed in the world, which is the part a face table can never do: a limb
    // that swings changes shade as it turns, and a body that walks round a corner is lit from the
    // other side afterwards
    vec3 light0 = normalize(vec3(0.2, 1.0, -0.7));
    vec3 light1 = normalize(vec3(-0.2, 1.0, 0.7));
    vec3 n = normalize(vNormal);
    float diffuse = min(1.0,
            (max(0.0, dot(light0, n)) + max(0.0, dot(light1, n))) * 0.6 + 0.4);

    vec2 uv = clamp((vLight / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
    vec3 light = texture(LightMap, uv).rgb;

    // the tint is what a place set on the part, white unless it asked for something
    vec3 rgb = skin.rgb * vColor.rgb * diffuse;

    // the game keeps this in a sixteen by sixteen table it draws once at startup: the top half is
    // red at an alpha of 179, the bottom half white at an alpha that falls off across the row.
    // the table is the reason a hurt body is exactly thirty percent red and not fifty
    //
    // the white column is stepped to fifteen the way a texel is, so a flash climbs in the same
    // sixteen steps it does in the game rather than sliding smoothly past them
    float stepped = floor(clamp(vOverlay.x, 0.0, 1.0) * 15.0) / 15.0;
    bool red = vOverlay.y > 0.5;
    vec3 wash = red ? vec3(1.0, 0.0, 0.0) : vec3(1.0);
    float keep = red ? 179.0 / 255.0 : floor((1.0 - stepped * 0.75) * 255.0) / 255.0;
    rgb = mix(wash, rgb, keep);

    // the light map last, exactly as the entity shader has it: a body washed red is still lit
    FragColor = vec4(rgb * light, skin.a * vColor.a);
}
