# Mi Reloj

App Android propia para el smartwatch Lhotse Ultimate GPS 217 (modelo IDO **ID217G**), que ya no recibe actualizaciones.

Se conecta directo al reloj por Bluetooth, sin VeryFit, y guarda todo solo en el teléfono.

## Qué hace (v0.1)

- Busca, conecta y vincula el reloj
- Mide el pulso en el momento
- Muestra pasos, calorías, distancia y minutos activos del día
- Sincroniza el historial de pulso (por día) y los ejercicios registrados en el reloj

## Descargar

En el celular abre la sección **Releases** de este repositorio y descarga `MiReloj.apk` de la versión más reciente.

## Cómo está hecho

- Kotlin + Jetpack Compose
- Kit oficial de IDO, el fabricante del reloj: [idoosmart/android_sdk](https://github.com/idoosmart/android_sdk) (licencia MIT), archivos en `app/libs`
- GitHub Actions compila el APK en cada cambio (`.github/workflows/build.yml`)
