package com.mcp.db.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.db.tool.DatabaseTools;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * MCP 工具注册配置 —— 显式扫描 DatabaseTools 中的 @Tool 方法并注册为 MCP 工具。
 * <p>
 * MCP Server 自动检测 ToolCallbackProvider Bean 并注册其中包含的全部工具。
 */
@Configuration
public class McpToolConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DatabaseTools databaseTools;

    public McpToolConfig(DatabaseTools databaseTools) {
        this.databaseTools = databaseTools;
    }

    @Bean
    public ToolCallbackProvider toolCallbackProvider() {
        List<ToolCallback> callbacks = new ArrayList<>();

        for (Method method : DatabaseTools.class.getDeclaredMethods()) {
            org.springframework.ai.tool.annotation.Tool toolAnn =
                    method.getAnnotation(org.springframework.ai.tool.annotation.Tool.class);
            if (toolAnn == null) continue;

            ToolDefinition definition = buildToolDefinition(method, toolAnn);
            callbacks.add(new ToolMethodCallback(databaseTools, method, definition));
        }

        return () -> callbacks.toArray(new ToolCallback[0]);
    }

    /**
     * 构建完整的 JSON Schema 工具定义
     */
    private ToolDefinition buildToolDefinition(Method method,
                                                org.springframework.ai.tool.annotation.Tool toolAnn) {
        Parameter[] params = method.getParameters();
        StringBuilder propsJson = new StringBuilder();
        List<String> requiredList = new ArrayList<>();

        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            org.springframework.ai.tool.annotation.ToolParam paramAnn =
                    param.getAnnotation(org.springframework.ai.tool.annotation.ToolParam.class);

            String paramName = param.getName();
            String paramDesc = paramAnn != null ? paramAnn.description() : paramName;
            boolean required = paramAnn == null || paramAnn.required();
            String schemaType = toJsonSchemaType(param.getType());

            if (i > 0) propsJson.append(",\n");
            propsJson.append(String.format(
                    "            \"%s\": {\"type\": \"%s\", \"description\": \"%s\"}",
                    paramName, schemaType, paramDesc));

            if (required) requiredList.add("\"" + paramName + "\"");
        }

        String inputSchema = String.format("""
                {
                    "type": "object",
                    "properties": {
                %s
                    },
                    "required": [%s]
                }""", propsJson, String.join(", ", requiredList));

        return ToolDefinition.builder()
                .name(method.getName())
                .description(toolAnn.description())
                .inputSchema(inputSchema)
                .build();
    }

    private String toJsonSchemaType(Class<?> clazz) {
        if (clazz == String.class) return "string";
        if (clazz == int.class || clazz == Integer.class) return "integer";
        if (clazz == long.class || clazz == Long.class) return "integer";
        if (clazz == boolean.class || clazz == Boolean.class) return "boolean";
        if (clazz == double.class || clazz == Double.class) return "number";
        return "string";
    }

    /**
     * 工具回调：将 MCP 传入的 JSON 参数解析后反射调用 DatabaseTools 方法
     */
    private static class ToolMethodCallback implements ToolCallback {

        private final Object target;
        private final Method method;
        private final ToolDefinition toolDefinition;

        ToolMethodCallback(Object target, Method method, ToolDefinition toolDefinition) {
            this.target = target;
            this.method = method;
            this.toolDefinition = toolDefinition;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public String call(String toolInput) {
            try {
                JsonNode root = MAPPER.readTree(toolInput);
                Parameter[] params = method.getParameters();
                Object[] args = new Object[params.length];

                for (int i = 0; i < params.length; i++) {
                    String paramName = params[i].getName();
                    JsonNode valueNode = root.get(paramName);
                    if (valueNode == null || valueNode.isNull()) {
                        args[i] = getDefaultValue(params[i].getType());
                    } else {
                        args[i] = MAPPER.treeToValue(valueNode, params[i].getType());
                    }
                }

                Object result = method.invoke(target, args);
                return MAPPER.writeValueAsString(result);
            } catch (Exception e) {
                throw new RuntimeException("工具 [" + toolDefinition.name() + "] 执行失败: " + e.getMessage(), e);
            }
        }

        private static Object getDefaultValue(Class<?> type) {
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == boolean.class) return false;
            if (type == double.class) return 0.0;
            return null;
        }
    }
}
