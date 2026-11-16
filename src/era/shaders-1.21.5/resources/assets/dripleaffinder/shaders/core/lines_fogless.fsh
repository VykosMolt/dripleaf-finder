#version 150


uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    fragColor = color;
}
