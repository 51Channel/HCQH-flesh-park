// 手电的投影：阴影图不是从太阳看，是从手电看。shadow.vsh 用 flashClip 把整个场景投到手电的视角上。
// 手电拿在哪只手，服务端一起写进饱食度：20/15 右手，19/14 左手（副手）
uniform float currentPlayerHunger;

float handSide() {
    float h = floor(currentPlayerHunger * 20.0 + 0.5);
    return (h == 19.0 || h == 14.0) ? -1.0 : 1.0;
}

#define HAND_POS vec3(0.16 * handSide(), -0.26, 0.05)
const float FLASH_NEAR = 0.25, FLASH_FAR = 48.0, FLASH_F = 1.25;

vec4 flashClip(vec3 view) {
    vec3 v = view - HAND_POS;
    float a = (FLASH_FAR + FLASH_NEAR) / (FLASH_FAR - FLASH_NEAR), b = 2.0 * FLASH_FAR * FLASH_NEAR / (FLASH_FAR - FLASH_NEAR);
    return vec4(v.x * FLASH_F, v.y * FLASH_F, -v.z * a - b, -v.z);
}

float flashDepth(float stored) {
    return 2.0 * FLASH_FAR * FLASH_NEAR / (FLASH_FAR + FLASH_NEAR - (stored * 2.0 - 1.0) * (FLASH_FAR - FLASH_NEAR));
}
