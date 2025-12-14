# Installation Guide

## System Requirements

### Required Software
- **Java 25+** with FFM API support
- **Vulkan Runtime** (NOT full SDK) - comes with GPU drivers or download from https://vulkan.lunarg.com/sdk/home#windows
- **GLFW** - bundled in JAR, no separate install needed

### Windows Setup

1. **Install Vulkan Runtime**
   - Usually already installed with GPU drivers
   - If missing, download runtime installer from https://vulkan.lunarg.com/sdk/home#windows
   - Verify: `where vulkan-1.dll`

2. **Verify Java**
   ```bash
   java --version  # Should be 25+
   ```

### Linux Setup

```bash
# Vulkan
sudo apt install vulkan-tools libvulkan-dev

# GLFW
sudo apt install libglfw3 libglfw3-dev
```

### macOS Setup

```bash
# Vulkan (via MoltenVK)
brew install molten-vk

# GLFW
brew install glfw
```

## Building from Source

```bash
# Generate bindings (Windows)
generate-vulkan-bindings.bat
generate-glfw-bindings.bat

# Build
mvn clean install

# Run sample
cd sample-app
mvn exec:java
```

## Troubleshooting

**"UnsatisfiedLinkError: vulkan-1"**
- Vulkan SDK not installed or not in PATH

**"Cannot find vkCreateInstance"**
- Wrong Vulkan SDK version or corrupted installation

**"GLFW initialization failed"**
- GLFW library not found in PATH
