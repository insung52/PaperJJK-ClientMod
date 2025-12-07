package com.justheare.paperjjk_client.util;

import net.minecraft.client.render.GameRenderer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Utility to inspect PostEffectProcessor via Reflection
 */
public class PostProcessorReflector {

    /**
     * Inspect GameRenderer for post-processor related fields
     */
    public static void inspectGameRenderer(GameRenderer renderer) {
        System.out.println("[PostProcessorReflector] === Inspecting GameRenderer ===");

        // Find fields
        Field[] fields = renderer.getClass().getDeclaredFields();
        for (Field field : fields) {
            String name = field.getName().toLowerCase();
            if (name.contains("post") || name.contains("effect") || name.contains("processor")) {
                System.out.println("[PostProcessorReflector] Field: " + field.getName() + " | Type: " + field.getType().getName());
            }
        }
    }

    /**
     * Try to get the PostEffectProcessor instance from GameRenderer
     */
    public static Object getPostProcessor(GameRenderer renderer) {
        try {
            Field[] fields = renderer.getClass().getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(renderer);

                if (value != null && value.getClass().getName().contains("PostEffectProcessor")) {
                    System.out.println("[PostProcessorReflector] Found PostEffectProcessor: " + field.getName());
                    return value;
                }
            }
        } catch (Exception e) {
            System.err.println("[PostProcessorReflector] Error getting PostEffectProcessor:");
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Inspect PostEffectProcessor methods
     */
    public static void inspectPostProcessor(Object postProcessor) {
        if (postProcessor == null) {
            System.out.println("[PostProcessorReflector] PostEffectProcessor is null");
            return;
        }

        System.out.println("[PostProcessorReflector] === Inspecting PostEffectProcessor ===");
        System.out.println("[PostProcessorReflector] Class: " + postProcessor.getClass().getName());

        // Find methods
        Method[] methods = postProcessor.getClass().getDeclaredMethods();
        for (Method method : methods) {
            String name = method.getName();
            if (name.contains("uniform") || name.contains("Uniform") ||
                name.contains("set") || name.contains("update")) {
                System.out.println("[PostProcessorReflector] Method: " + method.toString());
            }
        }

        // Find fields
        Field[] fields = postProcessor.getClass().getDeclaredFields();
        for (Field field : fields) {
            String name = field.getName().toLowerCase();
            if (name.contains("uniform") || name.contains("pass") || name.contains("shader")) {
                System.out.println("[PostProcessorReflector] Field: " + field.getName() + " | Type: " + field.getType().getName());
            }
        }
    }
}
