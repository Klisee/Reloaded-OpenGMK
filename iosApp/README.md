# Reloaded OpenGMK iOS

La UI compartida está en `shared/src/commonMain`. El host SwiftUI vive en `iosApp/iosApp`.

## Xcode

1. Crear un proyecto iOS con Xcode en esta carpeta.
2. Añadir `iOSApp.swift`, `ComposeView.swift` e `Info.plist`.
3. Añadir una fase de build antes de compilar:
   `cd "$SRCROOT/.." && ./gradlew :shared:embedAndSignAppleFrameworkForXcode`
4. Enlazar `Shared.framework` generado por Kotlin.
5. El motor iOS se conectará después mediante `Bridge.h` y `IosGameStore`.

La app usa por defecto el tema Liquid Glass y el host `MainViewController`.
