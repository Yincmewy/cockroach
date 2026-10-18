#version 150
#moj_import <light.glsl>
#moj_import <fog.glsl>
in vec3 Position;
in vec3 Normal;
in vec2 UV0;
in vec4 Joints;
in vec4 Weights;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 ModelNormalMat;
uniform mat4 SkinMatrices[31];
uniform float WalkAmount;
uniform int FogShape;
uniform sampler2D Sampler2;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
uniform ivec2 LightCoords;
out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
void main() {
    mat4 skin = SkinMatrices[int(Joints.x)] * Weights.x
              + SkinMatrices[int(Joints.y)] * Weights.y
              + SkinMatrices[int(Joints.z)] * Weights.z
              + SkinMatrices[int(Joints.w)] * Weights.w;
    vec3 position = mix(Position, (skin * vec4(Position, 1.0)).xyz, WalkAmount);
    vec3 normal = normalize(ModelNormalMat * mix(Normal, mat3(skin) * Normal, WalkAmount));
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    vertexDistance = fog_distance(ModelViewMat, position, FogShape);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, vec4(1.0));
    vertexColor *= texelFetch(Sampler2, clamp(LightCoords / 16, ivec2(0), ivec2(15)), 0);
    texCoord0 = UV0;
}
