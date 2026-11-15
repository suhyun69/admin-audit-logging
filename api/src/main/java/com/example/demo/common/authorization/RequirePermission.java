package com.example.demo.common.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
  String resource();

  String action();

  String resourceId() default "";

  String groupId() default "";

  PermissionMode mode() default PermissionMode.RESOURCE;
}
