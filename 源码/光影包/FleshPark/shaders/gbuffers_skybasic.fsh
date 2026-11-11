#version 330 compatibility
uniform vec3 fogColor;
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;
void main() {
    outColor = vec4(pow(fogColor, vec3(2.2)), 1.0);
}
