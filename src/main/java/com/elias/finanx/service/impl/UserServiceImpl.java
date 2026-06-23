package com.elias.finanx.service.impl;

import com.elias.finanx.dto.auth.LoginResponse;
import com.elias.finanx.dto.user.UserRequest;
import com.elias.finanx.dto.user.UserResponse;
import com.elias.finanx.entity.Category;
import com.elias.finanx.entity.enums.TimeZone;
import com.elias.finanx.entity.enums.TransactionType;
import com.elias.finanx.entity.enums.UserState;
import com.elias.finanx.entity.User;
import com.elias.finanx.mapper.UserMapper;
import com.elias.finanx.repository.CategoryRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.UserService;
import com.elias.finanx.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    @Override
    @Transactional
    public LoginResponse register(UserRequest request) {
        User user = userMapper.toEntity(request);
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already taken");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken");
        }

        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        User saved = userRepository.save(user);

        this.registerCategories(saved);

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

    private void registerCategories(User u) {
        Category salary = new Category();
        salary.setType(TransactionType.INCOME);
        salary.setName("Salario");
        salary.setDescription("Ingresos provenientes de trabajo dependiente o independiente.");

        Category miscellaneousIncomes = new Category();
        miscellaneousIncomes.setType(TransactionType.INCOME);
        miscellaneousIncomes.setName("Otros Ingresos");
        miscellaneousIncomes.setDescription("Otros ingresos no recurrentes, por ejemplo, ventas ocasionales, regalos o bonificaciones.");

        Category healthCare = new Category();
        healthCare.setType(TransactionType.SPENT);
        healthCare.setName("Salud");
        healthCare.setDescription("Gastos médicos, seguros de salud, farmacia y atención hospitalaria.");

        Category entertainment = new Category();
        entertainment.setType(TransactionType.SPENT);
        entertainment.setName("Entretenimiento");
        entertainment.setDescription("Actividades recreativas como cine, viajes, conciertos y ocio en general.");

        Category utilities = new Category();
        utilities.setType(TransactionType.SPENT);
        utilities.setName("Servicios Básicos");
        utilities.setDescription("Pagos de servicios como agua, luz, gas, internet, telefonía, etc.");

        Category housing = new Category();
        housing.setType(TransactionType.SPENT);
        housing.setName("vivienda");
        housing.setDescription("Descripción: Gastos relacionados con mantener el hogar, incluyendo alquiler o hipoteca, mantenimiento, seguros de vivienda, impuestos prediales y cuotas de condominio.");

        Category transport = new Category();
        transport.setType(TransactionType.SPENT);
        transport.setName("Transporte");
        transport.setDescription("Gastos en pasajes de transporte público y afines.");

        Category education = new Category();
        education.setType(TransactionType.SPENT);
        education.setName("Educación");
        education.setDescription("Inversión en formación académica y profesional: matrículas, cursos, libros.");

        Category food = new Category();
        food.setType(TransactionType.SPENT);
        food.setName("Alimentación");
        food.setDescription("Compras de alimentos, restaurantes, mercado y productos de consumo diario.");


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

        for (Category c: defaultCategories) {
            c.setUser(u);
            c.setActive(true);
        }
        categoryRepository.saveAll(defaultCategories);
    }
}
