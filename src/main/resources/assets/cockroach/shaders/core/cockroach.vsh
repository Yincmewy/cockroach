#version 150
#moj_import <light.glsl>
#moj_import <fog.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 ModelNormalMat;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
uniform ivec2 LightCoords;
out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexDistance = fog_distance(ModelViewMat, Position, FogShape);
    vec3 normal = normalize(ModelNormalMat * Normal);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, Color);
    vertexColor *= texelFetch(Sampler2, clamp(LightCoords / 16, ivec2(0), ivec2(15)), 0);
    texCoord0 = UV0;
}
