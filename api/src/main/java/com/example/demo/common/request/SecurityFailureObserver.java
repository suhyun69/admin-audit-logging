package com.example.demo.common.request;
import com.example.demo.common.exception.ErrorCode;

/** Common web errors notify observers without depending on any application use case or audit domain. */
public interface SecurityFailureObserver { void failed(ErrorCode code); }
