#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;

uniform vec2 BlurDir;
uniform float Radius;

out vec4 fragColor;

void main() {
    vec4 blurred = vec4(0.0);
    float totalStrength = 0.0;
    float totalWeight = 0.0;
    float distance = pow(length(texCoord - vec2(0.5f)), 3.0);
    for (float r = -Radius; r <= Radius; r += 1.0) {
        vec2 sampleCoord = clamp(texCoord + oneTexel * r * BlurDir * distance, vec2(0.0), vec2(1.0));
        vec4 sampleValue = texture(DiffuseSampler, sampleCoord);
        float sampleBrightness = max(max(sampleValue.r, sampleValue.g), sampleValue.b);
        float sampleWeight = step(0.001, sampleBrightness);

        // Accumulate smoothed blur
        float strength = 1.0 - abs(r / Radius);
        totalStrength = totalStrength + strength * sampleWeight;
        blurred = blurred + vec4(sampleValue.rgb * sampleWeight, sampleValue.a * sampleWeight);
        totalWeight = totalWeight + sampleWeight;
    }
    vec3 color = totalWeight > 0.0 ? blurred.rgb / totalWeight : texture(DiffuseSampler, texCoord).rgb;
    fragColor = vec4(color, 1.0);
}
