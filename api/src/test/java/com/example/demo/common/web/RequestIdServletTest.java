package com.example.demo.common.web;

import com.example.demo.support.PostgresTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.*;
import java.net.http.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class RequestIdServletTest extends PostgresTestSupport {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;

    @Test
    void actualServletErrorsHaveOneRequestIdMatchingBodyAndPreserveAllow() throws Exception {
        try(var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build()) {
            verify(send(client,"GET","/auth/me",null,null),401);
            var csrf=send(client,"GET","/auth/csrf",null,null);
            String token=mapper.readTree(csrf.body()).get("token").asText();
            verify(send(client,"POST","/auth/login","{\"username\":\"admin\",\"password\":\"wrong-password\"}",token),401);
            assertThat(send(client,"POST","/auth/login","{\"username\":\"admin\",\"password\":\"local-test-password\"}",token).statusCode()).isEqualTo(200);
            token=mapper.readTree(send(client,"GET","/auth/csrf",null,null).body()).get("token").asText();
            verify(send(client,"GET","/users/9223372036854775807",null,null),404);
            verify(send(client,"PATCH","/users/123/status","{\"status\":\"SUSPENDED\",\"version\":9223372036854775807}",token),409);
            var method=send(client,"PUT","/users/123/status","{}",token);
            verify(method,405);
            assertThat(method.headers().allValues("Allow")).anySatisfy(value->assertThat(value).contains("PATCH"));
        }
    }
    private HttpResponse<String> send(HttpClient client,String method,String path,String body,String csrf) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path));
        if(csrf!=null)request.header("X-CSRF-TOKEN",csrf);
        if(body!=null)request.header("Content-Type","application/json");
        return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private void verify(HttpResponse<String> response,int expected) throws Exception {
        assertThat(response.statusCode()).isEqualTo(expected);
        var values=response.headers().allValues("X-Request-ID");
        assertThat(values).hasSize(1);
        String id=values.getFirst();
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        assertThat(mapper.readTree(response.body()).get("requestId").asText()).isEqualTo(id);
    }
}
