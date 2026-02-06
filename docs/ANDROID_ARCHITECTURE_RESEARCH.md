# Deep Research: Android Architecture Patterns for Billion-Dollar Tactical/Military-Grade PTT Applications

**Research Date:** February 6, 2026  
**Target Platform:** Android 8.0+ (API 26+) to Android 15 (API 35)  
**Project Reference:** MeshRider Wave Android - Military-Grade PTT for DoodleLabs Mesh Radios  
**Classification:** Architecture Research - Military-Grade Reliability

---

## Executive Summary

This research provides comprehensive architectural recommendations for building a billion-dollar tactical PTT application. The analysis covers modern Android architecture (2025-2026), Kotlin Multiplatform Mobile, real-time audio processing, background execution, networking, and security - all tailored for mission-critical military use cases.

**Key Recommendations:**
- Use **Clean Architecture + MVI** with Compose 1.7+ for reactive UI
- Implement **Native C++ audio** via Oboe for <20ms latency
- Adopt **KMM 2.1+** for sharing 60-70% of code between Android/iOS
- Deploy **3GPP MCPTT-compliant** floor control with encrypted signaling
- Maintain **FIPS 140-2 Level 1** security with Android Keystore

---

## 1. MODERN ANDROID ARCHITECTURE (2025-2026)

### 1.1 Kotlin 2.1+ Best Practices

#### Language Features for Tactical Apps

```kotlin
// Kotlin 2.1.0 - K2 Compiler with new backend
// File: build.gradle.kts (project level)
plugins {
    kotlin("android") version "2.1.0" apply false
    kotlin("multiplatform") version "2.1.0" apply false
}

// Module-level build.gradle.kts
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-Xcontext-receivers"  // Kotlin 2.1 preview feature
        )
    }
}
```

#### Data Classes for Immutable State

```kotlin
// Tactical PTT State - Immutable by default
@Immutable
data class PTTState(
    val channel: PTTChannel? = null,
    val transmitStatus: TransmitStatus = TransmitStatus.IDLE,
    val currentSpeaker: PTTMember? = null,
    val floorPriority: FloorPriority = FloorPriority.NORMAL,
    val networkQuality: NetworkQuality = NetworkQuality.UNKNOWN,
    val error: PTTError? = null
) {
    val isTransmitting: Boolean 
        get() = transmitStatus == TransmitStatus.TRANSMITTING
    
    val canRequestFloor: Boolean
        get() = transmitStatus == TransmitStatus.IDLE || 
                transmitStatus == TransmitStatus.RECEIVING
}

// Sealed interface for type-safe state machine
sealed interface TransmitStatus {
    data object IDLE : TransmitStatus
    data object REQUESTING : TransmitStatus
    data object GRANTED : TransmitStatus
    data object TRANSMITTING : TransmitStatus
    data object RECEIVING : TransmitStatus
    data object BLOCKED : TransmitStatus
    data class ERROR(val reason: String) : TransmitStatus
}

// Value class for type safety (Kotlin 1.5+)
@JvmInline
value class ChannelId(val value: String) {
    init { require(value.isNotBlank()) { "ChannelId cannot be blank" } }
}

@JvmInline
value class TalkgroupId(val value: Int) {
    init { require(value in 1..255) { "TalkgroupId must be 1-255" } }
}
```

#### Context Receivers for Scoped Operations (Kotlin 2.1)

```kotlin
// Define context for PTT operations
interface PTTContext {
    val audioEngine: AudioEngine
    val networkManager: NetworkManager
    val cryptoManager: CryptoManager
}

// Use context receiver for operations that need PTT context
context(PTTContext)
suspend fun PTTChannel.startTransmission(): Result<Transmission> {
    // Automatically has access to audioEngine, networkManager, cryptoManager
    val encodedAudio = audioEngine.captureAudio()
    val encrypted = cryptoManager.encrypt(encodedAudio)
    return networkManager.transmit(this@startTransmission.id, encrypted)
}

// Usage with context
with(pttContext) {
    channel.startTransmission()
}
```

### 1.2 Jetpack Compose 1.7+ for Tactical UI

#### Material 3 Dynamic Theming

```kotlin
// Tactical theme with military-grade contrast ratios
@Composable
fun TacticalTheme(
    darkTheme: Boolean = true,  // Tactical apps use dark theme
    dynamicColor: Boolean = false,  // Disable for consistent military UI
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Military applications need consistent colors, not dynamic
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(LocalContext.current) 
            else dynamicLightColorScheme(LocalContext.current)
        }
        darkTheme -> TacticalDarkColorScheme
        else -> TacticalLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TacticalTypography,
        shapes = TacticalShapes,
        content = content
    )
}

// Military-grade color scheme (WCAG AAA compliant)
private val TacticalDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00B4D8),           // Starlink cyan - high visibility
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0077B6),  // Deeper cyan
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF48CAE4),         // Light cyan
    onSecondary = Color.Black,
    tertiary = Color(0xFFDC2626),          // Emergency red (SOS only)
    onTertiary = Color.White,
    background = Color(0xFF000000),        // Pure black for OLED
    onBackground = Color(0xFFFFFFFF),      // White
    surface = Color(0xFF0A0A0A),           // Near-black
    onSurface = Color(0xFFFFFFFF),
    error = Color(0xFFDC2626),             // Critical red
    onError = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),    // Elevated surface
    onSurfaceVariant = Color(0xFFA3A3A3),  // Secondary text
    outline = Color(0xFF404040)            // Borders
)
```

#### Tactical UI Components

```kotlin
@Composable
fun PTTButton(
    state: PTTState,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Haptic feedback on press/release
    val haptics = LocalHapticFeedback.current
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onPress()
        } else {
            onRelease()
        }
    }
    
    // Animated scale and glow when transmitting
    val scale by animateFloatAsState(
        targetValue = when (state.transmitStatus) {
            is TransmitStatus.TRANSMITTING -> 1.05f
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "ptt_scale"
    )
    
    val glowAlpha by animateFloatAsState(
        targetValue = when (state.transmitStatus) {
            is TransmitStatus.TRANSMITTING -> 0.8f
            is TransmitStatus.RECEIVING -> 0.4f
            else -> 0f
        },
        animationSpec = tween(300),
        label = "ptt_glow"
    )
    
    Box(
        modifier = modifier
            .size(200.dp)
            .scale(scale)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> interactionSource.tryEmit(
                                PressInteraction.Press(Offset.Zero)
                            )
                            PointerEventType.Release -> interactionSource.tryEmit(
                                PressInteraction.Release(PressInteraction.Press(Offset.Zero))
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Glow effect when active
        if (glowAlpha > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        when (state.transmitStatus) {
                            is TransmitStatus.TRANSMITTING -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }.copy(alpha = glowAlpha)
                    )
                    .blur(20.dp)
            )
        }
        
        // Main button
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = when (state.transmitStatus) {
                is TransmitStatus.TRANSMITTING -> MaterialTheme.colorScheme.tertiary
                is TransmitStatus.ERROR -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            shadowElevation = if (isPressed) 0.dp else 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (state.transmitStatus) {
                        is TransmitStatus.TRANSMITTING -> Icons.Filled.Mic
                        is TransmitStatus.RECEIVING -> Icons.Filled.Speaker
                        else -> Icons.Filled.MicNone
                    },
                    contentDescription = "PTT",
                    modifier = Modifier.size(80.dp),
                    tint = when (state.transmitStatus) {
                        is TransmitStatus.ERROR -> MaterialTheme.colorScheme.onError
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
        }
    }
}

// Tactical status indicator with military symbology
@Composable
fun TacticalStatusIndicator(
    status: TacticalStatus,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status.isCritical) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_pulse"
    )
    
    Canvas(modifier = modifier.size(16.dp)) {
        drawCircle(
            color = when (status) {
                TacticalStatus.ONLINE -> Color(0xFF22C55E)      // Green
                TacticalStatus.STANDBY -> Color(0xFFEAB308)    // Yellow
                TacticalStatus.ALERT -> Color(0xFFF97316)      // Orange
                TacticalStatus.CRITICAL -> Color(0xFFDC2626)   // Red
                TacticalStatus.OFFLINE -> Color(0xFF6B7280)    // Gray
            },
            radius = size.minDimension / 2 * if (status.isCritical) pulseScale else 1f
        )
    }
}
```

#### Responsive Layouts for Multi-Form Factor

```kotlin
@Composable
fun TacticalScreen(
    windowSizeClass: WindowSizeClass = calculateWindowSizeClass()
) {
    when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            // Phone portrait - single column
            PhoneTacticalLayout()
        }
        WindowWidthSizeClass.Medium -> {
            // Phone landscape / small tablet
            CompactTabletLayout()
        }
        WindowWidthSizeClass.Expanded -> {
            // Large tablet / foldable expanded
            ExpandedTacticalLayout()
        }
    }
}

@Composable
fun ExpandedTacticalLayout() {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left sidebar - channels and contacts (30%)
        SidebarPanel(
            modifier = Modifier.weight(0.3f)
        )
        
        // Main content - PTT and map (50%)
        MainPanel(
            modifier = Modifier.weight(0.5f)
        )
        
        // Right sidebar - telemetry and status (20%)
        TelemetryPanel(
            modifier = Modifier.weight(0.2f)
        )
    }
}
```

### 1.3 Clean Architecture + MVVM + MVI Patterns

#### Layered Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  UI (Jetpack Compose)                                   ││
│  │  - Composables                                          ││
│  │  - ViewModels (MVI pattern)                             ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      DOMAIN LAYER                            │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  Use Cases                                              ││
│  │  - RequestFloorUseCase                                  ││
│  │  - ReleaseFloorUseCase                                  ││
│  │  - JoinChannelUseCase                                   ││
│  └─────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────┐│
│  │  Repository Interfaces                                  ││
│  │  - PTTRepository                                        ││
│  │  - AudioRepository                                      ││
│  │  - NetworkRepository                                    ││
│  └─────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────┐│
│  │  Models (Domain Entities)                               ││
│  │  - PTTChannel, PTTMember, FloorState                    ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       DATA LAYER                             │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  Repository Implementations                             ││
│  └─────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────┐│
│  │  Data Sources                                           ││
│  │  - Local (Room, DataStore)                              ││
│  │  - Remote (WebSocket, Multicast)                        ││
│  │  - Native (JNI/NDK)                                     ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

#### MVI Pattern Implementation

```kotlin
// MVI Contract
interface MVIContract<State : UiState, Event : UiEvent, Effect : UiEffect> {
    interface UiState
    interface UiEvent
    interface UiEffect
}

// PTT-specific MVI
sealed interface PTTEvent : MVIContract.UiEvent {
    data object OnPttPress : PTTEvent
    data object OnPttRelease : PTTEvent
    data class OnChannelSelected(val channel: PTTChannel) : PTTEvent
    data class OnPriorityChanged(val priority: FloorPriority) : PTTEvent
    data object OnEmergencyActivated : PTTEvent
}

sealed interface PTTEffect : MVIContract.UiEffect {
    data class ShowToast(val message: String) : PTTEffect
    data class PlayTone(val tone: ToneType) : PTTEffect
    data class Vibrate(val pattern: VibrationPattern) : PTTEffect
    data class NavigateToEmergency(val channelId: String) : PTTEffect
}

// ViewModel with MVI
@HiltViewModel
class PTTViewModel @Inject constructor(
    private val requestFloorUseCase: RequestFloorUseCase,
    private val releaseFloorUseCase: ReleaseFloorUseCase,
    private val joinChannelUseCase: JoinChannelUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(PTTState())
    val state: StateFlow<PTTState> = _state.asStateFlow()

    private val _effect = Channel<PTTEffect>()
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: PTTEvent) {
        when (event) {
            is PTTEvent.OnPttPress -> handlePttPress()
            is PTTEvent.OnPttRelease -> handlePttRelease()
            is PTTEvent.OnChannelSelected -> selectChannel(event.channel)
            is PTTEvent.OnPriorityChanged -> updatePriority(event.priority)
            is PTTEvent.OnEmergencyActivated -> activateEmergency()
        }
    }

    private fun handlePttPress() {
        viewModelScope.launch {
            _state.update { it.copy(transmitStatus = TransmitStatus.REQUESTING) }
            
            requestFloorUseCase(_state.value.channel!!)
                .onSuccess { 
                    _state.update { it.copy(transmitStatus = TransmitStatus.TRANSMITTING) }
                    _effect.send(PTTEffect.Vibrate(VibrationPattern.TX_START))
                    _effect.send(PTTEffect.PlayTone(ToneType.TX_START))
                }
                .onFailure { error ->
                    _state.update { it.copy(transmitStatus = TransmitStatus.ERROR(error.message)) }
                    _effect.send(PTTEffect.ShowToast("Floor denied: ${error.message}"))
                }
        }
    }

    private fun handlePttRelease() {
        viewModelScope.launch {
            releaseFloorUseCase(_state.value.channel!!)
            _state.update { it.copy(transmitStatus = TransmitStatus.IDLE) }
            _effect.send(PTTEffect.Vibrate(VibrationPattern.TX_END))
        }
    }
}

// Compose UI with MVI
@Composable
fun PTTScreen(
    viewModel: PTTViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    // Collect side effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PTTEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is PTTEffect.Vibrate -> vibrator.vibrate(effect.pattern)
                is PTTEffect.PlayTone -> audioManager.playTone(effect.tone)
                // ...
            }
        }
    }
    
    PTTContent(
        state = state,
        onEvent = viewModel::onEvent
    )
}

@Composable
private fun PTTContent(
    state: PTTState,
    onEvent: (PTTEvent) -> Unit
) {
    Column {
        PTTButton(
            state = state,
            onPress = { onEvent(PTTEvent.OnPttPress) },
            onRelease = { onEvent(PTTEvent.OnPttRelease) }
        )
        
        ChannelSelector(
            channels = state.channels,
            selectedChannel = state.channel,
            onChannelSelected = { onEvent(PTTEvent.OnChannelSelected(it)) }
        )
    }
}
```

### 1.4 Hilt 2.53+ Dependency Injection

#### Module Structure

```kotlin
// AppModule.kt - Application-level dependencies
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideCoroutineDispatcher(): CoroutineDispatcher = 
        Dispatchers.IO
    
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = 
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

// AudioModule.kt - Audio dependencies
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    
    @Binds
    abstract fun bindAudioEngine(
        impl: NativeAudioEngine
    ): AudioEngine
    
    @Binds
    abstract fun bindCodecManager(
        impl: OpusCodecManager
    ): CodecManager
}

// NetworkModule.kt - Network dependencies
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        certificatePinner: CertificatePinner
    ): OkHttpClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_3))
        .connectionSpecs(
            listOf(
                ConnectionSpec.MODERN_TLS,
                ConnectionSpec.COMPATIBLE_TLS
            )
        )
        .certificatePinner(certificatePinner)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    
    @Provides
    @Singleton
    fun provideWebSocketManager(
        client: OkHttpClient,
        @ApplicationContext context: Context
    ): WebSocketManager = WebSocketManager(client, context)
    
    @Provides
    @Singleton
    @Named("pttMulticastSocket")
    fun providePTTMulticastSocket(): MulticastSocket {
        return MulticastSocket(5004).apply {
            // QoS: Expedited Forwarding (EF) for voice traffic
            trafficClass = 0xB8  // DSCP 46 << 2
            reuseAddress = true
        }
    }
}

// Qualifiers for different dispatchers
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IODispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides
    @IODispatcher
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO
    
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
    
    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}
```

#### Assisted Injection for Dynamic Dependencies

```kotlin
// Factory for creating PTT sessions with runtime parameters
@AssistedFactory
interface PTTSessionFactory {
    fun create(
        @Assisted channelId: ChannelId,
        @Assisted talkgroupId: TalkgroupId,
        @Assisted networkInterface: String
    ): PTTSession
}

class PTTSession @AssistedInject constructor(
    @Assisted private val channelId: ChannelId,
    @Assisted private val talkgroupId: TalkgroupId,
    @Assisted private val networkInterface: String,
    private val audioEngine: AudioEngine,
    private val cryptoManager: CryptoManager,
    private val floorControlManager: FloorControlManager
) {
    // Session implementation
}

// Usage
class PTTManager @Inject constructor(
    private val sessionFactory: PTTSessionFactory
) {
    fun createSession(channel: PTTChannel): PTTSession {
        return sessionFactory.create(
            channelId = ChannelId(channel.id),
            talkgroupId = TalkgroupId(channel.talkgroupId),
            networkInterface = "wlan0"
        )
    }
}
```

### 1.5 Coroutines vs Flow vs RxJava for Real-Time

#### Decision Matrix

| Use Case | Recommendation | Latency Target |
|----------|---------------|----------------|
| UI State Updates | StateFlow | <16ms (1 frame) |
| Audio Pipeline | Callbacks + Channel | <5ms |
| Network Events | SharedFlow (reliable) | <50ms |
| PTT Signaling | Coroutines + Flow | <100ms |
| Database Operations | Flow (Room) | <50ms |
| Crypto Operations | Coroutines (Dispatchers.Default) | <20ms |

#### Implementation Patterns

```kotlin
// Real-time audio - Use callbacks for <5ms latency
class AudioEngine @Inject constructor(
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    // Callback-based for lowest latency
    private var audioCallback: AudioCallback? = null
    
    fun setAudioCallback(callback: AudioCallback) {
        audioCallback = callback
    }
    
    interface AudioCallback {
        fun onAudioFrame(frame: ByteArray, timestamp: Long)
    }
}

// UI State - StateFlow with WhileSubscribed
class PTTViewModel @Inject constructor(
    repository: PTTRepository
) : ViewModel() {
    
    val uiState: StateFlow<PTTUiState> = repository
        .floorStateFlow
        .combine(repository.audioLevelFlow) { floor, level ->
            PTTUiState(floorStatus = floor, audioLevel = level)
        }
        .catch { emit(PTTUiState.Error(it)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PTTUiState.Loading
        )
}

// Network events - SharedFlow with replay
class NetworkManager @Inject constructor() {
    // Replay last event for late subscribers
    private val _incomingMessages = MutableSharedFlow<NetworkMessage>(
        replay = 1,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingMessages = _incomingMessages.asSharedFlow()
    
    suspend fun broadcast(message: NetworkMessage) {
        _incomingMessages.emit(message)
    }
}

// Periodic operations with ticker
fun CoroutineScope.launchPeriodicPTTBeacon(
    intervalMs: Long,
    action: suspend () -> Unit
): Job = launch {
    while (isActive) {
        action()
        delay(intervalMs)
    }
}

// Timeout handling for floor control
suspend fun requestFloorWithTimeout(
    channelId: String,
    timeoutMs: Long = 5000
): FloorResult = withTimeoutOrNull(timeoutMs) {
    floorControlManager.requestFloor(channelId)
} ?: FloorResult.Timeout

// Channel for backpressure handling
class AudioPipeline {
    private val audioChannel = Channel<ByteArray>(
        capacity = Channel.BUFFERED,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    suspend fun processAudio() {
        audioChannel.consumeEach { frame ->
            // Process frame
            encodeAndTransmit(frame)
        }
    }
    
    fun onAudioCaptured(frame: ByteArray) {
        audioChannel.trySend(frame)
    }
}
```

### 1.6 KSP (Kotlin Symbol Processing)

#### Migration from KAPT to KSP

```kotlin
// build.gradle.kts (project level)
plugins {
    alias(libs.plugins.ksp) version "2.1.0-1.0.29" apply false
}

// build.gradle.kts (module level)
plugins {
    alias(libs.plugins.ksp)
}

dependencies {
    // Room with KSP (2x faster than KAPT)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    
    // Hilt with KSP
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    
    // Moshi with KSP (instead of KAPT)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)
}
```

#### Custom KSP Processor for PTT Code Generation

```kotlin
// Annotation for generating floor control boilerplate
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class FloorControlStateMachine

// Processor implementation
class FloorControlProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {
    
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(
            FloorControlStateMachine::class.qualifiedName!!
        )
        
        symbols.filterIsInstance<KSClassDeclaration>()
            .forEach { classDeclaration ->
                generateStateMachine(classDeclaration)
            }
        
        return emptyList()
    }
    
    private fun generateStateMachine(classDeclaration: KSClassDeclaration) {
        val packageName = classDeclaration.packageName.asString()
        val className = "${classDeclaration.simpleName.asString()}StateMachine"
        
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(false, classDeclaration.containingFile!!),
            packageName = packageName,
            fileName = className
        )
        
        file.bufferedWriter().use { writer ->
            writer.write("""
                package $packageName
                
                // Auto-generated state machine for floor control
                class $className {
                    // Generated implementation
                }
            """.trimIndent())
        }
    }
}
```

---

## 2. KOTLIN MULTIPLATFORM MOBILE (KMM)

### 2.1 KMM 2.1+ Architecture

#### Project Structure

```
meshrider-ptt/
├── build.gradle.kts              # Root build configuration
├── settings.gradle.kts
├── gradle/
│
├── shared/                       # KMM Shared Module (60-70% of code)
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/           # Shared Kotlin code
│       │   ├── kotlin/
│       │   │   ├── domain/       # Business logic, models
│       │   │   ├── data/         # Repository interfaces
│       │   │   ├── network/      # Network protocols (Ktor)
│       │   │   ├── crypto/       # Cryptography (libsodium multiplatform)
│       │   │   └── ptt/          # PTT business logic
│       │   └── resources/
│       │
│       ├── androidMain/          # Android-specific implementations
│       │   └── kotlin/
│       │       ├── platform/     # expect/actual implementations
│       │       └── audio/        # Android audio
│       │
│       ├── iosMain/              # iOS-specific implementations
│       │   └── kotlin/
│       │       ├── platform/     # expect/actual implementations
│       │       └── audio/        # iOS audio (AVFoundation)
│       │
│       ├── commonTest/           # Shared tests
│       ├── androidUnitTest/
│       └── iosTest/
│
├── androidApp/                   # Android Application (30% native)
│   └── src/
│       ├── main/
│       │   ├── kotlin/
│       │   │   ├── ui/           # Compose UI
│       │   │   ├── services/     # Android services
││       │   │   └── di/           # Hilt modules
│       │   └── AndroidManifest.xml
│
└── iosApp/                       # iOS Application (30% native)
    ├── iosApp/
    │   ├── UI/                   # SwiftUI Views
    │   ├── Services/             # iOS-specific services
    │   └── AppDelegate.swift
    └── iosApp.xcodeproj/
```

#### Gradle Configuration

```kotlin
// shared/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget()
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "PTTShared"
            isStatic = true
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                // Kotlinx
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                
                // Ktor (networking)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.serialization.kotlinx.json)
                
                // Koin (KMP DI)
                implementation(libs.koin.core)
                
                // Multiplatform crypto (Libsodium bindings)
                implementation(libs.lazysodium.kmp)
                
                // Multiplatform logging
                implementation(libs.napier)
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.sql delight.android)
            }
        }
        
        val iosMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.sql delight.native)
            }
        }
    }
}

android {
    namespace = "com.doodlelabs.ptt.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
}
```

### 2.2 What to Share vs. What to Keep Native

#### Shared (60-70% of codebase)

| Component | Rationale | Implementation |
|-----------|-----------|----------------|
| **PTT Protocol Logic** | Platform-agnostic 3GPP MCPTT | Pure Kotlin in commonMain |
| **Floor Control State Machine** | Same algorithm on both platforms | Sealed classes + Flow |
| **Cryptography** | libsodium bindings available | lazysodium-kmp |
| **Network Protocols** | UDP multicast, RTP, WebSocket | Ktor client |
| **Data Models** | Serialization with Kotlinx | @Serializable |
| **Business Logic** | Use cases, repositories | Pure Kotlin |
| **Opus Codec Wrapper** | JNI bindings on both platforms | expect/actual |

#### Native (30% of codebase)

| Component | Android | iOS |
|-----------|---------|-----|
| **UI** | Jetpack Compose | SwiftUI |
| **Audio Capture/Playback** | AAudio/Oboe | AVAudioEngine |
| **Background Services** | Foreground Service + WorkManager | Background Modes + BG Task Scheduler |
| **Telecom Integration** | Core-Telecom | CallKit |
| **Push Notifications** | FCM | APNs |
| **Key Storage** | Android Keystore | iOS Keychain |
| **Permissions** | Android Permission System | iOS Permission System |

### 2.3 expect/actual Pattern for Platform-Specific Code

```kotlin
// shared/src/commonMain/kotlin/audio/AudioEngine.kt
expect class PlatformAudioEngine : AudioEngine {
    override fun initialize(config: AudioConfig): Boolean
    override fun startCapture(callback: (ByteArray) -> Unit)
    override fun stopCapture()
    override fun startPlayback()
    override fun stopPlayback()
    override fun writeAudio(data: ByteArray)
    override fun release()
}

interface AudioEngine {
    fun initialize(config: AudioConfig): Boolean
    fun startCapture(callback: (ByteArray) -> Unit)
    fun stopCapture()
    fun startPlayback()
    fun stopPlayback()
    fun writeAudio(data: ByteArray)
    fun release()
}

// shared/src/androidMain/kotlin/audio/PlatformAudioEngine.kt
actual class PlatformAudioEngine actual constructor() : AudioEngine {
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    
    actual override fun initialize(config: AudioConfig): Boolean {
        // Android AudioRecord/AudioTrack implementation
        val minBuffer = AudioRecord.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )
        
        return audioRecord?.state == AudioRecord.STATE_INITIALIZED
    }
    
    actual override fun startCapture(callback: (ByteArray) -> Unit) {
        audioRecord?.startRecording()
        // Launch coroutine to read audio
    }
    
    // ... other implementations
}

// shared/src/iosMain/kotlin/audio/PlatformAudioEngine.kt
import platform.AVFoundation.*
import platform.AudioToolbox.*
import kotlinx.cinterop.*

actual class PlatformAudioEngine actual constructor() : AudioEngine {
    private var audioEngine: AVAudioEngine? = null
    private var inputNode: AVAudioInputNode? = null
    
    actual override fun initialize(config: AudioConfig): Boolean {
        audioEngine = AVAudioEngine()
        inputNode = audioEngine?.inputNode
        
        // iOS AVAudioEngine setup
        return true
    }
    
    actual override fun startCapture(callback: (ByteArray) -> Unit) {
        val format = inputNode?.outputFormatForBus(0u)
        
        inputNode?.installTapOnBus(
            0u,
            bufferSize = 1024u,
            format = format
        ) { buffer, time ->
            // Convert AVAudioPCMBuffer to ByteArray
            val audioData = buffer.toByteArray()
            callback(audioData)
        }
        
        audioEngine?.startAndReturnError(null)
    }
    
    // ... other implementations
}
```

### 2.4 Compose Multiplatform for Shared UI

```kotlin
// shared/src/commonMain/kotlin/ui/PTTButton.kt
@Composable
fun PTTButton(
    state: PTTState,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Shared UI logic across platforms
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Platform-specific haptics via expect/actual
    val haptics = rememberPlatformHaptics()
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            haptics.performHapticFeedback(HapticType.Heavy)
            onPress()
        } else {
            onRelease()
        }
    }
    
    // Shared visual implementation
    Box(
        modifier = modifier
            .platformPTTGesture(interactionSource)
    ) {
        // ...
    }
}

// Platform-specific gesture handling
expect fun Modifier.platformPTTGesture(
    interactionSource: MutableInteractionSource
): Modifier

// Android implementation
actual fun Modifier.platformPTTGesture(
    interactionSource: MutableInteractionSource
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            when (event.type) {
                PointerEventType.Press -> interactionSource.tryEmit(
                    PressInteraction.Press(event.changes.first().position)
                )
                PointerEventType.Release -> interactionSource.tryEmit(
                    PressInteraction.Release(
                        PressInteraction.Press(Offset.Zero)
                    )
                )
            }
        }
    }
}

// iOS implementation (using UIKit gesture recognizers)
actual fun Modifier.platformPTTGesture(
    interactionSource: MutableInteractionSource
): Modifier = this.then(
    // iOS-specific gesture handling
)
```

### 2.5 Migration Strategy from Pure Android

#### Phase 1: Extract Domain Layer (Week 1-2)

```kotlin
// 1. Move domain models to shared module
// Before: app/src/main/kotlin/domain/model/PTTChannel.kt
// After: shared/src/commonMain/kotlin/domain/model/PTTChannel.kt

@Serializable
data class PTTChannel(
    val id: String,
    val name: String,
    val talkgroupId: Int,
    val multicastAddress: String,
    val members: List<PTTMember> = emptyList()
)
```

#### Phase 2: Extract Business Logic (Week 3-4)

```kotlin
// 2. Move use cases to shared module
// shared/src/commonMain/kotlin/domain/usecase/RequestFloorUseCase.kt

class RequestFloorUseCase(
    private val floorControlRepository: FloorControlRepository,
    private val audioRepository: AudioRepository
) {
    suspend operator fun invoke(
        channel: PTTChannel,
        priority: FloorPriority
    ): Result<FloorGrant> {
        // Platform-agnostic logic
        return floorControlRepository.requestFloor(channel.id, priority)
    }
}
```

#### Phase 3: Platform Abstractions (Week 5-6)

```kotlin
// 3. Create expect/actual for platform-specific code
// shared/src/commonMain/kotlin/data/repository/AudioRepository.kt

interface AudioRepository {
    suspend fun startCapture(): Flow<ByteArray>
    suspend fun playAudio(data: ByteArray)
}

expect class PlatformAudioRepository : AudioRepository
```

#### Phase 4: UI Migration (Week 7-8)

```kotlin
// 4. Migrate UI to Compose Multiplatform
// shared/src/commonMain/kotlin/ui/screens/PTTScreen.kt

@Composable
fun PTTScreen(
    viewModel: PTTViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    PTTScreenContent(
        state = state,
        onEvent = viewModel::onEvent
    )
}
```

---

## 3. REAL-TIME AUDIO PROCESSING

### 3.1 AAudio/Oboe NDK Library (<20ms Latency)

#### CMake Configuration

```cmake
# app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.22.1)
project("pttaudio")

set(CMAKE_CXX_STANDARD 20)

# Find Oboe package
find_package(oboe REQUIRED CONFIG)

# Find Opus
include(FetchContent)
FetchContent_Declare(
    opus
    GIT_REPOSITORY https://github.com/xiph/opus.git
    GIT_TAG v1.5.2
)
FetchContent_MakeAvailable(opus)

# Create shared library
add_library(
    pttaudio
    SHARED
    ptt/AudioEngine.cpp
    ptt/OpusCodec.cpp
    ptt/RtpPacketizer.cpp
    ptt/JniBridge.cpp
)

target_link_libraries(
    pttaudio
    oboe::oboe
    opus
    log
    android
)
```

#### Oboe Audio Engine Implementation

```cpp
// app/src/main/cpp/ptt/AudioEngine.h
#pragma once

#include <oboe/Oboe.h>
#include <opus/opus.h>
#include <atomic>
#include <thread>
#include <queue>
#include <mutex>

namespace ptt {

class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine();
    
    bool startRecording();
    bool stopRecording();
    bool startPlayback();
    bool stopPlayback();
    
    // Audio callback
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *stream,
        void *audioData,
        int32_t numFrames) override;
    
    // Error callback
    bool onError(oboe::AudioStream *stream, oboe::Result error) override;
    
    // Set callback for encoded audio
    using AudioCallback = std::function<void(const uint8_t* data, size_t len)>;
    void setAudioCallback(AudioCallback callback);
    
    // Write audio for playback
    void writeAudio(const uint8_t* data, size_t len);
    
    // Latency measurement
    double getCurrentLatencyMs() const;

private:
    std::shared_ptr<oboe::AudioStream> recordingStream;
    std::shared_ptr<oboe::AudioStream> playbackStream;
    
    OpusEncoder* opusEncoder = nullptr;
    OpusDecoder* opusDecoder = nullptr;
    
    std::atomic<bool> isRecording{false};
    std::atomic<bool> isPlaying{false};
    
    AudioCallback audioCallback;
    
    // Jitter buffer for playback
    std::queue<std::vector<uint8_t>> playbackQueue;
    std::mutex queueMutex;
    
    // Constants for PTT
    static constexpr int SAMPLE_RATE = 16000;
    static constexpr int CHANNELS = 1;
    static constexpr int OPUS_FRAME_SIZE = 320;  // 20ms at 16kHz
    static constexpr int OPUS_BITRATE = 24000;   // 24 kbps
    
    bool initOpus();
    void cleanupOpus();
};

} // namespace ptt
```

```cpp
// app/src/main/cpp/ptt/AudioEngine.cpp
#include "AudioEngine.h"
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "PTTAudio", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "PTTAudio", __VA_ARGS__)

namespace ptt {

AudioEngine::AudioEngine() = default;

AudioEngine::~AudioEngine() {
    stopRecording();
    stopPlayback();
    cleanupOpus();
}

bool AudioEngine::initOpus() {
    int error;
    
    // Create encoder
    opusEncoder = opus_encoder_create(SAMPLE_RATE, CHANNELS, 
                                       OPUS_APPLICATION_VOIP, &error);
    if (error != OPUS_OK) {
        LOGE("Failed to create Opus encoder: %s", opus_strerror(error));
        return false;
    }
    
    // Configure encoder for PTT
    opus_encoder_ctl(opusEncoder, OPUS_SET_BITRATE(OPUS_BITRATE));
    opus_encoder_ctl(opusEncoder, OPUS_SET_VBR(0));  // Constant bitrate
    opus_encoder_ctl(opusEncoder, OPUS_SET_INBAND_FEC(1));  // FEC
    opus_encoder_ctl(opusEncoder, OPUS_SET_PACKET_LOSS_PERC(10));
    
    // Create decoder
    opusDecoder = opus_decoder_create(SAMPLE_RATE, CHANNELS, &error);
    if (error != OPUS_OK) {
        LOGE("Failed to create Opus decoder: %s", opus_strerror(error));
        return false;
    }
    
    return true;
}

bool AudioEngine::startRecording() {
    if (!initOpus()) return false;
    
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::I16)
        ->setSampleRate(SAMPLE_RATE)
        ->setChannelCount(CHANNELS)
        ->setDataCallback(this);
    
    oboe::Result result = builder.openStream(recordingStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open recording stream: %d", result);
        return false;
    }
    
    // Set buffer size for low latency
    recordingStream->setBufferSizeInFrames(
        recordingStream->getFramesPerBurst() * 2
    );
    
    result = recordingStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start recording: %d", result);
        return false;
    }
    
    isRecording = true;
    LOGI("Recording started with latency: %.2f ms", 
         recordingStream->getLatency(nullptr).value_or(0.0));
    
    return true;
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream *stream,
    void *audioData,
    int32_t numFrames) {
    
    if (stream->getDirection() == oboe::Direction::Input) {
        // Recording: encode with Opus and send
        if (isRecording && opusEncoder) {
            int16_t* inputBuffer = static_cast<int16_t*>(audioData);
            
            // Opus encode buffer (max 1275 bytes per frame)
            uint8_t encodedData[1275];
            
            opus_int32 encodedBytes = opus_encode(
                opusEncoder,
                inputBuffer,
                numFrames,
                encodedData,
                sizeof(encodedData)
            );
            
            if (encodedBytes > 0 && audioCallback) {
                audioCallback(encodedData, encodedBytes);
            }
        }
    } else {
        // Playback: decode and play
        std::lock_guard<std::mutex> lock(queueMutex);
        
        if (!playbackQueue.empty() && opusDecoder) {
            auto& packet = playbackQueue.front();
            
            int16_t decodedBuffer[OPUS_FRAME_SIZE];
            int decodedFrames = opus_decode(
                opusDecoder,
                packet.data(),
                packet.size(),
                decodedBuffer,
                OPUS_FRAME_SIZE,
                0  // No FEC
            );
            
            if (decodedFrames > 0) {
                memcpy(audioData, decodedBuffer, decodedFrames * sizeof(int16_t));
                playbackQueue.pop();
            }
        } else {
            // Output silence
            memset(audioData, 0, numFrames * sizeof(int16_t));
        }
    }
    
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::writeAudio(const uint8_t* data, size_t len) {
    std::lock_guard<std::mutex> lock(queueMutex);
    
    // Limit queue size to prevent memory issues
    if (playbackQueue.size() < 100) {
        playbackQueue.emplace(data, data + len);
    }
}

double AudioEngine::getCurrentLatencyMs() const {
    if (recordingStream) {
        auto latency = recordingStream->getLatency(nullptr);
        if (latency) return *latency * 1000.0;
    }
    return 0.0;
}

} // namespace ptt
```

### 3.2 AudioRecord/AudioTrack Java APIs

#### High-Level Wrapper with Fallback

```kotlin
class JavaAudioEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioEngine {
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE_MS = 20
        const val SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_SIZE_MS / 1000  // 320
        const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * 2  // 640
    }
    
    override fun startCapture(callback: (ByteArray) -> Unit): Boolean {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        )
        
        if (bufferSize <= 0) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return false
        }
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize * 2
        ).apply {
            if (state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return false
            }
        }
        
        audioRecord?.startRecording()
        
        scope.launch {
            val buffer = ByteArray(BYTES_PER_FRAME)
            var offset = 0
            
            while (isActive) {
                val read = audioRecord?.read(buffer, offset, BYTES_PER_FRAME - offset) ?: 0
                
                if (read > 0) {
                    offset += read
                    if (offset >= BYTES_PER_FRAME) {
                        callback(buffer.copyOf())
                        offset = 0
                    }
                }
            }
        }
        
        return true
    }
    
    override fun startPlayback(): Boolean {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AUDIO_FORMAT
        )
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        audioTrack?.play()
        return true
    }
    
    override fun writeAudio(data: ByteArray) {
        audioTrack?.write(data, 0, data.size)
    }
}
```

### 3.3 Opus Codec Integration

#### Native Opus via JNI

```kotlin
// Kotlin wrapper for native Opus
class NativeOpusCodec @Inject constructor() {
    
    private var encoderHandle: Long = 0
    private var decoderHandle: Long = 0
    
    init {
        System.loadLibrary("pttaudio")
    }
    
    fun initialize(sampleRate: Int, channels: Int, bitrate: Int): Boolean {
        encoderHandle = nativeCreateEncoder(sampleRate, channels, bitrate)
        decoderHandle = nativeCreateDecoder(sampleRate, channels)
        return encoderHandle != 0L && decoderHandle != 0L
    }
    
    fun encode(pcmData: ByteArray): ByteArray? {
        return nativeEncode(encoderHandle, pcmData)
    }
    
    fun decode(opusData: ByteArray): ByteArray? {
        return nativeDecode(decoderHandle, opusData)
    }
    
    fun release() {
        nativeDestroyEncoder(encoderHandle)
        nativeDestroyDecoder(decoderHandle)
    }
    
    // JNI declarations
    private external fun nativeCreateEncoder(sampleRate: Int, channels: Int, bitrate: Int): Long
    private external fun nativeCreateDecoder(sampleRate: Int, channels: Int): Long
    private external fun nativeEncode(handle: Long, pcmData: ByteArray): ByteArray?
    private external fun nativeDecode(handle: Long, opusData: ByteArray): ByteArray?
    private external fun nativeDestroyEncoder(handle: Long)
    private external fun nativeDestroyDecoder(handle: Long)
}
```

### 3.4 Acoustic Echo Cancellation (AEC)

```kotlin
// Using WebRTC AEC3 via JNI
class AcousticEchoCanceler @Inject constructor() {
    
    private var aecHandle: Long = 0
    
    init {
        System.loadLibrary("webrtc_aec")
    }
    
    fun initialize(sampleRate: Int, channels: Int): Boolean {
        aecHandle = nativeCreateAEC(sampleRate, channels)
        return aecHandle != 0L
    }
    
    /**
     * Process captured audio with echo cancellation
     * @param captureFrame Audio from microphone (contains echo)
     * @param renderFrame Audio being played to speaker (reference)
     * @return Echo-cancelled audio
     */
    fun processCapture(captureFrame: ByteArray, renderFrame: ByteArray): ByteArray {
        return nativeProcessCapture(aecHandle, captureFrame, renderFrame)
    }
    
    fun release() {
        nativeDestroyAEC(aecHandle)
    }
    
    private external fun nativeCreateAEC(sampleRate: Int, channels: Int): Long
    private external fun nativeProcessCapture(handle: Long, capture: ByteArray, render: ByteArray): ByteArray
    private external fun nativeDestroyAEC(handle: Long)
}
```

### 3.5 Noise Suppression (NS) and AGC

```kotlin
// WebRTC NS and AGC integration
class AudioPreprocessor @Inject constructor() {
    
    private var nsHandle: Long = 0
    private var agcHandle: Long = 0
    
    init {
        System.loadLibrary("webrtc_audio_processing")
    }
    
    fun initialize(config: PreprocessorConfig): Boolean {
        nsHandle = nativeCreateNS(config.noiseSuppressionLevel)
        agcHandle = nativeCreateAGC(
            config.targetLevelDbfs,
            config.compressionGainDb,
            config.limitterEnable
        )
        return nsHandle != 0L && agcHandle != 0L
    }
    
    fun process(frame: ByteArray): ByteArray {
        // Apply NS then AGC
        var processed = nativeProcessNS(nsHandle, frame)
        processed = nativeProcessAGC(agcHandle, processed)
        return processed
    }
    
    data class PreprocessorConfig(
        val noiseSuppressionLevel: Int = 3,  // 0-3, higher = more suppression
        val targetLevelDbfs: Int = 3,        // Target loudness
        val compressionGainDb: Int = 9,      // Gain applied
        val limitterEnable: Boolean = true
    )
    
    private external fun nativeCreateNS(level: Int): Long
    private external fun nativeCreateAGC(target: Int, gain: Int, limit: Boolean): Long
    private external fun nativeProcessNS(handle: Long, frame: ByteArray): ByteArray
    private external fun nativeProcessAGC(handle: Long, frame: ByteArray): ByteArray
}
```

### 3.6 Low-Latency Audio Paths

```kotlin
// Audio routing for minimum latency
class LowLatencyAudioRouter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    /**
     * Enable communication mode for minimum latency
     */
    fun enableCommunicationMode() {
        // Set mode to IN_COMMUNICATION for VoIP optimization
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        // Disable effects that add latency
        audioManager.isSpeakerphoneOn = false
        
        // Set low latency audio attributes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Use hardware AEC if available
        }
    }
    
    /**
     * Route audio to specific device
     */
    fun routeToDevice(deviceType: AudioDeviceType) {
        when (deviceType) {
            AudioDeviceType.EARPIECE -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.isBluetoothScoOn = false
            }
            AudioDeviceType.SPEAKER -> {
                audioManager.isSpeakerphoneOn = true
                audioManager.isBluetoothScoOn = false
            }
            AudioDeviceType.BLUETOOTH -> {
                audioManager.isBluetoothScoOn = true
                audioManager.startBluetoothSco()
            }
            AudioDeviceType.WIRED_HEADSET -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.isBluetoothScoOn = false
            }
        }
    }
    
    enum class AudioDeviceType {
        EARPIECE, SPEAKER, BLUETOOTH, WIRED_HEADSET
    }
}
```

---

## 4. BACKGROUND EXECUTION & SERVICES

### 4.1 Android 14+ Foreground Service Restrictions

#### Service Declaration (AndroidManifest.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Foreground service permissions -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    
    <!-- Telecom permissions -->
    <uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    
    <!-- Audio permissions -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
    
    <!-- Network permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
    <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />

    <application>
        <!-- PTT Service - Microphone type for voice transmission -->
        <service
            android:name=".service.PTTForegroundService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="microphone"
            android:permission="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
        
        <!-- Call Service - Phone call type for VoIP -->
        <service
            android:name=".service.CallForegroundService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="phoneCall"
            android:permission="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />
        
        <!-- Connection Service for Telecom integration -->
        <service
            android:name=".service.PTTConnectionService"
            android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.telecom.ConnectionService" />
            </intent-filter>
        </service>
        
        <!-- Data sync service for background operations -->
        <service
            android:name=".service.SyncService"
            android:foregroundServiceType="dataSync"
            android:exported="false" />
    </application>
</manifest>
```

### 4.2 PhoneCall Foreground Service Type

```kotlin
// Call service for VoIP integration with Android Telecom
@AndroidEntryPoint
class CallForegroundService : Service() {
    
    @Inject lateinit var callNotificationManager: CallNotificationManager
    @Inject lateinit var telecomCallManager: TelecomCallManager
    
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): CallForegroundService = this@CallForegroundService
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CALL -> startCall(intent)
            ACTION_END_CALL -> endCall()
        }
        return START_NOT_STICKY
    }
    
    private fun startCall(intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: "Unknown"
        val isOutgoing = intent.getBooleanExtra(EXTRA_IS_OUTGOING, false)
        
        // Create notification for foreground service
        val notification = if (isOutgoing) {
            callNotificationManager.createOutgoingCallNotification(callId, displayName)
        } else {
            callNotificationManager.createIncomingCallNotification(callId, displayName)
        }
        
        // Start as foreground service with phoneCall type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                CALL_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else {
            startForeground(CALL_NOTIFICATION_ID, notification)
        }
        
        // Register with Telecom framework
        telecomCallManager.addCall(callId, displayName, isOutgoing)
    }
    
    private fun endCall() {
        telecomCallManager.endCall()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    companion object {
        const val ACTION_START_CALL = "START_CALL"
        const val ACTION_END_CALL = "END_CALL"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_DISPLAY_NAME = "display_name"
        const val EXTRA_IS_OUTGOING = "is_outgoing"
        const val CALL_NOTIFICATION_ID = 1001
    }
}
```

### 4.3 Microphone Foreground Service Type

```kotlin
// PTT Service for half-duplex voice transmission
@AndroidEntryPoint
class PTTForegroundService : Service() {
    
    @Inject lateinit var audioEngine: AudioEngine
    @Inject lateinit var pttManager: PTTManager
    @Inject lateinit var notificationManager: PTTNotificationManager
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isTransmitting = false
    
    override fun onCreate() {
        super.onCreate()
        // Initialize audio engine
        audioEngine.initialize()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PTT -> startPTTSession()
            ACTION_STOP_PTT -> stopPTTSession()
            ACTION_START_TRANSMIT -> startTransmitting()
            ACTION_STOP_TRANSMIT -> stopTransmitting()
        }
        return START_STICKY  // Restart if killed
    }
    
    private fun startPTTSession() {
        val notification = notificationManager.createPTTServiceNotification(
            channelName = pttManager.activeChannel.value?.name ?: "General"
        )
        
        // Android 14+ requires specific foreground service type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                PTT_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(PTT_NOTIFICATION_ID, notification)
        }
        
        // Start listening for incoming audio
        scope.launch {
            pttManager.incomingAudioFlow.collect { audioFrame ->
                if (!isTransmitting) {
                    audioEngine.playAudio(audioFrame)
                }
            }
        }
    }
    
    private fun startTransmitting() {
        isTransmitting = true
        
        // Update notification to show transmitting state
        val notification = notificationManager.createPTTTransmittingNotification()
        notificationManager.notify(PTT_NOTIFICATION_ID, notification)
        
        // Start audio capture
        audioEngine.startCapture { audioData ->
            scope.launch {
                pttManager.transmitAudio(audioData)
            }
        }
    }
    
    private fun stopTransmitting() {
        isTransmitting = false
        audioEngine.stopCapture()
        
        // Restore idle notification
        val notification = notificationManager.createPTTServiceNotification()
        notificationManager.notify(PTT_NOTIFICATION_ID, notification)
    }
    
    private fun stopPTTSession() {
        stopTransmitting()
        audioEngine.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
    
    companion object {
        const val ACTION_START_PTT = "START_PTT"
        const val ACTION_STOP_PTT = "STOP_PTT"
        const val ACTION_START_TRANSMIT = "START_TRANSMIT"
        const val ACTION_STOP_TRANSMIT = "STOP_TRANSMIT"
        const val PTT_NOTIFICATION_ID = 2001
    }
}
```

### 4.4 WorkManager for Deferred Tasks

```kotlin
// Background sync for offline messages and telemetry
@HiltWorker
class PTTSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messageRepository: MessageRepository,
    private val telemetryRepository: TelemetryRepository
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            // Sync pending messages
            val pendingMessages = messageRepository.getPendingMessages()
            pendingMessages.forEach { message ->
                messageRepository.sendMessage(message)
            }
            
            // Upload telemetry
            telemetryRepository.uploadPendingTelemetry()
            
            Result.success()
        } catch (e: Exception) {
            // Retry with exponential backoff
            Result.retry()
        }
    }
    
    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val syncWork = PeriodicWorkRequestBuilder<PTTSyncWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ptt_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncWork
            )
        }
    }
}
```

### 4.5 AlarmManager for Precise Timing

```kotlin
// Precise timing for floor control timeouts and beacons
class PTTAlarmManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    /**
     * Schedule precise alarm for floor timeout
     */
    fun scheduleFloorTimeout(channelId: String, timeoutMs: Long) {
        val intent = Intent(context, PTTAlarmReceiver::class.java).apply {
            action = ACTION_FLOOR_TIMEOUT
            putExtra(EXTRA_CHANNEL_ID, channelId)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            channelId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val triggerTime = SystemClock.elapsedRealtime() + timeoutMs
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires SCHEDULE_EXACT_ALARM permission
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                // Fallback to inexact alarm
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }
    
    companion object {
        const val ACTION_FLOOR_TIMEOUT = "com.doodlelabs.ptt.FLOOR_TIMEOUT"
        const val EXTRA_CHANNEL_ID = "channel_id"
    }
}

class PTTAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PTTAlarmManager.ACTION_FLOOR_TIMEOUT -> {
                val channelId = intent.getStringExtra(PTTAlarmManager.EXTRA_CHANNEL_ID)
                // Handle floor timeout
            }
        }
    }
}
```

### 4.6 Doze Mode and App Standby Handling

```kotlin
// Handling Doze mode for PTT background operation
class DozeModeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    
    /**
     * Acquire partial wake lock for critical PTT operation
     */
    fun acquireWakeLock(timeoutMs: Long = 10 * 60 * 1000) {  // 10 minutes max
        wakeLock?.release()
        
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MeshRider:PTTWakeLock"
        ).apply {
            acquire(timeoutMs)
        }
    }
    
    fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
            wakeLock = null
        }
    }
    
    /**
     * Check if device is in Doze mode
     */
    fun isInDozeMode(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isDeviceIdleMode
        } else {
            false
        }
    }
    
    /**
     * Request ignore battery optimizations
     */
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = context.packageName
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                activity.startActivity(intent)
            }
        }
    }
}

// Network monitoring for Doze-aware operations
class DozeAwareNetworkManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dozeManager: DozeModeManager
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
            as ConnectivityManager
    
    fun registerNetworkCallback(callback: NetworkCallback) {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        
        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Network available - can resume normal operation
                if (!dozeManager.isInDozeMode()) {
                    callback.onNetworkAvailable(network)
                }
            }
            
            override fun onLost(network: Network) {
                callback.onNetworkLost(network)
            }
        })
    }
    
    interface NetworkCallback {
        fun onNetworkAvailable(network: Network)
        fun onNetworkLost(network: Network)
    }
}
```

---

## 5. NETWORKING & CONNECTIVITY

### 5.1 OkHttp 5.0+ for HTTP/WebSocket

```kotlin
// NetworkModule.kt - OkHttp configuration
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        certificatePinner: CertificatePinner,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            // HTTP/2 and HTTP/3 support
            .protocols(listOf(Protocol.HTTP_3, Protocol.HTTP_2, Protocol.HTTP_1_1))
            
            // Connection settings
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            
            // Connection pooling
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            
            // Security
            .certificatePinner(certificatePinner)
            
            // Logging (debug builds only)
            .addInterceptor(loggingInterceptor)
            
            // Custom interceptor for auth
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-PTT-Version", BuildConfig.VERSION_NAME)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            
            // Retry on connection failure
            .retryOnConnectionFailure(true)
            
            // DNS caching for mesh networks
            .dns(object : Dns {
                private val cache = ConcurrentHashMap<String, List<InetAddress>>()
                private val dns = Dns.SYSTEM
                
                override fun lookup(hostname: String): List<InetAddress> {
                    // Cache mesh network addresses
                    if (hostname.startsWith("10.223.")) {
                        return cache.getOrPut(hostname) {
                            dns.lookup(hostname)
                        }
                    }
                    return dns.lookup(hostname)
                }
            })
            .build()
    }
    
    @Provides
    @Singleton
    fun provideWebSocketManager(
        okHttpClient: OkHttpClient
    ): WebSocketManager {
        return WebSocketManager(okHttpClient)
    }
}

// WebSocket manager with auto-reconnect
class WebSocketManager @Inject constructor(
    private val client: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val messages = _messages.asSharedFlow()
    
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10
    private val reconnectDelayMs = 5000L
    
    fun connect(url: String) {
        val request = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", "ptt-v1")
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                reconnectAttempts = 0
                // Send registration message
                ws.send("""{"type": "register", "client": "ptt-android"}""")
            }
            
            override fun onMessage(ws: WebSocket, text: String) {
                _messages.tryEmit(text)
            }
            
            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }
            
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                attemptReconnect(url)
            }
            
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                attemptReconnect(url)
            }
        })
    }
    
    private fun attemptReconnect(url: String) {
        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            Thread.sleep(reconnectDelayMs * reconnectAttempts)  // Exponential backoff
            connect(url)
        }
    }
    
    fun send(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }
}
```

### 5.2 Kotlin Serialization vs Gson/Moshi

```kotlin
// Kotlin Serialization - Recommended for KMM
@Serializable
data class FloorControlMessage(
    val type: MessageType,
    val channelId: String,
    val senderId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val priority: FloorPriority = FloorPriority.NORMAL,
    val sequenceNumber: Int = 0
) {
    @Serializable
    enum class MessageType {
        REQUEST, GRANT, DENY, RELEASE, QUEUED, PREEMPT
    }
    
    @Serializable
    enum class FloorPriority {
        EMERGENCY, HIGH, NORMAL, LOW
    }
}

// JSON configuration for tactical use (compact)
val pttJson = Json {
    encodeDefaults = false
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    useAlternativeNames = false
    explicitNulls = false  // Save bytes
}

// Usage
fun serializeMessage(msg: FloorControlMessage): String {
    return pttJson.encodeToString(msg)
}

fun deserializeMessage(json: String): FloorControlMessage {
    return pttJson.decodeFromString(json)
}

// Binary serialization for bandwidth efficiency (CBOR)
val cbor = Cbor {
    encodeDefaults = false
}

fun serializeToBinary(msg: FloorControlMessage): ByteArray {
    return cbor.encodeToByteArray(msg)
}
```

### 5.3 gRPC for Efficient Signaling

```kotlin
// gRPC configuration for control signaling
@Module
@InstallIn(SingletonComponent::class)
object GrpcModule {
    
    @Provides
    @Singleton
    fun provideGrpcChannel(
        okhttpClient: OkHttpClient
    ): ManagedChannel {
        return okhttpClient.dispatcher.executorService.let { executor ->
            ManagedChannelBuilder.forAddress("10.223.1.1", 50051)
                .usePlaintext()  // Internal mesh network
                .executor(executor)
                .maxRetryAttempts(3)
                .build()
        }
    }
    
    @Provides
    @Singleton
    fun providePTTControlStub(channel: ManagedChannel): PTTControlGrpc.PTTControlBlockingStub {
        return PTTControlGrpc.newBlockingStub(channel)
            .withDeadlineAfter(5, TimeUnit.SECONDS)
    }
}

// gRPC service usage
class FloorControlGrpcClient @Inject constructor(
    private val stub: PTTControlGrpc.PTTControlBlockingStub
) {
    fun requestFloor(channelId: String, priority: FloorPriority): FloorResult {
        val request = FloorRequest.newBuilder()
            .setChannelId(channelId)
            .setPriority(priority.toGrpcPriority())
            .setTimestamp(System.currentTimeMillis())
            .build()
        
        return try {
            val response = stub.requestFloor(request)
            when (response.status) {
                FloorResponse.Status.GRANTED -> FloorResult.Granted
                FloorResponse.Status.DENIED -> FloorResult.Denied(response.reason)
                FloorResponse.Status.QUEUED -> FloorResult.Queued(response.queuePosition)
                else -> FloorResult.Error("Unknown status")
            }
        } catch (e: StatusRuntimeException) {
            FloorResult.Error(e.status.description ?: "gRPC error")
        }
    }
}
```

### 5.4 WebRTC 119+ Native Integration

```kotlin
// WebRTC configuration for voice/video calls
class WebRTCManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    
    init {
        initializePeerConnectionFactory()
    }
    
    private fun initializePeerConnectionFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        
        val encoderFactory = DefaultVideoEncoderFactory(
            EglBase.create().eglBaseContext,
            true,  // enableIntelVp8Encoder
            true   // enableH264HighProfile
        )
        val decoderFactory = DefaultVideoDecoderFactory(EglBase.create().eglBaseContext)
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }
    
    fun createPeerConnection(
        iceServers: List<IceServer>,
        observer: PeerConnection.Observer
    ): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            // Disable bundle for mesh network compatibility
            bundlePolicy = PeerConnection.BundlePolicy.BUNDLE_POLICY_BALANCED
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.RTCP_MUX_POLICY_NEGOTIATE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            // Enable symmetric NAT for mesh
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }
        
        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
        return peerConnection
    }
    
    fun createAudioTrack(): AudioTrack? {
        val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        return peerConnectionFactory?.createAudioTrack("audio", audioSource)
    }
    
    // PTT-specific: Mute/unmute for half-duplex
    fun setMicrophoneEnabled(enabled: Boolean) {
        peerConnection?.senders
            ?.find { it.track()?.kind() == "audio" }
            ?.track()
            ?.setEnabled(enabled)
    }
}
```

### 5.5 MulticastSocket for Group Comms

```kotlin
// Multicast manager for PTT group communication
class MulticastPTTManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("pttDispatcher") private val dispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var multicastSocket: MulticastSocket? = null
    private val multicastGroup = InetAddress.getByName("239.255.0.1")
    
    private val _incomingAudio = MutableSharedFlow<AudioPacket>(
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingAudio = _incomingAudio.asSharedFlow()
    
    fun joinTalkgroup(talkgroupId: Int): Boolean {
        return try {
            val groupAddress = InetAddress.getByName("239.255.0.$talkgroupId")
            
            multicastSocket = MulticastSocket(5004).apply {
                // QoS: Expedited Forwarding for voice
                trafficClass = 0xB8  // DSCP 46 << 2
                reuseAddress = true
                joinGroup(groupAddress)
            }
            
            // Start receiving
            scope.launch {
                receiveAudio()
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join multicast group", e)
            false
        }
    }
    
    private suspend fun receiveAudio() {
        val buffer = ByteArray(1500)  // Max UDP packet size
        
        withContext(Dispatchers.IO) {
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    multicastSocket?.receive(packet)
                    
                    // Parse RTP packet
                    val audioPacket = parseRTPPacket(packet.data, packet.length)
                    _incomingAudio.tryEmit(audioPacket)
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Log.e(TAG, "Receive error", e)
                    }
                }
            }
        }
    }
    
    fun transmitAudio(encodedAudio: ByteArray, sequenceNumber: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                // Create RTP packet
                val rtpPacket = createRTPPacket(encodedAudio, sequenceNumber)
                
                val packet = DatagramPacket(
                    rtpPacket,
                    rtpPacket.size,
                    multicastGroup,
                    5004
                )
                
                multicastSocket?.send(packet)
            } catch (e: Exception) {
                Log.e(TAG, "Transmit error", e)
            }
        }
    }
    
    private fun createRTPPacket(audioData: ByteArray, seqNum: Int): ByteArray {
        val rtpHeader = ByteArray(12)
        
        // Version (2), Padding (0), Extension (0), CSRC count (0)
        rtpHeader[0] = 0x80.toByte()
        
        // Marker (1), Payload type (111 for Opus)
        rtpHeader[1] = 0xE7.toByte()
        
        // Sequence number
        rtpHeader[2] = (seqNum shr 8).toByte()
        rtpHeader[3] = (seqNum and 0xFF).toByte()
        
        // Timestamp
        val timestamp = System.nanoTime() / 1000000L
        rtpHeader[4] = (timestamp shr 24).toByte()
        rtpHeader[5] = (timestamp shr 16).toByte()
        rtpHeader[6] = (timestamp shr 8).toByte()
        rtpHeader[7] = (timestamp and 0xFF).toByte()
        
        // SSRC (synchronization source)
        val ssrc = Random.nextInt()
        rtpHeader[8] = (ssrc shr 24).toByte()
        rtpHeader[9] = (ssrc shr 16).toByte()
        rtpHeader[10] = (ssrc shr 8).toByte()
        rtpHeader[11] = (ssrc and 0xFF).toByte()
        
        return rtpHeader + audioData
    }
    
    companion object {
        private const val TAG = "MulticastPTT"
    }
}
```

### 5.6 NetworkRequest.Builder for WiFi Management

```kotlin
// WiFi network management for mesh radios
class MeshWifiManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
            as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) 
            as WifiManager
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    /**
     * Request connection to mesh WiFi network
     */
    fun connectToMeshNetwork(
        ssid: String,
        password: String? = null,
        callback: NetworkCallback
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - Use NetworkSpecifier
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsidPattern(PatternMatcher(ssid, PatternMatcher.PATTERN_PREFIX))
                .apply {
                    password?.let { setWpa2Passphrase(it) }
                }
                .build()
            
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()
            
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    connectivityManager.bindProcessToNetwork(network)
                    callback.onConnected(network)
                }
                
                override fun onLost(network: Network) {
                    super.onLost(network)
                    callback.onDisconnected()
                }
            }
            
            connectivityManager.requestNetwork(request, networkCallback!!)
        } else {
            // Legacy - Direct WiFi configuration
            @Suppress("DEPRECATION")
            val config = WifiConfiguration().apply {
                this.SSID = "\"$ssid\""
                password?.let {
                    preSharedKey = "\"$it\""
                }
            }
            
            val networkId = wifiManager.addNetwork(config)
            wifiManager.enableNetwork(networkId, true)
            wifiManager.reconnect()
        }
    }
    
    interface NetworkCallback {
        fun onConnected(network: Network)
        fun onDisconnected()
    }
    
    fun disconnect() {
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
        connectivityManager.bindProcessToNetwork(null)
    }
}
```

---

## 6. SECURITY ARCHITECTURE

### 6.1 Android Keystore for Key Storage

```kotlin
// Secure key management using Android Keystore
class KeystoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    
    /**
     * Generate Ed25519 signing key pair in hardware
     */
    fun generateIdentityKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )
        
        val params = KeyGenParameterSpec.Builder(
            IDENTITY_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)  // Can require biometric
            .setRandomizedEncryptionRequired(true)
            .build()
        
        keyPairGenerator.initialize(params)
        return keyPairGenerator.generateKeyPair()
    }
    
    /**
     * Generate AES-GCM key for message encryption
     */
    fun generateMessageKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        
        val params = KeyGenParameterSpec.Builder(
            MESSAGE_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        
        keyGenerator.init(params)
        return keyGenerator.generateKey()
    }
    
    /**
     * Get or create key for encrypted preferences
     */
    fun getMasterKey(): MasterKey {
        return MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setUserAuthenticationValidityDurationSeconds(30)
            .build()
    }
    
    companion object {
        private const val IDENTITY_KEY_ALIAS = "ptt_identity_key"
        private const val MESSAGE_KEY_ALIAS = "ptt_message_key"
        private const val MASTER_KEY_ALIAS = "ptt_master_key"
    }
}
```

### 6.2 libsodium-jni for Crypto

```kotlin
// libsodium integration for E2E encryption
class SodiumCryptoManager @Inject constructor() {
    
    init {
        SodiumAndroid.init()
    }
    
    private val lazySodium = LazySodiumAndroid(SodiumAndroid())
    
    /**
     * Generate Ed25519 key pair for identity
     */
    fun generateKeyPair(): KeyPair {
        val publicKey = ByteArray(Sign.PUBLICKEYBYTES)
        val secretKey = ByteArray(Sign.SECRETKEYBYTES)
        
        lazySodium.cryptoSignKeypair(publicKey, secretKey)
        
        return KeyPair(publicKey, secretKey)
    }
    
    /**
     * Sign message with Ed25519
     */
    fun sign(message: ByteArray, secretKey: ByteArray): ByteArray {
        val signature = ByteArray(Sign.BYTES)
        lazySodium.cryptoSignDetached(signature, message, message.size.toLong(), secretKey)
        return signature
    }
    
    /**
     * Verify Ed25519 signature
     */
    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return lazySodium.cryptoSignVerifyDetached(signature, message, message.size.toLong(), publicKey)
    }
    
    /**
     * X25519 key exchange
     */
    fun generateSharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val sharedSecret = ByteArray(ScalarMult.SCALARBYTES)
        lazySodium.cryptoScalarMult(sharedSecret, privateKey, publicKey)
        return sharedSecret
    }
    
    /**
     * Encrypt with XSalsa20-Poly1305 (secretbox)
     */
    fun encrypt(message: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray {
        val ciphertext = ByteArray(SecretBox.MACBYTES + message.size)
        lazySodium.cryptoSecretBoxEasy(ciphertext, message, message.size.toLong(), nonce, key)
        return ciphertext
    }
    
    /**
     * Decrypt with XSalsa20-Poly1305
     */
    fun decrypt(ciphertext: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray? {
        val message = ByteArray(ciphertext.size - SecretBox.MACBYTES)
        val success = lazySodium.cryptoSecretBoxOpenEasy(
            message, ciphertext, ciphertext.size.toLong(), nonce, key
        )
        return if (success) message else null
    }
    
    data class KeyPair(val publicKey: ByteArray, val secretKey: ByteArray)
}
```

### 6.3 Certificate Pinning

```kotlin
// Certificate pinning for secure signaling
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    
    @Provides
    @Singleton
    fun provideCertificatePinner(): CertificatePinner {
        return CertificatePinner.Builder()
            // Primary certificate pin (SHA-256 of SPKI)
            .add("api.meshrider.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            // Backup pin
            .add("api.meshrider.com", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
            // Development certificate (remove in production)
            .add("10.223.1.1", "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=")
            .build()
    }
    
    @Provides
    @Singleton
    fun provideNetworkSecurityConfig(): NetworkSecurityCustomizer {
        return NetworkSecurityCustomizer()
    }
}

// Network Security Config XML (res/xml/network_security_config.xml)
/*
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system"/>
        </trust-anchors>
        <pin-set expiration="2026-12-31">
            <pin digest="SHA-256">AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=</pin>
            <pin digest="SHA-256">BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=</pin>
        </pin-set>
    </base-config>
    
    <!-- Allow cleartext for mesh network (internal only) -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.223.0.0</domain>
    </domain-config>
    
    <!-- Debug configuration (remove in release) -->
    <debug-overrides>
        <trust-anchors>
            <certificates src="user"/>
        </trust-anchors>
    </debug-overrides>
</network-security-config>
*/
```

### 6.4 SafetyNet/Play Integrity API

```kotlin
// Device integrity verification
class IntegrityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val integrityManager = IntegrityManagerFactory.create(context)
    
    /**
     * Request integrity token before joining secure channel
     */
    suspend fun verifyDeviceIntegrity(): Result<IntegrityTokenResponse> = suspendCancellableCoroutine { continuation ->
        val nonce = generateNonce()
        
        val request = IntegrityTokenRequest.builder()
            .setNonce(Base64.encodeToString(nonce, Base64.URL_SAFE or Base64.NO_WRAP))
            .build()
        
        integrityManager.requestIntegrityToken(request)
            .addOnSuccessListener { response ->
                continuation.resume(Result.success(response))
            }
            .addOnFailureListener { exception ->
                continuation.resume(Result.failure(exception))
            }
    }
    
    private fun generateNonce(): ByteArray {
        val nonce = ByteArray(32)
        SecureRandom().nextBytes(nonce)
        return nonce
    }
}
```

### 6.5 Biometric Authentication

```kotlin
// Biometric authentication for sensitive operations
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val biometricManager = BiometricManager.from(context)
    
    fun canAuthenticate(): Boolean {
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
    
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        callback: BiometricCallback
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: AuthenticationResult) {
                    callback.onSuccess(result.cryptoObject)
                }
                
                override fun onAuthenticationFailed() {
                    callback.onFailure("Authentication failed")
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    callback.onFailure(errString.toString())
                }
            }
        )
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    interface BiometricCallback {
        fun onSuccess(cryptoObject: BiometricPrompt.CryptoObject?)
        fun onFailure(error: String)
    }
}
```

### 6.6 Encrypted SharedPreferences/DataStore

```kotlin
// Secure preferences storage
class SecurePreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val masterKey: MasterKey
) {
    /**
     * Encrypted SharedPreferences for sensitive settings
     */
    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "secure_ptt_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Encrypted DataStore for typed preferences
     */
    private val Context.secureDataStore by preferencesDataStore(
        name = "secure_ptt_datastore",
        produceMigrations = { context ->
            listOf(SharedPreferencesMigration(context, "secure_ptt_prefs"))
        }
    )
    
    // Preference keys
    private val IDENTITY_PUBLIC_KEY = stringPreferencesKey("identity_public_key")
    private val ENCRYPTED_SECRET_KEY = stringPreferencesKey("encrypted_secret_key")
    private val DEVICE_TOKEN = stringPreferencesKey("device_token")
    private val CHANNEL_KEYS_PREFIX = "channel_key_"
    
    suspend fun saveIdentityKeyPair(publicKey: String, encryptedSecretKey: String) {
        context.secureDataStore.edit { prefs ->
            prefs[IDENTITY_PUBLIC_KEY] = publicKey
            prefs[ENCRYPTED_SECRET_KEY] = encryptedSecretKey
        }
    }
    
    suspend fun getIdentityKeyPair(): Pair<String, String>? {
        val prefs = context.secureDataStore.data.first()
        val publicKey = prefs[IDENTITY_PUBLIC_KEY] ?: return null
        val secretKey = prefs[ENCRYPTED_SECRET_KEY] ?: return null
        return publicKey to secretKey
    }
    
    fun saveChannelKey(channelId: String, encryptedKey: String) {
        encryptedPrefs.edit()
            .putString("$CHANNEL_KEYS_PREFIX$channelId", encryptedKey)
            .apply()
    }
    
    fun getChannelKey(channelId: String): String? {
        return encryptedPrefs.getString("$CHANNEL_KEYS_PREFIX$channelId", null)
    }
    
    /**
     * Securely wipe all data
     */
    suspend fun wipeAllData() {
        context.secureDataStore.edit { it.clear() }
        encryptedPrefs.edit().clear().apply()
    }
}
```

---

## Appendix A: Dependency Versions (February 2026)

```kotlin
// libs.versions.toml
[versions]
# Kotlin & Gradle
kotlin = "2.1.0"
agp = "8.7.3"
ksp = "2.1.0-1.0.29"

# AndroidX
core-ktx = "1.15.0"
lifecycle = "2.9.0"
activity = "1.10.0"

# Compose
compose-bom = "2024.12.01"
compose = "1.7.6"
compose-material3 = "1.4.0-alpha04"

# DI
hilt = "2.53.1"

# Networking
okhttp = "5.0.0-alpha.14"
ktor = "3.0.0"

# WebRTC
webrtc = "119.0.0"

# Audio
oboe = "1.9.2"

# Crypto
lazysodium = "2.0.2"

# Storage
datastore = "1.1.1"
room = "2.7.0-alpha03"

# Testing
junit = "5.11.3"
turbine = "1.2.0"
mockk = "1.13.13"

[libraries]
# Kotlin
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version = "1.10.0" }
kotlinx-serialization = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version = "1.8.0" }

# Compose
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-navigation = { module = "androidx.navigation:navigation-compose", version = "2.8.5" }

# DI
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }

# Networking
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }

# Audio
oboe = { module = "com.google.oboe:oboe", version.ref = "oboe" }

# Crypto
lazysodium = { module = "com.goterl:lazysodium-android", version.ref = "lazysodium" }

[plugins]
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
```

---

## Appendix B: Performance Benchmarks

| Metric | Target | Implementation |
|--------|--------|----------------|
| Audio Capture Latency | <20ms | Oboe + LowLatency mode |
| Audio Playback Latency | <20ms | Oboe + LowLatency mode |
| PTT Activation Time | <100ms | Optimized state machine |
| Floor Grant Time | <50ms | UDP multicast signaling |
| Codec Latency | <5ms | Opus 20ms frames |
| Network Latency | <30ms | DSCP EF marking |
| UI Frame Time | <16ms | Compose optimization |
| Cold Start | <500ms | Lazy initialization |
| Memory Usage | <256MB | Resource pooling |
| Battery (idle) | <1%/hour | Doze mode optimization |

---

## Conclusion

This research provides a comprehensive blueprint for building a billion-dollar tactical PTT application using modern Android architecture patterns. The key recommendations are:

1. **Architecture**: Clean Architecture + MVI with Jetpack Compose 1.7+ for reactive, testable UI
2. **Multiplatform**: Adopt KMM 2.1+ to share 60-70% of code with iOS
3. **Audio**: Use native Oboe/AAudio for <20ms latency with Opus codec
4. **Background**: Implement foreground services with proper Android 14+ restrictions
5. **Networking**: Combine WebRTC, multicast UDP, and OkHttp for resilient communications
6. **Security**: Implement FIPS 140-2 Level 1 with Android Keystore and libsodium

The MeshRider Wave Android project implements these patterns in production, serving as a reference implementation for military-grade PTT applications.

---

*Research compiled for DoodleLabs MeshRider Wave Android - February 6, 2026*
