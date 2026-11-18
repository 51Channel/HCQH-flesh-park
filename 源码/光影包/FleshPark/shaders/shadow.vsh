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
    vec3 still = (gbufferModelView * vec4(player, 1.0)).xyz;
    if (fleshMarked(gl_Color)) {
        vec3 world = player + cameraPosition;
        vec3 n = octDecode(gl_MultiTexCoord0.xy);
        vertex.xyz += n * fleshPush(gl_Color, world, frameTimeCounter);
        player = (shadowModelViewInverse * (gl_ModelViewMatrix * vertex)).xyz;
        // 怕手电：和画面里一模一样地缩，不然自己给自己投影。法线先转到玩家坐标再转到镜头坐标
        mat3 toView = mat3(gbufferModelView);
        vec3 nPlayer = normalize(mat3(shadowModelViewInverse) * (mat3(gl_ModelViewMatrix) * n));
        player += transpose(toView) * fleshShy(still, toView * nPlayer, world, frameTimeCounter);
    }
    playerPos = player;
    gl_Position = flashClip((gbufferModelView * vec4(player, 1.0)).xyz);
}
