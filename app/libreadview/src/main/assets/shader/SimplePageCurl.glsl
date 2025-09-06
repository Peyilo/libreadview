// fragment.glsl
precision mediump float;

const float M_PI = 3.14159265359;

// 源/目标纹理
uniform sampler2D uFromTex;
uniform sampler2D uToTex;

// 过渡控制
uniform float uProgress;  // 0..1
uniform float uRatio;     // 宽/高，用于方向向量里你的 ratio
uniform int   uAngleDeg;  // 例如 80
uniform float uRadius;    // 例如 0.15
uniform int   uRoll;      // 0/1
uniform int   uUncurl;    // 0/1
uniform int   uGreyback;  // 0/1
uniform float uOpacity;   // 0.8
uniform float uShadow;    // 0.2

varying vec2 vTexCoord;

vec4 getFromColor(vec2 uv) { return texture2D(uFromTex, uv); }
vec4 getToColor(vec2 uv)   { return texture2D(uToTex,   uv); }

vec4 transition(vec2 uv) {
    // setup
    float phi = radians(float(uAngleDeg)) - M_PI / 2.0; // target curl angle
    vec2 dir = normalize(vec2(cos(phi) * uRatio, sin(phi))); // direction unit vector
    vec2 q = vec2((dir.x >= 0.0) ? 0.5 : -0.5, (dir.y >= 0.0) ? 0.5 : -0.5); // quadrant corner
    vec2 i = dir * dot(q, dir); // initial position, curl axis on corner
    vec2 f = -(i + dir * uRadius * 2.0); // final position, curl & shadow just out of view
    vec2 m = f - i; // path extent, perpendicular to curl axis

    // get point relative to curl axis
    float t = (uUncurl == 1) ? 1.0 - uProgress : uProgress;
    vec2 p = i + m * t; // current axis point from origin
    q = uv - 0.5; // distance of current point from centre
    float dist = dot(q - p, dir); // distance of point from curl axis
    p = q - dir * dist; // point perpendicular to curl axis

    // map point to curl
    vec4 a = getFromColor(uv), b = getToColor(uv), c = (uUncurl == 1) ? a : b;
    bool g = false, o = false, s = false; // getcolor & opacity & shadow flags

    if (dist < 0.0) { // point is over flat or rolling A
        if (uRoll == 0) { // curl
            p += dir * (M_PI * uRadius - dist) + 0.5;
            g = true;
        } else if (-dist < uRadius) { // possibly on roll over
            float phi2 = asin(-dist / uRadius);
            p += dir * (M_PI + phi2) * uRadius + 0.5;
            g = true; s = true;
        }
        if (g && p.x >= 0.0 && p.x <= 1.0 && p.y >= 0.0 && p.y <= 1.0) // on back of A
        o = true;
        else {
            c = (uUncurl == 1) ? b : a; g = false;
        }
    } else if (uRadius > 0.0) { // point is over curling A or flat B
        // map to cylinder point
        float phi3 = asin(dist / uRadius);
        vec2 p2 = p + dir * (M_PI - phi3) * uRadius + 0.5;
        vec2 p1 = p + dir * phi3 * uRadius + 0.5;
        if (p2.x >= 0.0 && p2.x <= 1.0 && p2.y >= 0.0 && p2.y <= 1.0) { // on curling back of A
            p = p2; g = true; o = true; s = true;
        } else if (p1.x >= 0.0 && p1.x <= 1.0 && p1.y >= 0.0 && p1.y <= 1.0) { // on curling front of A
            p = p1; g = true;
        } else { // on B
            s = true;
        }
    }

    if (g) // on A
    c = (uUncurl == 1) ? getToColor(p) : getFromColor(p);

    if (o) { // need opacity
        if (uGreyback == 1) {
            float gray = (c.r + c.g + c.b) / 3.0;
            c.rgb = vec3(gray);
        }
        c.rgb += (1.0 - c.rgb) * uOpacity;
    }

    if (s && uRadius > 0.0) {
        // shadow (注：大半径时在 B 上会有“潮线”，与原注释一致)
        float k = abs(dist + (g ? uRadius : -uRadius)) / uRadius;
        c.rgb *= pow(clamp(k, 0.0, 1.0), uShadow);
    }
    return c;
}

void main() {
    gl_FragColor = transition(vTexCoord);
}
