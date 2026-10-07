package app.idl.domain.avatar

import java.io.File

internal fun repoRoot(): File {
    var dir: File? = File("").absoluteFile
    while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
    return requireNotNull(dir) { "repo root not found" }
}

internal fun coreRegistry(): AssetRegistry {
    val root = repoRoot()
    return AssetPacks.registry { path -> File(root, "app/src/main/assets/$path").readText() }
}

internal fun config(
    base: String = "base_blob",
    eyes: String? = "eyefam_round",
    mouth: String? = "mouthfam_classic",
    features: List<String> = emptyList(),
    head: String? = null,
    face: String? = null,
    body: String? = null,
    prop: String? = null,
    scene: String? = "scene_plain",
    frame: String? = "frame_squircle",
    resting: String = "neutral",
    dna: StyleDna = StyleDna(),
) = AvatarConfiguration(
    baseAssetId = base,
    paletteAssetId = "palette_sunny",
    eyeFamilyAssetId = eyes,
    mouthFamilyAssetId = mouth,
    signatureFeatureAssetIds = features,
    signatureHeadAccessoryAssetId = head,
    signatureFaceAccessoryAssetId = face,
    signatureBodyAccessoryAssetId = body,
    defaultPropAssetId = prop,
    defaultSceneAssetId = scene,
    defaultFrameAssetId = frame,
    restingExpressionId = resting,
    styleDna = dna,
)

internal fun request(
    configuration: AvatarConfiguration = config(),
    presence: VisiblePresence = VisiblePresence.NONE,
    reactions: List<String> = emptyList(),
    target: RenderTarget = RenderTarget.PROFILE,
    sizePx: Int = target.defaultSizePx,
) = AvatarRenderRequest(
    configuration = configuration,
    presence = presence,
    reactionOverlayAssetIds = reactions,
    target = target,
    sizePx = sizePx,
)

internal fun ResolvedAvatar.has(id: String) = layers.any { it.assetId == id }

internal fun sandbox(
    version: Int = 1,
    extra: List<AssetDef> = emptyList(),
    semantics: Map<String, SemanticMapping> = emptyMap(),
    retired: Map<String, String> = emptyMap(),
    expressions: List<ExpressionDef> = listOf(ExpressionDef("neutral", "Neutral", eyes = "eye_open", mouth = "mouth_line")),
    expressionOverrides: Map<String, Map<String, ExpressionParts>> = emptyMap(),
    defaults: PackDefaults = PackDefaults("base_a", "pal_a", "scene_a", "frame_a", "eye_a", "mouth_a"),
): AssetRegistry {
    val required = listOf(
        AssetDef(
            "base_a", AssetCategory.BASE, "Base",
            anchors = AssetRegistry.REQUIRED_BASE_ANCHORS.associateWith { Anchor(0.5f, 0.5f) },
            faceSafeZone = NormalizedRect(0f, 0f, 1f, 1f),
        ),
        AssetDef("pal_a", AssetCategory.PALETTE, "Palette", colors = PaletteColors(1, 2, 3, "Palette")),
        AssetDef("scene_a", AssetCategory.SCENE, "Scene"),
        AssetDef("frame_a", AssetCategory.FRAME, "Frame"),
        AssetDef("eye_a", AssetCategory.EYE_FAMILY, "Eyes"),
        AssetDef("mouth_a", AssetCategory.MOUTH_FAMILY, "Mouth"),
        AssetDef("eye_open", AssetCategory.FACE_EYE, "Open"),
        AssetDef("mouth_line", AssetCategory.FACE_MOUTH, "Flat"),
    )
    return AssetRegistry(listOf(AssetManifest(
        packId = "sandbox",
        version = version,
        minimumRendererVersion = 3,
        defaults = defaults,
        assets = required + extra,
        expressions = expressions,
        semantics = semantics,
        expressionOverrides = expressionOverrides,
        retired = retired,
    )))
}

internal fun piece(
    id: String,
    category: AssetCategory,
    conflicts: List<String> = emptyList(),
    requires: List<String> = emptyList(),
    fallback: String? = null,
    widgetSafe: Boolean = true,
    minSizePx: Int = 0,
) = AssetDef(
    id = id,
    category = category,
    accessibilityLabel = id,
    conflictsWith = conflicts,
    requires = requires,
    fallback = fallback,
    widgetSafe = widgetSafe,
    minSizePx = minSizePx,
)
