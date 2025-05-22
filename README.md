# AppHWgps

Aplicación Android para el seguimiento de ubicación en tiempo real utilizando GPS.

## Características

- Seguimiento de ubicación en tiempo real
- Soporte para GPS y ubicación por red
- Interfaz de usuario moderna con Jetpack Compose
- Manejo de permisos de ubicación
- Detección de ubicaciones simuladas
- Actualizaciones de ubicación configurables

## Requisitos

- Android Studio Hedgehog | 2023.1.1 o superior
- Kotlin 1.9.0 o superior
- Android SDK 34
- Gradle 8.2

## Configuración

1. Clona el repositorio:
```bash
git clone https://github.com/tu-usuario/AppHWgps.git
```

2. Abre el proyecto en Android Studio

3. Sincroniza el proyecto con Gradle

4. Ejecuta la aplicación en un emulador o dispositivo físico

## Permisos

La aplicación requiere los siguientes permisos:
- `ACCESS_FINE_LOCATION`: Para obtener ubicación precisa
- `ACCESS_COARSE_LOCATION`: Para obtener ubicación aproximada

## Tecnologías Utilizadas

- Kotlin
- Jetpack Compose
- Google Play Services Location
- Coroutines
- Flow
- Material Design 3

## Estructura del Proyecto

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/apphwgps/
│   │   │   ├── LocationManager.kt    # Gestor de ubicación
│   │   │   ├── MainActivity.kt       # Actividad principal
│   │   │   └── ui/                   # Componentes de UI
│   │   └── res/                      # Recursos
│   └── test/                         # Pruebas unitarias
└── build.gradle                      # Configuración de Gradle
```

## Contribuir

1. Haz un Fork del proyecto
2. Crea una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

## Licencia

Este proyecto está bajo la Licencia MIT - ver el archivo [LICENSE](LICENSE) para más detalles. 