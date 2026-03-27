package foundry.veil.api.client.render.shader.program;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.ShaderFeature;
import foundry.veil.api.client.render.shader.ShaderManager;
import foundry.veil.api.client.render.shader.compiler.CompiledShader;
import foundry.veil.api.client.render.shader.uniform.ShaderUniform;
import foundry.veil.impl.client.render.shader.program.ShaderProgramImpl;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.lwjgl.system.NativeResource;

import java.util.Set;
import net.minecraft.class_280;
import net.minecraft.class_284;
import net.minecraft.class_293;
import net.minecraft.class_2960;
import net.minecraft.class_5944;

import static org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL31C.GL_INVALID_INDEX;
import static org.lwjgl.opengl.GL31C.glUniformBlockBinding;
import static org.lwjgl.opengl.GL41C.*;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43C.glShaderStorageBlockBinding;

/**
 * Represents a usable shader program with shaders attached.
 *
 * @author Ocelot
 */
@ApiStatus.NonExtendable
public interface ShaderProgram extends NativeResource, MutableUniformAccess, TextureUniformAccess {

    /**
     * Binds this program for use.
     */
    default void bind() {
        int program = this.getProgram();
        if (class_5944.field_29486 != program) {
            class_5944.field_29486 = program;
            GlStateManager._glUseProgram(program);
        }
        class_280.field_1505 = -1;
    }

    /**
     * Unbinds the currently bound shader program.
     */
    static void unbind() {
        VeilRenderSystem.clearShaderBlocks();
        GlStateManager._glUseProgram(0);
        class_5944.field_29486 = -1;
        class_280.field_1505 = -1;
        VeilRenderSystem.unbindSamplers(0, VeilRenderSystem.maxCombinedTextureUnits());
        ShaderProgramImpl.restoreBlendState();
    }

    /**
     * Sets the default uniforms in this shader.
     *
     * @param mode The expected draw mode
     */
    default void setDefaultUniforms(class_293.class_5596 mode) {
        this.setDefaultUniforms(mode, RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix());
    }

    /**
     * Sets the default uniforms in this shader.
     *
     * @param mode             The expected draw mode
     * @param modelViewMatrix  The view matrix transform
     * @param projectionMatrix The projection matrix transform
     */
    void setDefaultUniforms(class_293.class_5596 mode, Matrix4fc modelViewMatrix, Matrix4fc projectionMatrix);

    /**
     * @return The OpenGL id of this program
     */
    int getProgram();

    /**
     * @return The active buffers in the compiled program
     * @since 2.3.0
     */
    int getActiveDynamicBuffers();

    @Override
    ShaderUniform getUniform(CharSequence name);

    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "3.0.0")
    @Override
    ShaderUniform getOrCreateUniform(CharSequence name);

    @Override
    default void setUniformBlock(CharSequence name, int binding) {
        int index = this.getUniformBlock(name);
        if (index != GL_INVALID_INDEX) {
            glUniformBlockBinding(this.getProgram(), index, binding);
        }
    }

    @Override
    default void setStorageBlock(CharSequence name, int binding) {
        int index = this.getStorageBlock(name);
        if (index != GL_INVALID_INDEX) {
            glShaderStorageBlockBinding(this.getProgram(), index, binding);
        }
    }

    /**
     * @return The definition used to compile the latest version of this shader
     */
    @Nullable
    ProgramDefinition getDefinition();

    /**
     * @return The shaders attached to this program
     */
    Int2ObjectMap<CompiledShader> getShaders();

    /**
     * @return Whether this program has a valid compiled shader
     * @since 2.0.0
     */
    boolean isValid();

    /**
     * @return Whether this program has the vertex stage
     */
    default boolean hasVertex() {
        return this.getShaders().containsKey(GL_VERTEX_SHADER);
    }

    /**
     * @return Whether this program has the geometry stage
     */
    default boolean hasGeometry() {
        return this.getShaders().containsKey(GL_GEOMETRY_SHADER);
    }

    /**
     * @return Whether this program has the fragment stage
     */
    default boolean hasFragment() {
        return this.getShaders().containsKey(GL_VERTEX_SHADER);
    }

    /**
     * @return Whether this program has the tesselation stages
     */
    default boolean hasTesselation() {
        Int2ObjectMap<CompiledShader> shaders = this.getShaders();
        return shaders.containsKey(GL_TESS_CONTROL_SHADER) && shaders.containsKey(GL_TESS_EVALUATION_SHADER);
    }

    /**
     * @return Whether this program has the compute stage
     */
    default boolean isCompute() {
        return this.getShaders().containsKey(GL_COMPUTE_SHADER);
    }

    /**
     * @return The features this program needs to function
     * @since 2.0.0
     */
    Set<ShaderFeature> getRequiredFeatures();

    /**
     * @return A guess at the best vertex format for this program
     */
    @Nullable
    class_293 getFormat();

    /**
     * @return All shader definitions this program depends on
     */
    Set<String> getDefinitionDependencies();

    /**
     * @return The name of this program
     */
    class_2960 getName();

    /**
     * <p>Wraps this shader with a vanilla Minecraft shader instance wrapper</p>
     * @return A lazily loaded shader instance wrapper for this program
     * @deprecated Use {@link VeilRenderBridge#toShaderInstance(ShaderProgram)}
     */
    @Deprecated
    class_5944 toShaderInstance();
}
