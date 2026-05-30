package com.elias.finanx.service.impl;

import com.elias.finanx.dto.budget.BudgetRequest;
import com.elias.finanx.dto.budget.BudgetResponse;
import com.elias.finanx.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.finanx.entity.Budget;
import com.elias.finanx.entity.RecurrenceRule;
import com.elias.finanx.entity.enums.BudgetState;
import com.elias.finanx.mapper.BudgetMapper;
import com.elias.finanx.repository.BudgetRepository;
import com.elias.finanx.repository.CategoryRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.BudgetService;
import com.elias.finanx.service.RecurrenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Service
@RequiredArgsConstructor
public class BudgetServiceImpl implements BudgetService {
    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final BudgetMapper budgetMapper;
    private final RecurrenceService recurrenceService;

    @Override
    @Transactional
    public BudgetResponse create(BudgetRequest request) {
        Budget b = budgetMapper.toEntity(request);
        b.setCategory(categoryRepository.getReferenceById(request.getCategoryId()));
        b.setUser(userRepository.getReferenceById(request.getUserId()));
        resolveRecurrence(b, request.getRecurrenceRule());

        return budgetMapper.toResponse(budgetRepository.save(b));
    }

    @Override
    @Transactional
    public BudgetResponse update(Long id, BudgetRequest request) {
        Budget existing = budgetRepository.findById(id).orElseThrow();
        budgetMapper.updateFromDto(request, existing);

        existing.setCategory(categoryRepository.getReferenceById(request.getCategoryId()));
        existing.setUser(userRepository.getReferenceById(request.getUserId()));

        if (request.getRecurrenceRule() != null) {
            RecurrenceRuleRequest rrReq = request.getRecurrenceRule();
            if (existing.getRecurrenceRule() != null) {
                recurrenceService.update(existing.getRecurrenceRule().getId(), rrReq);
            } else {
                resolveRecurrence(existing, rrReq);
            }
        }

        return budgetMapper.toResponse(budgetRepository.save(existing));
    }

    private void resolveRecurrence(Budget budget, RecurrenceRuleRequest rrReq) {
        RecurrenceRuleRequest toCreate = new RecurrenceRuleRequest();
        toCreate.setRecurrenceType(rrReq.getRecurrenceType());
        toCreate.setInterval(rrReq.getInterval());
        toCreate.setDayOfWeek(rrReq.getDayOfWeek());
        toCreate.setStart(rrReq.getStart());
        toCreate.setEnd(rrReq.getEnd());

        RecurrenceRule rr = recurrenceService.create(budget.getUser().getId(), toCreate);
        budget.setRecurrenceRule(rr);
    }

    @Override
    public BudgetResponse findById(Long id) {
        return budgetMapper.toResponse(budgetRepository.findById(id).orElseThrow());
    }

    @Override
    public List<BudgetResponse> findAllByUser(Long idUser) {
        return budgetRepository.findAllByUser_Id(idUser)
                .stream()
                .map(budgetMapper::toResponse)
                .toList();
    }

    @Override
    public List<BudgetResponse> findAllActiveByUserAndState(Long idUser, BudgetState state) {
        return budgetRepository.findAllByUser_IdAndActiveTrueAndState(idUser, state)
                .stream()
                .map(budgetMapper::toResponse)
                .toList();
    }

    @Override
    public void cancel(Long id) {
        Budget b = budgetRepository.findById(id).orElseThrow();
        b.setState(BudgetState.CANCELLED);
        budgetRepository.save(b);
    }

    @Override
    public void disable(Long id) {
        Budget b = budgetRepository.findById(id).orElseThrow();
        b.setState(BudgetState.CANCELLED);
        b.setActive(false);
        budgetRepository.save(b);
    }
}
