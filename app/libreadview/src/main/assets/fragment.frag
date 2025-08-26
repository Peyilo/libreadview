precision mediump float;
uniform vec3 iResolution;
uniform vec4 iMouse;
uniform float iTime;
uniform sampler2D iChannel0;
uniform sampler2D iChannel1;
varying vec2 vTexCoord; // 顶点着色器传来的 [0,1] 范围坐标

#define pi 3.14159265359
#define radius .05

void main() {
    // vTexCoord 原本就是 [0,1]，我们可以直接用
    vec2 uv = vTexCoord;

    // 修正宽高比
    float aspect = iResolution.x / iResolution.y;
    uv.x *= aspect;

    // 处理鼠标坐标（需要归一化到 [0,1]）
    vec2 mouse = iMouse.xy / iResolution.xy;
    mouse.x *= aspect;

    vec2 click = iMouse.zw / iResolution.xy;
    click.x *= aspect;

    vec2 mouseDir = normalize(click - mouse);
    vec2 origin = clamp(mouse - mouseDir * mouse.x / mouseDir.x, 0., 1.);

    float mouseDist = clamp(length(mouse - origin) + (aspect - (abs(iMouse.z) / iResolution.x) * aspect) / mouseDir.x, 0., aspect / mouseDir.x);

    if (mouseDir.x < 0.) {
        mouseDist = distance(mouse, origin);
    }

    float proj = dot(uv - origin, mouseDir);
    float dist = proj - mouseDist;
    vec2 linePoint = uv - dist * mouseDir;

    vec4 fragColor;
    if (dist > radius) {
        fragColor = texture2D(iChannel1, vTexCoord);
        fragColor.rgb *= pow(clamp(dist - radius, 0., 1.) * 1.5, .2);
    }
    else if (dist >= 0.) {
        float theta = asin(dist / radius);
        vec2 p2 = linePoint + mouseDir * (pi - theta) * radius;
        vec2 p1 = linePoint + mouseDir * theta * radius;
        uv = (p2.x <= aspect && p2.y <= 1. && p2.x > 0. && p2.y > 0.) ? p2 : p1;
        fragColor = texture2D(iChannel0, uv / vec2(aspect,1.));
        fragColor.rgb *= pow(clamp((radius - dist) / radius, 0., 1.), .2);
    }
    else {
        vec2 p = linePoint + mouseDir * (abs(dist) + pi * radius);
        uv = (p.x <= aspect && p.y <= 1. && p.x > 0. && p.y > 0.) ? p : uv;
        fragColor = texture2D(iChannel0, uv / vec2(aspect,1.));
    }

    gl_FragColor = fragColor;
}