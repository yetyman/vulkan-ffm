# GLFW Bindings

Auto-generated GLFW FFM bindings with bundled native libraries.

## Setup

1. Copy `glfw3.dll` from `C:\GLFW\glfw-3.4\lib-vc2022\glfw3.dll` to `src/main/resources/natives/windows/`
2. Run `generate-glfw-bindings.bat` from project root
3. Build: `mvn clean install`

## Usage

```java
import io.github.yetyman.glfw.GLFWLoader;
import io.github.yetyman.glfw.generated.GLFWFFM;

GLFWLoader.load();
GLFWFFM.glfwInit();
```

The native library is automatically extracted and loaded from the JAR.
