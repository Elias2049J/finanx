package com.elias.mismonedas.identity.repository;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.shared.repository.TextSearchSpecification;
import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> list(String search) {
        return TextSearchSpecification.containsIgnoreCase(search, "name", "lastname", "email", "username");
    }
}
