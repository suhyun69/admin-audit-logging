package com.example.demo.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import java.io.IOException;

/** Public managed boundary keeps filter-chain failures and security rejections observable by AOP. */
@Component
public class HttpAuditExecution {
    public void execute(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        chain.doFilter(request,response);
    }
}
