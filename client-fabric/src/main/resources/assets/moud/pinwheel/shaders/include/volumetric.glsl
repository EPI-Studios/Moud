void beerLambertStep(inout float transmittance,
inout vec3  colourAccum,
float       density,
float       stepLen,
vec3        litColour) {
    float stepT = exp(-density * stepLen);
    colourAccum   += transmittance * (1.0 - stepT) * litColour;
    transmittance *= stepT;
}

vec3 compositeOverScene(vec3 colourAccum, float transmittance, vec3 baseColour) {
    return colourAccum + transmittance * baseColour;
}