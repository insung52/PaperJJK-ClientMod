# Iris Rendering Pipeline Analysis (v1.21.9)

## 목적
Iris가 GUI/HUD를 제외하고 world에만 post-processing을 적용하는 방법 분석

---

## 1. 렌더링 파이프라인 순서

```
GameRenderer.render()
├─> GameRenderer.renderLevel()  <-- World rendering
│   └─> LevelRenderer.renderLevel()
│       ├─> World, entities, particles 렌더링
│       ├─> popMatrix() 호출 직전  <-- ⭐ Iris injection point #1
│       └─> [LevelRenderer.renderLevel() RETURNS]
│
│   [GameRenderer.renderLevel() RETURNS]  <-- ⭐ Iris injection point #2
│
└─> GUI/HUD 렌더링 (renderLevel() 이후)
```

---

## 2. Iris의 핵심 Injection Points

### A. LevelRenderer.renderLevel() - Composite & Final Pass
**파일:** `Iris/common/src/main/java/net/irisshaders/iris/mixin/MixinLevelRenderer.java:154`

```java
@Inject(method = "renderLevel",
        at = @At(value = "INVOKE",
                 target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"))
private void iris$endLevelRender(...) {
    HandRenderer.INSTANCE.renderTranslucent(...);
    Profiler.get().popPush("iris_final");

    // 🎯 POST-PROCESSING 적용 시점
    pipeline.finalizeLevelRendering();
    pipeline = null;
}
```

**핵심:**
- `popMatrix()` 호출 **직전**에 injection
- World 렌더링은 완료됨
- 하지만 아직 `renderLevel()` 메소드 **내부**
- GUI는 아직 렌더링 안됨 (renderLevel() 리턴 후에 시작)

### B. GameRenderer.renderLevel() - Color Space Conversion
**파일:** `Iris/common/src/main/java/net/irisshaders/iris/mixin/MixinGameRenderer.java:84`

```java
@Inject(method = "renderLevel", at = @At("TAIL"))
private void iris$runColorSpace(DeltaTracker deltaTracker, CallbackInfo ci) {
    Iris.getPipelineManager().getPipeline()
        .ifPresent(WorldRenderingPipeline::finalizeGameRendering);
}
```

**핵심:**
- `renderLevel()` 메소드 끝에서 실행
- Color space conversion 등 추가 처리
- GUI 렌더링 **전**

---

## 3. Post-Processing 실행 흐름

### finalizeLevelRendering() 내부
**파일:** `IrisRenderingPipeline.java:1064-1075`

```java
@Override
public void finalizeLevelRendering() {
    isRenderingWorld = false;
    removePhaseIfNeeded();
    compositeRenderer.renderAll();      // composite0..N passes
    finalPassRenderer.renderFinalPass(); // final pass → main FB
}
```

### Composite Passes
**파일:** `CompositeRenderer.java:273-363`

```java
public void renderAll() {
    for (Pass compositePass : passes) {
        // Compute shaders 실행
        for (ComputeProgram compute : compositePass.computes) {
            compute.use();
            customUniforms.push(compute);
            compute.dispatch(main.width, main.height);
        }

        // Fullscreen quad shader 실행
        compositePass.program.use();
        renderPass.drawIndexed(0, 0, 6, 1);
    }
}
```

### Final Pass
**파일:** `FinalPassRenderer.java:207-329`

```java
public void renderFinalPass() {
    final RenderTarget main = Minecraft.getInstance().getMainRenderTarget();

    if (this.finalPass != null) {
        // Compute shaders
        for (ComputeProgram compute : finalPass.computes) {
            compute.use();
            customUniforms.push(compute);
            compute.dispatch(baseWidth, baseHeight);
        }

        // Final pass를 main framebuffer에 렌더링
        finalPass.program.use();
        renderPass.drawIndexed(0, 0, 6, 1); // main FB에 출력
    } else {
        // Final pass 없으면 colortex0를 main FB로 복사
        baseline.bindAsReadBuffer();
        IrisRenderSystem.copyTexSubImage2D(
            main.getColorTexture().iris$getGlId(), ...);
    }
}
```

---

## 4. GUI/HUD가 영향받지 않는 이유

### A. 시간적 분리 (Temporal Separation)
- GUI/HUD는 `GameRenderer.renderLevel()` **리턴 후**에 렌더링
- Post-processing은 renderLevel() **내부**에서 완료
- GUI가 렌더링될 때는 이미 post-processing이 끝난 상태

### B. Framebuffer 관리
```java
// Final pass가 main framebuffer에 최종 결과 씀
finalPass.program.use();
renderPass.drawIndexed(0, 0, 6, 1); // → main FB

// 이후 GUI는 같은 main FB 위에 렌더링됨
// GUI는 post-processed image 위에 그려짐
```

### C. Shader Override 시스템
```java
@Override
public boolean shouldOverrideShaders() {
    return isRenderingWorld && isMainBound;
}
```

- `isRenderingWorld = false` (finalizeLevelRendering()에서 설정)
- GUI 렌더링 시 shader override 비활성화
- GUI는 vanilla shader 사용

---

## 5. 렌더링 단계 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│ GameRenderer.render()                                        │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ GameRenderer.renderLevel()                          │    │
│  │                                                     │    │
│  │  ┌──────────────────────────────────────────────┐  │    │
│  │  │ LevelRenderer.renderLevel()                   │  │    │
│  │  │  - World geometry                             │  │    │
│  │  │  - Entities                                   │  │    │
│  │  │  - Translucents                               │  │    │
│  │  │  - Particles                                  │  │    │
│  │  │                                               │  │    │
│  │  │  [popMatrix() 직전 - INJECTION #1] ⭐         │  │    │
│  │  │  ├─> pipeline.finalizeLevelRendering()       │  │    │
│  │  │  │   ├─> compositeRenderer.renderAll()       │  │    │
│  │  │  │   │   └─> composite0..N passes            │  │    │
│  │  │  │   └─> finalPassRenderer.renderFinalPass() │  │    │
│  │  │  │       └─> final pass (→ main FB)          │  │    │
│  │  │  │                                            │  │    │
│  │  │  └─> popMatrix()                             │  │    │
│  │  └──────────────────────────────────────────────┘  │    │
│  │                                                     │    │
│  │  [AT TAIL - INJECTION #2] ⭐                        │    │
│  │  └─> pipeline.finalizeGameRendering()              │    │
│  │      └─> Color space conversion                    │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  [POST-PROCESSING 완료 - main FB에 최종 이미지]             │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │ GUI/HUD Rendering                                   │    │
│  │  - Chat, hotbar, crosshair, inventory              │    │
│  │  - Post-processed image 위에 렌더링                │    │
│  │  - Vanilla rendering 사용                          │    │
│  └────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

---

## 6. 핵심 포인트 요약

### ✅ GUI 제외 방법
1. **Post-processing을 renderLevel() 내부에서 완료**
2. **GUI는 renderLevel() 리턴 후에 렌더링**
3. **시간적 분리로 자연스럽게 GUI 제외**

### ✅ 핵심 Injection Point
```java
@Inject(method = "renderLevel",
        at = @At(value = "INVOKE",
                 target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"))
```

### ✅ 타이밍
- **popMatrix() 직전**: World 렌더링 완료, renderLevel() 내부
- **GUI 렌더링**: renderLevel() 리턴 **후**

### ✅ Framebuffer 흐름
```
World rendering → Composite passes → Final pass → main FB
                                                     ↓
                                            GUI renders on top
```

---

## 7. Minecraft 1.21.10 적용 방법

### 현재 문제
- `renderWorld` 메소드가 1.21.10에는 없음
- `render` 메소드 TAIL에 injection하면 GUI도 포함됨

### 해결 방법
Iris처럼 `LevelRenderer.renderLevel()` 메소드에서:
```java
@Inject(method = "renderLevel",
        at = @At(value = "INVOKE",
                 target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"))
```

또는 Minecraft 1.21.10에서 해당하는 메소드/injection point 찾기

---

## 8. 참고 파일 위치

- **MixinLevelRenderer.java**: `Iris/common/src/main/java/net/irisshaders/iris/mixin/MixinLevelRenderer.java`
- **MixinGameRenderer.java**: `Iris/common/src/main/java/net/irisshaders/iris/mixin/MixinGameRenderer.java`
- **IrisRenderingPipeline.java**: `Iris/common/src/main/java/net/irisshaders/iris/pipeline/IrisRenderingPipeline.java`
- **CompositeRenderer.java**: `Iris/common/src/main/java/net/irisshaders/iris/pipeline/CompositeRenderer.java`
- **FinalPassRenderer.java**: `Iris/common/src/main/java/net/irisshaders/iris/pipeline/FinalPassRenderer.java`
