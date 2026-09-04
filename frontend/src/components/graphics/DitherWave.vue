<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

/**
 * nexus-reference의 Dither 배경을 **셰이더 그대로** 옮긴 것.
 *
 * 원본은 three.js + @react-three/fiber + postprocessing 세 패키지를 쓴다. 하지만 실체는
 * 「풀스크린 쿼드 하나에 프래그먼트 셰이더를 굽는 것」뿐이라, raw WebGL2로 같은 그림이 나온다.
 * 후처리로 분리돼 있던 Bayer 디더 패스도 파형이 절차적(procedural)이라 같은 셰이더 안에서
 * 픽셀화된 좌표로 다시 계산하면 결과가 동일하다. 그래서 의존성이 0이다.
 *
 * 원본에서 그대로 가져온 것: cnoise(Perlin) · fbm · pattern · bayerMatrix8x8 · dither 양자화,
 * 그리고 waveColor 값까지.
 */
const props = defineProps({
  /** 디더 색 단계 수. 원본 기본값 4. */
  colorNum: { type: Number, default: 4 },
  /** 픽셀 크기. 클수록 굵은 디더. 원본 기본값 2. */
  pixelSize: { type: Number, default: 2 },
  waveSpeed: { type: Number, default: 0.045 },
  waveFrequency: { type: Number, default: 3.0 },
  waveAmplitude: { type: Number, default: 0.32 },
  /** 파형이 0인 자리의 색. 원본과 같이 순백 — 종이(#F9FAFD)보다 밝아 흰색이 포인트가 된다. */
  base: { type: Array, default: () => [1, 1, 1] },
  /** 색이 얹히는 세기. 낮출수록 흰색이 더 비친다. */
  intensity: { type: Number, default: 0.82 },
})

const canvas = ref(null)
let gl = null, raf = 0, io = null, start = 0, program = null
let uTime = null, uRes = null

const VERT = `#version 300 es
in vec2 p;
void main() { gl_Position = vec4(p, 0.0, 1.0); }`

/* 원본 waveFragmentShader + ditherFragmentShader를 하나로 합쳤다. */
const FRAG = `#version 300 es
precision highp float;
out vec4 fragColor;

uniform vec2 resolution;
uniform float time;
uniform float waveSpeed;
uniform float waveFrequency;
uniform float waveAmplitude;
uniform float colorNum;
uniform float pixelSize;
uniform vec3 waveColor;
uniform vec3 base;
uniform float intensity;

vec4 mod289(vec4 x) { return x - floor(x * (1.0/289.0)) * 289.0; }
vec4 permute(vec4 x) { return mod289(((x * 34.0) + 1.0) * x); }
vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }
vec2 fade(vec2 t) { return t*t*t*(t*(t*6.0-15.0)+10.0); }

float cnoise(vec2 P) {
  vec4 Pi = floor(P.xyxy) + vec4(0.0,0.0,1.0,1.0);
  vec4 Pf = fract(P.xyxy) - vec4(0.0,0.0,1.0,1.0);
  Pi = mod289(Pi);
  vec4 ix = Pi.xzxz, iy = Pi.yyww, fx = Pf.xzxz, fy = Pf.yyww;
  vec4 i = permute(permute(ix) + iy);
  vec4 gx = fract(i * (1.0/41.0)) * 2.0 - 1.0;
  vec4 gy = abs(gx) - 0.5;
  vec4 tx = floor(gx + 0.5);
  gx = gx - tx;
  vec2 g00 = vec2(gx.x, gy.x), g10 = vec2(gx.y, gy.y);
  vec2 g01 = vec2(gx.z, gy.z), g11 = vec2(gx.w, gy.w);
  vec4 norm = taylorInvSqrt(vec4(dot(g00,g00), dot(g01,g01), dot(g10,g10), dot(g11,g11)));
  g00 *= norm.x; g01 *= norm.y; g10 *= norm.z; g11 *= norm.w;
  float n00 = dot(g00, vec2(fx.x, fy.x));
  float n10 = dot(g10, vec2(fx.y, fy.y));
  float n01 = dot(g01, vec2(fx.z, fy.z));
  float n11 = dot(g11, vec2(fx.w, fy.w));
  vec2 fxy = fade(Pf.xy);
  vec2 n_x = mix(vec2(n00, n01), vec2(n10, n11), fxy.x);
  return 2.3 * mix(n_x.x, n_x.y, fxy.y);
}

const int OCTAVES = 4;
float fbm(vec2 p) {
  float value = 0.0, amp = 1.0, freq = waveFrequency;
  for (int i = 0; i < OCTAVES; i++) {
    value += amp * abs(cnoise(p));
    p *= freq;
    amp *= waveAmplitude;
  }
  return value;
}

float pattern(vec2 p) {
  vec2 p2 = p - time * waveSpeed;
  return fbm(p + fbm(p2));
}

const float bayer[64] = float[64](
  0.0/64.0, 48.0/64.0, 12.0/64.0, 60.0/64.0,  3.0/64.0, 51.0/64.0, 15.0/64.0, 63.0/64.0,
  32.0/64.0,16.0/64.0, 44.0/64.0, 28.0/64.0, 35.0/64.0,19.0/64.0, 47.0/64.0, 31.0/64.0,
  8.0/64.0, 56.0/64.0,  4.0/64.0, 52.0/64.0, 11.0/64.0,59.0/64.0,  7.0/64.0, 55.0/64.0,
  40.0/64.0,24.0/64.0, 36.0/64.0, 20.0/64.0, 43.0/64.0,27.0/64.0, 39.0/64.0, 23.0/64.0,
  2.0/64.0, 50.0/64.0, 14.0/64.0, 62.0/64.0,  1.0/64.0,49.0/64.0, 13.0/64.0, 61.0/64.0,
  34.0/64.0,18.0/64.0, 46.0/64.0, 30.0/64.0, 33.0/64.0,17.0/64.0, 45.0/64.0, 29.0/64.0,
  10.0/64.0,58.0/64.0,  6.0/64.0, 54.0/64.0,  9.0/64.0,57.0/64.0,  5.0/64.0, 53.0/64.0,
  42.0/64.0,26.0/64.0, 38.0/64.0, 22.0/64.0, 41.0/64.0,25.0/64.0, 37.0/64.0, 21.0/64.0
);

vec3 dither(vec2 fragUV, vec3 color) {
  vec2 sc = floor(fragUV * resolution / pixelSize);
  int x = int(mod(sc.x, 8.0));
  int y = int(mod(sc.y, 8.0));
  float threshold = bayer[y * 8 + x] - 0.25;
  float st = 1.0 / (colorNum - 1.0);
  color += threshold * st;
  color = clamp(color - 0.2, 0.0, 1.0);
  return floor(color * (colorNum - 1.0) + 0.5) / (colorNum - 1.0);
}

void main() {
  vec2 uv0 = gl_FragCoord.xy / resolution.xy;

  // 원본 후처리와 같은 픽셀화 — 좌표를 먼저 뭉갠 뒤 파형을 계산한다.
  vec2 npx = pixelSize / resolution;
  vec2 uvPix = npx * floor(uv0 / npx);

  vec2 uv = uvPix - 0.5;
  uv.x *= resolution.x / resolution.y;

  float f = pattern(uv);

  // 원본과 같다 — 순백에서 단색 하나로 섞는다. 흰색이 그대로 포인트로 남는다.
  vec3 col = mix(base, waveColor, clamp(f, 0.0, 1.0) * intensity);
  col = dither(uv0, col);

  // 종이색과 같은 자리는 투명하게 빼서, 얹는 곳의 배경을 그대로 통과시킨다.
  float a = 1.0 - smoothstep(0.0, 0.06, distance(col, base));
  fragColor = vec4(col, 1.0 - a);
}`

function compile(type, src) {
  const sh = gl.createShader(type)
  gl.shaderSource(sh, src)
  gl.compileShader(sh)
  if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) {
    console.error('[DitherWave] shader', gl.getShaderInfoLog(sh))
    return null
  }
  return sh
}

function resize() {
  const c = canvas.value
  if (!c || !gl) return
  // 디더는 픽셀 격자가 생명이라 DPR을 1로 고정한다 — 레티나에서 격자가 뭉개지지 않는다.
  const w = Math.max(1, Math.round(c.clientWidth))
  const h = Math.max(1, Math.round(c.clientHeight))
  if (c.width !== w || c.height !== h) {
    c.width = w
    c.height = h
    gl.viewport(0, 0, w, h)
  }
  gl.uniform2f(uRes, w, h)
}

function frame(now) {
  raf = requestAnimationFrame(frame)
  if (!gl) return
  resize()
  gl.uniform1f(uTime, (now - start) / 1000)
  gl.drawArrays(gl.TRIANGLES, 0, 3)
}

onMounted(() => {
  const c = canvas.value
  gl = c.getContext('webgl2', { alpha: true, antialias: false, premultipliedAlpha: false })
  // WebGL2가 없으면 아무것도 그리지 않는다 — 배경 장식이라 없어도 화면은 성립한다.
  if (!gl) return

  const vs = compile(gl.VERTEX_SHADER, VERT)
  const fs = compile(gl.FRAGMENT_SHADER, FRAG)
  if (!vs || !fs) return

  program = gl.createProgram()
  gl.attachShader(program, vs)
  gl.attachShader(program, fs)
  gl.linkProgram(program)
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    console.error('[DitherWave] link', gl.getProgramInfoLog(program))
    return
  }
  gl.useProgram(program)

  // 화면을 덮는 삼각형 하나. 쿼드보다 프래그먼트가 적다.
  const buf = gl.createBuffer()
  gl.bindBuffer(gl.ARRAY_BUFFER, buf)
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW)
  const loc = gl.getAttribLocation(program, 'p')
  gl.enableVertexAttribArray(loc)
  gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0)

  gl.enable(gl.BLEND)
  gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA)

  const u = (n) => gl.getUniformLocation(program, n)
  uTime = u('time')
  uRes = u('resolution')
  gl.uniform1f(u('waveSpeed'), props.waveSpeed)
  gl.uniform1f(u('waveFrequency'), props.waveFrequency)
  gl.uniform1f(u('waveAmplitude'), props.waveAmplitude)
  gl.uniform1f(u('colorNum'), props.colorNum)
  gl.uniform1f(u('pixelSize'), props.pixelSize)
  // 레퍼런스 Hero가 Dither에 넘기는 값 그대로: waveColor={[0.35, 0.55, 1.0]}.
  // 로고 3색 그라디언트로 바꿔 봤더니 4단계 디더에서 중간색이 탁해졌다.
  // 단색 하나를 순백에서 섞는 원본 방식이 흰색을 포인트로 남겨 가장 맑다.
  gl.uniform3f(u('waveColor'), 0.35, 0.55, 1.0)
  gl.uniform3f(u('base'), ...props.base)
  gl.uniform1f(u('intensity'), props.intensity)

  start = performance.now()

  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches
  if (reduce) {
    // 한 프레임만 그리고 멈춘다 — 그림은 남기고 움직임만 없앤다.
    resize()
    gl.uniform1f(uTime, 0)
    gl.drawArrays(gl.TRIANGLES, 0, 3)
    return
  }

  // 화면 밖으로 나가면 GPU를 놀린다.
  io = new IntersectionObserver(([e]) => {
    if (e.isIntersecting && !raf) raf = requestAnimationFrame(frame)
    else if (!e.isIntersecting && raf) { cancelAnimationFrame(raf); raf = 0 }
  })
  io.observe(c)
})

onUnmounted(() => {
  if (raf) cancelAnimationFrame(raf)
  io?.disconnect()
  const ext = gl?.getExtension('WEBGL_lose_context')
  ext?.loseContext()
  gl = null
})
</script>

<template>
  <canvas ref="canvas" class="dither" aria-hidden="true" />
</template>

<style scoped>
.dither { display: block; width: 100%; height: 100%; }
</style>
