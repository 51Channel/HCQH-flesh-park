#version 330 compatibility
#define REFLECT_STEPS 18 // [10 18 28]
#define HAZE
//#define FULL_RES_FOG
//#define FP_DEBUG_UV
// 第二趟，全分辨率：画面本身、地上和羊水的倒影，再把半分辨率算好的环境遮蔽和雾贴回来，最后是光晕
in vec2 texcoord;

/*
const bool colortex0MipmapEnabled = true;
*/

uniform sampler2D colortex0;
uniform sampler2D colortex4;
uniform sampler2D colortex5;
uniform sampler2D colortex6;
uniform sampler2D colortex7;
uniform mat4 gbufferProjection;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform mat4 gbufferProjectionInverse;
uniform float frameTimeCounter;
uniform float viewWidth;
uniform float viewHeight;
uniform float fogEnd;

#include "/lib/light.glsl"

/* RENDERTARGETS: 0,3 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outBright;

vec3 viewAt(vec2 uv, float depth) {
    vec4 p = gbufferProjectionInverse * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

float dither() {
    return fract(52.9829189 * fract(dot(gl_FragCoord.xy + frameTimeCounter * 61.0, vec2(0.06711056, 0.00583715))));
}

// 打不中的时候：反射的那一路最后还在画面里的地方是什么颜色，就借一点过来。
// 不然积水从远处看倒映着一片红，走近了反射照到画面外头，一下子全黑，像水没了
vec3 fallbackAt(vec2 seen, vec3 miss) {
    if (seen.x < 0.0) return miss;
    return mix(miss, min(texture(colortex0, seen).rgb, vec3(1.5)), 0.6);
}

// 屏幕空间倒影：沿反射方向走，走到画面深度后面就算打中，再往回细找一下
vec3 reflectionAt(vec3 p, vec3 r, float jitter, vec3 miss) {
    float t = 0.2, last = 0.0;
    vec2 seen = vec2(-1.0);
    for (int i = 0; i < REFLECT_STEPS; i++) {
        vec3 q = p + r * t;
        vec4 c = gbufferProjection * vec4(q, 1.0);
        if (c.w <= 0.0) return fallbackAt(seen, miss);
        vec2 uv = c.xy / c.w * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return fallbackAt(seen, miss);
        vec3 s = viewAt(uv, texture(depthtex0, uv).r);
        if (s.z > q.z) {
            if (s.z - q.z > (t - last) * 2.0 + 0.3) return fallbackAt(seen, miss);
            float lo = last, hi = t;
            for (int k = 0; k < 4; k++) {
                float m = (lo + hi) * 0.5;
                vec4 cm = gbufferProjection * vec4(p + r * m, 1.0);
                vec2 um = cm.xy / cm.w * 0.5 + 0.5;
                if (viewAt(um, texture(depthtex0, um).r).z > (p + r * m).z) hi = m;
                else lo = m;
                uv = um;
            }
            vec2 edge = smoothstep(vec2(0.0), vec2(0.1), uv) * smoothstep(vec2(1.0), vec2(0.9), uv);
            return mix(miss, min(texture(colortex0, uv).rgb, vec3(2.5)), edge.x * edge.y);
        }
        last = t;
        seen = uv;
        // 步数少就一步迈得大一点，够得着的距离差不多
        t = t * (REFLECT_STEPS >= 28 ? 1.18 : REFLECT_STEPS >= 18 ? 1.3 : 1.5) + 0.08;
    }
    return fallbackAt(seen, miss);
}

// 半分辨率的雾和遮蔽贴回来：取周围四个半分辨率像素，按离得多近和远近像不像加权。
// 远近差得多的（物体边上，一边是近处的东西一边是远处）不混，边上就不会糊出一圈雾
void halfRes(float z, out vec4 fog, out float ao) {
#ifdef FULL_RES_FOG
    fog = texelFetch(colortex6, ivec2(gl_FragCoord.xy), 0);
    ao = texelFetch(colortex7, ivec2(gl_FragCoord.xy), 0).r;
    return;
#endif
    vec2 at = gl_FragCoord.xy * 0.5 - 0.5;
    ivec2 base = ivec2(floor(at));
    vec2 f = at - vec2(base);
    ivec2 last = max(ivec2(int(ceil(viewWidth * 0.5)), int(ceil(viewHeight * 0.5))) - 1, ivec2(0));
    vec4 fogSum = vec4(0.0);
    float aoSum = 0.0, weight = 0.0, closest = 1e9;
    vec4 nearFog = vec4(0.0, 0.0, 0.0, 1.0);
    float nearAo = 1.0;
    for (int i = 0; i < 4; i++) {
        ivec2 o = ivec2(i & 1, i >> 1);
        ivec2 c = clamp(base + o, ivec2(0), last);
        vec4 s = texelFetch(colortex6, c, 0);
        vec2 shade = texelFetch(colortex7, c, 0).rg;
        float gap = abs(shade.g - z);
        float w = (o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y) * exp(-gap / (0.04 + 0.03 * z));
        fogSum += s * w;
        aoSum += shade.r * w;
        weight += w;
        if (gap < closest) {
            closest = gap;
            nearFog = s;
            nearAo = shade.r;
        }
    }
    if (weight > 1e-4) {
        fog = fogSum / weight;
        ao = aoSum / weight;
    } else {
        fog = nearFog;
        ao = nearAo;
    }
}

void main() {
    vec3 col = texture(colortex0, texcoord).rgb;
    float front = texture(depthtex0, texcoord).r;
    float depth = texture(depthtex1, texcoord).r;
    bool sky = depth >= 1.0;
    vec3 near = viewAt(texcoord, min(front, 0.9999));
    float range = max(fogEnd, 10.0);
    vec3 p = viewAt(texcoord, sky ? 0.9999 : depth);
    float dist = sky ? range * 1.2 : length(p);
    float jitter = dither();

    vec3 fogLinear = toLinear(fogColor);
    float gloss = texture(colortex4, texcoord).r;
    if (gloss > 0.02 && front < 1.0) {
        vec3 surface = normalize(texture(colortex5, texcoord).rgb * 2.0 - 1.0);
        float facing = max(dot(-normalize(near), surface), 0.0);
        float fresnel = 0.03 + 0.97 * pow(1.0 - facing, 4.0);
        vec3 mirror = reflectionAt(near, reflect(normalize(near), surface), jitter, fogLinear * 0.5);
        // 地上的积水只反一点：反得太狠，远处看像一片红水，走近反射打到画面外头就没了，像水不见了。羊水（反光 0.9）照旧
        float strength = gloss > 0.88 ? gloss * (0.1 + fresnel * 0.9) : gloss * (0.08 + fresnel * 0.42);
        col = mix(col, mirror, clamp(strength, 0.0, gloss > 0.88 ? 1.0 : 0.4));
    }

    vec4 fog;
    float ao;
    halfRes(sky ? 60000.0 : -p.z, fog, ao);
    col *= ao;
    float keep = fog.a;
    col = sky ? fogLinear * keep + fog.rgb : col * keep + fog.rgb;
    // 只有比雾亮的东西才会晕开：灯、被手电照亮的地方。雾本身不算，不然大块暗影的边上会多一圈描边
#ifdef HAZE
    vec3 haze = textureLod(colortex0, texcoord, 4.5).rgb * 0.45 + textureLod(colortex0, texcoord, 6.5).rgb * 0.55;
    haze = max(haze - fogLinear * 1.6 - vec3(0.004), vec3(0.0));
    col += haze * (1.0 - keep) * (sky ? 0.25 : 0.7);
#endif
    if (!sky) col = mix(col, fogLinear, smoothstep(range * 0.82, range * 1.25, dist));

    outColor = vec4(col, 1.0);
#ifdef FP_DEBUG_UV
    outColor = vec4(fog.rg, 0.0, 1.0);
#endif
    // 只有真正的亮点才起光晕，而且封顶：一大块亮面不能把整屏冲白
    outBright = vec4(min(max(col - 1.35, vec3(0.0)), vec3(0.7)), 1.0);
}
