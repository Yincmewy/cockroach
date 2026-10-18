#version 150

// Color algorithm adapted from Seraphina/dddddd, branch 1.20.1:
// assets/silent_love_sword/shaders/core/rainbow_lightning.vsh
in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float EntityWorldY;
uniform float FlowTime;

out vec4 finalVertexColor;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    float worldY = EntityWorldY + Position.y;
    float span = 5.0;
    float speed = 0.34;
    float hue = fract(worldY / span + FlowTime * speed + 0.08);
    float brightness = clamp((Color.r + Color.g + Color.b) / 3.0, 0.66, 0.88);
    vec3 rainbow = hsv2rgb(vec3(hue, 1.0, 1.0));
    vec3 finalColor = rainbow * brightness * 0.84;
    finalVertexColor = vec4(finalColor, 0.82);
}
