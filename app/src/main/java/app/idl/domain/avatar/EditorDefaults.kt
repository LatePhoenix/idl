package app.idl.domain.avatar

/**
 * A new avatar and the three onboarding samples (AP-11). Samples are teardrop recipes.
 */
object EditorDefaults {
    fun starter(registry: AssetRegistry): AvatarConfiguration {
        val defaults = registry.defaults
        val base = if (registry.asset("base_teardrop") != null) "base_teardrop" else defaults.base
        return AvatarConfiguration(
            baseAssetId = base,
            paletteAssetId = defaults.palette,
            eyeFamilyAssetId = defaults.eyeFamily,
            mouthFamilyAssetId = defaults.mouthFamily,
            defaultSceneAssetId = defaults.scene,
            defaultFrameAssetId = defaults.frame,
            familyId = registry.baseFamilies[base].orEmpty(),
            packId = AvatarConfiguration.DEFAULT_PACK_ID,
            restingExpressionId = resting(registry),
        )
    }

    /** Three different teardrop looks for the onboarding row. Missing ids are skipped. */
    fun onboardingSamples(registry: AssetRegistry): List<AvatarConfiguration> = listOf(
        sample(registry, "smiling_face_with_smiling_eyes", "hair_bob", null, "#F0C9A8"),
        sample(registry, "sleeping_face", "hair_short_crop", null, "#C68642"),
        sample(registry, "neutral_face", null, "glasses_round_wire", "#8D5524"),
    )

    private fun sample(
        registry: AssetRegistry,
        expressionId: String,
        hairId: String?,
        glassesId: String?,
        skin: String,
    ): AvatarConfiguration {
        val session = EditorSession(starter(registry), registry)
        session.setExpression(expressionId)
        hairId?.let { session.wear(it) }
        glassesId?.let { session.wear(it) }
        session.setColor("face.primary", skin)
        return session.configuration
    }

    private fun resting(registry: AssetRegistry): String = when {
        registry.expression("neutral_face") != null -> "neutral_face"
        registry.expression("neutral") != null -> "neutral"
        else -> registry.allExpressions.minByOrNull { it.id }?.id ?: "neutral"
    }
}

/** What the editor shows when a save is refused. Labels, not raw ids (F-40). */
fun saveRefusal(
    configuration: AvatarConfiguration,
    registry: AssetRegistry,
    entitlements: Entitlements,
): String? = when (val write = configuration.prepareForWrite(registry.baseFamilies, registry, entitlements)) {
    is AvatarWrite.NeedsAppUpdate -> AvatarWrite.NeedsAppUpdate.message.replaceFirstChar { it.uppercase() }
    is AvatarWrite.NeedsEntitlement -> {
        val names = write.assetIds.joinToString(", ") { id -> registry.asset(id)?.accessibilityLabel ?: id }
        "Unlock $names to save this avatar"
    }
    is AvatarWrite.Ready -> null
}
