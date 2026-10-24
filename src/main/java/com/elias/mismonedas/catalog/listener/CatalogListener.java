package com.elias.mismonedas.catalog.listener;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.repository.ReasonRepository;
import com.elias.mismonedas.identity.domain.event.UserDataResetEvent;
import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.identity.domain.event.UserRegisteredEvent;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogListener {

    private final CategoryRepository categoryRepository;
    private final ReasonRepository reasonRepository;
    private final UserRepository userRepository;

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        seedDefaultCategories(event.userId());
    }

    @EventListener
    public void onUserDataReset(UserDataResetEvent event) {
        seedDefaultCategories(event.userId());
    }

    @EventListener
    public void onUserDeleted(UserDeletedEvent event) {
        categoryRepository.deleteAllByUser_Id(event.userId());
        reasonRepository.deleteAllByUser_Id(event.userId());
        log.info("Deleted categories and reasons for user {}", event.userId());
    }

    private void seedDefaultCategories(Long userId) {
        log.info("Registering default categories for user {}", userId);
        var owner = userRepository.getReferenceById(userId);

        List<Category> defaultCategories = List.of(
                category(TransactionType.INCOME, "Salario",
                        "Ingresos provenientes de trabajo dependiente o independiente."),
                category(TransactionType.INCOME, "Otros Ingresos",
                        "Ventas ocasionales, regalos, bonificaciones, etc."),
                category(TransactionType.SPENT, "Salud",
                        "Gastos medicos, seguros de salud, farmacia y atencion hospitalaria."),
                category(TransactionType.SPENT, "Entretenimiento",
                        "Cine, viajes, conciertos y ocio en general."),
                category(TransactionType.SPENT, "Servicios Basicos",
                        "Pagos de servicios como agua, luz, gas, internet, telefonia, etc."),
                category(TransactionType.SPENT, "Vivienda",
                        "Alquiler o hipoteca, mantenimiento, seguros de vivienda, impuestos prediales y cuotas de condominio."),
                category(TransactionType.SPENT, "Transporte",
                        "Gastos en pasajes de transporte publico y afines."),
                category(TransactionType.SPENT, "Educacion",
                        "Formacion academica y profesional: matriculas, cursos, libros, etc."),
                category(TransactionType.SPENT, "Alimentacion",
                        "Alimentos, restaurantes, mercado y productos de consumo diario."));

        defaultCategories.forEach(c -> {
            c.setUser(owner);
            c.setActive(true);
        });
        categoryRepository.saveAll(defaultCategories);
    }

    private static Category category(TransactionType type, String name, String description) {
        Category c = new Category();
        c.setType(type);
        c.setName(name);
        c.setDescription(description);
        return c;
    }
}
