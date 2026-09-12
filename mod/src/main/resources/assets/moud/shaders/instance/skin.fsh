#version 330 core

in vec4 vColor;
in vec3 vPos;
in vec2 vLight;
in vec2 vUv;
in float vCutout;
in vec2 vOverlay;

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

    // no per face shade. that is a block's lighting -- top full, bottom half, sides six and eight
    // tenths -- and the game does not apply it to an entity: a player model takes the light map and
    // nothing else. shading it like a block darkens the whole body, and darkens a broad flat limb
    // most, which reads as the torso being wrong rather than as the shader being wrong
    vec2 uv = clamp((vLight / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
    vec3 light = texture(LightMap, uv).rgb;

    // the tint is what a place set on the part, white unless it asked for something
    vec3 rgb = skin.rgb * vColor.rgb;

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
