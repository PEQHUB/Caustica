package dev.comfyfluffy.caustica.rt.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.comfyfluffy.caustica.CausticaMod;
import dev.comfyfluffy.caustica.mixin.ModelPartAccessor;
import dev.comfyfluffy.caustica.rt.RtFrameStats;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Exact direct traversal for vanilla {@link ModelPart.Cube} geometry. */
final class RtCuboidEmitter {
    private static final int STANDARD_CORNERS = 8;
    private static final ClassValue<Boolean> VANILLA_MODEL_CLASS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return type.getName().startsWith("net.minecraft.");
        }
    };

    private final IdentityHashMap<Model<?>, ModelTemplate> templates = new IdentityHashMap<>();
    private final IdentityHashMap<Model<?>, ModelPart> structurallyRejectedRoots = new IdentityHashMap<>();
    private final Set<Class<?>> loggedNamespaceRejects = new HashSet<>();
    private final Set<Class<?>> loggedTopologyRejects = new HashSet<>();
    private final Set<String> loggedSemanticRejects = new HashSet<>();
    private final StringBuilder emfSemanticScratch = new StringBuilder(128);
    private final Vector3f scratch = new Vector3f();
    private final float[] quadX = new float[4];
    private final float[] quadY = new float[4];
    private final float[] quadZ = new float[4];
    private final float[] quadU = new float[4];
    private final float[] quadV = new float[4];
    private final float[] cornerX = new float[STANDARD_CORNERS];
    private final float[] cornerY = new float[STANDARD_CORNERS];
    private final float[] cornerZ = new float[STANDARD_CORNERS];

    /**
     * Return a template only after validating the complete ordered tree. Nothing is written on failure,
     * so the caller can safely use vanilla's final render method as the fallback.
     */
    ModelTemplate prepare(Model<?> model) {
        if (!VANILLA_MODEL_CLASS.get(model.getClass())) {
            RtFrameStats.FRAME.count("entityDirectRejectNamespace", 1);
            logFallbackOnce(loggedNamespaceRejects, model, "class namespace");
            return null;
        }
        ModelPart root = model.root();
        if (structurallyRejectedRoots.get(model) == root) {
            RtFrameStats.FRAME.count("entityDirectRejectCachedTopology", 1);
            return null;
        }
        ModelTemplate template = templates.get(model);
        // The root identity is the cheap invalidation guard. Vanilla ModelPart topology is immutable for
        // the lifetime of a baked model and resource reload clears this cache. EMF is different: animate()
        // may select another variant, so its complete tree is validated once below after that selection.
        if (template != null && template.root.part != root) {
            templates.remove(model);
            template = null;
        }
        if (template == null) {
            template = ModelTemplate.create(root);
            if (template == null) {
                structurallyRejectedRoots.put(model, root);
                RtFrameStats.FRAME.count("entityDirectRejectTopology", 1);
                logFallbackOnce(loggedTopologyRejects, model,
                        "nonstandard cube topology: " + describeRejection(root));
                return null;
            }
            structurallyRejectedRoots.remove(model);
            templates.put(model, template);
        }
        // EMF's render boundary performs stateful animation before emitting geometry. The current
        // compatibility traversal cannot share that exact invocation with the generic parity oracle,
        // so direct EMF capture is quarantined until a snapshot hook can prove same-invocation parity.
        if (template.emf && !emfDirectCaptureProven()) {
            rejectSemantic(model, "EMF direct capture is unavailable; preserving generic render semantics");
            return null;
        }
        if (template.emf) {
            RtEmfCompatibility.Submission submission = RtEmfCompatibility.beginSubmission();
            if (!submission.supported()) {
                rejectSemantic(model, submission.unsupportedReason());
                return null;
            }
            String animationFailure = applyEmfPreRenderAnimation(template.root, submission);
            if (animationFailure != null) {
                rejectSemantic(model, animationFailure);
                return null;
            }
            // EMF animation can select a state variant with different cube/child lists. Recompile the
            // immutable template after that selection instead of traversing the pre-animation topology.
            if (!template.matches(root)) {
                template = ModelTemplate.create(root);
                if (template == null) {
                    rejectSemantic(model, "EMF animation selected unsupported cube topology");
                    return null;
                }
                templates.put(model, template);
            }
            if (!prepareEmfSubmission(template, model, submission)) {
                return null;
            }
        }
        return template;
    }

    private static boolean emfDirectCaptureProven() {
        return false;
    }

    private boolean prepareEmfSubmission(ModelTemplate template, Model<?> model,
                                         RtEmfCompatibility.Submission submission) {
        emfSemanticScratch.setLength(0);
        prepareEmfPart(template.root, submission, emfSemanticScratch);
        if (template.root.preparedUnsupportedReason != null) {
            rejectSemantic(model, template.root.preparedUnsupportedReason);
            return false;
        }
        if (template.lastSemanticKey == null || !template.lastSemanticKey.contentEquals(emfSemanticScratch)) {
            template.lastSemanticKey = emfSemanticScratch.toString();
        }
        template.preparedSemanticKey = template.lastSemanticKey;
        if (template.quarantinedSemanticKeys.contains(template.preparedSemanticKey)) {
            RtFrameStats.FRAME.count("entityEmfRejectQuarantined", 1);
            return false;
        }
        return true;
    }

    private String applyEmfPreRenderAnimation(PartTemplate template,
                                               RtEmfCompatibility.Submission submission) {
        if (RtEmfCompatibility.isEmfPart(template.part)) {
            return submission.applyPreRenderAnimation(template.part);
        }
        for (PartTemplate child : template.children) {
            String result = applyEmfPreRenderAnimation(child, submission);
            if (result == null || !"EMF topology has no EMF model part".equals(result)) {
                return result;
            }
        }
        return "EMF topology has no EMF model part";
    }

    private void prepareEmfPart(PartTemplate template, RtEmfCompatibility.Submission submission,
                                StringBuilder semanticKey) {
        template.preparedUnsupportedReason = null;
        RtEmfCompatibility.PartDecision decision = submission.inspect(template.part);
        template.skipPreparedSubtree = decision.action() == RtEmfCompatibility.Action.SKIP_SUBTREE;
        semanticKey.append(decision.action().ordinal()).append(':').append(decision.variant()).append(';');
        if (decision.action() == RtEmfCompatibility.Action.UNSUPPORTED) {
            template.preparedUnsupportedReason = decision.reason();
            return;
        }
        if (!template.skipPreparedSubtree) {
            for (PartTemplate child : template.children) {
                prepareEmfPart(child, submission, semanticKey);
                if (child.preparedUnsupportedReason != null) {
                    template.preparedUnsupportedReason = child.preparedUnsupportedReason;
                    break;
                }
            }
        }
    }

    private void rejectSemantic(Model<?> model, String reason) {
        RtFrameStats.FRAME.count("entityEmfRejectSemantic", 1);
        String key = model.getClass().getName() + '\n' + reason;
        if (loggedSemanticRejects.add(key)) {
            logFallback(model, reason);
        }
    }

    void clear() {
        templates.clear();
        structurallyRejectedRoots.clear();
        loggedNamespaceRejects.clear();
        loggedTopologyRejects.clear();
        loggedSemanticRejects.clear();
    }

    private static void logFallbackOnce(Set<Class<?>> logged, Model<?> model, String reason) {
        Class<?> type = model.getClass();
        if (!logged.add(type)) {
            return;
        }
        logFallback(model, reason);
    }

    private static void logFallback(Model<?> model, String reason) {
        Class<?> type = model.getClass();
        var codeSource = type.getProtectionDomain() != null ? type.getProtectionDomain().getCodeSource() : null;
        CausticaMod.LOGGER.info("Entity capture backend=GENERIC_FALLBACK: reason={}, modelClass={}, codeSource={}",
                reason, type.getName(), codeSource != null ? codeSource.getLocation() : "unknown");
    }

    private static String describeRejection(ModelPart part) {
        ModelPartAccessor access = (ModelPartAccessor) (Object) part;
        for (ModelPart.Cube cube : access.caustica$cubes()) {
            if (cube.getClass() != ModelPart.Cube.class) {
                return "cube subclass " + cube.getClass().getName();
            }
            for (ModelPart.Polygon polygon : cube.polygons) {
                if (polygon == null) {
                    return "null polygon";
                }
                if (polygon.normal() == null) {
                    return "null polygon normal";
                }
                if (polygon.vertices().length != 4) {
                    return "polygon vertex count " + polygon.vertices().length;
                }
                for (ModelPart.Vertex vertex : polygon.vertices()) {
                    if (vertex == null) {
                        return "null polygon vertex";
                    }
                }
            }
        }
        for (ModelPart child : access.caustica$children().values()) {
            String childReason = describeRejection(child);
            if (!"unknown".equals(childReason)) {
                return childReason;
            }
        }
        return "unknown";
    }

    /** Return packed actual cube counts: specialized in the high 32 bits, generic in the low 32 bits. */
    long emit(ModelTemplate template, PoseStack poseStack, RtEntityCapture capture, int color) {
        capture.ensureAdditionalVertexCapacity(template.maxVertices);
        return emitPart(template.root, poseStack, capture, color);
    }

    private long emitPart(PartTemplate template, PoseStack poseStack, RtEntityCapture capture, int color) {
        ModelPart part = template.part;
        if (!part.visible || template.empty() || template.skipPreparedSubtree) {
            return 0L;
        }
        poseStack.pushPose();
        part.translateAndRotate(poseStack);
        long counts = 0L;
        if (!part.skipDraw) {
            PoseStack.Pose pose = poseStack.last();
            for (CubeTemplate cube : template.cubes) {
                if (cube instanceof EightCornerCube eight) {
                    emitEightCornerCube(eight, pose, capture, color);
                    counts += 1L << 32;
                } else {
                    emitGenericCube(cube.cube, pose, capture, color);
                    counts++;
                }
            }
        }
        for (PartTemplate child : template.children) {
            counts += emitPart(child, poseStack, capture, color);
        }
        poseStack.popPose();
        return counts;
    }

    boolean needsCanary(ModelTemplate template) {
        return template.emf && !template.verifiedSemanticKeys.contains(template.preparedSemanticKey);
    }

    void approveCanary(ModelTemplate template) {
        template.verifiedSemanticKeys.add(template.preparedSemanticKey);
        RtFrameStats.FRAME.count("entityEmfCanaryPass", 1);
        CausticaMod.LOGGER.info("Entity capture backend=EMF_DIRECT enabled: semanticKey=0x{}",
                Integer.toHexString(template.preparedSemanticKey.hashCode()));
    }

    void rejectCanary(ModelTemplate template, Model<?> model, String reason) {
        template.quarantinedSemanticKeys.add(template.preparedSemanticKey);
        RtFrameStats.FRAME.count("entityEmfCanaryReject", 1);
        rejectSemantic(model, "strict EMF canary mismatch: " + reason);
    }

    boolean isEmf(ModelTemplate template) {
        return template.emf;
    }

    private void emitEightCornerCube(EightCornerCube cube, PoseStack.Pose pose,
                                     RtEntityCapture capture, int color) {
        Matrix4f matrix = pose.pose();
        for (int i = 0; i < STANDARD_CORNERS; i++) {
            matrix.transformPosition(cube.x[i], cube.y[i], cube.z[i], scratch);
            cornerX[i] = scratch.x();
            cornerY[i] = scratch.y();
            cornerZ[i] = scratch.z();
        }
        for (FaceTemplate face : cube.faces) {
            pose.transformNormal(face.nx, face.ny, face.nz, scratch);
            capture.addIndexedDirectQuad(cornerX, cornerY, cornerZ, face.corners, face.u, face.v,
                    scratch.x(), scratch.y(), scratch.z(), color);
        }
    }

    /** A2's verified direct-polygon path retained for nonstandard but valid cube topology. */
    private void emitGenericCube(ModelPart.Cube cube, PoseStack.Pose pose,
                                 RtEntityCapture capture, int color) {
        Matrix4f matrix = pose.pose();
        for (ModelPart.Polygon polygon : cube.polygons) {
            Vector3f normal = pose.transformNormal(polygon.normal(), scratch);
            float nx = normal.x();
            float ny = normal.y();
            float nz = normal.z();
            ModelPart.Vertex[] vertices = polygon.vertices();
            for (int i = 0; i < 4; i++) {
                ModelPart.Vertex vertex = vertices[i];
                matrix.transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), scratch);
                quadX[i] = scratch.x();
                quadY[i] = scratch.y();
                quadZ[i] = scratch.z();
                quadU[i] = vertex.u();
                quadV[i] = vertex.v();
            }
            capture.addDirectQuad(quadX, quadY, quadZ, quadU, quadV, nx, ny, nz, color);
        }
    }

    static final class ModelTemplate {
        final PartTemplate root;
        final int maxVertices;
        final boolean emf;
        final Set<String> verifiedSemanticKeys = new HashSet<>();
        final Set<String> quarantinedSemanticKeys = new HashSet<>();
        String lastSemanticKey;
        String preparedSemanticKey;

        private ModelTemplate(PartTemplate root) {
            this.root = root;
            this.maxVertices = root.maxVertices;
            this.emf = root.emf;
        }

        static ModelTemplate create(ModelPart root) {
            PartTemplate part = PartTemplate.create(root);
            return part != null ? new ModelTemplate(part) : null;
        }

        boolean matches(ModelPart root) {
            return this.root.matches(root);
        }
    }

    private static final class PartTemplate {
        final ModelPart part;
        final CubeTemplate[] cubes;
        final PartTemplate[] children;
        final int maxVertices;
        final boolean emf;
        boolean skipPreparedSubtree;
        String preparedUnsupportedReason;

        private PartTemplate(ModelPart part, CubeTemplate[] cubes, PartTemplate[] children) {
            this.part = part;
            this.cubes = cubes;
            this.children = children;
            int vertices = 0;
            for (CubeTemplate cube : cubes) {
                vertices = Math.addExact(vertices, Math.multiplyExact(cube.faceCount, 4));
            }
            for (PartTemplate child : children) {
                vertices = Math.addExact(vertices, child.maxVertices);
            }
            this.maxVertices = vertices;
            boolean containsEmf = RtEmfCompatibility.isEmfPart(part);
            for (CubeTemplate cube : cubes) {
                containsEmf |= cube.emf;
            }
            for (PartTemplate child : children) {
                containsEmf |= child.emf;
            }
            this.emf = containsEmf;
        }

        static PartTemplate create(ModelPart part) {
            ModelPartAccessor access = (ModelPartAccessor) (Object) part;
            List<ModelPart.Cube> sourceCubes = access.caustica$cubes();
            CubeTemplate[] cubes = new CubeTemplate[sourceCubes.size()];
            for (int i = 0; i < cubes.length; i++) {
                CubeTemplate cube = CubeTemplate.create(sourceCubes.get(i));
                if (cube == null) {
                    return null;
                }
                cubes[i] = cube;
            }
            Map<String, ModelPart> sourceChildren = access.caustica$children();
            PartTemplate[] children = new PartTemplate[sourceChildren.size()];
            int i = 0;
            for (ModelPart child : sourceChildren.values()) {
                PartTemplate childTemplate = create(child);
                if (childTemplate == null) {
                    return null;
                }
                children[i++] = childTemplate;
            }
            return new PartTemplate(part, cubes, children);
        }

        boolean empty() {
            return cubes.length == 0 && children.length == 0;
        }

        boolean matches(ModelPart candidate) {
            if (part != candidate) {
                return false;
            }
            ModelPartAccessor access = (ModelPartAccessor) (Object) candidate;
            List<ModelPart.Cube> currentCubes = access.caustica$cubes();
            if (currentCubes.size() != cubes.length) {
                return false;
            }
            for (int i = 0; i < cubes.length; i++) {
                if (currentCubes.get(i) != cubes[i].cube) {
                    return false;
                }
            }
            Map<String, ModelPart> currentChildren = access.caustica$children();
            if (currentChildren.size() != children.length) {
                return false;
            }
            int i = 0;
            for (ModelPart child : currentChildren.values()) {
                if (!children[i++].matches(child)) {
                    return false;
                }
            }
            return true;
        }

    }

    private static class CubeTemplate {
        final ModelPart.Cube cube;
        final int faceCount;
        final boolean emf;

        CubeTemplate(ModelPart.Cube cube) {
            this.cube = cube;
            this.faceCount = cube.polygons.length;
            this.emf = RtEmfCompatibility.isEmfCube(cube);
        }

        static CubeTemplate create(ModelPart.Cube cube) {
            if (cube.getClass() != ModelPart.Cube.class && !RtEmfCompatibility.isEmfCube(cube)) {
                return null;
            }
            ModelPart.Polygon[] polygons = cube.polygons;
            float[] sourceX = new float[STANDARD_CORNERS];
            float[] sourceY = new float[STANDARD_CORNERS];
            float[] sourceZ = new float[STANDARD_CORNERS];
            FaceTemplate[] faces = new FaceTemplate[polygons.length];
            int cornerCount = 0;
            boolean overflow = false;
            for (int faceIndex = 0; faceIndex < polygons.length; faceIndex++) {
                ModelPart.Polygon polygon = polygons[faceIndex];
                if (polygon == null || polygon.normal() == null || polygon.vertices().length != 4) {
                    return null;
                }
                int[] corners = new int[4];
                float[] u = new float[4];
                float[] v = new float[4];
                for (int i = 0; i < 4; i++) {
                    ModelPart.Vertex vertex = polygon.vertices()[i];
                    if (vertex == null) {
                        return null;
                    }
                    int corner = findCorner(sourceX, sourceY, sourceZ, cornerCount,
                            vertex.x(), vertex.y(), vertex.z());
                    if (corner < 0) {
                        if (cornerCount == STANDARD_CORNERS) {
                            overflow = true;
                        } else {
                            corner = cornerCount++;
                            sourceX[corner] = vertex.x();
                            sourceY[corner] = vertex.y();
                            sourceZ[corner] = vertex.z();
                        }
                    }
                    corners[i] = corner;
                    u[i] = vertex.u();
                    v[i] = vertex.v();
                }
                Vector3fc normal = polygon.normal();
                faces[faceIndex] = new FaceTemplate(corners, u, v, normal.x(), normal.y(), normal.z());
            }
            if (overflow || cornerCount != STANDARD_CORNERS) {
                return new CubeTemplate(cube);
            }
            for (int i = 0; i < STANDARD_CORNERS; i++) {
                sourceX[i] /= ModelPart.Vertex.SCALE_FACTOR;
                sourceY[i] /= ModelPart.Vertex.SCALE_FACTOR;
                sourceZ[i] /= ModelPart.Vertex.SCALE_FACTOR;
            }
            return new EightCornerCube(cube, sourceX, sourceY, sourceZ, faces);
        }

        private static int findCorner(float[] x, float[] y, float[] z, int count,
                                      float px, float py, float pz) {
            int xb = Float.floatToRawIntBits(px);
            int yb = Float.floatToRawIntBits(py);
            int zb = Float.floatToRawIntBits(pz);
            for (int i = 0; i < count; i++) {
                if (Float.floatToRawIntBits(x[i]) == xb
                        && Float.floatToRawIntBits(y[i]) == yb
                        && Float.floatToRawIntBits(z[i]) == zb) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static final class EightCornerCube extends CubeTemplate {
        final float[] x;
        final float[] y;
        final float[] z;
        final FaceTemplate[] faces;

        EightCornerCube(ModelPart.Cube cube, float[] x, float[] y, float[] z, FaceTemplate[] faces) {
            super(cube);
            this.x = Arrays.copyOf(x, STANDARD_CORNERS);
            this.y = Arrays.copyOf(y, STANDARD_CORNERS);
            this.z = Arrays.copyOf(z, STANDARD_CORNERS);
            this.faces = faces;
        }
    }

    private record FaceTemplate(int[] corners, float[] u, float[] v, float nx, float ny, float nz) {
    }
}
