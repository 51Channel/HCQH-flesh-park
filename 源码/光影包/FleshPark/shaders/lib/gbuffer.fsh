// 下面三个只给测帧数用：分别关掉肉的噪声、地上墙上的脏、透明裁剪，看各自要多少时间
//#define FP_CHEAP_FLESH
//#define FP_NO_GRIME
//#define FP_NO_DISCARD
in vec2 texcoord;
in vec2 lmcoord;
in vec4 tint;
in vec3 viewPos;
in vec3 viewNormal;
in vec3 worldPos;
in float fleshMark;
in float liquidMark;
in float veinMark;

uniform sampler2D gtexture;
uniform sampler2D normals;
uniform sampler2D specular;
uniform float alphaTestRef;
uniform float frameTimeCounter;
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;

#include "/lib/light.glsl"
#include "/lib/noise.glsl"

/* RENDERTARGETS: 0,4,5 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outMask;
layout(location = 2) out vec4 outNormal;

float jitterAt() {
    return fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
}

// 第二个缓冲：反光多强、朝哪边。合成时按它算倒影
void extras(float reflectivity, vec3 n) {
    outMask = vec4(reflectivity, 0.0, 0.0, 1.0);
    outNormal = vec4(n * 0.5 + 0.5, 1.0);
}

vec4 triplanar(sampler2D map, vec2 a, vec2 b, vec2 c, vec3 blend) {
    return texture(map, a) * blend.x + texture(map, b) * blend.y + texture(map, c) * blend.z;
}

void main() {
    float jitter = jitterAt();
    vec4 tex = texture(gtexture, texcoord) * tint;
#ifndef FP_NO_DISCARD
    if (tex.a < alphaTestRef) discard;
#endif
    vec3 raw = tex.rgb;
    vec3 albedo = toLinear(raw);
    vec3 n = normalize(gl_FrontFacing ? viewNormal : -viewNormal);
    vec3 gn = n;
#ifdef FLAT_FACE_CAMERA
    n = normalize(-viewPos);
#endif
    float flesh = smoothstep(0.05, 0.22, raw.r - max(raw.g, raw.b));
    float wet = flesh;
    float ao = 1.0;

    if (liquidMark > 0.5) {
        // 羊水：很暗很混，几乎不透，看得见的主要是倒影和手电的亮斑
        float t = frameTimeCounter;
        vec3 ripple = vec3(sin(worldPos.x * 1.7 + t * 0.5) + sin((worldPos.x + worldPos.z) * 3.1 - t * 0.7) * 0.6, 0.0, sin(worldPos.z * 2.1 - t * 0.45) + sin((worldPos.x - worldPos.z) * 3.7 + t * 0.8) * 0.5);
        vec3 surface = normalize(mat3(gbufferModelView) * (vec3(0.0, 1.0, 0.0) + ripple * 0.04));
        outColor = vec4(shade(toLinear(tint.rgb) * 0.24, surface, surface, viewPos, lmcoord, 0.95, 0.0, jitter), 0.97);
        extras(0.9, surface);
        return;
    }

    if (fleshMark > 0.5) {
        vec3 wn = mat3(gbufferModelViewInverse) * n;
        vec3 blend = pow(abs(wn), vec3(5.0));
        blend /= blend.x + blend.y + blend.z;
        vec3 q = worldPos / 2.6, big = worldPos / 13.7;
        vec2 ux = q.zy, uy = q.xz, uz = q.xy, bx = big.zy, by = big.xz, bz = big.xy;
        // 屏幕上一个像素盖住好几个贴图像素，就别再采细节，不然远处全是闪烁的横线
        float texel = length(fwidth(worldPos)) / 2.6 * 512.0;
        float sharp = 1.0 / (1.0 + texel * texel * 0.16);
        vec3 color = mix(triplanar(gtexture, bx, by, bz, blend).rgb, triplanar(gtexture, ux, uy, uz, blend).rgb, sharp);
        vec4 mid = triplanar(normals, ux, uy, uz, blend), wide = triplanar(normals, bx, by, bz, blend);
        vec4 fine = triplanar(normals, ux * 3.3, uy * 3.3, uz * 3.3, blend);
        // 肉的层次：一团团的鼓包，中间夹着又深又暗的沟。按世界坐标算，不会重复，也不成条纹
        // 近处是拳头大的褶皱；几十米外细节看不清，换成几米大的肉团和深沟，才显得巨大
        float span = length(fwidth(worldPos));
        float close = 1.0 / (1.0 + span * span * 45.0);
        float lump = 0.5, groove = 0.0;
#ifndef FP_CHEAP_FLESH
        if (close > 0.02) {
            vec3 wq = worldPos * 0.62;
            float warp = vnoise3(wq * 1.3);
            lump = fbm3(wq + warp * 1.3);
            groove = smoothstep(0.84, 1.0, 1.0 - abs(2.0 * fbm3(wq * 1.6 + warp * 2.2 + 11.0) - 1.0));
        }
        if (close < 0.98) {
            vec3 wb = worldPos * 0.17;
            float warpB = vnoise3(wb * 1.3);
            float lumpB = fbm3(wb + warpB * 1.3);
            float grooveB = smoothstep(0.72, 1.0, 1.0 - abs(2.0 * fbm3(wb * 1.6 + warpB * 2.2 + 11.0) - 1.0));
            lumpB = smoothstep(0.36, 0.64, lumpB) * 0.6 + 0.22;
            lump = mix(lumpB, lump, close);
            groove = mix(grooveB, groove, close);
        }
#endif
        float fold = smoothstep(0.3, 0.62, lump) * (1.0 - groove * 0.9);
        // 颜色：陈旧的暗红，鼓起来的地方带一点粉，沟里接近黑紫
        vec3 meat = toLinear(color) * vec3(0.8, 0.72, 0.74);
#ifdef FP_CHEAP_FLESH
        float old = 0.5;
#else
        float old = smoothstep(0.38, 0.66, vnoise3(worldPos * 0.21 + 5.0));
#endif
        meat = mix(meat, meat * vec3(0.62, 0.5, 0.42), old * 0.7);
        albedo = mix(meat * vec3(0.13, 0.05, 0.08), meat * vec3(1.0, 0.92, 0.9), fold);
        albedo = mix(albedo, albedo + vec3(0.03, 0.012, 0.012), smoothstep(0.62, 0.8, lump));
        vec2 m = (mid.xy * 2.0 - 1.0) * sharp + (wide.xy * 2.0 - 1.0) * 1.4 + (fine.xy * 2.0 - 1.0) * 0.5 * sharp * sharp;
        vec3 detail = vec3(0.0, m.y, m.x) * blend.x + vec3(m.x, 0.0, m.y) * blend.y + vec3(m.x, m.y, 0.0) * blend.z;
        n = normalize(mat3(gbufferModelView) * normalize(wn + detail * 0.9));
        // 鼓包和沟也要真的有起伏，手电照过去才有明暗
        vec3 fdx = dFdx(viewPos), fdy = dFdy(viewPos);
        vec3 r1f = cross(fdy, gn), r2f = cross(gn, fdx);
        float det = dot(fdx, r1f);
        float bump = lump - groove * 0.35;
        n = normalize(abs(det) * n - mix(5.0, 0.6, close) * sign(det) * (dFdx(bump) * r1f + dFdy(bump) * r2f));
        ao = mix(1.0, mid.b, sharp);
        wet = triplanar(specular, ux, uy, uz, blend).r;
        // 碎碎的湿亮点：一小片一小片地反光
        wet = clamp(wet + (fine.a - 0.5) * 0.9 * sharp, 0.0, 1.0);
        // 远处看不清细节时高光要散开，不然长肉索上只剩一根细亮线
        wet *= mix(0.4, 1.0, sharp) * (0.25 + 0.75 * fold);
        outColor = vec4(shade(albedo, n, gn, viewPos, lmcoord, wet, 1.0, jitter) * ao, 1.0);
        // 血管跟着心跳亮：心跳的亮度放在光照值里。大空腔里看不清肉壁，就靠它们看出有多大
        if (veinMark > 0.5) outColor.rgb += vec3(0.8, 0.07, 0.1) * (0.012 + pow(lmcoord.x, 3.0) * 1.2) * (0.4 + 0.6 * fold);
        else outColor.rgb += albedo * vec3(1.0, 0.62, 0.55) * min(lmcoord.x, 0.5) * 1.05 * (0.2 + 0.8 * max(dot(n, normalize(-viewPos)), 0.0));
        extras(wet * 0.3, normalize(mix(gn, n, 0.3)));
        return;
    }

    float puddle = 0.0;
#ifndef FLAT_FACE_CAMERA
    // 法线贴图：切线空间从贴图坐标的变化率算，什么网格都能用
    vec3 dx = dFdx(viewPos), dy = dFdy(viewPos);
    vec2 du = dFdx(texcoord), dv = dFdy(texcoord);
    vec4 nm = texture(normals, texcoord);
    vec2 nxy = nm.xy * 2.0 - 1.0;
    // 透明通道小于 1 说明这张贴图真有法线；不能看法线偏了多少，平的地方会被判成没有
    if (nm.a < 0.99) {
        vec3 r1 = cross(dy, n), r2 = cross(n, dx);
        vec3 tangent = r1 * du.x + r2 * dv.x, bitangent = r1 * du.y + r2 * dv.y;
        float scale = max(dot(tangent, tangent), dot(bitangent, bitangent));
        if (scale > 0.0) {
            scale = 1.0 / sqrt(scale);
            float nz = sqrt(max(0.0, 1.0 - dot(nxy, nxy)));
            n = normalize(tangent * scale * nxy.x + bitangent * scale * nxy.y + n * nz);
        }
        ao = nm.b;
        float spec = texture(specular, texcoord).r;
        wet = flesh > 0.5 ? spec : spec * 0.8;
    } else {
        // 没有法线贴图：用贴图的明暗当起伏
        float height = dot(raw, vec3(0.3, 0.5, 0.2));
        vec3 r1 = cross(dy, n), r2 = cross(n, dx);
        float det = dot(dx, r1);
        vec3 grad = sign(det) * (dFdx(height) * r1 + dFdy(height) * r2);
        n = normalize(abs(det) * n - (0.018 + wet * 0.05) * grad);
    }
#endif
#if defined(GRIME) && !defined(FP_NO_GRIME)
    // 荒废了 19 年：墙上一道道往下流的脏，地上一片片的污渍和积水
    vec3 wg = mat3(gbufferModelViewInverse) * gn;
    if (abs(wg.y) > 0.7) {
        float stainFloor = fbm2(worldPos.xz * 0.23);
        albedo *= 0.5 + 0.7 * smoothstep(0.22, 0.72, stainFloor);
        if (wg.y > 0.7) {
            // 19 年没人扫：一层厚薄不均的灰，墙根和角落更厚，灰把反光吃掉
            float dust = 0.35 + 0.65 * smoothstep(0.3, 0.7, fbm2(worldPos.xz * 0.31 + 3.0));
            dust = clamp(dust + (vnoise2(worldPos.xz * 9.0) - 0.5) * 0.25, 0.0, 1.0);
            albedo = mix(albedo, vec3(0.118, 0.106, 0.09), dust * 0.55);
            wet *= 1.0 - dust * 0.92;
            n = normalize(mix(n, gn, dust * 0.6));
            puddle = smoothstep(0.66, 0.72, fbm2(worldPos.xz * 0.16 + 19.0)) * (1.0 - dust * 0.8);
            float t = frameTimeCounter;
            vec2 ring = vec2(sin(worldPos.x * 3.1 + t * 0.4) + sin(worldPos.z * 4.3 - t * 0.3), sin(worldPos.z * 2.7 + t * 0.5) + sin(worldPos.x * 3.9 + t * 0.2)) * 0.006;
            n = normalize(mix(n, normalize(gn + mat3(gbufferModelView) * vec3(ring.x, 0.0, ring.y)), puddle));
            albedo *= 1.0 - 0.55 * puddle;
            wet = max(wet, puddle * 0.95);
            ao = mix(ao, 1.0, puddle);
        }
    } else {
        float streak = fbm3(vec3(worldPos.x * 1.1, worldPos.y * 0.14, worldPos.z * 1.1));
        albedo *= 0.46 + 0.8 * smoothstep(0.26, 0.7, streak);
    }
#endif
#if defined(PROP_GRIME) && !defined(FP_NO_GRIME)
    // 混凝土拼的摆设是一块纯色：按世界坐标补一层旧漆、磨损、划痕和灰。按米算，不会跟着方块的缩放拉长或压扁。
    // 只动纯色的面：贴图本来就有花纹的（木头、铜、布、海报上的图）基本不碰
    {
        vec3 base = texture(gtexture, texcoord).rgb, avg = textureLod(gtexture, texcoord, 4.0).rgb, near4 = textureLod(gtexture, texcoord, 2.0).rgb;
        float flatness = (1.0 - smoothstep(0.015, 0.06, distance(base, avg))) * (1.0 - smoothstep(0.015, 0.06, distance(near4, avg))) * (1.0 - flesh);
        if (flatness > 0.01) {
            vec3 wn = mat3(gbufferModelViewInverse) * gn;
            vec3 an = abs(wn);
            vec2 uv = an.y > an.x && an.y > an.z ? worldPos.xz : an.x > an.z ? worldPos.zy : worldPos.xy;
            float span = length(fwidth(worldPos));
            float close = 1.0 / (1.0 + span * span * 400.0);
            float blot = fbm2(uv * 1.7 + 7.0);
            float grain = vnoise2(uv * 38.0);
            float scratch = smoothstep(0.93, 1.0, abs(sin(dot(uv, vec2(31.0, 7.0)) + fbm2(uv * 3.0) * 6.0))) * smoothstep(0.55, 0.8, fbm2(uv * 0.9 + 3.0)) * close;
            vec3 dirty = albedo * (0.78 + 0.3 * smoothstep(0.25, 0.75, blot));
            dirty *= 1.0 + (grain - 0.5) * 0.14 * close;
            dirty = mix(dirty, dirty * vec3(0.72, 0.66, 0.58), smoothstep(0.55, 0.8, fbm2(uv * 0.6 + 21.0)) * 0.6);
            dirty += scratch * 0.07 * (vec3(0.85) - dirty);
            if (wn.y > 0.7) dirty = mix(dirty, vec3(0.13, 0.12, 0.1), 0.45 * smoothstep(0.3, 0.8, fbm2(uv * 2.3)));
            else if (an.y < 0.3) dirty *= 0.8 + 0.22 * smoothstep(0.2, 0.75, fbm3(vec3(worldPos.x * 1.3, worldPos.y * 0.18, worldPos.z * 1.3)));
            albedo = mix(albedo, dirty, flatness);
            // 漆面有一点凹凸，手电扫过去不是死平的一块
            vec3 bumpW = vec3(vnoise2(uv * 21.0 + 1.0) - 0.5, 0.0, vnoise2(uv * 21.0 + 5.0) - 0.5);
            n = normalize(n + mat3(gbufferModelView) * bumpW * 0.08 * flatness * close);
        }
    }
#endif
    float reflectivity = max(wet * wet * 0.5, puddle * 0.85);
    // 半透明的东西（玻璃、水汽）不反手电，也不抢画面
    if (tex.a < 0.7) {
        wet = 0.0;
        reflectivity = 0.1;
    }
    outColor = vec4(shade(albedo, n, gn, viewPos, lmcoord, wet, flesh, jitter) * ao, tex.a);
    if (tex.a < 0.7) outColor.rgb *= 0.2;
#ifdef HELD
    // 拿在手上的手和手电：手电往前照，地面和墙会反回来一点光，再加上身边那圈光。不补的话手上的东西是一团黑，看着像地上有个洞
    outColor.rgb += albedo * vec3(1.0, 0.93, 0.84) * (0.015 + 0.07 * max(dot(n, normalize(-viewPos)), 0.0) + 0.05 * max(dot(n, vec3(0.0, 0.6, 0.8)), 0.0));
#endif
    extras(reflectivity, normalize(mix(gn, n, 0.12)));
}
