#version 330 compatibility
// 从手电看过去的深度。肉要和正常画面一样地动，不然自己给自己投影
out vec2 texcoord;
out float vertexAlpha;
out vec3 playerPos;

uniform mat4 shadowModelViewInverse;
uniform mat4 gbufferModelView;
uniform vec3 cameraPosition;
uniform float frameTimeCounter;

#include "/lib/flash.glsl"
#include "/lib/move.glsl"

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    vertexAlpha = gl_Color.a;
    vec4 vertex = gl_Vertex;
    vec3 player = (shadowModelViewInverse * (gl_ModelViewMatrix * vertex)).xyz;
    if (fleshMarked(gl_Color)) {
        vertex.xyz += octDecode(gl_MultiTexCoord0.xy) * fleshPush(gl_Color, player + cameraPosition, frameTimeCounter);
        player = (shadowModelViewInverse * (gl_ModelViewMatrix * vertex)).xyz;
    }
    playerPos = player;
    gl_Position = flashClip((gbufferModelView * vec4(player, 1.0)).xyz);
}
