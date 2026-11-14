package com.example.demo.usecase.support.audit;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited { Operation value(); }
