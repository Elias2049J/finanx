package com.elias.finanx.mapper;

import com.elias.finanx.dto.budget.BudgetRequest;
import com.elias.finanx.dto.budget.BudgetResponse;
import com.elias.finanx.entity.Budget;
import com.elias.finanx.entity.Category;
import com.elias.finanx.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = RecurrenceRuleMapper.class)
public interface BudgetMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "category.id", target = "categoryId")
    BudgetResponse toResponse(Budget entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "userId", target = "user")
    @Mapping(source = "categoryId", target = "category")
    Budget toEntity(BudgetRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "recurrenceRule", ignore = true)
    void updateFromDto(BudgetRequest dto, @MappingTarget Budget entity);

    default User mapUserId(Long id) {
        if (id == null) return null;
        User u = new User();
        u.setId(id);
        return u;
    }

    default Category mapCategoryId(Long id) {
        if (id == null) return null;
        Category c = new Category();
        c.setId(id);
        return c;
    }
}
