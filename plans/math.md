Math Library Implementation Plan

Location

helpers-core/src/main/java/io/github/yetyman/helpers/math/

Design Decisions Already Made

1. Mutable-first with copy variants

- In-place operations modify this and return this for chaining
- Copy variants use *New suffix (e.g., addNew returns a new instance)
- Fields are public for direct access (performance, no getter overhead)

2. Flat fields, not arrays

- Vec3 has public float x, y, z not float[] data
- Mat4 has 16 named float fields in column-major order
- Enables JIT scalar optimization, avoids bounds checks

3. Builders as reusable configurators (not just construction ceremony)

- All types have direct constructors AND optional builder()
- Builders are designed to be allocated once and reused across frames
- Builders can memoize intermediate calculations (e.g., sin/cos for axis-angle quaternion)
- When only one parameter changes between frames, the builder only recomputes what's affected
- Build output goes through a pluggable BuildStrategy<T> — defaults to new, can be swapped to pooling

4. BuildStrategy interface

public interface BuildStrategy<T> {
T obtain();
default void release(T instance) {}
static <T> BuildStrategy<T> allocating(Supplier<T> factory) { return factory::get; }
}

- Each math type has a static buildStrategy field (global default)
- Builder's build() uses the global strategy; build(strategy) overrides per-call
- This allows transparent pooling opt-in later without changing calling code

5. Column-major matrices matching GLSL/Vulkan

- Field naming: mColumnRow — e.g., m30 = column 3, row 0 = translation X
- Writing sequentially (m00, m01, m02, m03, m10, ...) produces what GLSL mat4 expects

6. GPU-layout types as separate classes

- Live in math/gpu/ subpackage
- Implement a local GpuWritable interface (not dependent on vulkan-core's BufferWritable)
- Handle std140/std430 alignment (e.g., vec3 padded to 16 bytes)
- Write/read directly to/from MemorySegment

7. Coordinate conventions

- Right-handed coordinate system
- Y-up for world space
- Vulkan NDC: X right, Y down, Z into screen, depth 0..1
- Projection matrices account for Vulkan clip space (Y flip, 0..1 depth)

Package Structure

io.github.yetyman.helpers.math/
BuildStrategy.java          -- pooling/allocation strategy interface
MathUtil.java               -- constants (EPSILON, PI, DEG_TO_RAD, etc.) + scalar utilities (clamp, lerp, smoothstep, etc.)
Vec2.java                   -- 2-component float vector
Vec3.java                   -- 3-component float vector (+ cross product, reflect, project)
Vec4.java                   -- 4-component float vector (+ perspectiveDivide)
Mat3.java                   -- 3x3 matrix (column-major)
Mat4.java                   -- 4x4 matrix (column-major, projection/view/transform factories)
Quaternion.java             -- unit quaternion for rotations (slerp, nlerp, axis-angle, euler, matrix conversion)
Transform.java              -- position + rotation + scale, dirty flag, lazy matrix, parent-child hierarchy

io.github.yetyman.helpers.math.geometry/
Plane.java                  -- normal + distance
Ray.java                    -- origin + direction
AABB.java                   -- axis-aligned bounding box (min/max)
OBB.java                    -- oriented bounding box (center + half-extents + rotation)
Sphere.java                 -- center + radius
Frustum.java                -- 6 planes extracted from view-projection matrix
Intersections.java          -- static intersection tests (ray-plane, ray-AABB, ray-sphere, ray-OBB, frustum-AABB, frustum-sphere, AABB-AABB, sphere-sphere, plane-AABB)
ContainmentResult.java      -- enum: INSIDE, OUTSIDE, INTERSECT

io.github.yetyman.helpers.math.gpu/
GpuWritable.java            -- interface: byteSize(), writeTo(MemorySegment, offset), readFrom(MemorySegment, offset)
GpuVec3.java                -- std140-aligned vec3 (16 bytes with padding)
GpuVec4.java                -- vec4 (16 bytes)
GpuMat3.java                -- std140-aligned mat3 (3 columns of vec4 = 48 bytes)
GpuMat4.java                -- mat4 (64 bytes)

Implementation Order

Phase 1: Foundation (MathUtil, Vec2, Vec3, Vec4)

MathUtil — constants and scalar utilities:

- EPSILON, PI, TWO_PI, HALF_PI, DEG_TO_RAD, RAD_TO_DEG
- clamp(float), clamp(int), lerp, epsilonEquals (default + custom tolerance), toRadians, toDegrees, fract, smoothstep, inverseSqrt, sign, step, min3, max3

BuildStrategy — the interface as described above.

Vec2 — fields: public float x, y

- Constructors: default (0,0), (x,y), copy, scalar
- Static factories: zero, one, unitX, unitY
- Setters: set(x,y), set(Vec2), set(scalar)
- In-place ops: add, sub, mul, div (component-wise and scalar), negate, normalize, lerp, min, max, clamp (vec and scalar), abs, floor, ceil
- Copy ops: addNew, subNew, mulNew, divNew, negateNew, normalizeNew, lerpNew
- Queries: dot, length, lengthSquared, distance, distanceSquared, angle (atan2), angleTo
- Array interop: toArray(float[], offset), toArray(), fromArray(float[], offset)
- Object overrides: equals (exact), epsilonEquals, hashCode, toString
- Builder inner class: holds x, y; build() uses global strategy; build(strategy) overrides

Vec3 — fields: public float x, y, z

- Everything Vec2 has plus:
- Static factories: up, down, forward, back, left, right
- In-place ops: cross, reflect, project
- Copy ops: crossNew, reflectNew, projectNew
- Query: angleTo

Vec4 — fields: public float x, y, z, w

- Constructor from Vec3+w
- In-place ops: perspectiveDivide (divides xyz by w)
- Accessors: xyz() returning a new Vec3 copy, or setXyz(Vec3)

Phase 2: Matrices (Mat3, Mat4)

Mat3 — 9 float fields, column-major (m00,m01,m02 = column 0):

- Constructors: identity, from columns, from float array, copy
- Static factories: identity(), zero()
- Multiply: matmat (in-place + new), matVec3
- transpose, determinant, inverse
- set/get by column index, set/get by row index
- Builder: minimal (mostly just pooling hook)

Mat4 — 16 float fields, column-major (m00,m01,m02,m03 = column 0):

- Everything Mat3 has plus:
- Multiply: matVec4, matVec3 (with implicit w=1, perspective divide result)
- Static factories:
    - identity()
    - perspective(fovY, aspect, near, far) — Vulkan clip space (Y flip, 0..1 depth)
    - orthographic(left, right, bottom, top, near, far) — Vulkan clip space
    - lookAt(eye, center, up)
    - translation(Vec3), translation(x, y, z)
    - rotation(Quaternion), rotation(Vec3 axis, float angle)
    - scale(Vec3), scale(x, y, z), scale(float uniform)
    - trs(Vec3 pos, Quaternion rot, Vec3 scale) — combined transform matrix

- decompose() — extract position, rotation (Quaternion), scale from TRS matrix
- transformPoint(Vec3) — multiply with w=1, return Vec3
- transformDirection(Vec3) — multiply with w=0, return Vec3
- Builder: this is where memoization matters
    - perspective(fov, aspect, near, far) — caches tan(fov/2); if only near/far change, only recomputes depth terms
    - lookAt(eye, center, up) — caches forward/right/up basis; if only eye changes, only recomputes translation
    - trs(pos, rot, scale) — caches rotation matrix from quaternion; if only pos changes, only stamps translation

Phase 3: Quaternion

Quaternion — fields: public float x, y, z, w (identity = 0,0,0,1):

- Constructors: identity, (x,y,z,w), copy
- Static factories:
    - identity()
    - fromAxisAngle(Vec3 axis, float angle) — normalizes axis, computes sin/cos
    - fromEuler(float pitch, float yaw, float roll)
    - fromMat3(Mat3), fromMat4(Mat4)
    - fromDirection(Vec3 forward, Vec3 up) — look rotation

- In-place ops: multiply, conjugate, inverse, normalize
- Copy ops: multiplyNew, conjugateNew, inverseNew, normalizeNew
- Interpolation: slerp(other, t), nlerp(other, t) — both in-place and new
- Conversion: toMat3(), toMat4(), toAxisAngle(Vec3 outAxis) returning angle, toEuler(Vec3 outAngles)
- Apply: rotateVector(Vec3) — in-place rotation of the vector
- Queries: dot, length, lengthSquared
- Builder: high memoization value
    - fromAxisAngle(axis, angle) — caches normalized axis, sin(angle/2), cos(angle/2)
    - If only angle changes, skips axis normalization
    - If only axis changes, skips trig

Phase 4: Transform

Transform — composition of position(Vec3) + rotation(Quaternion) + scale(Vec3):

- Fields: position, rotation, scale, localMatrix (Mat4), dirty flag
- Optional parent (Transform), worldMatrix (Mat4), worldDirty flag
- localMatrix() — lazy recompute only when dirty
- worldMatrix() — chains parent.worldMatrix * localMatrix, lazy
- Setters mark dirty: setPosition, setRotation, setScale, translate, rotate, scaleBy
- Static decompose(Mat4) — extract TRS components from an arbitrary matrix
- No builder (construct then mutate pattern is natural here)

Phase 5: Geometry

Plane — public float nx, ny, nz, d (normal + signed distance):

- Static factories: fromPointNormal, fromThreePoints
- distanceTo(Vec3 point), classify(Vec3 point) (FRONT/BACK/ON)
- normalize() in-place

Ray — holds Vec3 origin, Vec3 direction (direction should be unit length):

- pointAt(float t) — returns origin + direction * t
- Static factory: from(origin, direction)

AABB — holds Vec3 min, Vec3 max:

- Static factories: fromMinMax, fromCenterExtents
- contains(Vec3 point), contains(AABB other)
- expand(Vec3 point) — grow to include point
- merge(AABB other) — union
- center(), extents(), size()
- intersects(AABB other) — boolean overlap test
- transform(Mat4) — returns new AABB enclosing the transformed box

OBB — holds Vec3 center, Vec3 halfExtents, Quaternion orientation:

- contains(Vec3 point)
- toAABB() — enclosing AABB

Sphere — holds Vec3 center, float radius:

- contains(Vec3 point), intersects(Sphere other)
- merge(Sphere other) — enclosing sphere

Frustum — 6 Plane instances (near, far, left, right, top, bottom):

- Static factory: fromViewProjection(Mat4 vp) — extracts planes from combined matrix
- testAABB(AABB) — returns ContainmentResult
- testSphere(Sphere) — returns ContainmentResult
- testPoint(Vec3) — boolean inside test
- Planes tested in rejection-likely order: near, far, left, right, top, bottom

Intersections — all static methods:

- rayPlane(Ray, Plane) — returns t (negative = no hit)
- rayAABB(Ray, AABB) — returns t (negative = no hit)
- raySphere(Ray, Sphere) — returns t (negative = no hit)
- rayOBB(Ray, OBB) — returns t
- frustumAABB(Frustum, AABB) — ContainmentResult
- frustumSphere(Frustum, Sphere) — ContainmentResult
- aabbAABB(AABB, AABB) — boolean
- sphereSphere(Sphere, Sphere) — boolean
- planeAABB(Plane, AABB) — ContainmentResult

ContainmentResult — enum: INSIDE, OUTSIDE, INTERSECT

Phase 6: GPU Layout Types

GpuWritable — interface in math/gpu/:

public interface GpuWritable {
int byteSize();
void writeTo(MemorySegment segment, long offset);
void readFrom(MemorySegment segment, long offset);
}

- Local to helpers-core, does NOT depend on vulkan-core's BufferWritable
- vulkan-core can bridge with an adapter later

GpuVec3 — wraps a Vec3, writes 16 bytes (12 data + 4 padding for std140):

- writeTo writes x, y, z as 3 floats, leaves 4 bytes padding
- readFrom reads x, y, z, ignores padding byte

GpuVec4 — wraps a Vec4, writes 16 bytes (no padding needed)

GpuMat3 — wraps a Mat3, writes 48 bytes (std140: each column is vec4-aligned = 3 columns x 16 bytes)

GpuMat4 — wraps a Mat4, writes 64 bytes (4 columns x 4 floats x 4 bytes, no extra padding needed)

Each GPU type has:

- Constructor from CPU type
- toCpu() method returning the CPU type
- set(cpuType) to update without reallocating
- Implements GpuWritable

Key Non-Functional Requirements

- Zero allocation in hot-path operations when using mutable API
- No dependencies outside java.base (pure Java)
- Thread-safe for reads; mutation is caller-synchronized
- No static mutable state beyond BuildStrategy (which is intentionally global-swappable)
- All static factories return new instances (never shared mutables)
- Zero-length normalize returns zero vector (never NaN or throws)
- Float epsilon comparisons where appropriate

What's NOT in scope

- Spatial acceleration structures (octree, BVH) — deferred to later
- Eventing/instrumented math variants — deferred, depends on composition system
- Panama Vector API (SIMD) — future optimization, not initial implementation
- Tests — should be written alongside but aren't blocking implementation