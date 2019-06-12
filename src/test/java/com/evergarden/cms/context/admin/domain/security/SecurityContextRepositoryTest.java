package com.evergarden.cms.context.admin.domain.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

import java.lang.reflect.Executable;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class SecurityContextRepositoryTest {

    @Autowired
    JwtHelper jwtHelper;

    @Autowired
    Logger logger;

    @Test
    void save() {
        EvergardenAuthenticationManager manager = new EvergardenAuthenticationManager(jwtHelper, logger);
        SecurityContextRepository       context = new SecurityContextRepository(manager);

        ServerWebExchange mockServer  = mock(ServerWebExchange.class);
        SecurityContext   mockContext = mock(SecurityContext.class);

        assertThrows(UnsupportedOperationException.class, () -> context.save(mockServer, mockContext));
    }

    @Test
    void load() {

        EvergardenAuthenticationManager   manager = new EvergardenAuthenticationManager(jwtHelper, logger);
        SecurityContextRepository         context = new SecurityContextRepository(manager);
        ArrayList<SimpleGrantedAuthority> roles   = new ArrayList<>();
        roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

        String token = jwtHelper.generateToken("b@mail.com", roles).getToken();

        ServerHttpRequest mockRequest = mock(ServerHttpRequest.class);
        HttpHeaders       headers     = mock(HttpHeaders.class);
        ServerWebExchange mockServer  = mock(ServerWebExchange.class);

        when(mockServer.getRequest()).thenReturn(mockRequest);
        when(mockRequest.getHeaders()).thenReturn(headers);
        when(headers.getFirst(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);

        Authentication      userToken = new UsernamePasswordAuthenticationToken("b@mail.com", token, roles);
        SecurityContextImpl sci       = new SecurityContextImpl(userToken);

        StepVerifier.create(context.load(mockServer))
            .expectNextMatches(securityContext -> {
                assertEquals(sci, securityContext);
                return true;
            })
            .verifyComplete();

        when(headers.getFirst(HttpHeaders.AUTHORIZATION)).thenReturn(token);
        StepVerifier.create(context.load(mockServer))
            .verifyComplete();

    }
}