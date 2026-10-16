package com.elias.mismonedas.identity.service.impl;

import com.elias.mismonedas.billing.domain.enums.BillingCycle;
import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;
import com.elias.mismonedas.billing.dto.subscription.SubscriptionRequest;
import com.elias.mismonedas.billing.service.BillingService;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.repository.ReasonRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.domain.enums.UserState;
import com.elias.mismonedas.identity.dto.auth.LoginResponse;
import com.elias.mismonedas.identity.dto.user.UserRequest;
import com.elias.mismonedas.identity.dto.user.UserResponse;
import com.elias.mismonedas.identity.mapper.UserMapper;
import com.elias.mismonedas.identity.repository.UserPreferencesRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.repository.UserSpecifications;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.identity.service.JwtTokenService;
import com.elias.mismonedas.identity.service.UserService;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.planning.repository.SavingGoalRepository;
import com.elias.mismonedas.scheduling.repository.BudgetScheduleRepository;
import com.elias.mismonedas.scheduling.repository.TransactionScheduleRepository;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService, UserDetailsService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final NotificationRepository notificationRepository;
    private final SavingGoalRepository savingGoalRepository;
    private final ReasonRepository reasonRepository;
    private final BudgetScheduleRepository budgetScheduleRepository;
    private final TransactionScheduleRepository transactionScheduleRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final BillingService billingService;
    private final SecurityService securityService;

    @Override
    @Transactional
    public LoginResponse register(UserRequest request) {
        User user = userMapper.toEntity(request);
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already taken");
        }
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        User saved = userRepository.save(user);

        this.registerCategories(saved);

        // TODO: Remove after testing — auto-assign PREMIUM subscription to all new users
        var subRequest = SubscriptionRequest.builder()
                .userId(saved.getId())
                .plan(SubscriptionPlan.PREMIUM)
                .billingCycle(BillingCycle.MONTHLY)
                .amount(BigDecimal.ZERO)
                .currency("PEN")
                .autoRenew(false)
                .build();
        billingService.subscribeAndActivate(subRequest);

        String token = jwtTokenService.generateToken(saved);
        return new LoginResponse(userMapper.toResponse(saved), token);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll()
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, Pageable pageable) {
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> page = userRepository.findAll(UserSpecifications.list(search), stablePage);
        return PageResponseFactory.from(page, userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findCurrentUser() {
        return userMapper.toResponse(securityService.getCurrentUser());
    }

    private User getEntityById(Long aLong) {
        return userRepository.findById(aLong).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long aLong) {
        return userMapper.toResponse(getEntityById(aLong));
    }

    @Override
    public UserResponse disable(Long id) {
        User user = getEntityById(id);
        ZoneId zoneId = user.getTimeZone().toZoneId();
        user.disable();
        user.setDisabledAt(OffsetDateTime.now(zoneId));
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse enable(Long id) {
        User user = getEntityById(id);
        user.setState(UserState.ENABLED);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse block(Long id) {
        User user = getEntityById(id);
        user.block();
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse update(Long id, UserRequest request) {
        User existing = userRepository.findById(id).orElseThrow();
        userMapper.updateFromDto(request, existing);
        existing.setPassword(passwordEncoder.encode(request.getPassword()));

        return userMapper.toResponse(userRepository.save(existing));
    }

    @Override
    public List<UserResponse> searchByName(String q) {
        return userRepository.findAllByNameContainingIgnoreCase(q)
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String emailClean = email.trim().toLowerCase();
        if (email.isBlank()) {
            throw new IllegalArgumentException("Bad credentials");
        }
        return userRepository.findByEmail(emailClean)
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));
    }

    @Override
    public TimeZone[] listTimeZones() {
        return TimeZone.values();
    }

    @Transactional
    @Override
    public void resetAccountData(Long userId) {
        this.deleteUserData(userId);
        User u = this.getEntityById(userId);
        this.registerCategories(u);
    }

    @Transactional
    @Override
    public void deleteUserAccount(Long userId) {
        this.deleteUserData(userId);
        userRepository.deleteById(userId);
        log.info("Deleted user with ID: {}", userId);
    }

    private void registerCategories(User u) {
        log.info("Registering default categories");
        Category salary = new Category();
        salary.setType(TransactionType.INCOME);
        salary.setName("Salario");
        salary.setDescription("Ingresos provenientes de trabajo dependiente o independiente.");

        Category miscellaneousIncomes = new Category();
        miscellaneousIncomes.setType(TransactionType.INCOME);
        miscellaneousIncomes.setName("Otros Ingresos");
        miscellaneousIncomes.setDescription("Ventas ocasionales, regalos, bonificaciones, etc.");

        Category healthCare = new Category();
        healthCare.setType(TransactionType.SPENT);
        healthCare.setName("Salud");
        healthCare.setDescription("Gastos medicos, seguros de salud, farmacia y atencion hospitalaria.");

        Category entertainment = new Category();
        entertainment.setType(TransactionType.SPENT);
        entertainment.setName("Entretenimiento");
        entertainment.setDescription("Cine, viajes, conciertos y ocio en general.");

        Category utilities = new Category();
        utilities.setType(TransactionType.SPENT);
        utilities.setName("Servicios Basicos");
        utilities.setDescription("Pagos de servicios como agua, luz, gas, internet, telefonia, etc.");

        Category housing = new Category();
        housing.setType(TransactionType.SPENT);
        housing.setName("Vivienda");
        housing.setDescription("Alquiler o hipoteca, mantenimiento, seguros de vivienda, impuestos prediales y cuotas de condominio.");

        Category transport = new Category();
        transport.setType(TransactionType.SPENT);
        transport.setName("Transporte");
        transport.setDescription("Gastos en pasajes de transporte publico y afines.");

        Category education = new Category();
        education.setType(TransactionType.SPENT);
        education.setName("Educacion");
        education.setDescription("Formacion academica y profesional: matriculas, cursos, libros, etc.");

        Category food = new Category();
        food.setType(TransactionType.SPENT);
        food.setName("Alimentacion");
        food.setDescription("Alimentos, restaurantes, mercado y productos de consumo diario.");


        List<Category> defaultCategories = new ArrayList<>();

        defaultCategories.add(salary);
        defaultCategories.add(miscellaneousIncomes);
        defaultCategories.add(healthCare);
        defaultCategories.add(entertainment);
        defaultCategories.add(utilities);
        defaultCategories.add(transport);
        defaultCategories.add(education);
        defaultCategories.add(food);
        defaultCategories.add(housing);

        for (Category c : defaultCategories) {
            c.setUser(u);
            c.setActive(true);
        }
        categoryRepository.saveAll(defaultCategories);
    }

    private void logDelete(
            Long id, String relationshipName) {
        log.info("Deleted all {} for user with ID: {}", relationshipName, id);
    }

    private void deleteUserData(Long userId) {
        preferencesRepository.deleteByUser_Id(userId);

        savingGoalRepository.deleteAllByUser_Id(userId);
        logDelete(userId, "savingGoals");

        notificationRepository.deleteAllByUser_Id(userId);
        logDelete(userId, "notifications");

        transactionRepository.deleteAllByWallet_User_Id(userId);
        logDelete(userId, "transactions");

        reasonRepository.deleteAllByUser_Id(userId);
        logDelete(userId, "categories");

        budgetRepository.deleteAllByUser_Id(userId);
        logDelete(userId, "budgets");

        budgetScheduleRepository.deleteAllByUser_Id(userId);
        logDelete(userId, "budget schedules");

        transactionScheduleRepository.deleteAllByUser_Id(userId);
        logDelete(userId, "transaction schedules");

        categoryRepository.deleteAllByUser_Id(userId);
        logDelete(userId, "categories");
    }
}
