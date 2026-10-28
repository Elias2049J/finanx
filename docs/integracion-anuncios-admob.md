# Integración de anuncios con Google AdMob (Android)

## 1) Preparación en AdMob
Antes de tocar código:

1. Crear una cuenta en [Google AdMob](https://admob.google.com/)
2. Registrar la app (`com.elias.mismonedas`)
3. Crear bloques de anuncios. Para empezar, usa un **Banner** (menos intrusivo)
4. Guardar:
   - `APP_ID` (`ca-app-pub-xxxx~yyyy`)
   - `AD_UNIT_ID` (para el banner). Mientras pruebas usa los [IDs de test de Google](https://developers.google.com/admob/android/test-ads)

## 2) Configurar el proyecto Android

### Añadir dependencia
En `app/build.gradle.kts` (o `app/build.gradle`):

```kotlin
dependencies {
    implementation("com.google.android.gms:play-services-ads:24.6.0")
}
```

Sincroniza Gradle tras añadirlo.

### Añadir App ID al Manifest
En `app/src/main/AndroidManifest.xml`, dentro de `<application>` añade:

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-XXXXXXXXXXXXXXX~XXXXXXXXXX" />
```

> Usa el App ID real solo en `release`. Para `debug` puedes usar el App ID de test o uno real, pero **siempre usa Ad Unit IDs de test mientras desarrollas**.

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

**Orden a seguir:** 1) Añadir dependencia + App ID + inicialización -> 2) Crear `AdBanner` -> 3) Integrar solo en `WalletScreen` con flag para ocultar a Premium -> 4) Probar con IDs de test -> 5) Pasar a IDs reales.

Si me dices el `applicationId`, si ya tienes alguna clase `Application` o quieres usar otro formato de anuncio (intersticial/recompensado), lo adapto al código exacto de Finanx sin inventar nada.
