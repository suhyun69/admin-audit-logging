package com.example.demo.usecase.support.audit;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.usecase.dto.AuditChangeAppRequest;
import com.example.demo.usecase.dto.AuditDetailsPageAppResponse;
import com.example.demo.usecase.dto.AuditHttpAppRequest;
import com.example.demo.usecase.dto.AuditPageAppResponse;
import com.example.demo.usecase.dto.AuditSummaryAppResponse;
import com.example.demo.usecase.dto.AuditWriteAppRequest;
import com.example.demo.usecase.dto.ChangeUserStatusAppRequest;
import com.example.demo.usecase.dto.NormalizedAuditSearchAppRequest;
import com.example.demo.usecase.dto.NormalizedInteractionAppRequest;
import com.example.demo.usecase.dto.StatusChangeAppResponse;
import com.example.demo.usecase.dto.UserSearchAppRequest;
import com.example.demo.usecase.dto.UsersPageAppResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Maps only explicit audit facts; never serializes method arguments or responses. */
@Component
public class BusinessAuditEvents {
  public AuditWriteAppRequest event(
      Operation operation, Object[] args, Object response, RuntimeException failure) {
    boolean success = failure == null;
    String action = operation.name(), resourceType = "AUDIT_LOG", resourceId = null;
    var metadata = new LinkedHashMap<String, Object>();
    List<AuditChangeAppRequest> changes = List.of();
    switch (operation) {
      case USER_LIST -> {
        resourceType = "USER";
        var request = (UserSearchAppRequest) args[0];
        metadata.put("searched", !request.q().isEmpty());
        metadata.put("size", request.size());
        if (request.status() != null) metadata.put("status", request.status());
        if (success)
          metadata.put("returnedCount", ((UsersPageAppResponse) response).items().size());
      }
      case USER_DETAIL -> {
        resourceType = "USER";
        resourceId = args[0].toString();
      }
      case USER_STATUS_CHANGE -> {
        resourceType = "USER";
        resourceId = args[0].toString();
        if (success) {
          var result = (StatusChangeAppResponse) response;
          if (result.previousStatus().equals(result.currentStatus())) return null;
          metadata.put("status", result.currentStatus());
          changes =
              List.of(
                  new AuditChangeAppRequest(
                      "status", result.previousStatus(), result.currentStatus()));
        } else metadata.put("status", ((ChangeUserStatusAppRequest) args[1]).status());
      }
      case AUDIT_SEARCH -> {
        var request = (NormalizedAuditSearchAppRequest) args[0];
        action = request.action();
        metadata.put("searched", true);
        metadata.put("size", request.size());
        if (success)
          metadata.put("returnedCount", ((AuditPageAppResponse) response).items().size());
      }
      case AUDIT_DETAIL -> resourceId = args[0].toString();
      case AUDIT_TIMELINE -> {
        var request = (NormalizedInteractionAppRequest) args[0];
        resourceType = "INTERACTION";
        resourceId = request.id().toString();
        if (success) {
          metadata.put("size", request.size());
          metadata.put("returnedCount", ((AuditDetailsPageAppResponse) response).items().size());
        }
      }
      case AUDIT_SUMMARY -> {
        if (success)
          metadata.put("returnedCount", ((AuditSummaryAppResponse) response).recentEvents().size());
      }
      default -> throw new IllegalArgumentException("Not a business audit operation: " + operation);
    }
    var event =
        AuditWriteAppRequest.event(
                operation == Operation.USER_STATUS_CHANGE ? "DATA_UPDATE" : "DATA_READ",
                action,
                "BUSINESS",
                success ? "SUCCESS" : "FAILURE")
            .resource(resourceType, resourceId, metadata, changes);
    if (!success
        && Set.of(
                Operation.USER_DETAIL,
                Operation.USER_STATUS_CHANGE,
                Operation.AUDIT_DETAIL,
                Operation.AUDIT_TIMELINE)
            .contains(operation)) {
      var code =
          failure instanceof ApiException api ? api.getErrorCode() : ErrorCode.INTERNAL_ERROR;
      int status =
          code == ErrorCode.USER_NOT_FOUND || code == ErrorCode.AUDIT_NOT_FOUND
              ? 404
              : code == ErrorCode.VERSION_CONFLICT ? 409 : 500;
      event =
          event.http(new AuditHttpAppRequest(null, null, status, null, null, null, code.name()));
    }
    return event;
  }
}
