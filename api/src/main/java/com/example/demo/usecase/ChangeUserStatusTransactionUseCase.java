package com.example.demo.usecase;

import com.example.demo.usecase.dto.ChangeUserStatusAppRequest;
import com.example.demo.usecase.dto.StatusChangeAppResponse;

public interface ChangeUserStatusTransactionUseCase {
  StatusChangeAppResponse change(long id, ChangeUserStatusAppRequest request);
}
