package com.example.demo.iam;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.common.authorization.RequirePermission;
import com.example.demo.controller.IamController;
import com.example.demo.controller.UserController;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

class IamAnnotationTest {
  @Test
  void everyProtectedBusinessAndManagementEndpointDeclaresPermission() {
    for (var type : java.util.List.of(IamController.class, UserController.class))
      for (var method : type.getDeclaredMethods()) {
        boolean endpoint =
            Arrays.stream(method.getAnnotations())
                .anyMatch(a -> a.annotationType().isAnnotationPresent(RequestMapping.class));
        if (endpoint && !(type == IamController.class && method.getName().equals("me")))
          assertThat(method.isAnnotationPresent(RequirePermission.class))
              .as(type.getSimpleName() + "." + method.getName())
              .isTrue();
      }
  }
}
