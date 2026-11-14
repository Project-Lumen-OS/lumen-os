#version 300 es
// Lumen game menu controller (Gm3dRenderer): the look of the pre-rendered frames (qp_design/models/render, three.js
// MeshStandardMaterial / MeshPhysicalMaterial): metal-roughness PBR with GGX highlights, the studio's directional
// lights (key, fill, two accent rims, top), the room environment for ambient light and reflections (env.lge, sampled
// from the same three.js PMREM), the filmic tone curve and sRGB output. Premultiplied alpha for the TextureView.
precision highp float;
precision highp int;
precision highp sampler2DArray;

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec4 vTangent;

uniform vec3 uCam;
uniform vec4 uBase;          // linear rgb, opacity
uniform vec3 uEmissive;
uniform vec4 uRMN;           // roughness, metalness, normal scale x, y
uniform vec4 uAoCc;          // ao map intensity, constant ao, clearcoat, clearcoat roughness
uniform int uFlags;          // Gm3dModel.M_* | Gm3dRenderer.U_*
uniform sampler2D uBaseMap;
uniform sampler2D uNormalMap;
uniform sampler2D uOrmMap;
uniform sampler2DArray uEnv;
uniform float uEnvRough[16];
uniform int uEnvLayers;
uniform float uEnvIntensity;
uniform float uExposure;
uniform vec3 uLightDir[8];
uniform vec3 uLightCol[8];
uniform int uLightCount;
out vec4 outColor;

const float PI = 3.141592653589793;
const int M_DOUBLE = 1, M_BLEND = 2, M_ROUGH_MAP = 8, M_METAL_MAP = 16, M_AO_MAP = 32;
const int U_BASE_MAP = 64, U_NORMAL_MAP = 128, U_TANGENT = 256;

float pow2(float x) { return x * x; }

// ---- environment: equirect layers of rising roughness (u = atan(x, z) / 2pi + 0.5, v = acos(y) / pi)
float envLayer(float r) {
    if (r <= uEnvRough[0]) return 0.0;
    for (int i = 1; i < 16; i++) {
        if (i >= uEnvLayers) break;
        if (r <= uEnvRough[i]) return float(i - 1) + (r - uEnvRough[i - 1]) / (uEnvRough[i] - uEnvRough[i - 1]);
    }
    return float(uEnvLayers - 1);
}

vec3 envAt(vec3 d, float r) {
    d = normalize(d);
    vec2 uv = vec2(atan(d.x, d.z) * (0.5 / PI) + 0.5, acos(clamp(d.y, -1.0, 1.0)) / PI);
    float l = envLayer(r);
    float l0 = floor(l);
    vec3 a = texture(uEnv, vec3(uv, l0)).rgb;
    vec3 b = texture(uEnv, vec3(uv, min(l0 + 1.0, float(uEnvLayers - 1)))).rgb;
    return mix(a, b, l - l0);
}

// ---- BRDF (GGX, height-correlated Smith, Schlick)
vec3 fSchlick(vec3 f0, float f90, float dotVH) {
    float f = exp2((-5.55473 * dotVH - 6.98316) * dotVH);
    return f0 * (1.0 - f) + f90 * f;
}

float vSmith(float alpha, float dotNL, float dotNV) {
    float a2 = pow2(alpha);
    float gv = dotNL * sqrt(a2 + (1.0 - a2) * pow2(dotNV));
    float gl = dotNV * sqrt(a2 + (1.0 - a2) * pow2(dotNL));
    return 0.5 / max(gv + gl, 1e-6);
}

float dGgx(float alpha, float dotNH) {
    float a2 = pow2(alpha);
    float d = pow2(dotNH) * (a2 - 1.0) + 1.0;
    return a2 / (PI * pow2(d));
}

vec3 brdfGgx(vec3 l, vec3 v, vec3 n, vec3 f0, float f90, float rough) {
    float alpha = pow2(rough);
    vec3 h = normalize(l + v);
    float dotNL = clamp(dot(n, l), 0.0, 1.0), dotNV = clamp(dot(n, v), 0.0, 1.0);
    float dotNH = clamp(dot(n, h), 0.0, 1.0), dotVH = clamp(dot(v, h), 0.0, 1.0);
    return fSchlick(f0, f90, dotVH) * (vSmith(alpha, dotNL, dotNV) * dGgx(alpha, dotNH));
}

// Karis' analytic fit of the split-sum environment BRDF
vec2 dfg(vec3 n, vec3 v, float rough) {
    float dotNV = clamp(dot(n, v), 0.0, 1.0);
    vec4 r = rough * vec4(-1.0, -0.0275, -0.572, 0.022) + vec4(1.0, 0.0425, 1.04, -0.04);
    float a004 = min(r.x * r.x, exp2(-9.28 * dotNV)) * r.x + r.y;
    return vec2(-1.04, 1.04) * a004 + r.zw;
}

vec3 radiance(vec3 v, vec3 n, float rough) {
    vec3 r = reflect(-v, n);
    r = normalize(mix(r, n, rough * rough));
    return envAt(r, rough) * uEnvIntensity;
}

// ---- output: the filmic curve (ACES fit by Stephen Hill), then sRGB
vec3 rrtOdt(vec3 v) {
    vec3 a = v * (v + 0.0245786) - 0.000090537;
    vec3 b = v * (0.983729 * v + 0.4329510) + 0.238081;
    return a / b;
}

vec3 filmic(vec3 c) {
    const mat3 inM = mat3(vec3(0.59719, 0.07600, 0.02840), vec3(0.35458, 0.90834, 0.13383), vec3(0.04823, 0.01566, 0.83777));
    const mat3 outM = mat3(vec3(1.60475, -0.10208, -0.00327), vec3(-0.53108, 1.10813, -0.07276), vec3(-0.07367, -0.00605, 1.07602));
    c *= uExposure / 0.6;
    return clamp(outM * rrtOdt(inM * c), 0.0, 1.0);
}

vec3 srgb(vec3 c) {
    return mix(pow(c, vec3(0.41666)) * 1.055 - vec3(0.055), c * 12.92, vec3(lessThanEqual(c, vec3(0.0031308))));
}

void main() {
    float face = gl_FrontFacing ? 1.0 : -1.0;
    bool dbl = (uFlags & M_DOUBLE) != 0;
    vec3 n0 = normalize(vNormal);
    if (dbl) n0 *= face;
    vec4 base = uBase;
    if ((uFlags & U_BASE_MAP) != 0) base *= texture(uBaseMap, vUv);
    float rough = uRMN.x, metal = uRMN.y, ao = uAoCc.y;
    if ((uFlags & (M_ROUGH_MAP | M_METAL_MAP | M_AO_MAP)) != 0) {
        vec3 orm = texture(uOrmMap, vUv).rgb;
        if ((uFlags & M_ROUGH_MAP) != 0) rough *= orm.g;
        if ((uFlags & M_METAL_MAP) != 0) metal *= orm.b;
        if ((uFlags & M_AO_MAP) != 0) ao *= (orm.r - 1.0) * uAoCc.x + 1.0;
    }
    vec3 n = n0;
    if ((uFlags & U_NORMAL_MAP) != 0) {
        vec2 xy = texture(uNormalMap, vUv).rg * 2.0 - 1.0;
        vec3 mapN = vec3(xy, sqrt(max(0.0, 1.0 - dot(xy, xy))));
        mapN.xy *= uRMN.zw;
        mat3 tbn;
        if ((uFlags & U_TANGENT) != 0) {
            vec3 t = normalize(vTangent.xyz);
            vec3 b = normalize(cross(normalize(vNormal), t) * vTangent.w);
            tbn = mat3(t, b, n0);
        } else {
            vec3 q0 = dFdx(vWorld), q1 = dFdy(vWorld);
            vec2 st0 = dFdx(vUv), st1 = dFdy(vUv);
            vec3 q1p = cross(q1, n0), q0p = cross(n0, q0);
            vec3 t = q1p * st0.x + q0p * st1.x;
            vec3 b = q1p * st0.y + q0p * st1.y;
            float det = max(dot(t, t), dot(b, b));
            float s = det == 0.0 ? 0.0 : inversesqrt(det);
            tbn = mat3(t * s, b * s, n0);
        }
        if (dbl) { tbn[0] *= face; tbn[1] *= face; }
        n = normalize(tbn * mapN);
    }
    vec3 v = normalize(uCam - vWorld);
    // specular antialiasing: rough where the geometric normal changes fast across a pixel
    vec3 dxy = max(abs(dFdx(n0)), abs(dFdy(n0)));
    float gRough = max(max(dxy.x, dxy.y), dxy.z);
    rough = min(max(rough, 0.0525) + gRough, 1.0);
    vec3 diffuse = base.rgb * (1.0 - metal);
    vec3 f0 = mix(vec3(0.04), base.rgb, metal);
    float cc = uAoCc.z;
    float ccRough = min(max(uAoCc.w, 0.0525) + gRough, 1.0);

    vec3 direct = vec3(0.0), ccDirect = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        if (i >= uLightCount) break;
        vec3 l = uLightDir[i];
        vec3 irr = clamp(dot(n, l), 0.0, 1.0) * uLightCol[i];
        direct += irr * (brdfGgx(l, v, n, f0, 1.0, rough) + diffuse / PI);
        if (cc > 0.0) ccDirect += clamp(dot(n0, l), 0.0, 1.0) * uLightCol[i] * brdfGgx(l, v, n0, vec3(0.04), 1.0, ccRough);
    }
    // image based light, with the multiple-scattering energy term
    vec3 irradiance = envAt(n, 1.0) * uEnvIntensity;
    vec2 fab = dfg(n, v, rough);
    vec3 fssEss = f0 * fab.x + fab.y;
    float ess = fab.x + fab.y, ems = 1.0 - ess;
    vec3 favg = f0 + (1.0 - f0) * 0.047619;
    vec3 fms = fssEss * favg / (1.0 - ems * favg);
    vec3 total = fssEss + fms * ems;
    vec3 indDiffuse = diffuse * (1.0 - max(max(total.r, total.g), total.b)) * irradiance;
    vec3 indSpec = radiance(v, n, rough) * fssEss + fms * ems * irradiance;
    if (ao < 1.0) {
        float dotNV = clamp(dot(n, v), 0.0, 1.0);
        indDiffuse *= ao;
        indSpec *= clamp(pow(dotNV + ao, exp2(-16.0 * rough - 1.0)) - 1.0 + ao, 0.0, 1.0);
    }
    vec3 c = direct + indDiffuse + indSpec + uEmissive;
    if (cc > 0.0) {
        vec2 fcc = dfg(n0, v, ccRough);
        vec3 ccInd = radiance(v, n0, ccRough) * (0.04 * fcc.x + fcc.y);
        vec3 fc = fSchlick(vec3(0.04), 1.0, clamp(dot(n0, v), 0.0, 1.0));
        c = c * (1.0 - cc * fc) + (ccDirect + ccInd) * cc;
    }
    float alpha = (uFlags & M_BLEND) != 0 ? base.a : 1.0;
    outColor = vec4(srgb(filmic(c)) * alpha, alpha);
}
