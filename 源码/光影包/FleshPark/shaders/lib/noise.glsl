// 世界坐标上的噪声：污渍和水洼按它来，不会跟着方块一格一格重复
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float hash31(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

// 格点上的随机数先存在贴图里（生成噪声.py）：把小数部分先做平滑，再让显卡按线性插值取一次，
// 和逐个格点算哈希再插值是一回事，一个像素省下几十次运算。肉上每个像素要取十几次噪声，这是光影里最贵的一处
uniform sampler2D noise2d;
uniform sampler3D noise3d;
// 只给测帧数用：换回逐点算哈希
//#define FP_PROC_NOISE

float vnoise2(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
#ifdef FP_PROC_NOISE
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x), mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
#else
    return texture(noise2d, (i + f + 0.5) / 256.0).r;
#endif
}

float vnoise3(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
#ifndef FP_PROC_NOISE
    return texture(noise3d, (i + f + 0.5) / 64.0).r;
#endif
    float a = mix(mix(hash31(i), hash31(i + vec3(1.0, 0.0, 0.0)), f.x), mix(hash31(i + vec3(0.0, 1.0, 0.0)), hash31(i + vec3(1.0, 1.0, 0.0)), f.x), f.y);
    float b = mix(mix(hash31(i + vec3(0.0, 0.0, 1.0)), hash31(i + vec3(1.0, 0.0, 1.0)), f.x), mix(hash31(i + vec3(0.0, 1.0, 1.0)), hash31(i + vec3(1.0, 1.0, 1.0)), f.x), f.y);
    return mix(a, b, f.z);
}

float fbm2(vec2 p) {
    return vnoise2(p) * 0.55 + vnoise2(p * 2.13 + 7.1) * 0.3 + vnoise2(p * 4.31 + 3.7) * 0.15;
}

float fbm3(vec3 p) {
    return vnoise3(p) * 0.55 + vnoise3(p * 2.13 + 7.1) * 0.3 + vnoise3(p * 4.31 + 3.7) * 0.15;
}
