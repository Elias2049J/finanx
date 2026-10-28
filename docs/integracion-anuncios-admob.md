# Integración de anuncios con Google AdMob (Android)

## 1) Preparación en AdMob

La app ya está registrada y tiene un bloque de banner creado:

| Elemento | Valor |
|---|---|
| App | Finanx - Registra tus gastos e ingresos (`com.elias.mismonedas`) |
| App ID | `ca-app-pub-5907274046626357~2927461877` |
| Banner | `ca-app-pub-5907274046626357/7803102779` |

En `debug` se usan los [IDs de test de Google](https://developers.google.com/admob/android/test-ads):

- App ID: `ca-app-pub-3940256099942544~3347511713`
- Banner adaptativo: `ca-app-pub-3940256099942544/9214589741`

El orden de implementación y las decisiones de producto viven en
[`tasks/backlog-anuncios.md`](../tasks/backlog-anuncios.md).

## 2) Configurar el proyecto Android

### Añadir dependencia
En `app/build.gradle.kts` (o `app/build.gradle`):

```kotlin
dependencies {
    implementation("com.google.android.gms:play-services-ads:25.5.0")
}
```

Sincroniza Gradle tras añadirlo.

### Añadir App ID al Manifest
En `app/src/main/AndroidManifest.xml`, dentro de `<application>`:

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="${admobAppId}" />
```

Y en `app/build.gradle.kts`, el placeholder y el Ad Unit ID por buildType:

```kotlin
defaultConfig {
    manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
    buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
}

buildTypes {
    release {
        manifestPlaceholders["admobAppId"] = "ca-app-pub-5907274046626357~2927461877"
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-5907274046626357/7803102779\"")
    }
    debug {
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
    }
}
```

> **Nunca** uses el Ad Unit real en `debug`: genera impresiones inválidas y puede
> suspender la cuenta. El App ID real en `debug` sí es aceptable, pero aquí se usa el
> de test para mantener ambos entornos aislados.

### Inicializar el SDK
Busca si ya existe una clase `Application` (ej. `FinanxApplication`, `MismonedasApp`). Si no existe, créala:

`app/src/main/java/com/elias/mismonedas/FinanxApplication.kt`

```kotlin
package com.elias.mismonedas

import android.app.Application
import com.google.android.gms.ads.MobileAds

class FinanxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
    }
}
```

Y regístrala en `AndroidManifest.xml`:

```xml
<application
    android:name=".FinanxApplication"
    ...>
    ...
</application>
```

## 3) Crear un componente de banner para Compose

Crea `app/src/main/java/com/elias/mismonedas/ui/components/AdBanner.kt`:

```kotlin
package com.elias.mismonedas.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun AdBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-3940256099942544/6300978111" // ID de test
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
```

> El composable definitivo usa banner adaptativo, mide su altura y libera el `AdView`
> con `DisposableEffect` (ver ADS-05 en el backlog).

## 4) Integrarlo en la UI

Mi recomendación para esta app: **poner un banner inferior en pantallas con listas, nunca en flujos críticos**.

Ejemplo en `WalletScreen.kt` (abajo de la lista):

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // ... contenido existente ...

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
    ) {
        AdBanner()
        Spacer(Modifier.height(8.dp))
    }
}
```

Otros sitios razonables: `HomeScreen`, `TransactionsScreen`. **Evita** mostrarlos en login, registro, verificación de email, pago de suscripción o pantallas con formularios sensibles.

## 5) Ocultar anuncios para usuarios Premium

Ya tienes lógica de suscripciones. Lo ideal es **no mostrar anuncios a usuarios PREMIUM**.

> Decisión aprobada: `AdsPolicy` singleton + `AdsViewModel`, ocultando también
> `PREMIUM_TRIAL` (ver ADS-04 en el backlog).

Tienes varias opciones:

1. **Desde ViewModel**: exponer `isPremium` (basado en `SubscriptionService`/plan) y pasarle un `showAds` al composable
2. **Desde Repository**: crear un `AdsPolicy` que decida en base al plan activo

Ejemplo con el composable:

```kotlin
if (showAds) {
    AdBanner()
}
```

## 6) Buenas prácticas y checklist

- [ ] **Usar IDs de test** en debug, reales solo en release
- [ ] **Consentimiento (GDPR/EEA)**: si vas a lanzar en Europa, considera [Google UMP SDK](https://developers.google.com/admob/android/privacy). No es obligatorio siempre, pero sí recomendado
- [ ] **Políticas Google Play**: revisa las políticas para apps financieras. Suele estar permitido con anuncios no invasivos, pero hay restricciones
- [ ] **Rendimiento**: usa banners, evita intersticiales automáticos tras cada acción
- [ ] **UX**: no solapes con FABs/botones importantes (como ya haces con el padding)
- [ ] **ProGuard/R8**: el SDK de AdMob funciona con las reglas por defecto

## Mi recomendación para este repo

**Orden a seguir:** el definido en [`tasks/backlog-anuncios.md`](../tasks/backlog-anuncios.md):
1) dependencia + App ID + inicialización (ADS-01/02),
2) `AdsPolicy` por plan (ADS-04),
3) `AdBanner` + `AppScaffold` (ADS-05/06/07),
4) QA y release (ADS-08/09),
5) Play Console (ADS-10).

La app ya tiene `applicationId`, `MisMonedasApplication` y los IDs configurados; el
siguiente paso es ADS-02 del backlog.
