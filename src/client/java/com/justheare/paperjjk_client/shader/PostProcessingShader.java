package com.justheare.paperjjk_client.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Manages a single post-processing shader program.
 */
public class PostProcessingShader {
    private int programId = -1;
    private int vertexShaderId = -1;
    private int fragmentShaderId = -1;

    private final String vertexSource;
    private final String fragmentSource;

    public PostProcessingShader(String vertexSource, String fragmentSource) {
        this.vertexSource = vertexSource;
        this.fragmentSource = fragmentSource;
    }

    /**
     * Load inline shaders (simplified for now)
     */
    public static PostProcessingShader loadInline() {
        String vertexSource = "#version 330 core\n" +
            "in vec2 in_position;\n" +
            "out vec2 texCoord;\n" +
            "void main() {\n" +
            "    gl_Position = vec4(in_position, 0.0, 1.0);\n" +
            "    texCoord = in_position * 0.5 + 0.5;\n" +
            "}\n";

        String fragmentSource = "#version 330 core\n" +
            "uniform sampler2D textureSampler;\n" +
            "in vec2 texCoord;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = texture(textureSampler, texCoord);\n" +
            "}\n";

        return new PostProcessingShader(vertexSource, fragmentSource);
    }

    /**
     * Compile and link the shader program
     */
    public void compile() {
        if (programId != -1) {
            return; // Already compiled
        }

        RenderSystem.assertOnRenderThread();

        // Compile vertex shader
        vertexShaderId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertexShaderId, vertexSource);
        GL20.glCompileShader(vertexShaderId);

        if (GL20.glGetShaderi(vertexShaderId, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(vertexShaderId);
            throw new RuntimeException("Failed to compile vertex shader:\n" + log);
        }

        // Compile fragment shader
        fragmentShaderId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShaderId, fragmentSource);
        GL20.glCompileShader(fragmentShaderId);

        if (GL20.glGetShaderi(fragmentShaderId, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(fragmentShaderId);
            throw new RuntimeException("Failed to compile fragment shader:\n" + log);
        }

        // Link program
        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vertexShaderId);
        GL20.glAttachShader(programId, fragmentShaderId);
        GL20.glLinkProgram(programId);

        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(programId);
            throw new RuntimeException("Failed to link shader program:\n" + log);
        }
    }

    /**
     * Clean up shader resources
     */
    public void delete() {
        RenderSystem.assertOnRenderThread();
        if (programId != -1) {
            GL20.glDeleteProgram(programId);
            programId = -1;
        }
        if (vertexShaderId != -1) {
            GL20.glDeleteShader(vertexShaderId);
            vertexShaderId = -1;
        }
        if (fragmentShaderId != -1) {
            GL20.glDeleteShader(fragmentShaderId);
            fragmentShaderId = -1;
        }
    }

    public boolean isCompiled() {
        return programId != -1;
    }
}
