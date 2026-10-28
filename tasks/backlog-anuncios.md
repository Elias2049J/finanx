# Backlog: anuncios con AdMob (Android)

> Referencia de integración: [`docs/integracion-anuncios-admob.md`](../docs/integracion-anuncios-admob.md).
> Este backlog reemplaza el orden sugerido de esa guía y fija las decisiones de producto.

## 1. Objetivo

Monetizar la app Android con **un banner adaptativo no intrusivo**, sin degradar flujos
críticos ni la experiencia de usuarios de pago. Un solo tramo de banner; la arquitectura
queda preparada para evaluar otros formatos más adelante.

## 2. Decisiones aprobadas

| Tema | Decisión |
|---|---|
| Emplazamiento | Banner adaptativo anclado al pie, solo en pantallas principales (Home, Movimientos, Metas). |
| Instancia | Una sola instancia global en `AppScaffold`. |
| Usuarios de pago | Ocultar anuncios a `PREMIUM` **y** `PREMIUM_TRIAL`. |
| IDs | IDs de test en `debug`; IDs reales solo en `release`. |
| Consentimiento UMP | Fuera de este tramo (mercado inicial LatAm). |
| Formatos | Solo banner ahora; `AdsPolicy`/`AdsConfig` extensibles. |
| Métricas | Solo las de AdMob; sin Firebase Analytics. |
| Play Console | Declaración "contiene anuncios" y Data Safety incluidas como tramo de publicación. |

## 3. Estándar de emplazamiento (criterio aplicado)

El estándar de AdMob para apps utilitarias/financieras es:

- **Banner adaptativo anclado** al borde inferior, respetando insets y por encima de la
  barra de navegación.
- Visible solo en **pantallas principales o de listado**, donde el usuario está explorando
  contenido y el anuncio no interrumpe una tarea.
- **Nunca** en login, registro, verificación de email, onboarding, bloqueo de seguridad,
  pago/suscripción, formularios de creación/edición, confirmación de recordatorios ni
  escáner.
- **Nunca** junto al teclado ni cubriendo botones/FAB; el anuncio no debe provocar taps
  accidentales (política de AdMob).
- Sin intersticiales automáticos al abrir la app ni tras cada acción.

Aplicado a Finanx:

| Pantalla | ¿Banner? | Motivo |
|---|---|---|
| Home | Sí | Pantalla principal de resumen. |
| Movimientos | Sí | Listado; el banner queda bajo la lista. |
| Metas de ahorro | Sí | Listado principal. |
| Chat | No | Teclado + contexto conversacional; alto riesgo de tap accidental. |
| Detalle de movimiento / presupuesto | No | Tarea concreta. |
| Carteras, Presupuestos, Análisis, Recordatorios | No en este tramo | Pantallas de gestión; se evalúan después. |
| Auth, onboarding, pago, formularios, confirmación | No | Flujos críticos. |

> El FAB central está a `-44.dp` del borde inferior. Con banner, hay que desplazarlo por
> la altura real del banner (ver ADS-06) para que no quede tapado.

## 4. Formatos evaluados

| Formato | Encaje en Finanx | Decisión |
|---|---|---|
| **Banner adaptativo** | Alto: no interrumpe, encaja en Home/listas y es el estándar para utilidades. | **Ahora** |
| Nativo | Medio-alto: podría integrarse entre movimientos, pero exige diseño propio y puede confundirse con contenido de la app. | Futuro (ADS-F1) |
| Intersticial | Bajo: interrumpe una app financiera y eleva el riesgo de taps accidentales. | No |
| Rewarded | Medio: serviría para una función premium (p. ej. escaneo extra), pero canibaliza la suscripción. | Futuro solo con aprobación de producto (ADS-F2) |
| App open | Bajo: pantalla de carga con anuncio; mala UX y escrutinio de políticas. | No |

## 5. Configuración de AdMob

Datos ya creados en la consola:

| Elemento | Valor |
|---|---|
| App | Finanx - Registra tus gastos e ingresos |
| App ID | `ca-app-pub-5907274046626357~2927461877` |
| Bloque | Banner para anuncios varios |
| Ad Unit ID (banner) | `ca-app-pub-5907274046626357/7803102779` |

IDs de test de Google (solo `debug`):

| Elemento | Valor |
|---|---|
| App ID de test | `ca-app-pub-3940256099942544~3347511713` |
| Banner adaptativo de test | `ca-app-pub-3940256099942544/9214589741` |
| Banner clásico de test | `ca-app-pub-3940256099942544/6300978111` |

SDK: `com.google.android.gms:play-services-ads:25.5.0` (última estable en Google Maven
verificada el 2026-09-17; la guía actual dice `24.6.0` y queda desactualizada). Validar
el primer build; si hay incompatibilidad con `compileSdk 36`, bajar a `24.9.0`.

> Los IDs de AdMob son públicos (viajan en el APK); no son secretos. Aun así, los IDs
> reales solo deben activarse en `release`.

## 6. Arquitectura propuesta

```
MisMonedasApplication
  └── MobileAds.initialize()                      (una vez, al arrancar)

AdsPolicy (@Singleton)
  ├── refresh(): GET /api/v1/subscriptions/current
  │     404 / null  -> FREE  -> showAds = true
  │     PREMIUM     -> showAds = false
  │     PREMIUM_TRIAL -> showAds = false
  │     error       -> conservar último valor; si no hay, false
  └── showAds: StateFlow<Boolean>                 (default false)

AdsViewModel (@HiltViewModel, alcance de sesión)
  └── expone showAds + refresh()

AppScaffold
  └── bottomBar = Column { AdBanner(); NavigationBar() }
        solo si showBottomBar && showAds && destino habilitado

AdBanner
  └── AndroidView(AdView adaptativo) + lifecycle + destroy
```

Reglas:

- `showAds` arranca en `false` hasta conocer el plan: evita que un usuario PREMIUM vea un
  flash de banner al abrir la app.
- Un error de red **no** debe mostrar anuncios a un usuario de pago: se conserva el último
  valor conocido y, si no existe, se asume `false`. Se reintenta en el siguiente arranque
  o refresh explícito.
- `refresh()` se llama al iniciar sesión, al volver a primer plano (con throttle) y tras
  crear/cancelar una suscripción.
- El banner no se muestra en destinos fuera de `adsEnabledDestinations` ni con el teclado
  abierto.

## 7. Tramos y tareas

### Tramo 0 — Base técnica

#### ADS-01 — Dependencia, App ID y BuildConfig por buildType

**Descripción:** añadir el SDK al catálogo de versiones, el App ID al manifest mediante
placeholder y el Ad Unit ID como `BuildConfig` distinto por buildType.

**Criterios de aceptación:**
- [ ] `gradle/libs.versions.toml` incluye `play-services-ads = "25.5.0"` y el alias de librería.
- [ ] `app/build.gradle.kts` agrega `implementation(libs.play.services.ads)` y `buildFeatures.buildConfig = true`.
- [ ] `manifestPlaceholders["admobAppId"]` usa el App ID de test en `debug` y el real en `release`.
- [ ] `buildConfigField("String", "ADMOB_BANNER_UNIT_ID", ...)` usa el banner de test en `debug` y el real en `release`.
- [ ] El manifest declara `com.google.android.gms.ads.APPLICATION_ID` con `${admobAppId}`.
- [ ] La guía `docs/integracion-anuncios-admob.md` se actualiza con la versión y los IDs.

**Verificación:**
- [ ] `gradlew.bat :app:assembleDebug` y `:app:assembleRelease` compilan.
- [ ] `merged_manifests` de debug/release muestran el App ID correcto.
- [ ] `BuildConfig.ADMOB_BANNER_UNIT_ID` es el de test en debug y el real en release.

**Dependencias:** ninguna.
**Archivos:** `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `docs/integracion-anuncios-admob.md`.
**Tamaño:** S.

#### ADS-02 — Inicializar el SDK en la Application

**Descripción:** inicializar `MobileAds` una sola vez en `MisMonedasApplication`, sin
bloquear el arranque.

**Criterios de aceptación:**
- [ ] `MobileAds.initialize(this) { ... }` se ejecuta en `onCreate`.
- [ ] El callback registra el resultado solo en `debug`.
- [ ] No se inicializa dos veces ni se hace trabajo pesado en el hilo principal.

**Verificación:**
- [ ] La app arranca sin crash ni ANR.
- [ ] Logcat muestra la inicialización en debug.

**Dependencias:** ADS-01.
**Archivos:** `MisMonedasApplication.kt`.
**Tamaño:** XS.

#### ADS-03 — `AdsConfig` y destinos habilitados

**Descripción:** centralizar IDs, flag de test y la lista de destinos con anuncio.

**Criterios de aceptación:**
- [ ] `AdsConfig` expone `bannerUnitId`, `isTest` y `adsEnabledDestinations`.
- [ ] `adsEnabledDestinations = { Home, Transactions, SavingGoals }`; excluye `Chat`.
- [ ] Test unitario verifica la lista de destinos y que no incluye pantallas críticas.

**Verificación:**
- [ ] `:app:testDebugUnitTest --tests "*AdsConfigTest*"`.

**Dependencias:** ADS-01.
**Archivos:** `data/ads/AdsConfig.kt`, `data/ads/AdsConfigTest.kt`.
**Tamaño:** XS.

### Tramo 1 — Política de anuncios según plan

#### ADS-04 — `AdsPolicy` + `AdsViewModel`

**Descripción:** decidir si se muestran anuncios según el plan activo, una sola vez por
sesión y con refresh explícito.

**Criterios de aceptación:**
- [ ] `AdsPolicy` consulta `GET /api/v1/subscriptions/current`; `404`/`null` → FREE.
- [ ] `PREMIUM` y `PREMIUM_TRIAL` → `showAds = false`.
- [ ] `FREE` → `showAds = true`.
- [ ] Error de red → conserva el último valor; sin valor previo → `false`.
- [ ] No hay peticiones por recomposición: una al iniciar sesión + refresh explícito.
- [ ] `AdsViewModel` expone `showAds` y `refresh()`; `AppNavigation` lo crea con `hiltViewModel()`.
- [ ] `SubscriptionViewModel` dispara `refresh()` tras crear o cancelar una suscripción.
- [ ] Tests unitarios cubren FREE, PREMIUM, PREMIUM_TRIAL y error.

**Verificación:**
- [ ] `:app:testDebugUnitTest --tests "*AdsPolicyTest*"`.
- [ ] Manual: usuario FREE ve banner; usuario PREMIUM no; tras cancelar, el banner aparece sin reiniciar.

**Dependencias:** ADS-01.
**Archivos:** `data/ads/AdsPolicy.kt`, `ui/viewmodel/AdsViewModel.kt`, `ui/AppNavigation.kt`, `ui/viewmodel/SubscriptionViewModel.kt`, tests.
**Tamaño:** M.

### Tramo 2 — Banner

#### ADS-05 — Composable `AdBanner` adaptativo

**Descripción:** banner adaptativo con `AndroidView`, medición de altura y liberación
correcta de recursos.

**Criterios de aceptación:**
- [ ] Usa `AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize` (o el tamaño inline adaptativo) con el ancho disponible en dp.
- [ ] `loadAd` se ejecuta una sola vez por instancia, no en cada recomposición.
- [ ] `onAdLoaded` reporta la altura al contenedor; `onAdFailedToLoad` colapsa el banner y registra el error en debug.
- [ ] `DisposableEffect` llama a `destroy()`; el ciclo de vida pausa/reanuda el `AdView`.
- [ ] En debug usa el Ad Unit de test; nunca el real.
- [ ] Sin fugas: entrar y salir de Home 20 veces no incrementa `AdView` vivos.

**Verificación:**
- [ ] Manual con test ads: el banner carga y se ve.
- [ ] Logcat sin `Ad failed to load` inesperado.
- [ ] `:app:assembleDebug`.

**Dependencias:** ADS-01, ADS-02, ADS-03.
**Archivos:** `ui/components/AdBanner.kt`.
**Tamaño:** M.

#### ADS-06 — Integrar en `AppScaffold` y ajustar el FAB

**Descripción:** colocar el banner sobre la barra de navegación y desplazar el FAB central
para que no quede tapado.

**Criterios de aceptación:**
- [ ] `bottomBar = Column { AdBanner(); NavigationBar() }` solo si `showBottomBar && showAds && destino habilitado`.
- [ ] El FAB central se desplaza por la altura real del banner y sigue siendo visible y tappable.
- [ ] El último ítem de las listas no queda oculto tras el banner (padding del Scaffold).
- [ ] Sin solapamiento en portrait, landscape, dark mode y con font scale alto.
- [ ] El banner no aparece en destinos no habilitados ni en pantallas de detalle.

**Verificación:**
- [ ] Matriz manual de pantallas y orientaciones.
- [ ] Capturas de Home/Movimientos/Metas con banner y sin banner.
- [ ] `:app:assembleDebug`.

**Dependencias:** ADS-04, ADS-05.
**Archivos:** `ui/navigation/AppScaffold.kt`, `ui/AppNavigation.kt`.
**Tamaño:** M.

#### ADS-07 — Exclusiones y teclado

**Descripción:** blindar los flujos críticos y el chat.

**Criterios de aceptación:**
- [ ] Sin banner en auth, onboarding, verificación, bloqueo, pago, formularios, confirmación de recordatorios, escáner y Chat.
- [ ] Si en el futuro una pantalla habilitada abre el teclado, el banner se oculta mientras el IME esté visible.
- [ ] Al volver a un destino habilitado, el banner reaparece sin recargar la pantalla.

**Verificación:**
- [ ] Recorrido manual por cada pantalla crítica.
- [ ] Prueba de teclado en Chat: no hay banner.

**Dependencias:** ADS-06.
**Archivos:** `ui/navigation/AppScaffold.kt`, `ui/navigation/AppDestinations.kt`.
**Tamaño:** S.

### Tramo 3 — QA y publicación

#### ADS-08 — QA manual con anuncios de test

**Descripción:** validar la integración completa antes de tocar IDs reales.

**Criterios de aceptación:**
- [ ] Matriz: Android 10/14/16, teléfono/emulador, portrait/landscape, dark/light, font scale.
- [ ] Sin red / red lenta: el banner colapsa sin romper el layout.
- [ ] FREE ve banner; PREMIUM y PREMIUM_TRIAL no.
- [ ] Comprar/cancelar suscripción actualiza el banner sin reiniciar.
- [ ] Sin crashes, ANR ni fugas evidentes.

**Verificación:**
- [ ] Checklist firmado y capturas adjuntas.
- [ ] `:app:testDebugUnitTest` y `:app:assembleDebug` en verde.

**Dependencias:** ADS-04, ADS-06, ADS-07.
**Tamaño:** S.

#### ADS-09 — Release con IDs reales

**Descripción:** activar los IDs reales solo en `release` y verificar el artefacto.

**Criterios de aceptación:**
- [ ] `release` usa `ca-app-pub-5907274046626357/7803102779` y el App ID real.
- [ ] `debug` sigue usando IDs de test.
- [ ] `merged_manifests` y `BuildConfig` de release no contienen IDs de test.
- [ ] R8/ProGuard no rompe el SDK (reglas por defecto).
- [ ] Prueba en pista interna: AdMob registra solicitudes e impresiones.

**Verificación:**
- [ ] `:app:bundleRelease` + inspección del APK/AAB.
- [ ] Consola de AdMob sin avisos de políticas.

**Dependencias:** ADS-08.
**Tamaño:** S.

#### ADS-10 — Play Console: "contiene anuncios" y Data Safety

**Descripción:** alinear las declaraciones de la tienda con la integración de AdMob.

**Criterios de aceptación:**
- [ ] Ficha de Play marca "contiene anuncios".
- [ ] Data Safety declara el ID de publicidad y la recolección de datos de AdMob.
- [ ] Política de privacidad enlazada y con sección de publicidad.
- [ ] Clasificación de contenido y público objetivo revisados (app financiera, no infantil).
- [ ] Copy de suscripción menciona que PREMIUM oculta anuncios.

**Verificación:**
- [ ] Revisión de la ficha en Play Console antes de enviar a revisión.

**Dependencias:** ADS-09.
**Tamaño:** S (documentación).

### Tramo 4 — Futuro (no comprometido)

| ID | Idea | Nota |
|---|---|---|
| ADS-F1 | Anuncio nativo en Movimientos | Requiere diseño de tarjeta y revisión de políticas. |
| ADS-F2 | Rewarded para una función premium | Solo si producto acepta el canibalismo con PREMIUM. |
| ADS-F3 | Consentimiento UMP (EEA/UK) | Necesario antes de apuntar a Europa. |
| ADS-F4 | Eventos `ad_impression`/`ad_failed` en Analytics | Útil para diagnosticar fill rate. |

## 8. Checkpoints

### Checkpoint 0 — Base (ADS-01..03)
- [ ] Debug y release compilan.
- [ ] App ID y Ad Unit ID correctos por buildType.
- [ ] SDK inicializa sin crash.

### Checkpoint 1 — Política (ADS-04)
- [ ] Tests de `AdsPolicy` en verde.
- [ ] FREE ve anuncios; PREMIUM/PREMIUM_TRIAL no.
- [ ] Revisión humana antes de seguir.

### Checkpoint 2 — Banner (ADS-05..07)
- [ ] Banner visible solo en Home/Movimientos/Metas.
- [ ] FAB y contenido sin solapamientos.
- [ ] Sin anuncios en flujos críticos ni Chat.

### Checkpoint 3 — Publicación (ADS-08..10)
- [ ] QA manual completo.
- [ ] Release con IDs reales verificado.
- [ ] Play Console actualizado.

## 9. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Impresiones inválidas por clicks propios | Suspensión de AdMob | IDs de test en debug; nunca tocar anuncios reales; dispositivos de prueba. |
| Banner tapa el FAB o el contenido | Mala UX / quejas | Medir altura real, desplazar FAB y validar padding del Scaffold. |
| Flash de banner a usuario PREMIUM | Molestia y pérdida de confianza | `showAds` inicia en `false` hasta resolver el plan. |
| Fallo de red al consultar el plan | Anuncios a PREMIUM o pérdida de ingresos | Conservar último valor; sin valor previo, `false`; reintentar. |
| Fill rate bajo en LatAm | Ingresos menores | Banner de bajo riesgo; monitorear en AdMob y evaluar nativo después. |
| Política de apps financieras | Rechazo de Play/AdMob | Sin anuncios en flujos críticos; copy claro; revisar políticas antes de release. |
| SDK 25.5.0 incompatible con el proyecto | Build roto | Validar en ADS-01; fallback a 24.9.0. |
| IDs de test en release | Cero ingresos | Verificación de `BuildConfig`/manifest en ADS-09. |
| Fuga de `AdView` | Memoria | `destroy()` + lifecycle en ADS-05. |
| Data Safety incompleto | Rechazo de Play | ADS-10 antes de enviar. |

## 10. Fuera de alcance

- Intersticiales, rewarded, nativos y app open.
- Consentimiento UMP/GDPR para EEA/UK.
- Server-side ads, mediación y A/B testing.
- Anuncios en iOS o web.
- Cambios de precio o beneficios de la suscripción (salvo el copy de "sin anuncios").

## 11. Preguntas abiertas

1. ¿Confirmas excluir Chat del banner? La recomendación es sí (teclado + tap accidental).
2. ¿La política de privacidad actual ya menciona AdMob y el ID de publicidad? Si no, ADS-10 la actualiza.
3. ¿Quieres que la pantalla de suscripción diga explícitamente "Sin anuncios" como beneficio? La recomendación es sí.
4. ¿La cuenta de AdMob ya tiene datos de pago/fiscales completos? Es operativo, no bloquea el código.
