package com.ray.shared.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.annotations.Operation;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** 不启动数据库或 Redis 即可执行的 Controller 路径与 operationId 静态契约。 */
class ControllerMappingContractTest {
    @Test
    void controllerMappingsMatchRuntimeExpectationAndOperationIdsAreUnique() throws Exception {
        Set<String> operations = new HashSet<>();
        Set<String> operationIds = new HashSet<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var bean : scanner.findCandidateComponents("com.ray.controller")) {
            Class<?> type = Class.forName(bean.getBeanClassName());
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            Set<String> prefixes = paths(classMapping);
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                Operation operation = AnnotatedElementUtils.findMergedAnnotation(method, Operation.class);
                assertNotNull(operation, type.getSimpleName() + "." + method.getName() + " 缺少 @Operation");
                assertTrue(!operation.operationId().isBlank(), type.getSimpleName() + "." + method.getName() + " 缺少 operationId");
                assertTrue(operationIds.add(operation.operationId()), "operationId 重复: " + operation.operationId());
                for (String prefix : prefixes) for (String path : paths(mapping)) for (RequestMethod verb : mapping.method()) {
                    operations.add(verb.name() + " " + normalize(prefix, path));
                }
            }
        }
        assertEquals(OpenApiAndAuthRuntimeTest.expectedOperations(), operations);
        assertEquals(operations.size(), operationIds.size());
    }

    private Set<String> paths(RequestMapping mapping) {
        if (mapping == null || mapping.path().length == 0 && mapping.value().length == 0) return Set.of("");
        String[] values = mapping.path().length == 0 ? mapping.value() : mapping.path();
        return Set.of(values);
    }

    private String normalize(String prefix, String path) {
        String combined = (prefix + "/" + path).replaceAll("/{2,}", "/");
        return combined.length() > 1 && combined.endsWith("/") ? combined.substring(0, combined.length() - 1) : combined;
    }
}
