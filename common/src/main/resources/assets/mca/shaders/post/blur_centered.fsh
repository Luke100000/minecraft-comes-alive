#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
    vec2 BlurDir;
    float Radius;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 oneTexel = 1.0 / InSize;
    vec2 center = vec2(0.5);
    float distanceFromCenter = pow(length(texCoord - center), 3.0);
    float actualRadius = max(1.0, round(Radius));

    vec4 blurred = vec4(0.0);
    for (float r = -actualRadius; r <= actualRadius; r += 1.0) {
        blurred += texture(InSampler, texCoord + oneTexel * r * BlurDir * distanceFromCenter);
    }

    fragColor = blurred / (actualRadius * 2.0 + 1.0);
}
