package com.example.demo.security;

import com.example.demo.common.authorization.AuthorizationService;
import com.example.demo.common.authorization.PermissionMode;
import com.example.demo.common.authorization.RequirePermission;
import com.example.demo.common.request.RequestContext;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;

/** Boundary guard. SCOPE requires the service/repository to apply AccessScope to its SQL. */
@Aspect
@Component
@Order(30)
public class RequirePermissionAspect {
  private final AuthorizationService auth;
  private final SpelExpressionParser parser = new SpelExpressionParser();

  public RequirePermissionAspect(AuthorizationService auth) {
    this.auth = auth;
  }

  @Around("@annotation(permission)")
  public Object check(ProceedingJoinPoint point, RequirePermission permission) throws Throwable {
    long actor =
        RequestContext.current()
            .map(RequestContext::actor)
            .map(a -> a.id())
            .orElseThrow(
                () -> new AuthenticationCredentialsNotFoundException("Authentication required"));
    Method method =
        AopUtils.getMostSpecificMethod(
            ((MethodSignature) point.getSignature()).getMethod(), point.getTarget().getClass());
    var context = new StandardEvaluationContext();
    var names = new DefaultParameterNameDiscoverer().getParameterNames(method);
    var args = point.getArgs();
    for (int i = 0; i < args.length; i++) {
      context.setVariable("p" + i, args[i]);
      context.setVariable("a" + i, args[i]);
      if (names != null) context.setVariable(names[i], args[i]);
    }
    if (permission.mode() == PermissionMode.SCOPE) {
      auth.getScope(actor, permission.resource(), permission.action());
      return point.proceed();
    }
    Long id = evaluate(permission.resourceId(), context),
        group = evaluate(permission.groupId(), context);
    if (id != null && id <= 0)
      throw new IllegalArgumentException("Positive resource identifier required");
    boolean allowed =
        id != null
            ? auth.can(actor, permission.resource(), permission.action(), id)
            : group != null
                && auth.canInGroup(actor, permission.resource(), permission.action(), group);
    if (id != null && group != null)
      allowed = allowed && auth.isResourceInGroup(permission.resource(), id, group);
    if (!allowed) throw new AccessDeniedException("Permission denied");
    return point.proceed();
  }

  private Long evaluate(String expression, StandardEvaluationContext context) {
    if (expression.isBlank()) return null;
    try {
      return parser.parseExpression(expression).getValue(context, Long.class);
    } catch (RuntimeException e) {
      throw new AccessDeniedException("Invalid permission context");
    }
  }
}
