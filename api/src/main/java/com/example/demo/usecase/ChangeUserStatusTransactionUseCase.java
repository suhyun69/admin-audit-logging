package com.example.demo.usecase;
import com.example.demo.usecase.dto.*;
public interface ChangeUserStatusTransactionUseCase { StatusChangeAppResponse change(long id,ChangeUserStatusAppRequest request); }
