package com.evergarden.cms.context.admin.infrastructure.controller;

import com.evergarden.cms.context.admin.domain.entity.*;
import com.evergarden.cms.context.admin.domain.security.EvergardenEncoder;
import com.evergarden.cms.context.admin.domain.security.JwtHelper;
import com.evergarden.cms.context.admin.infrastructure.controller.response.UserCreateResponse;
import com.evergarden.cms.context.admin.infrastructure.controller.response.UserResponse;
import com.evergarden.cms.context.admin.infrastructure.persistence.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

@Component
public class LoginHandler {

    private UserRepository userRepository;

    private Logger logger;

    private ObjectMapper objectMapper;

    private EvergardenEncoder encoder;

    private Environment env;

    private JwtHelper jwtHelper;

    @Autowired
    public LoginHandler(UserRepository userRepository, Logger logger, ObjectMapper objectMapper,
                        EvergardenEncoder encoder, Environment env, JwtHelper jwtHelper) {
        this.userRepository = userRepository;
        this.logger = logger;
        this.objectMapper = objectMapper;
        this.encoder = encoder;
        this.env = env;
        this.jwtHelper = jwtHelper;
    }

    public Mono<ServerResponse> login(ServerRequest request) {

        return request.body(BodyExtractors.toMono(UnAuthUser.class))
            .flatMap(unAuthUser -> {

                //logger.warn("befor call find by email"+userRepository.toString());
                return userRepository.findByEmail(unAuthUser.getEmail())
                    .flatMap(user -> {

                        EncodedCredential encodedCredential = new EncodedCredential(user.getSalt(), user.getPassword());

                        EvergardenEncoder encoder = new EvergardenEncoder(env, logger, encodedCredential);

                        boolean isValidPass = encoder.matches(unAuthUser.getPassword(), user.getPassword());
                        logger.info("is valid pass " + isValidPass);
                        Collection<Role> roles = user.getRoles();
                        if (isValidPass) {
                            ArrayList<SimpleGrantedAuthority> authorities = new ArrayList();
                            user.getRoles().stream()
                                .peek(role -> {
                                    authorities.add(new SimpleGrantedAuthority(role.getRoleValue()));
                                })
                                .count();
                            logger.info("try to generate token");
                            Token token = jwtHelper.generateToken(user.getEmail(), authorities);
                            logger.info("token is generated with this value " + token.getToken());

                            return ServerResponse.ok()
                                .body(BodyInserters.fromObject(token));
                        }
                        // TODO generate token if pass is valid and maybe save it in cache

                        return ServerResponse.badRequest().build();
                    })
                    .onErrorResume(throwable -> {
                        logger.info(throwable.toString());
                        return ServerResponse.badRequest().build();
                    });
            });
    }

    private Mono<JsonNode> bodyToJsonNode(Mono<DataBuffer> buffer) {

        return buffer.flatMap(dataBuffer -> {
            try {
                return Mono.just(objectMapper.readTree(dataBuffer.asInputStream()))
                    .publishOn(Schedulers.elastic());
            } catch (IOException e) {
                return Mono.empty();
            }
        });
    }

    public Mono<ServerResponse> create(ServerRequest request) {

        return bodyToJsonNode(request.bodyToMono(DataBuffer.class))
            .flatMap(jsonNode -> {
                try {
                    encoder.encode(jsonNode.get("user").get("password").asText());
                    logger.warn("suces to decode some value " + jsonNode.get("user").get("email").asText());
                    User user = new User();
                    user.setEmail(jsonNode.get("user").get("email").asText())
                        .setFirstname(jsonNode.get("user").get("firstname").asText())
                        .setLastname(jsonNode.get("user").get("lastname").asText())
                        .setPseudo(jsonNode.get("user").get("pseudo").asText())
                        .setEncodedCredential(encoder.getEncodedCredential());

                    Iterator<JsonNode> it = jsonNode.get("user").get("roles").elements();

                    while (it.hasNext()) {
                        user.addRole(new Role(it.next().asText()));
                    }

                    return userRepository.create(user)
                        .flatMap(integer -> {
                            logger.warn("maybe integer is null" + integer);
                            if (integer > 0) {

                                return userRepository.findById(integer).flatMap(userMappingInterface -> {

                                    UserCreateResponse userCreateResponse =
                                        UserCreateResponse.mapToUserResponse(userMappingInterface).dropRoles();

                                    user.getRoles().stream()
                                        .peek(role -> userCreateResponse.addRole(role.getRoleValue()))
                                        .count();

                                    return ServerResponse.ok()
                                        .body(Mono.just(userCreateResponse), UserCreateResponse.class);
                                })
                                    .onErrorResume(throwable -> {
                                        logger.warn("1er first bad request");
                                        return ServerResponse.badRequest().build();
                                    });
                            } else {
                                return ServerResponse.status(HttpStatus.CONFLICT).build();
                            }
                        })
                        .onErrorResume(throwable -> {
                            logger.warn("2 seconde bad request");
                            return ServerResponse.badRequest().build();
                        });

                } catch (NullPointerException e) {
                    e.printStackTrace();
                    logger.warn("3 last bad request");
                    return ServerResponse.badRequest().build();
                }
            });
    }

    /**
     * Some resource it exposed for visitor and we need to know you want visit without authenticate so
     * this resource will create a token for anonymous visitor with 5 minutes expiration date
     *
     * @param request
     * @return
     */
    public Mono<ServerResponse> guest(ServerRequest request) {
        // TODO extract email from request if present return error if no subject field filled
        return request.body(BodyExtractors.toMono(Guest.class))
            .flatMap(guest -> {
                logger.warn("no log");
                ArrayList<SimpleGrantedAuthority> authoritie = new ArrayList<>();
                authoritie.add(new SimpleGrantedAuthority("ROLE_GUEST"));
                String subject = guest.getSubject();
                Guest  guest1  = new Guest();
                logger.warn(subject);
                if (guest.getSubject() == null) {
                    subject = "unknow";
                }
                String token = jwtHelper.generateToken(subject, authoritie, 1L).getToken();
                guest1.setToken(token);
                return ServerResponse.ok()
                    .body(Mono.just(guest1), Guest.class);
            });
    }

    public Mono<ServerResponse> read(ServerRequest request) {

        return userRepository.findById(new Integer(request.pathVariable("id")))
            .flatMap(userMappingInterface -> {

                UserResponse us = UserResponse.mapToUserResponse(userMappingInterface);

                return ServerResponse.ok()
                    .body(Mono.just(us), UserResponse.class);
            })
            .onErrorResume(throwable -> ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> show(ServerRequest request) {

        Flux<UserResponse> userResponseFlux = userRepository.fetchAll()
            .map(userMappingInterface -> UserResponse.mapToUserResponse(userMappingInterface));

        return ServerResponse.ok()
            .body(userResponseFlux, UserResponse.class);
    }

    // TODO refactor and use private method as create()
    public Mono<ServerResponse> update(ServerRequest request) {

        return bodyToJsonNode(request.bodyToMono(DataBuffer.class))
            .flatMap(jsonNode -> {

                UpdatedUser us = new UpdatedUser();
                us.setEmail(jsonNode.get("email").asText());
                us.setFirstname(jsonNode.get("firstname").asText());
                us.setLastname(jsonNode.get("lastname").asText());
                us.setPseudo(jsonNode.get("pseudo").asText());
                us.setPassword(jsonNode.get("password").asText());
                us.setId(jsonNode.get("id").asInt());

                Iterator<JsonNode> it    = jsonNode.get("roles").elements();
                Collection<Role>   roles = new ArrayList<>();

                while (it.hasNext()) {
                    roles.add(new Role(it.next().asText()));
                }

                us.setRoles(roles);

                return userRepository.update(us);
            })
            .flatMap(userMappingInterface -> ServerResponse.ok().body(
                Mono.just(UserResponse.mapToUserResponse(userMappingInterface)), UserResponse.class)
            );
    }

    // TODO really used ?
    private Mono<ServerResponse> addRole(ServerRequest request) {
        return request
            .body(BodyExtractors.toMono(UpdatedUser.class))
            .flatMap(updatedUser -> userRepository.update(updatedUser))
            .flatMap(userMappingInterface -> ServerResponse.ok().body(Mono
                .just(UserResponse.mapToUserResponse(userMappingInterface)), UserResponse.class)
            );
    }
}
