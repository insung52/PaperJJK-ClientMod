package com.justheare.paperjjk_client.shader;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Generates post-effect JSON with dynamic uniform values
 */
public class DynamicJsonGenerator {

    public static JsonObject createRefractionJson(float centerX, float centerY, float radius, float strength) {
        JsonObject root = new JsonObject();

        // Targets
        JsonObject targets = new JsonObject();
        targets.add("swap", new JsonObject());
        root.add("targets", targets);

        // Passes
        JsonArray passes = new JsonArray();

        // Pass 1: Refraction
        JsonObject pass1 = new JsonObject();
        pass1.addProperty("vertex_shader", "paperjjk_client:core/blit");
        pass1.addProperty("fragment_shader", "paperjjk_client:post/refraction");

        // Inputs
        JsonArray inputs1 = new JsonArray();
        JsonObject input1 = new JsonObject();
        input1.addProperty("sampler_name", "In");
        input1.addProperty("target", "minecraft:main");
        inputs1.add(input1);
        pass1.add("inputs", inputs1);

        pass1.addProperty("output", "swap");

        // Uniforms with dynamic values
        JsonObject uniforms = new JsonObject();
        JsonArray refractionConfig = new JsonArray();

        // EffectCenter
        JsonObject centerUniform = new JsonObject();
        centerUniform.addProperty("name", "EffectCenter");
        centerUniform.addProperty("type", "vec2");
        JsonArray centerValue = new JsonArray();
        centerValue.add(centerX);
        centerValue.add(centerY);
        centerUniform.add("value", centerValue);
        refractionConfig.add(centerUniform);

        // EffectRadius
        JsonObject radiusUniform = new JsonObject();
        radiusUniform.addProperty("name", "EffectRadius");
        radiusUniform.addProperty("type", "float");
        radiusUniform.addProperty("value", radius);
        refractionConfig.add(radiusUniform);

        // EffectStrength
        JsonObject strengthUniform = new JsonObject();
        strengthUniform.addProperty("name", "EffectStrength");
        strengthUniform.addProperty("type", "float");
        strengthUniform.addProperty("value", strength);
        refractionConfig.add(strengthUniform);

        uniforms.add("RefractionConfig", refractionConfig);
        pass1.add("uniforms", uniforms);

        passes.add(pass1);

        // Pass 2: Blit
        JsonObject pass2 = new JsonObject();
        pass2.addProperty("vertex_shader", "paperjjk_client:core/blit");
        pass2.addProperty("fragment_shader", "paperjjk_client:post/blit");

        JsonArray inputs2 = new JsonArray();
        JsonObject input2 = new JsonObject();
        input2.addProperty("sampler_name", "In");
        input2.addProperty("target", "swap");
        inputs2.add(input2);
        pass2.add("inputs", inputs2);

        pass2.addProperty("output", "minecraft:main");

        passes.add(pass2);

        root.add("passes", passes);

        return root;
    }
}
