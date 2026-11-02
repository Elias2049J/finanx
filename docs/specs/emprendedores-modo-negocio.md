# Spec — Modo negocio para emprendedores y personas que mueven dinero constante

**Estado:** DRAFT — pendiente de aprobación  
**Fecha:** 2026-10-28  
**Alcance:** posicionamiento, semántica y MVP de negocio. No incluye implementación.

---

## Decisiones cerradas en esta revisión

Se descartan explícitamente del producto y del roadmap:

- Facturación electrónica y emisión de facturas.
- Multi-usuario, roles e invitaciones.
- Importación de datos (CSV, OFX, Excel).
- Integración con bancos.
- App web.
- Planilla.
- Inventario.
- Conciliación bancaria.

El MVP es **manual-first**: el usuario registra, adjunta y clasifica. No se conecta al banco ni importa archivos.

Decisiones de alcance cerradas:
- Target primario: freelancer/solopreneur.
- Modelo comercial: un solo PREMIUM.
- Espacios de negocio en el MVP: uno.
- Mercado inicial: LatAm.

Pendientes de decisión (no aprobados):
- Múltiples cuentas: propuesta de descartar.
- Transferencias: propuesta de descartar.
- Contactos: propuesta de descartar.
- Proyectos/centros de costo: propuesta de dejar para después.
- Cobros/pagos pendientes: propuesta de descartar.
- Documentos: reusar adjunto existente; sin módulo nuevo.
- Flujo de caja: propuesta de dejar para después.
- Reportes: básicos existentes; avanzados ignorados por ahora.
- Copiloto: solo consulta, sin acciones ni RAG.
- Exportación: propuesta de descartar.
- Onboarding: propuesta de categorías/flujo pendiente de aprobación.

---

## 1. Resumen

Finanx hoy es una app de finanzas personales con multi-moneda, presupuestos, metas, recordatorios, analíticas, chat con IA y OCR. El producto funciona, pero su semántica y su core están pensados para una persona que registra gastos, no para alguien que cobra, paga o separa dinero personal del negocio.

La propuesta es evolucionar de “finanzas personales” a **“finanzas personales + modo negocio”**, sin convertir a Finanx en un software contable. El target inicial no es la pyme con planilla, inventario o facturación electrónica; es el **freelancer, independiente, micro-negocio o persona con ingresos variables/multi-moneda** que hoy usa Excel, WhatsApp o una libreta y quiere orden, flujo de caja y un copiloto que le diga qué hacer.

La semántica debe volverse neutral para servir a ambos mundos: **cuentas**, **movimientos**, **categorías**, **proyectos**, **clientes/proveedores**, **cobros/pagos**, **reportes** y **copiloto**. No se renombra todo a “negocio” porque el usuario personal seguiría siendo parte del producto.

---

## 2. Problema

### 2.1. Usuario personal
Quiere registrar gastos e ingresos, presupuestar, ahorrar y entender sus hábitos. Finanx ya lo cubre.

### 2.2. Freelancer / independiente
- Tiene ingresos variables y varios clientes.
- Mezcla dinero personal y del negocio.
- Necesita saber cuánto ganó, cuánto le deben, cuánto debe y si puede cubrir sus gastos.
- Guarda comprobantes y necesita reportes simples para su contador.
- No quiere un ERP ni un software contable; quiere claridad y rapidez.

### 2.3. Micro-negocio / persona con dinero constante
- Maneja caja, banco, tarjeta y billeteras digitales.
- Registra ventas, compras, gastos operativos y pagos a proveedores.
- Necesita flujo de caja, categorías, proyectos/servicios y alertas de vencimientos.
- Quiere saber qué le queda y qué viene; no necesita contabilidad formal.

### 2.4. Brecha actual
Finanx no tiene:
- espacios de negocio separados de lo personal;
- saldo por moneda dentro de un espacio;
- copiloto de solo consulta sobre datos del espacio;
- onboarding adaptado a personal/negocio/ambos.

El resto (contactos, proyectos, cobros/pagos, flujo de caja, reportes avanzados, exportación, facturación, multi-usuario, importación) queda fuera del MVP por decisión de alcance.

Renombrar sin agregar espacios y copiloto generaría una promesa que el producto no cumple.

---

## 3. Target

### 3.1. Target primario
**Freelancers, independientes y personas con ingresos variables o multi-moneda** que quieren digitalizar sus registros y están dispuestos a pagar por orden, reportes y un copiloto financiero.

Ejemplos:
- Diseñadores, desarrolladores, consultores, coaches, fotógrafos.
- Profesionales independientes que cobran por proyecto.
- Personas con rentas, side hustles o ingresos en más de una moneda.
- Pequeños comercios que necesitan caja y gastos, no contabilidad completa.

### 3.2. Target secundario
**Micro-negocios de 1 a 5 personas** que necesitan control de caja, clientes, proveedores y reportes simples. Se validará después del MVP.

### 3.3. No target
- Pymes que requieren planilla, inventario, facturación electrónica o conciliación bancaria.
- Estudios contables que buscan un ERP.
- Empresas que necesitan multi-usuario o app web.

Finanx no cubre esas funciones; están descartadas explícitamente.

---

## 4. Propuesta de valor

> **“El copiloto financiero para emprendedores que no quieren un software contable.”**

Finanx debe ser:
- **Simple:** registrar un movimiento en segundos.
- **Claro:** saber cuánto entró, cuánto salió, cuánto queda y qué viene.
- **Útil:** separar personal/negocio, clientes, proyectos y cobros.
- **Inteligente:** un copiloto que conoce presupuestos, metas, recordatorios, clientes y proyectos.
- **LatAm:** multi-moneda, español, precio local y comprobantes.

No debe ser:
- Un ERP.
- Un software de contabilidad.
- Un reemplazo del contador.
- Una herramienta de facturación electrónica.
- Una herramienta de conciliación bancaria.

---

## 5. Semántica

La semántica debe ser neutral y comprensible para personal y negocio.

| Actual | Propuesto | Motivo |
|---|---|---|
| Finanzas personales | Finanzas personales y negocio | No alienar al usuario personal |
| Billeteras | Cuentas | Funciona para caja, banco, tarjeta y billetera digital |
| Transacciones | Movimientos | Más natural para ingresos, gastos y transferencias |
| Categorías | Categorías | Se mantiene |
| Motivos | Centros de costo / proyectos | Lenguaje de negocio, opcional |
| Presupuestos | Presupuestos | Se mantiene |
| Metas | Objetivos | Neutral para ahorro personal y negocio |
| Recordatorios | Pagos y cobros programados | Más claro para negocio |
| Analíticas | Flujo de caja / Reportes | Lenguaje de negocio |
| Chat | Copiloto financiero | Vende valor, no tecnología |
| OCR | Escaneo de comprobantes | Claro para recibos y documentos |
| Suscripción | Plan | Neutral |
| Usuario | Espacio personal / negocio | Permite separar contextos |

**Regla:** no usar “negocio” en toda la app. El onboarding pregunta qué quiere controlar y adapta el lenguaje:
- “Mis finanzas personales”
- “Mi negocio”
- “Ambos”

---

## 6. Propuesta de core mínimo (pendiente de decisión)

> Esta sección es una propuesta. No está aprobada hasta cerrar los pendientes del inicio.

### 6.1. Espacios (personal / negocio)

**Qué es:** contexto de trabajo, no contenedor de dinero. Reemplaza la idea de “varias billeteras” por separación de contextos.

**Por qué importa:** el freelancer mezcla dinero personal y del negocio. Sin espacios, los reportes mienten y no sabe qué parte es del negocio.

**MVP:**
- Un espacio personal por defecto.
- Un espacio de negocio en el MVP.
- Cada espacio tiene sus propias categorías y movimientos.
- Cambio rápido de espacio.
- Sin compartir espacios ni invitar usuarios.

**No incluye:** múltiples espacios de negocio, multi-usuario, permisos, auditoría.

**Criterio de aceptación:** el usuario crea un espacio de negocio, registra un movimiento y ve que no aparece en el espacio personal; puede volver al personal sin perder datos.

---

### 6.2. Movimientos

**Qué es:** registro central de ingresos y gastos.

**Por qué importa:** es la materia prima del producto. Si registrar es lento, el usuario abandona.

**MVP:**
- Tipo: ingreso o gasto.
- Campos: fecha, monto, moneda, categoría, nota.
- Sin cuentas múltiples: el saldo se calcula por moneda dentro del espacio.
- Sin transferencias.
- Búsqueda y filtros por fecha, categoría y texto.

**No incluye:** transferencias, contactos, proyectos, facturación, importación, conexión bancaria, reglas automáticas complejas.

**Criterio de aceptación:** un gasto aparece en los totales del espacio y actualiza el saldo por moneda; no requiere cuenta ni contacto.

---

### 6.3. Recordatorios

**Qué es:** avisos de pagos y cobros programados.

**Por qué importa:** el emprendedor no puede depender de la memoria para vencimientos.

**MVP:**
- Pagos y cobros recurrentes.
- Modo manual y automático.
- Confirmación de ocurrencias pendientes.
- Calendario.
- Notificación push.
- Una sola notificación por ocurrencia; al tocarla, abre Recordatorios.
- La ocurrencia pendiente bloquea la siguiente hasta que se confirme o ignore.

**No incluye:** email/SMS, automatización bancaria, pagos automáticos reales, integración con calendarios externos.

**Criterio de aceptación:** se genera una ocurrencia, llega una notificación, el usuario confirma y se registra el movimiento; la siguiente ocurrencia queda habilitada.

---

### 6.4. Copiloto financiero (solo consulta)

**Qué es:** respuestas basadas en los datos del espacio activo.

**Por qué importa:** es el diferencial frente a una app de registros. El usuario pregunta en lenguaje natural y obtiene respuestas con datos.

**MVP:**
- Preguntas de lectura: totales, tendencias, comparaciones, categorías con más gasto, ingresos por periodo.
- Respuestas basadas en datos estructurados del espacio activo.
- No ejecuta acciones.
- No es RAG ni analiza documentos.
- No da asesoría fiscal, legal o de inversión regulada.
- Cuotas en free; ilimitado en premium.
- Privacidad: consentimiento, minimización de datos y opción de borrar historial.

**No incluye:** crear movimientos, presupuestos u objetivos por chat; ejecución autónoma; voz; análisis de comprobantes.

**Criterio de aceptación:** el copiloto responde con datos reales del espacio activo y cita el periodo; no crea ni modifica datos.

---

### 6.5. Onboarding

**Qué es:** la configuración inicial que adapta Finanx al usuario.

**Por qué importa:** si el usuario no entiende qué espacio usar ni cómo empezar, no activa.

**MVP:**
- Elegir “personal”, “negocio” o “ambos”.
- País y moneda base.
- Categorías sugeridas según el tipo de negocio (ver anexo de categorías).
- Empezar de cero; sin importación.
- Todo editable después.

**No incluye:** importación de datos, conexión bancaria, configuración contable, multi-usuario, creación de cuenta obligatoria.

**Criterio de aceptación:** el usuario termina el onboarding en el espacio correcto, con categorías listas para registrar.

---

## 7. Fuera de alcance (descartado)

Estas funciones **no forman parte del producto ni del roadmap**:

- Facturación electrónica y emisión de facturas.
- Multi-usuario, roles e invitaciones.
- Importación de datos (CSV, OFX, Excel).
- Integración con bancos.
- App web.
- Planilla.
- Inventario.
- Conciliación bancaria.
- Contabilidad de doble partida.
- Declaración de impuestos.
- CRM.

Si un usuario las pide, se le explica que Finanx no las cubre y se le sugiere usar una herramienta especializada o su contador.

---

## 8. Modelo de negocio

### 8.1. Opción recomendada (simple)
Un solo **PREMIUM** que incluye:
- modo negocio;
- multi-moneda;
- copiloto IA;
- OCR;
- automatización;
- reportes avanzados;
- sin anuncios.

Precio sugerido:
- **US$5.99/mes** o **US$49.99/año**.
- 14 días de trial.
- Precio regional en Play Store.
- Lifetime early-bird opcional con fair-use de IA.

### 8.2. Opción alternativa (si se valida disposición a pagar)
- **Personal Premium:** US$3.99–4.99/mes.
- **Business Premium:** US$7.99–9.99/mes.
- Business incluye modo negocio y reportes.

No implementar dos tiers hasta validar con usuarios; añade complejidad de compra y soporte.

### 8.3. Free
- Core personal ilimitado.
- 3 presupuestos, 3 objetivos, 5 recordatorios.
- Analíticas básicas.
- 3 mensajes de copiloto/día o 10/semana.
- 1–2 escaneos/día + rewarded.
- Banner.
- Modo negocio limitado: 1 espacio, 3 clientes, 3 proyectos, 1 cuenta por tipo.

### 8.4. Rewarded
- Solo para OCR y, opcionalmente, créditos extra de copiloto.
- No para guardar recordatorios/objetivos.
- Tope diario.
- SSV de AdMob.

---

## 9. Fases propuestas

### Fase 0 — Semántica y espacio
- Términos neutrales.
- Onboarding personal/negocio/ambos.
- Espacio personal/negocio.
- Tipos de cuenta.
- Sin cambios de modelo contable.

### Fase 1 — MVP negocio (podado)
- Espacios personal/negocio.
- Movimientos con saldo por moneda.
- Recordatorios.
- Copiloto de solo consulta.
- Onboarding con categorías sugeridas.

### Fase 2 — Profundidad
- Proyectos/centros de costo.
- Flujo de caja.
- Reportes avanzados.
- Exportación.
- Automatizaciones y alertas.
- Copiloto proactivo.
- Más monedas y más espacios de negocio, si se valida.

No se planifican facturación, multi-usuario, importación, banca, web, planilla, inventario ni conciliación.

---

## 10. Métricas

- % de nuevos usuarios que eligen modo negocio.
- Activación: primer movimiento + primera cuenta + primer reporte.
- Uso semanal del copiloto.
- % de conversaciones que terminan en acción (crear movimiento, cobro, presupuesto, objetivo).
- Conversión free → premium.
- Conversión del trial.
- Retención D7/D30.
- ARPU y churn.
- Coste por mensaje de IA y por escaneo.
- Tickets por funciones fuera de alcance (señal de límites claros).

---

## 11. Riesgos

- **Scope creep contable:** el usuario pedirá funciones que Finanx descartó; hay que responder con límites claros.
- **Soporte:** un emprendedor espera más ayuda que un usuario personal.
- **Migración:** datos personales y de negocio mezclados.
- **Competencia:** Alegra, Siigo, QuickBooks, Xero, Wave, Zoho Books, Contifico, Nubox, Bind.
- **IA:** coste, latencia, alucinaciones y privacidad.
- **Precio:** si se cobra más, el valor debe ser real, no semántico.

---

## 12. Decisiones abiertas

1. ¿Modo negocio desde el onboarding o activable después?
2. ¿Precio y trial definitivos?
3. ¿Se mantiene el nombre Finanx o se cambia la semántica de marca?

---

## 13. Criterios de éxito del piloto

- 20 entrevistas con freelancers/micro-negocios.
- 10 usuarios piloto usando modo negocio durante 4 semanas.
- Al menos 5 convertidos a premium.
- Uso semanal del copiloto > 30% de los pilotos.
- Al menos 3 reportes exportados por usuario/mes.
- NPS cualitativo positivo y tickets manejables.

---

## 14. Anexo — Competencia

### Personal
YNAB, Monarch, Copilot, Wallet, Spendee, Money Lover, Goodbudget, PocketGuard, Fintonic.

### Negocio/contabilidad
Alegra, Siigo, QuickBooks, Xero, Wave, Zoho Books, Contifico, Nubox, Bind.

### Posición de Finanx
No competir de frente con contabilidad. Ser el **copiloto de flujo de caja y orden financiero** para freelancers/micro-negocios, con la opción de exportar al contador.

---

## 15. Anexo — Categorías sugeridas y flujo de onboarding

### Categorías de ingresos
- Servicios / consultoría
- Proyectos
- Ventas
- Rentas
- Otros ingresos

### Categorías de gastos
- Software y suscripciones
- Marketing y publicidad
- Transporte
- Comida de negocio
- Oficina / coworking
- Equipos
- Servicios (internet, telefonía)
- Comisiones bancarias
- Impuestos y tasas
- Otros gastos

### Flujo de onboarding
1. Elegir “personal”, “negocio” o “ambos”.
2. Elegir país y moneda base.
3. Mostrar categorías sugeridas y permitir editar.
4. Crear la primera cuenta.
5. Empezar de cero.
6. Opcional: crear primer movimiento o primer cobro pendiente.
