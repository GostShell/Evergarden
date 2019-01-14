package com.hanami.cms.context.admin.domain.entity;

import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Embeddable
public enum RoleEnum {
	@Enumerated(EnumType.STRING)
	MASTER_ADMIN("ROLE_MASTER_ADMIN"), GUEST("ROLE_GUEST"), ADMIN("ROLE_ADMIN"), USER("ROLE_USER");

	private String role;

	RoleEnum(String role) {
		this.role = role;
	}
	
	public String toString() {
		return role;
	}
}
