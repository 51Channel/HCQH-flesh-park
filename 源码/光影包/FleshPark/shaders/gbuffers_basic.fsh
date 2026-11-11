#version 330 compatibility
in vec4 tint;
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;
void main() {
    outColor = vec4(pow(tint.rgb, vec3(2.2)), tint.a);
}
