package com.evergarden.cms.context.user.infrastructure.controller;

import com.evergarden.cms.app.config.security.EvergardenEncoder;
import com.evergarden.cms.app.config.security.JwtHelper;
import com.evergarden.cms.app.config.security.JwtRequest;
import com.evergarden.cms.context.publisher.infrastructure.persistence.PostRepository;
import com.evergarden.cms.context.user.application.service.AvatarFolderHelper;
import com.evergarden.cms.context.user.application.service.CRUDUserService;
import com.evergarden.cms.context.user.application.service.GenerateGuestTokenService;
import com.evergarden.cms.context.user.application.service.GenerateTokenService;
import com.evergarden.cms.context.user.application.service.UpdatePasswordService;
import com.evergarden.cms.context.user.domain.entity.Avatar;
import com.evergarden.cms.context.user.domain.entity.Role;
import com.evergarden.cms.context.user.domain.entity.Token;
import com.evergarden.cms.context.user.infrastructure.controller.input.UnSaveUser;
import com.evergarden.cms.context.user.infrastructure.controller.input.UpdatedUser;
import com.evergarden.cms.context.user.infrastructure.controller.output.UserResponse;
import com.evergarden.cms.context.user.infrastructure.persistence.RoleRepository;
import com.evergarden.cms.context.user.infrastructure.persistence.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.cache.Cache;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@WebFluxTest
public class UserRouterTest {

    @Autowired
    private Environment env;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private UserHandler userHandler;

    private RouterFunction router;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PostRepository postRepository;

    @MockBean
    private RoleRepository roleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private EvergardenEncoder encoder;

    @Autowired
    private WebTestClient client;

    @MockBean
    private Cache<String, Token> tokenCache;

    @MockBean
    private JwtHelper jwtHelper;

    @MockBean
    private GenerateTokenService generateTokenService;

    @MockBean
    private GenerateGuestTokenService generateGuestTokenService;

    @MockBean
    CRUDUserService crudUserService;

    @MockBean
    AvatarFolderHelper avatarFolderHelper;

    @MockBean
    UpdatePasswordService updatePasswordService;

    @BeforeEach
    void setUp() {
        // TODO refactor LoginHandler constructor parameter to simplify reduce number argument
        encoder = new EvergardenEncoder(env);
        jwtHelper = new JwtHelper(new JwtRequest(env.getProperty("jwt.secret")), tokenCache);
        userHandler = new UserHandler(crudUserService, avatarFolderHelper, jwtHelper, updatePasswordService);
        router = (new UserRouter()).userRoute(userHandler, env);
        client = WebTestClient.bindToRouterFunction(router).build();
    }


    @Test
    void read() {
        Collection<Role> roles = new ArrayList<>();
        Role             r1    = new Role().setRole("user").setId("1");
        roles.add(r1);

        UserResponse userResponse = UserResponse.builder()
            .email("batou@mail.com")
            .firstname("Batou")
            .lastname("Ranger")
            .pseudo("Batou")
            .id("1")
            .roles(roles)
            .avatarUrl("avatar/uri")
            .activated(true)
            .build();

        BDDMockito.given(crudUserService.readUser("1"))
            .willReturn(Mono.just(userResponse));

        client.get()
            .uri(env.getProperty("v1s") + "/user/{id}", "1")
            .exchange()
            .expectStatus().isOk()
            .expectBody(UserResponse.class)
            .consumeWith(userResponseEntityExchangeResult -> {
                UserResponse userR = userResponseEntityExchangeResult.getResponseBody();
                assertEquals("batou@mail.com", userR.getEmail());
                assertEquals("Batou", userR.getFirstname());
                assertEquals("Ranger", userR.getLastname());
                assertEquals("Batou", userR.getPseudo());
                assertEquals("1", userR.getId());
                assertEquals("avatar/uri", userR.getAvatarUrl());
                assertTrue(userR.isActivated());
                assertNotNull(userR.getRoles());
            });
    }

    @Test
    void create() {
        ArrayList<Role> roles = new ArrayList<>();
        roles.add(new Role().setRole("ROLE_COFFEE_MAKER"));

        encoder.encode("pass");

        UnSaveUser userToSave = UnSaveUser.builder()
            .email("batou@mail.com")
            .firstname("Batou")
            .lastname("Ranger")
            .pseudo("Batou")
            .activated(true)
            .roles(roles)
            .password(encoder.getEncodedCredential().getEncodedPassword())
            .build();

        UserResponse userResponse = UserResponse.builder()
            .email("batou@mail.com")
            .firstname("Batou")
            .lastname("Ranger")
            .pseudo("Batou")
            .id("1")
            .roles(roles)
            .avatarUrl("avatar/uri")
            .activated(true)
            .build();

        BDDMockito.given(crudUserService.createUser(ArgumentMatchers.any(UnSaveUser.class)))
            .willReturn(Mono.just(userResponse));

        client.post()
            .uri(env.getProperty("v1s") + "/user")
            .bodyValue(userToSave)
            .exchange()
            .expectBody(UserResponse.class)
            .consumeWith(userCreateResponseEntityExchangeResult -> {
                UserResponse user = userCreateResponseEntityExchangeResult.getResponseBody();
                assertEquals("batou@mail.com", user.getEmail());
                assertEquals("Batou", user.getFirstname());
                assertEquals("Ranger", user.getLastname());
                assertEquals("Batou", user.getPseudo());
                assertEquals("1", user.getId());
                assertEquals(roles, user.getRoles());
            });
    }

    // TODO some save/update method don't need to return the new modified value
    @Test
    void update() {
        encoder.encode("pass");

        Collection<Role> roles = new ArrayList<>();
        roles.add(new Role("admin").setId("1"));

        // TODO is useful ?
        UpdatedUser updatedUser = UpdatedUser.builder()
            .id("1")
            .activated(true)
            .email("batou@mail.com")
            .firstname("Batou")
            .lastname("Ranger")
            .pseudo("Batou")
            .avatarUrl("avatar/uri")
            .avatar(Avatar.builder().relativeUri("uri").build())
            .roles(roles)
            .build();

        UserResponse userResponse = UserResponse.builder()
            .email("batou@mail.com")
            .firstname("Batou")
            .lastname("Ranger")
            .pseudo("Batou")
            .id("1")
            .roles(roles)
            .avatarUrl("avatar/uri")
            .activated(true)
            .build();

        BDDMockito.given(crudUserService.updateUser(updatedUser))
            .willReturn(Mono.just(userResponse));

        client.put()
            .uri(env.getProperty("v1s") + "/user")
            .bodyValue(updatedUser)
            .exchange()
            .expectStatus().isOk()
            .expectBody(UserResponse.class)
            .consumeWith(userCreateResponseExchangeResult -> {
                UserResponse us = userCreateResponseExchangeResult.getResponseBody();
                assertEquals("batou@mail.com", us.getEmail());
                assertEquals("Batou", us.getFirstname());
                assertEquals("Ranger", us.getLastname());
                assertEquals("Batou", us.getPseudo());
                assertEquals("1", us.getId());
            });
    }

    @Test
    void show() {
        UserResponse u1 = new UserResponse();
        u1.setId("1");
        u1.setEmail("batou@mail.com");
        u1.setFirstname("Batou");
        u1.setLastname("Ranger");
        u1.setPseudo("Batou");
        u1.setActivated(true);
        u1.addRole(new Role("admin").setId("1"));

        UserResponse u2 = new UserResponse();
        u2.setId("2");
        u2.setEmail("denver@mail.com");
        u2.setFirstname("denver");
        u2.setLastname("dino");
        u2.setPseudo("denver");
        u2.setActivated(true);
        u2.addRole(new Role("writer").setId("2"));

        UserResponse u3 = new UserResponse();
        u3.setId("3");
        u3.setEmail("motoko@mail.com");
        u3.setFirstname("motoko");
        u3.setLastname("kisanagi");
        u3.setPseudo("moto");
        u3.setActivated(true);
        u3.addRole(new Role("major").setId("3"));

        BDDMockito.given(crudUserService.showUser())
            .willReturn(Flux.just(u1, u2, u3));

        client.get()
            .uri(env.getProperty("v1s") + "/user")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(UserResponse.class)
            .consumeWith(listEntityExchangeResult -> {
                UserResponse ur1 = listEntityExchangeResult.getResponseBody().get(0);

                assertEquals("batou@mail.com", ur1.getEmail());
                assertEquals("1", ur1.getId());
                assertNotNull(ur1.getRoles());
                assertEquals(1, ur1.getRoles().toArray().length);
                assertEquals("Batou", ur1.getPseudo());
                assertEquals("Ranger", ur1.getLastname());
                assertEquals("Batou", ur1.getFirstname());
            })
            .hasSize(3);
    }
}
