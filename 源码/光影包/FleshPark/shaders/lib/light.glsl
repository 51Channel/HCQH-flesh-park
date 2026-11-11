// 所有物体都用这一个函数打光：很暗的环境光、灯、身边一圈光、手电。
// 手电的开关和强弱由服务端写进饱食度（客户端看不见这个条）：20 全亮，15 变弱，10 关，8 连身边的光也没了。
uniform vec3 fogColor;
uniform sampler2D shadowtex1;

#include "/lib/flash.glsl"

vec3 toLinear(vec3 c) {
    return pow(c, vec3(2.2));
}

float flashPower() {
    float h = currentPlayerHunger * 20.0;
    return h > 18.5 ? 1.0 : (h > 13.5 ? 0.5 : 0.0);
}

float aroundPower() {
    return currentPlayerHunger * 20.0 > 9.0 ? 1.0 : 0.0;
}

#define BEAM_DIR vec3(-0.0114 * handSide(), 0.0143, -0.99983)

float spotAt(vec3 p, out vec3 toLight, out float dist) {
    toLight = HAND_POS - p;
    dist = length(toLight);
    toLight /= max(dist, 0.001);
    float theta = acos(clamp(dot(-toLight, BEAM_DIR), -1.0, 1.0));
    // 真手电的光斑：中间一个亮芯，外面一圈暗一点的光，再外面一点点散光。反光杯留下很淡的同心圆
    float core = exp(-theta * theta / (2.0 * 0.06 * 0.06));
    float body = exp(-theta * theta / (2.0 * 0.19 * 0.19)) * 0.62;
    float skirt = smoothstep(0.7, 0.3, theta) * 0.1;
    float rings = 1.0 + 0.06 * sin(theta * 92.0) * smoothstep(0.03, 0.1, theta) * smoothstep(0.36, 0.14, theta);
    // 手电照得远，贴脸也不会照成死白
    float attenuate = 1.0 / (1.5 + 0.1 * dist + 0.006 * dist * dist);
    return (core + body + skirt) * rings * attenuate;
}

// 手电的投影。jitter 让软边的噪点每个像素不一样，后面的颗粒会把它盖住
float flashShadow(vec3 p, float jitter, int taps, float slope) {
    vec4 c = flashClip(p);
    if (c.w < FLASH_NEAR * 1.5 || c.w > FLASH_FAR) return 1.0;
    vec2 uv = c.xy / c.w * 0.5 + 0.5;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return 1.0;
    float radius = 0.0012 + 0.00004 * min(c.w, 30.0);
    // 斜着照的面，软边采样会打到自己。坡度越陡偏移越大
    float bias = 0.04 + c.w * 0.003 + slope * radius * c.w * 1.7;
    float lit = 0.0;
    for (int i = 0; i < taps; i++) {
        float a = (float(i) + jitter) * 2.39996;
        vec2 o = taps > 1 ? vec2(cos(a), sin(a)) * radius * sqrt((float(i) + 0.5) / float(taps)) : vec2(0.0);
        lit += flashDepth(texture(shadowtex1, uv + o).r) + bias >= c.w ? 1.0 : 0.0;
    }
    return lit / float(taps);
}

float ggx(float ndh, float rough) {
    float a = rough * rough, a2 = a * a, d = ndh * ndh * (a2 - 1.0) + 1.0;
    return a2 / (3.14159 * d * d);
}

vec3 shade(vec3 albedo, vec3 n, vec3 gn, vec3 p, vec2 lm, float wet, float flesh, float jitter) {
    vec3 v = normalize(-p);
    float dist = length(p);
    // 环境光：跟着这一段雾的颜色，压得很低，远处的东西只剩一点轮廓
    vec3 col = albedo * (toLinear(fogColor) * 0.55 + vec3(0.007, 0.006, 0.006));
    // 掠射角多一点环境光，黑暗里还能看出形状
    col += albedo * pow(1.0 - max(dot(n, v), 0.0), 3.0) * (toLinear(fogColor) * 0.5 + vec3(0.008, 0.006, 0.006));

    // 场景里的灯。亮度在屏幕上往哪边变大，灯就在哪边：灯才有方向，凹凸才看得出来
    float lamp = pow(max(lm.x - 0.04, 0.0), 2.6);
    vec2 slope = vec2(dFdx(lm.x), dFdy(lm.x));
    vec3 dpx = dFdx(p), dpy = dFdy(p);
    vec3 toLamp = dpx * slope.x / max(dot(dpx, dpx), 1e-7) + dpy * slope.y / max(dot(dpy, dpy), 1e-7);
    float pull = length(toLamp);
    vec3 lampDir = pull > 1e-4 ? normalize(toLamp / pull + gn * 0.7) : gn;
    float lampFacing = max(dot(n, lampDir), 0.0);
    col += albedo * lamp * vec3(1.0, 0.7, 0.42) * 3.0 * (0.3 + 0.9 * lampFacing);
    vec3 h0 = normalize(lampDir + v);
    col += lamp * wet * ggx(max(dot(n, h0), 0.0), 0.26) * 0.1 * vec3(1.0, 0.82, 0.66);

    // 身边一小圈光：看得清脚下和手边，七八米外就没了
    float around = aroundPower() / (1.0 + 0.16 * dist * dist);
    col += albedo * around * 0.5 * vec3(1.0, 0.9, 0.78) * (0.3 + 0.7 * max(dot(n, v), 0.0));
    col += around * wet * pow(max(dot(n, v), 0.0), 26.0) * 0.06;

    // 手电
    float power = flashPower();
    if (power > 0.0) {
        vec3 l;
        float d;
        float raw = spotAt(p, l, d) * power * 3.4;
        float shadow = raw > 0.002 ? flashShadow(p + gn * (0.04 + d * 0.0035), jitter, 5, min(sqrt(max(1.0 - pow(max(dot(gn, l), 0.02), 2.0), 0.0)) / max(dot(gn, l), 0.02), 10.0)) : 1.0;
        float spot = raw * shadow;
        float ndl = max(dot(n, l), 0.0);
        col += albedo * spot * ndl * vec3(1.0, 0.94, 0.84);
        // 肉会透光：背光的一侧发红
        col += albedo * flesh * raw * (0.3 + 0.7 * shadow) * pow(1.0 - ndl, 3.0) * 0.34 * vec3(1.0, 0.2, 0.14);
        vec3 h = normalize(l + v);
        float vdh = max(dot(v, h), 0.0);
        float fresnel = 0.04 + 0.96 * pow(1.0 - vdh, 5.0);
        float rough = mix(mix(0.62, 0.26, wet), 0.6, smoothstep(6.0, 26.0, d));
        col += spot * ndl * ggx(max(dot(n, h), 0.0), rough) * fresnel * (0.1 + 0.6 * wet) * vec3(1.0, 0.96, 0.9);
    }

    // 自发光：亮度拉满的展示实体，比如灯管、招牌、评价机的按钮
    col += albedo * smoothstep(0.86, 0.97, lm.x) * 1.5;
    return col;
}
