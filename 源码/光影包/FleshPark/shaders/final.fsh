#version 330 compatibility
// 泛光、色调映射、暗角、胶片颗粒
in vec2 texcoord;

const bool colortex3MipmapEnabled = true;

uniform sampler2D colortex0;
uniform sampler2D colortex3;
uniform float frameTimeCounter;
uniform float viewWidth;
uniform float viewHeight;

layout(location = 0) out vec4 outColor;

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    vec2 centered = texcoord - 0.5;
    // 边缘一点点色散，像便宜的镜头
    float ca = dot(centered, centered) * 0.0035 + 0.0006;
    // 录像带：横向轻轻抖，偶尔一条跟踪噪带扫过
    float roll = fract(frameTimeCounter * 0.061);
    float tear = smoothstep(0.012, 0.0, abs(texcoord.y - roll)) * 0.006;
    vec2 uv = vec2(texcoord.x + sin(texcoord.y * 260.0 + frameTimeCounter * 3.0) * 0.00028 + tear, texcoord.y);
    vec3 col = vec3(texture(colortex0, uv + centered * ca).r, texture(colortex0, uv).g, texture(colortex0, uv - centered * ca).b);
    vec3 bloom = vec3(0.0);
    float weight = 0.0;
    // 灯在雾里的光晕：越大的一圈权重越高
    for (int lod = 2; lod <= 7; lod++) {
        float w = 0.5 + float(lod) * 0.3;
        bloom += textureLod(colortex3, texcoord, float(lod)).rgb * w;
        weight += w;
    }
    col += bloom / weight * 0.55;
    col = aces(col * 1.85);
    col = pow(col, vec3(1.0 / 2.2));
    // 调色：暗部压死、偏冷，亮部偏暖，饱和度拉一点。参考画面的层次就在这里
    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(luma), col, 1.03);
    col = clamp(col, 0.0, 1.0);
    col = mix(col, col * col * (3.0 - 2.0 * col), 0.3);
    col *= mix(vec3(0.9, 1.0, 1.08), vec3(1.05, 1.0, 0.92), smoothstep(0.0, 0.55, luma));
    col = max(col - 0.004, 0.0);
    float vignette = 1.0 - dot(centered, centered) * 1.15;
    col *= vignette * vignette;
    float grain = fract(sin(dot(gl_FragCoord.xy + frameTimeCounter * 137.0, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    col += grain * 0.03 * (0.35 + col) * (1.0 - col * 0.6);
    col *= 0.985 + 0.015 * sin(gl_FragCoord.y * 1.57);
    outColor = vec4(col, 1.0);
}
