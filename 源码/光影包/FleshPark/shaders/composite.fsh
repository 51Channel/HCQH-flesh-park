#version 330 compatibility
const int shadowMapResolution = 1024; // [512 1024 2048]
const float shadowDistance = 48.0;
// 画质选项：Iris 的光影设置里能调。机器带不动就往小调
#define FOG_STEPS 14 // [8 14 24]
#define REFLECT_STEPS 18 // [10 18 28]
#define AO_SAMPLES 8 // [0 8 12]
#define MOTES
//#define FULL_RES_FOG
// 只给测帧数用：整个关掉手电阴影图那一趟
//#define FP_NO_SHADOWPASS
// 只给测试用：雾那一层直接显示屏幕坐标，看半分辨率那一趟是不是盖满全屏
//#define FP_DEBUG_UV
// 阴影这一趟只画身边这么远。写 -1 的话整个世界都要在阴影里再画一遍，还不剔除
const float shadowDistanceRenderMul = 1.0;
const float shadowIntervalSize = 0.0;
// 第一趟，半分辨率：环境遮蔽、雾、灰尘。都是一大片慢慢变的东西，只算四分之一的像素；composite1 用全分辨率按深度把它们贴回去，再算倒影。
// 结果放在缓冲左下角四分之一：那里一个像素管全屏 2×2 一块，其余四分之三的像素进来就走。
// （Iris 的 scale.composite 在这个版本不起作用：画的时候视口又被设回全屏。）开了 FULL_RES_FOG 就每个像素都算
// 整屏一起算的时候这一趟一帧要四十毫秒，是光影里最贵的一步
in vec2 texcoord;

/*
const int colortex0Format = RGBA16F;
const int colortex3Format = RGB16F;
const int colortex4Format = R8;
const int colortex5Format = RGB8;
const int colortex6Format = RGBA16F;
const int colortex7Format = RG16F;
const bool colortex4Clear = true;
const vec4 colortex4ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const bool colortex5Clear = true;
const vec4 colortex5ClearColor = vec4(0.5, 0.5, 1.0, 1.0);
*/

uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelViewInverse;
uniform vec3 cameraPosition;
uniform float frameTimeCounter;
uniform float viewWidth;
uniform float viewHeight;
uniform float fogEnd;

#include "/lib/light.glsl"

/* RENDERTARGETS: 6,7 */
layout(location = 0) out vec4 outFog;
layout(location = 1) out vec4 outShade;

vec3 viewAt(vec2 uv, float depth) {
    vec4 p = gbufferProjectionInverse * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

float dither() {
    return fract(52.9829189 * fract(dot(gl_FragCoord.xy + frameTimeCounter * 61.0, vec2(0.06711056, 0.00583715))));
}

// 雾的浓淡：几层慢慢飘的起伏叠起来，有的地方浓，有的地方几乎没有
float mist(vec3 w) {
    float t = frameTimeCounter;
    float a = sin(w.x * 0.21 + t * 0.09 + sin(w.z * 0.17 - t * 0.05) * 1.7) * sin(w.y * 0.31 - t * 0.07 + sin(w.x * 0.13) * 1.3) * sin(w.z * 0.19 + t * 0.06);
    float b = sin(w.x * 0.57 - t * 0.14 + a * 2.0) * sin(w.y * 0.71 + t * 0.11) * sin(w.z * 0.63 + t * 0.09 + a * 2.0);
    float c = sin(w.x * 1.43 + t * 0.21 + b * 2.0) * sin(w.y * 1.67 - t * 0.17) * sin(w.z * 1.31 - t * 0.13);
    float v = 0.5 + a * 0.75 + b * 0.4 + c * 0.16;
    return 0.05 + 2.3 * max(v, 0.0) * max(v, 0.0);
}

vec3 hash3(vec3 c) {
    return fract(sin(vec3(dot(c, vec3(127.1, 311.7, 74.7)), dot(c, vec3(269.5, 183.3, 246.1)), dot(c, vec3(113.5, 271.9, 124.6)))) * 43758.5453);
}

// 灰尘：空气里飘的小颗粒，世界里固定位置上慢慢晃。平时看不见，被光扫到才一闪一闪
vec3 motes(vec3 dir, float reach, mat3 toWorld, vec3 eye, float power, float around) {
    vec3 wdir = toWorld * dir;
    vec3 sum = vec3(0.0);
    float t = frameTimeCounter;
    for (int i = 0; i < 14; i++) {
        float along0 = 0.5 + float(i) * 0.95;
        if (along0 > reach) break;
        vec3 w = eye + wdir * along0;
        vec3 cell = floor(w / 1.1);
        vec3 h = hash3(cell);
        vec3 c = (cell + h) * 1.1 + vec3(sin(t * 0.21 + h.x * 40.0), cos(t * 0.17 + h.y * 40.0), sin(t * 0.19 + h.z * 40.0)) * 0.16;
        vec3 rel = c - eye;
        float along = dot(rel, wdir);
        if (along < 0.25 || along > reach) continue;
        float miss = length(rel - wdir * along);
        float size = (0.0035 + h.x * 0.005) * max(1.0, along * 0.16);
        float dot_ = smoothstep(size, size * 0.2, miss);
        if (dot_ <= 0.0) continue;
        vec3 l;
        float dd;
        float lit = spotAt(dir * along, l, dd) * power * 1.6 + around * 0.05 / (1.0 + 0.2 * along * along);
        sum += dot_ * lit * (0.25 + h.y * 0.75) * vec3(1.0, 0.95, 0.86);
    }
    return sum;
}

void main() {
#ifdef FULL_RES_FOG
    vec2 here = texcoord;
#else
    ivec2 cell = ivec2(gl_FragCoord.xy);
    if (cell.x >= int(ceil(viewWidth * 0.5)) || cell.y >= int(ceil(viewHeight * 0.5))) {
        outFog = vec4(0.0, 0.0, 0.0, 1.0);
        outShade = vec4(1.0, 60000.0, 0.0, 1.0);
        return;
    }
    vec2 here = (vec2(cell) * 2.0 + 1.0) / vec2(viewWidth, viewHeight);
#endif
    // 玻璃和水汽不算表面：雾和天空按它们后面的东西算，不然窗外是一片没处理过的底色
    float front = texture(depthtex0, here).r;
    float depth = texture(depthtex1, here).r;
    bool sky = depth >= 1.0;
    float range = max(fogEnd, 10.0);
    vec3 p = viewAt(here, sky ? 0.9999 : depth);
    float dist = sky ? range * 1.2 : length(p);
    vec3 dir = normalize(p);
    float jitter = dither();
    vec3 fogLinear = toLinear(fogColor);

    float ao = 1.0;
    if (AO_SAMPLES > 0 && !sky && front >= depth - 1e-5) {
        // 环境遮蔽：角落、褶皱、物体贴地的地方都压暗，画面才有厚度
        float occluded = 0.0;
        float radius = 1.1 / max(dist, 0.8);
        for (int i = 0; i < AO_SAMPLES; i++) {
            float a = (float(i) + jitter) * 2.39996;
            float r = sqrt((float(i) + 0.5) / float(AO_SAMPLES)) * radius;
            vec2 uv = here + vec2(cos(a) * viewHeight / viewWidth, sin(a)) * r * 0.5;
            float d = texture(depthtex0, uv).r;
            float gap = viewAt(uv, d).z - p.z;
            if (gap > 0.03 && gap < 2.4) occluded += 1.0 - gap / 2.4;
        }
        ao = 1.0 - 0.78 * clamp(occluded / float(max(AO_SAMPLES, 1)) * 1.3, 0.0, 1.0);
    }

    float density = 1.7 / range;
    float reach = min(dist, range * 1.2);
    const int STEPS = FOG_STEPS;
    float dt = reach / float(STEPS);
    float optical = 0.0;
    vec3 glow = vec3(0.0);
    float power = flashPower(), around = aroundPower();
    mat3 toWorld = mat3(gbufferModelViewInverse);
    for (int i = 0; i < STEPS; i++) {
        float t = (float(i) + jitter) * dt;
        vec3 q = dir * t;
        vec3 w = toWorld * q + gbufferModelViewInverse[3].xyz + cameraPosition;
        // 贴着地面的雾更浓
        float low = exp(-max(w.y - (gbufferModelViewInverse[3].y + cameraPosition.y - 1.6), 0.0) * 0.4);
        float d = density * mist(w) * (0.65 + low * 1.1);
        optical += d * dt;
        float keep = exp(-optical);
        vec3 light = fogLinear;
        light += around * vec3(1.0, 0.9, 0.78) * 0.018 / (1.0 + 0.2 * t * t);
        if (power > 0.0) {
            vec3 l;
            float dd;
            float beam = spotAt(q, l, dd);
            if (beam > 0.002) light += beam * flashShadow(q, jitter, 1, 0.0) * power * vec3(1.0, 0.92, 0.8) * 0.13 * exp(-t * 0.11) * smoothstep(0.2, 2.2, t);
        }
        glow += keep * d * dt * light;
    }
#ifdef MOTES
    glow += motes(dir, sky ? 14.0 : min(dist, 14.0), toWorld, gbufferModelViewInverse[3].xyz + cameraPosition, power, around);
#endif
    outFog = vec4(glow, exp(-optical));
#ifdef FP_DEBUG_UV
    outFog = vec4(here, 0.0, 0.0);
#endif
    // 第二个通道是这个像素的远近，贴回全分辨率时拿它比：前后差得多的不混在一起，物体边上不会糊出一圈
    outShade = vec4(ao, sky ? 60000.0 : -p.z, 0.0, 1.0);
}
