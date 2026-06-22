package com.elias.finanx.mapper;

import com.elias.finanx.dto.budget.BudgetRequest;
import com.elias.finanx.dto.budget.BudgetResponse;
import com.elias.finanx.entity.Budget;
import com.elias.finanx.entity.Category;
import com.elias.finanx.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {RecurrenceRuleMapper.class, DateMapper.class})
public interface BudgetMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    @Mapping(source = "schedule.id", target = "scheduleId")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "disabledAt", target = "disabledAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "start", target = "start", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "end", target = "end", qualifiedByName = "OffsetToLocal")
    BudgetResponse toResponseWithContext(Budget entity, @Context User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "userId", target = "user")
    @Mapping(source = "categoryId", target = "category")
    @Mapping(target = "start", ignore = true)
    @Mapping(target = "end", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "disabledAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "schedule", ignore = true)
    @Mapping(target = "state", ignore = true)
    Budget toEntity(BudgetRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "start", ignore = true)
    @Mapping(target = "end", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "disabledAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "schedule", ignore = true)
    @Mapping(target = "state", ignore = true)
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

    default BudgetResponse toResponse(Budget entity) {
        return toResponseWithContext(entity, entity.getUser());
    }
}
