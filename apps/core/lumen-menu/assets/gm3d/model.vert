#version 300 es
// Lumen game menu controller (Gm3dRenderer): positions as normalized shorts in the model box, normals and tangents
// as 10:10:10:2, UVs as half floats. World = yaw * tilt * model (the turntable of the pre-rendered frames).
layout(location = 0) in vec4 aPos;
layout(location = 1) in vec4 aNor;
layout(location = 2) in vec2 aUv;
layout(location = 3) in vec4 aTan;
uniform mat4 uModel;
uniform mat4 uViewProj;
uniform vec3 uPosScale;
uniform vec3 uPosOffset;
out vec3 vWorld;
out vec3 vNormal;
out vec2 vUv;
out vec4 vTangent;

void main() {
    vec4 w = uModel * vec4(aPos.xyz * uPosScale + uPosOffset, 1.0);
    vWorld = w.xyz;
    mat3 r = mat3(uModel);
    vNormal = r * aNor.xyz;
    vTangent = vec4(r * aTan.xyz, aTan.w);
    vUv = aUv;
    gl_Position = uViewProj * w;
}
