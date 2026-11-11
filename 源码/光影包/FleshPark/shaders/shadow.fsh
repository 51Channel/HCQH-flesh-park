#version 330 compatibility
const int shadowMapResolution = 1024; // [512 1024 2048]
const float shadowDistance = 48.0;
const float shadowDistanceRenderMul = 1.0;
const float shadowIntervalSize = 0.0;

in vec2 texcoord;
in float vertexAlpha;
in vec3 playerPos;

uniform sampler2D gtexture;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
    // 玻璃和水汽不挡手电
    if (texture(gtexture, texcoord).a * vertexAlpha < 0.5) discard;
    // 第一人称看不见自己，但阴影图里画了玩家的模型，正好挡在手电前面。走路摆臂时手臂会伸到身前半米多，范围要留够
    if (dot(playerPos.xz, playerPos.xz) < 0.9 * 0.9 && playerPos.y > -1.9 && playerPos.y < 0.5) discard;
    outColor = vec4(1.0);
}
