package com.example.demo.security;

import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.web.ApiErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Bounds raw bytes before Jackson parsing, including unknown-length/chunked request bodies. */
public class ClientPayloadLimitFilter extends OncePerRequestFilter {
    private static final int MAX_BYTES=8192;
    private final ObjectMapper mapper;
    public ClientPayloadLimitFilter(ObjectMapper mapper) { this.mapper=mapper; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getServletPath();
        if(path==null||path.isEmpty())path=request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod())||!"/api/v1/audit-events/client".equals(path);
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        if(request.getContentLengthLong()>MAX_BYTES) { ApiErrors.write(response,mapper,ErrorCode.PAYLOAD_TOO_LARGE);return; }
        byte[] bytes=request.getInputStream().readNBytes(MAX_BYTES+1);
        if(bytes.length>MAX_BYTES) { ApiErrors.write(response,mapper,ErrorCode.PAYLOAD_TOO_LARGE);return; }
        chain.doFilter(new CachedRequest(request,bytes),response);
    }
    private static final class CachedRequest extends HttpServletRequestWrapper {
        private final byte[] bytes;
        CachedRequest(HttpServletRequest request,byte[] bytes) { super(request);this.bytes=bytes; }
        @Override public int getContentLength() { return bytes.length; }
        @Override public long getContentLengthLong() { return bytes.length; }
        @Override public ServletInputStream getInputStream() {
            var input=new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] target,int offset,int length) { return input.read(target,offset,length); }
                @Override public boolean isFinished() { return input.available()==0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous request body"); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8)); }
    }
}
