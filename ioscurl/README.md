# iOSCurl

Portable Kotlin transcription of the verified iOS12.5.7 (16H81) page-curl algorithms. Production sources use Kotlin standard types, arithmetic and managed arrays only. There are no Android, JVM library, Widget, EPUB, JNI, C/C++, firmware, Unicorn or WebAssembly dependencies in this module's source code. The current Gradle target produces a JVM JAR for Android consumption.

Gradle module: `:ioscurl`. Maven coordinates: `org.peyilo.ireadview:ioscurl:<version>`. Kotlin packages: `org.peyilo.ioscurl`. This module implements iOSCurl; GoogleCurl remains in `libreadview` and does not use it.

| Package | Responsibility |
| --- | --- |
| `model` | Logical points, rectangles, six binding positions, physical directions and filter values |
| `gesture` | Pan/tap recognizers, original sampling, hysteresis, direction and release decisions |
| `control` | Touch-to-filter conversion, endpoints, durations and independent CA timing tracks |
| `math` | Recovered math polynomials, software fused operations and half conversion |
| `layout` | Page sources, recto/verso passes, mirrors and underlay order |
| `transition` | Optional original compatibility, scheduling, layer events and FIFO cleanup |
| `mesh` | Typed material inputs and draw packets |
| `mesh/kernel` | Named geometry stages, live-value views, numeric record layouts and managed scratch storage |

`CurlMesh` is single-owner and reusable. Returned draw batches own their buffers and survive subsequent builds. Positions use logical points; UVs use source pixel coordinates; colors are premultiplied half4. A platform renderer consumes the packets without knowing book navigation or gestures. A book host maps `CurlPageSource` to its own page/snapshot objects.

The kernel contains ordinary Kotlin expressions and 244 named `KernelStep` operations. No instruction bytes, opcode decoder or CPU emulator ship. Its implementation is organized by algorithm:

| File | Responsibility |
| --- | --- |
| `KernelProjection.kt` | Material modulation, page projection, breakpoint sorting and visible sample range |
| `KernelSurfaceSampling.kt` | Cross sections, curved sheet strips and back-strip ordering |
| `KernelShadowTopology.kt` | Shadow boundaries, breakpoint merging and segment connectivity |
| `KernelPenumbra.kt` | Shadow cross sections, fading strips, end caps and corner rings |
| `KernelFaceSampling.kt` | Front-face boundaries, merging and textured cross sections |
| `KernelDrawing.kt` | Clip iteration, shader/blend state, draw batches and state restoration |

`KernelValues.kt` names individual **live ranges**, rather than giving a reused register one misleading permanent name. Its inline properties are views onto shared scratch storage, not independent persistent model fields. Reaching-definition analysis assigns the views and preserves all vector bits, including unused lanes. Names such as `curlRadius`, `curlNormalX`, `frontTintRgba`, `vertexBufferAddress` and `drawIndexCount` identify established roles. Short-lived intermediate expressions without a separately established physical meaning retain phase-local names such as `penumbraValue`; they must not be treated as cross-stage state. Reference step labels remain beside each function for parity debugging.

`KernelLayout` contains **byte offsets and bit layouts**. `KernelTuning` contains **sampling limits, fade profiles and appearance constants**. The integer indices in the generated value bindings describe storage reuse, not configurable curve parameters. Vector lanes and little-endian offsets remain managed-array views for numerical parity; UI or Widget code must not depend on them.

Numeric cleanup preserves precision rather than applying a fixed number of decimal places:

- Binary32 literals use a shorter decimal only after checking an identical IEEE-754 bit pattern.
- A widened float remains explicit, e.g. `0.13500000536441803` becomes `0.135f.toDouble()`, not the different binary64 value `0.135`.
- Recovered radius ratios use exact equivalent fractions such as `5.0 / 246.0`; trigonometric fit coefficients that need binary64 retain their precision.
- FMA evaluation order, signed zero, Float/Double boundaries and half-color quantization are preserved.

Normal builds and tests need only Gradle/JDK. The checked-in compressed test fixtures contain inputs, expected numeric values, indices and hashes of shader-consumed attributes, not firmware or native libraries. The 64-byte vertex record has unused padding, which is deliberately excluded from parity hashes; position, UV and half-color bytes are compared exactly.

Optional regeneration against the research C port:

```powershell
python tools/Port-CurlKernel.py --source <research>/cport
python tools/Generate-KotlinCurlFixtures.py --source <research>/cport
./gradlew.bat :ioscurl:test
```

The generator needs Python/pycparser only when regenerating the checked-in Kotlin. It calls `tools/CurlKernelReadability.py` to recover the live-value names and semantic stages, so regeneration does not restore register or numbered-block names. Fixture generation additionally needs the previously verified C reference DLL; these tools are never part of the Android build or runtime. See [libreadview integration](../docs/IOS_CURL.md) for Android integration, single-page scope and spread extension APIs.

Format and verify the module (including tests and the Gradle script):

```powershell
./gradlew.bat :ioscurl:formatKotlin
./gradlew.bat :ioscurl:checkKotlinFormat :ioscurl:test
```

The formatter uses [ktfmt](https://kotlin.github.io/ktfmt/) 0.64 with four-space Kotlin language style. It is a separate build-time configuration, excluded from runtime and published library dependencies. Golden fixture files and expected numeric tolerances are unchanged by the readability refactor.
