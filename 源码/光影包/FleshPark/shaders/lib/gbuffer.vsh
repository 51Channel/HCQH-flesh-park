out vec2 texcoord;
out vec2 lmcoord;
out vec4 tint;
out vec3 viewPos;
out vec3 viewNormal;
out vec3 worldPos;
out float fleshMark;
out float liquidMark;
out float veinMark;

uniform mat4 gbufferModelViewInverse;
uniform vec3 cameraPosition;
uniform float frameTimeCounter;

#include "/lib/move.glsl"

void main() {
    vec4 vertex = gl_Vertex;
    vec3 normal = gl_Normal;
    tint = gl_Color;
    fleshMark = 0.0;
    liquidMark = 0.0;
    veinMark = 0.0;
    worldPos = (gbufferModelViewInverse * (gl_ModelViewMatrix * vertex)).xyz + cameraPosition;
    if (liquidMarked(gl_Color)) {
        liquidMark = 1.0;
        tint = vec4(gl_Color.rgb, 1.0);
    } else if (fleshMarked(gl_Color)) {
        // 肉的网格：贴图坐标里是光滑法线，贴图按世界坐标贴
        fleshMark = 1.0;
        veinMark = abs(gl_Color.b * 255.0 - 4.0) < 0.5 ? 1.0 : 0.0;
        tint = vec4(1.0);
        normal = octDecode(gl_MultiTexCoord0.xy);
        vertex.xyz += normal * fleshPush(gl_Color, worldPos, frameTimeCounter);
    } else if (smoothMarked(gl_Color)) {
        normal = octDecode(gl_Color.rg);
        tint = vec4(TINTS[int(gl_Color.b * 255.0 + 0.5)], 1.0);
    }
    gl_Position = gl_ModelViewProjectionMatrix * vertex;
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    viewPos = (gl_ModelViewMatrix * vertex).xyz;
    viewNormal = normalize(gl_NormalMatrix * normal);
}
