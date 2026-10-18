#version 150

in vec4 finalVertexColor;
out vec4 fragColor;

void main() {
    fragColor = finalVertexColor;
}
