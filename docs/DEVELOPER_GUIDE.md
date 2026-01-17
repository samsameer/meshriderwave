# MeshRider Wave Android - Developer Guide

**Version:** 2.2.0 | **Last Updated:** January 2026 | **Classification:** Development Reference

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Development Environment](#development-environment)
3. [Project Structure](#project-structure)
4. [Coding Standards](#coding-standards)
5. [Architecture Guidelines](#architecture-guidelines)
6. [Testing](#testing)
7. [Building & Deployment](#building--deployment)
8. [Contributing](#contributing)
9. [Common Tasks](#common-tasks)
10. [Resources](#resources)

---

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Android Studio | 2024.3.1+ (Ladybug) | Required IDE |
| JDK | 17 | Not 21! Use OpenJDK |
| Kotlin | 2.1.0 | Bundled with AGP |
| Git | 2.40+ | Version control |
| Android SDK | API 26-35 | Min 26, Target 35 |

### Quick Setup

```bash
# 1. Clone the repository
git clone https://github.com/doodlelabs/meshrider-wave-android.git
cd meshrider-wave-android

# 2. Set JAVA_HOME (critical!)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# 3. Sync Gradle
./gradlew --refresh-dependencies

# 4. Build debug APK
./gradlew assembleDebug

# 5. Install on device
./gradlew installDebug

# 6. Run tests
./gradlew test
```

### First Run Checklist

- [ ] Gradle sync completes without errors
- [ ] Build succeeds
- [ ] App installs on emulator/device
- [ ] App launches without crashes
- [ ] Permissions granted (Mic, Location)

---

## Development Environment

### Android Studio Configuration

**1. Import Project**

```
File > Open > Select meshrider-wave-android directory
Wait for Gradle sync (may take 5-10 minutes first time)
```

**2. SDK Configuration**

```
Tools > SDK Manager
- SDK Platforms: API 26, 34, 35
- SDK Tools:
  - Android SDK Build-Tools 35.0.0
  - Android SDK Command-line Tools
  - Android SDK Platform-Tools
  - NDK (for native libs)
```

**3. Code Style**

```
File > Settings > Editor > Code Style > Kotlin
Import from: .editorconfig in project root

Or manually set:
- Indent: 4 spaces
- Continuation indent: 4 spaces
- Max line length: 120
- Trailing commas: Always
```

**4. Plugins (Recommended)**

| Plugin | Purpose |
|--------|---------|
| Kotlin | Language support |
| Compose Multiplatform | Compose tooling |
| ktlint | Code formatting |
| Detekt | Static analysis |
| ADB Idea | Quick ADB actions |

### Emulator Setup

```bash
# Create AVD (Android Virtual Device)
# Tools > Device Manager > Create Device

Recommended configuration:
- Device: Pixel 6
- API: 34 (Android 14)
- RAM: 4096 MB
- Internal Storage: 8 GB
- Enable: Hardware keyboard
```

**Emulator Limitations:**
- No real microphone (use device for audio testing)
- No GPS (location mocking available)
- Network may differ from real mesh

### Real Device Setup

```bash
# 1. Enable Developer Options
# Settings > About Phone > Tap "Build Number" 7 times

# 2. Enable USB Debugging
# Settings > Developer Options > USB Debugging

# 3. Connect device
adb devices  # Should show device

# 4. Install and run
./gradlew installDebug
adb shell am start -n com.doodlelabs.meshriderwave/.presentation.MainActivity
```

---

## Project Structure

```
meshrider-wave-android/
├── app/                           # Main application module
│   ├── build.gradle.kts          # App-level build config
│   └── src/
│       ├── main/
│       │   ├── kotlin/com/doodlelabs/meshriderwave/
│       │   │   ├── MeshRiderApp.kt      # Hilt Application
│       │   │   ├── core/                # Core business logic
│       │   │   │   ├── audio/           # Audio processing
│       │   │   │   ├── atak/            # ATAK integration
│       │   │   │   ├── crypto/          # Encryption
│       │   │   │   ├── di/              # Dependency injection
│       │   │   │   ├── location/        # GPS/BFT
│       │   │   │   ├── messaging/       # Offline messaging
│       │   │   │   ├── network/         # P2P networking
│       │   │   │   ├── ptt/             # Push-to-talk
│       │   │   │   ├── radio/           # Radio API
│       │   │   │   ├── sos/             # Emergency SOS
│       │   │   │   └── util/            # Utilities
│       │   │   ├── data/                # Data layer
│       │   │   │   ├── local/           # Local storage
│       │   │   │   └── repository/      # Repository impl
│       │   │   ├── domain/              # Domain layer
│       │   │   │   ├── model/           # Entities
│       │   │   │   └── repository/      # Interfaces
│       │   │   └── presentation/        # UI layer
│       │   │       ├── navigation/      # NavGraph
│       │   │       ├── ui/              # Composables
│       │   │       └── viewmodel/       # ViewModels
│       │   ├── res/                     # Resources
│       │   └── AndroidManifest.xml
│       ├── test/                        # Unit tests
│       └── androidTest/                 # Instrumented tests
├── atak-plugin/                   # ATAK plugin module
│   ├── src/main/java/.../atak/
│   └── atak-stubs/                # SDK stubs for development
├── docs/                          # Documentation
├── gradle/                        # Gradle wrapper
├── build.gradle.kts              # Root build config
├── settings.gradle.kts           # Module settings
├── libs.versions.toml            # Version catalog
├── CLAUDE.md                     # AI assistant context
├── README.md                     # Project overview
└── claude-progress.txt           # Development log
```

### Key Files

| File | Purpose |
|------|---------|
| `libs.versions.toml` | Dependency versions (TOML catalog) |
| `build.gradle.kts` | Root build configuration |
| `app/build.gradle.kts` | App module configuration |
| `MeshRiderApp.kt` | Hilt Application entry point |
| `MainActivity.kt` | Main Activity |
| `NavGraph.kt` | Compose Navigation |
| `AppModule.kt` | Hilt DI modules |

---

## Coding Standards

### Kotlin Style Guide

Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) with these additions:

**1. Naming**

```kotlin
// Classes: PascalCase
class PTTManager

// Functions: camelCase
fun startTransmission()

// Properties: camelCase
val isTransmitting: Boolean

// Constants: SCREAMING_SNAKE_CASE
const val MAX_RETRY_COUNT = 3

// Private backing properties: _camelCase
private val _uiState = MutableStateFlow(...)
val uiState: StateFlow<...> = _uiState.asStateFlow()
```

**2. Formatting**

```kotlin
// Trailing commas (always)
data class Contact(
    val name: String,
    val address: String,  // <- trailing comma
)

// When expressions (exhaustive)
when (state) {
    is State.Loading -> showLoading()
    is State.Success -> showData(state.data)
    is State.Error -> showError(state.message)
}

// Lambda formatting
list.map { item ->
    item.transform()
}

// Single-expression functions
fun isValid() = name.isNotBlank() && address.isNotBlank()
```

**3. Null Safety**

```kotlin
// Prefer safe calls
contact?.let { processContact(it) }

// Use Elvis for defaults
val name = contact?.name ?: "Unknown"

// Avoid !! except in tests
// BAD: contact!!.name
// GOOD: contact?.name ?: throw IllegalStateException("Contact is null")

// Use require/check for preconditions
fun process(data: ByteArray) {
    require(data.isNotEmpty()) { "Data cannot be empty" }
    // ...
}
```

**4. Coroutines**

```kotlin
// Use structured concurrency
viewModelScope.launch {
    val result = withContext(Dispatchers.IO) {
        repository.fetchData()
    }
    _uiState.update { it.copy(data = result) }
}

// Cancel-safe operations
suspend fun fetchData() = coroutineScope {
    val deferred1 = async { api.getData1() }
    val deferred2 = async { api.getData2() }
    Pair(deferred1.await(), deferred2.await())
}

// Handle exceptions
viewModelScope.launch {
    try {
        val data = repository.fetchData()
        _uiState.update { it.copy(data = data) }
    } catch (e: Exception) {
        _uiState.update { it.copy(error = e.message) }
    }
}
```

### Compose Guidelines

**1. Composable Naming**

```kotlin
// Screens: NameScreen
@Composable
fun DashboardScreen(...)

// Components: DescriptiveName
@Composable
fun PTTButton(...)

// Preview: NamePreview
@Preview
@Composable
fun DashboardScreenPreview()
```

**2. State Hoisting**

```kotlin
// BAD: State inside composable
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }
    Button(onClick = { count++ }) { Text("$count") }
}

// GOOD: State hoisted to caller
@Composable
fun Counter(
    count: Int,
    onCountChange: (Int) -> Unit,
) {
    Button(onClick = { onCountChange(count + 1) }) { Text("$count") }
}
```

**3. Modifier Order**

```kotlin
// Follow consistent order
Box(
    modifier = Modifier
        .fillMaxWidth()           // Size
        .padding(16.dp)           // Spacing
        .background(Color.Blue)   // Decoration
        .clickable { }            // Interaction
)
```

**4. Preview Annotations**

```kotlin
@Preview(
    name = "Light Mode",
    showBackground = true,
)
@Preview(
    name = "Dark Mode",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun MyComponentPreview() {
    MeshRiderTheme {
        MyComponent()
    }
}
```

### Documentation

**1. KDoc for Public APIs**

```kotlin
/**
 * Starts PTT transmission on the current channel.
 *
 * This method acquires the floor and begins audio capture.
 * If the floor is already held by another user, returns false.
 *
 * @param priority Transmission priority (default: NORMAL)
 * @param isEmergency Set to true for emergency override
 * @return true if floor acquired, false if floor busy
 * @throws IllegalStateException if not joined to a channel
 */
suspend fun startTransmission(
    priority: PTTPriority = PTTPriority.NORMAL,
    isEmergency: Boolean = false,
): Boolean
```

**2. TODO Comments**

```kotlin
// TODO: Implement proper error handling
// FIXME: Memory leak on rotation
// HACK: Workaround for Android 12 bug (remove when min SDK > 31)
// NOTE: This is intentionally synchronous for testing
```

---

## Architecture Guidelines

### Layer Responsibilities

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ PRESENTATION                                                                 │
│ - Composables render UI                                                     │
│ - ViewModels expose StateFlow                                               │
│ - No business logic here                                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│ DOMAIN                                                                       │
│ - Pure Kotlin (no Android imports)                                          │
│ - Business logic and rules                                                  │
│ - Repository interfaces                                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│ DATA                                                                         │
│ - Repository implementations                                                 │
│ - Data sources (local, remote)                                              │
│ - Data mapping                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│ CORE                                                                         │
│ - Cross-cutting concerns                                                    │
│ - Crypto, Network, Audio                                                    │
│ - Shared by all layers                                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Dependency Injection

```kotlin
// Module definition
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun providePTTManager(
        audioManager: MulticastAudioManager,
        cryptoManager: CryptoManager,
    ): PTTManager = PTTManager(audioManager, cryptoManager)
}

// Interface binding
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindContactRepository(
        impl: ContactRepositoryImpl,
    ): ContactRepository
}

// ViewModel injection
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val pttManager: PTTManager,
    private val contactRepository: ContactRepository,
) : ViewModel()
```

### State Management

```kotlin
// UI State class
data class DashboardUiState(
    val isLoading: Boolean = false,
    val contacts: List<Contact> = emptyList(),
    val error: String? = null,
    val radioStatus: RadioStatus? = null,
)

// ViewModel with StateFlow
@HiltViewModel
class DashboardViewModel @Inject constructor(...) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Update state immutably
    private fun showLoading() {
        _uiState.update { it.copy(isLoading = true, error = null) }
    }

    // Combine multiple flows
    val combinedState = combine(
        contactRepository.contacts,
        pttManager.transmissionState,
    ) { contacts, pttState ->
        DashboardUiState(contacts = contacts, pttState = pttState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}

// Collect in Composable
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> LoadingIndicator()
        uiState.error != null -> ErrorMessage(uiState.error)
        else -> DashboardContent(uiState)
    }
}
```

---

## Testing

### Test Structure

```
app/src/
├── test/                          # Unit tests (JVM)
│   └── kotlin/com/doodlelabs/meshriderwave/
│       ├── core/
│       │   ├── crypto/
│       │   │   └── CryptoManagerTest.kt
│       │   └── ptt/
│       │       └── PTTManagerTest.kt
│       └── data/
│           └── repository/
│               └── ContactRepositoryTest.kt
└── androidTest/                   # Instrumented tests (device)
    └── kotlin/com/doodlelabs/meshriderwave/
        ├── ui/
        │   └── DashboardScreenTest.kt
        └── integration/
            └── NetworkIntegrationTest.kt
```

### Unit Testing

```kotlin
// CryptoManagerTest.kt
class CryptoManagerTest {
    private lateinit var cryptoManager: CryptoManager

    @Before
    fun setup() {
        cryptoManager = CryptoManager()
    }

    @Test
    fun `generateKeyPair returns valid 32-byte public key`() {
        val keyPair = cryptoManager.generateKeyPair()

        assertEquals(32, keyPair.publicKey.size)
        assertEquals(64, keyPair.secretKey.size)
    }

    @Test
    fun `encrypt and decrypt roundtrip succeeds`() {
        val sender = cryptoManager.generateKeyPair()
        val receiver = cryptoManager.generateKeyPair()
        val message = "Hello, World!".toByteArray()

        val encrypted = cryptoManager.encryptMessage(
            message = message,
            recipientPublicKey = receiver.publicKey,
            ownPublicKey = sender.publicKey,
            ownSecretKey = sender.secretKey,
        )

        val senderKeyOut = ByteArray(32)
        val decrypted = cryptoManager.decryptMessage(
            ciphertext = encrypted,
            senderPublicKeyOut = senderKeyOut,
            ownPublicKey = receiver.publicKey,
            ownSecretKey = receiver.secretKey,
        )

        assertArrayEquals(message, decrypted)
        assertArrayEquals(sender.publicKey, senderKeyOut)
    }

    @Test
    fun `decrypt with wrong key fails`() {
        val sender = cryptoManager.generateKeyPair()
        val receiver = cryptoManager.generateKeyPair()
        val wrongKey = cryptoManager.generateKeyPair()

        val encrypted = cryptoManager.encryptMessage(
            message = "Secret".toByteArray(),
            recipientPublicKey = receiver.publicKey,
            ownPublicKey = sender.publicKey,
            ownSecretKey = sender.secretKey,
        )

        val decrypted = cryptoManager.decryptMessage(
            ciphertext = encrypted,
            senderPublicKeyOut = ByteArray(32),
            ownPublicKey = wrongKey.publicKey,
            ownSecretKey = wrongKey.secretKey,
        )

        assertNull(decrypted)
    }
}
```

### Compose UI Testing

```kotlin
// DashboardScreenTest.kt
@HiltAndroidTest
class DashboardScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun dashboardScreen_showsNetworkOrb() {
        composeTestRule.onNodeWithContentDescription("Network Status").assertExists()
    }

    @Test
    fun pttButton_startsTransmission_whenPressed() {
        composeTestRule
            .onNodeWithContentDescription("PTT Button")
            .performClick()

        composeTestRule
            .onNodeWithText("Transmitting")
            .assertIsDisplayed()
    }

    @Test
    fun radioStatus_showsConnectedState() {
        // Mock connected state
        composeTestRule
            .onNodeWithText("Connected")
            .assertIsDisplayed()
    }
}
```

### Running Tests

```bash
# All unit tests
./gradlew test

# Specific test class
./gradlew test --tests "CryptoManagerTest"

# With coverage report
./gradlew testDebugUnitTestCoverage

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Specific instrumented test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.doodlelabs.meshriderwave.ui.DashboardScreenTest
```

---

## Building & Deployment

### Build Variants

| Variant | Use Case | Signing |
|---------|----------|---------|
| `debug` | Development | Debug keystore |
| `release` | Production | Release keystore |

### Build Commands

```bash
# Debug build
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release build
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# Bundle (for Play Store)
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab

# Install debug on device
./gradlew installDebug

# Clean build
./gradlew clean assembleDebug
```

### Signing Configuration

```kotlin
// app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            storeFile = file("../keystore/release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "meshrider"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}
```

### ProGuard/R8 Rules

```proguard
# proguard-rules.pro

# Keep libsodium JNI
-keep class com.goterl.lazysodium.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep data classes for JSON serialization
-keep class com.doodlelabs.meshriderwave.domain.model.** { *; }
```

---

## Contributing

### Git Workflow

```
main ──────────────────────────────────────────►
        │
        └── feature/FEAT-001-opus-codec ────────►
                    │
                    └── PR → Code Review → Merge
```

### Branch Naming

```
feature/FEAT-XXX-description   # New features
bugfix/BUG-XXX-description     # Bug fixes
hotfix/HOT-XXX-description     # Production hotfixes
refactor/REF-XXX-description   # Refactoring
docs/DOC-XXX-description       # Documentation
```

### Commit Messages

```
feat(ptt): add emergency transmission priority

- Add PTTPriority.EMERGENCY level
- Implement floor override for emergency
- Update UI to show emergency indicator

Closes #123
```

Format: `type(scope): subject`

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation |
| `style` | Formatting |
| `refactor` | Code restructure |
| `test` | Testing |
| `chore` | Build/tooling |

### Pull Request Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests pass
- [ ] UI tests pass
- [ ] Manual testing completed

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-reviewed code
- [ ] Commented complex code
- [ ] Updated documentation
- [ ] No new warnings

## Screenshots (if UI changes)
```

### Code Review Guidelines

**Reviewer Checklist:**

- [ ] Code compiles and tests pass
- [ ] Logic is correct and complete
- [ ] Error handling is appropriate
- [ ] No security vulnerabilities
- [ ] Performance is acceptable
- [ ] Code is readable and documented
- [ ] Follows project conventions

---

## Common Tasks

### Add a New Screen

```kotlin
// 1. Create Screen composable
// presentation/ui/screens/newfeature/NewFeatureScreen.kt
@Composable
fun NewFeatureScreen(
    onNavigateBack: () -> Unit,
    viewModel: NewFeatureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("New Feature") }) },
    ) { padding ->
        // Content
    }
}

// 2. Create ViewModel
// presentation/viewmodel/NewFeatureViewModel.kt
@HiltViewModel
class NewFeatureViewModel @Inject constructor(
    private val repository: SomeRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NewFeatureUiState())
    val uiState: StateFlow<NewFeatureUiState> = _uiState.asStateFlow()
}

// 3. Add route to NavGraph.kt
sealed class Screen(val route: String) {
    data object NewFeature : Screen("new_feature")
}

// In NavHost
composable(Screen.NewFeature.route) {
    NewFeatureScreen(onNavigateBack = { navController.popBackStack() })
}
```

### Add a New Core Module

```kotlin
// 1. Create manager class
// core/newmodule/NewModuleManager.kt
@Singleton
class NewModuleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coroutineScope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ModuleState())
    val state: StateFlow<ModuleState> = _state.asStateFlow()

    fun doSomething() {
        // Implementation
    }
}

// 2. Add to Hilt module
// core/di/AppModule.kt
@Provides
@Singleton
fun provideNewModuleManager(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
): NewModuleManager = NewModuleManager(context, scope)
```

### Add a New Dependency

```toml
# 1. Add to libs.versions.toml
[versions]
newlib = "1.0.0"

[libraries]
newlib-core = { group = "com.example", name = "newlib", version.ref = "newlib" }

# 2. Add to app/build.gradle.kts
dependencies {
    implementation(libs.newlib.core)
}

# 3. Sync Gradle
./gradlew --refresh-dependencies
```

---

## Resources

### Official Documentation

| Resource | URL |
|----------|-----|
| Kotlin | https://kotlinlang.org/docs/ |
| Jetpack Compose | https://developer.android.com/jetpack/compose |
| Hilt | https://developer.android.com/training/dependency-injection/hilt-android |
| Coroutines | https://kotlinlang.org/docs/coroutines-guide.html |
| Flow | https://kotlinlang.org/docs/flow.html |

### Project References

| Document | Purpose |
|----------|---------|
| [README.md](../README.md) | Project overview |
| [ARCHITECTURE.md](ARCHITECTURE.md) | System design |
| [API_REFERENCE.md](API_REFERENCE.md) | API documentation |
| [FIELD_TESTING.md](FIELD_TESTING.md) | Testing procedures |
| [CLAUDE.md](../CLAUDE.md) | AI assistant context |

### Third-Party Libraries

| Library | Documentation |
|---------|---------------|
| libsodium | https://doc.libsodium.org/ |
| WebRTC | https://webrtc.github.io/webrtc-org/ |
| ZXing | https://github.com/zxing/zxing |
| OkHttp | https://square.github.io/okhttp/ |

### Internal Tools

```bash
# View logs
adb logcat -s MeshRider:*

# Clear app data
adb shell pm clear com.doodlelabs.meshriderwave

# Take screenshot
adb shell screencap /sdcard/screen.png && adb pull /sdcard/screen.png

# Record screen
adb shell screenrecord /sdcard/demo.mp4
# Ctrl+C to stop, then:
adb pull /sdcard/demo.mp4

# Profile app
# Android Studio > View > Tool Windows > Profiler
```

---

## Appendix: Troubleshooting

### Build Issues

**Gradle sync fails with "Toolchain" error**

```bash
# Use JDK 17, not 21
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew --stop
./gradlew --refresh-dependencies
```

**"Could not find" dependency**

```bash
# Clear Gradle cache
rm -rf ~/.gradle/caches
./gradlew clean build
```

**Out of memory**

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m
```

### Runtime Issues

**App crashes on launch**

```bash
# Check crash log
adb logcat -s AndroidRuntime:E

# Common causes:
# - Missing permissions in manifest
# - Hilt component not initialized
# - Native library not loaded
```

**Audio not working**

```bash
# Check permissions
adb shell dumpsys package com.doodlelabs.meshriderwave | grep permission

# Check audio focus
adb shell dumpsys audio
```

**Network connection fails**

```bash
# Check network config
adb shell ip addr

# Check connectivity
adb shell ping 10.223.232.1
```

---

**Document Control:**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Jan 2026 | Jabbir Basha | Initial release |
| 2.0 | Jan 2026 | Claude Code | Complete rewrite |

---

*Copyright (C) 2024-2026 DoodleLabs Singapore Pte Ltd. All Rights Reserved.*
