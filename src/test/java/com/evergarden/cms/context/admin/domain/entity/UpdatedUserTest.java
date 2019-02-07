package com.evergarden.cms.context.admin.domain.entity;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class UpdatedUserTest {
    // Check assigned roles

    @Test
    void createInstanceAllParamConstructor() {
        Collection<SimpleGrantedAuthority> roles = new ArrayList<>();
        roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        roles.add(new SimpleGrantedAuthority("ROLE_GUEST"));

        UpdatedUser updatedUser = new UpdatedUser(
            1,
            true,
            "violet@mail.com",
            "Batou",
            "Ranger",
            "Batou",
            "password",
            roles
        );

        assertEquals(1, updatedUser.getId());
        assertEquals("violet@mail.com", updatedUser.getEmail());
        assertEquals("Batou", updatedUser.getFirstname());
        assertEquals("Ranger", updatedUser.getLastname());
        assertEquals("Batou", updatedUser.getPseudo());
        assertEquals("password", updatedUser.getPassword());
        assertTrue(updatedUser.isActivated());
        //updatedUser.getRoles()
    }

    @Test
    void createInstanceWithDefaultConstructor() {
        Collection<SimpleGrantedAuthority> roles = new ArrayList<>();
        roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

        UpdatedUser updatedUser = new UpdatedUser();

        updatedUser.setEmail("violet@mail.com");
        updatedUser.setFirstname("Batou");
        updatedUser.setLastname("Ranger");
        updatedUser.setPseudo("Batou");
        updatedUser.setActivated(true);
        updatedUser.setId(1);
        updatedUser.setRoles(roles);
        updatedUser.setPassword("pass");

        assertEquals(1, updatedUser.getId());
        assertEquals("violet@mail.com", updatedUser.getEmail());
        assertEquals("Batou", updatedUser.getFirstname());
        assertEquals("Ranger", updatedUser.getLastname());
        assertEquals("Batou", updatedUser.getPseudo());
        assertEquals("pass", updatedUser.getPassword());
        assertTrue(updatedUser.isActivated());
    }

}