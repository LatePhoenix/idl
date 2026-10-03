package app.idl.domain

import kotlinx.serialization.Serializable

@Serializable(with = BaseForm.Serializer::class)
enum class BaseForm(val label: String) {
    HUMAN("Emoji"), BLOB("Blob"), ROBOT("Robot"), GHOST("Ghost"), CAT("Cat"),
    FOX("Fox"), BEAR("Bear"), ALIEN("Alien"), PIXEL("Pixel");

    object Serializer : WireEnumSerializer<BaseForm>("BaseForm", entries, HUMAN)
}

@Serializable(with = Expression.Serializer::class)
enum class Expression(val label: String) {
    NEUTRAL("Neutral"), HAPPY("Happy"), EXCITED("Excited"), SLEEPY("Sleepy"), TIRED("Tired"),
    SAD("Sad"), ANXIOUS("Anxious"), ANGRY("Angry"), SICK("Sick"), FOCUSED("Focused"),
    OVERWHELMED("Overwhelmed"), SOCIAL("Social"), MISCHIEVOUS("Mischievous"), AFK("AFK"),
    DND("Do not disturb");

    object Serializer : WireEnumSerializer<Expression>("Expression", entries, NEUTRAL)
}

@Serializable(with = FaceStyle.Serializer::class)
enum class FaceStyle(val label: String) {
    CLASSIC("Classic"), BLUSHY("Blushy"), FRECKLES("Freckles");

    object Serializer : WireEnumSerializer<FaceStyle>("FaceStyle", entries, CLASSIC)
}

@Serializable(with = HeadAccessory.Serializer::class)
enum class HeadAccessory(val label: String) {
    NONE("None"), HEADPHONES("Headphones"), VR_HEADSET("VR headset"), BEANIE("Beanie"),
    CROWN("Crown"), WIZARD_HAT("Wizard hat"), CAT_EARS("Cat ears"), HELMET("Helmet"),
    SLEEP_CAP("Sleep cap");

    object Serializer : WireEnumSerializer<HeadAccessory>("HeadAccessory", entries, NONE)
}

@Serializable(with = FaceAccessory.Serializer::class)
enum class FaceAccessory(val label: String) {
    NONE("None"), GLASSES("Glasses"), SUNGLASSES("Sunglasses");

    object Serializer : WireEnumSerializer<FaceAccessory>("FaceAccessory", entries, NONE)
}

@Serializable(with = BodyAccessory.Serializer::class)
enum class BodyAccessory(val label: String) {
    NONE("None"), HOODIE("Hoodie"), BLANKET("Blanket");

    object Serializer : WireEnumSerializer<BodyAccessory>("BodyAccessory", entries, NONE)
}

@Serializable(with = Prop.Serializer::class)
enum class Prop(val label: String, val emoji: String) {
    NONE("None", ""), COFFEE("Coffee", "☕"), TEA("Tea", "🍵"), CONTROLLER("Controller", "🎮"),
    BOOK("Book", "📖"), PHONE("Phone", "📱"), MICROPHONE("Microphone", "🎤"), POTION("Potion", "🧪"),
    PIZZA("Pizza", "🍕"), KEYBOARD("Keyboard", "⌨️"), WRENCH("Wrench", "🔧"), SWORD("Sword", "🗡️"),
    GAMEPAD("Gamepad", "🕹️");

    object Serializer : WireEnumSerializer<Prop>("Prop", entries, NONE)
}

@Serializable(with = Scene.Serializer::class)
enum class Scene(val label: String) {
    PLAIN_GRADIENT("Plain"), COZY_BEDROOM("Cozy bedroom"), DESK_SETUP("Desk setup"),
    CAMPFIRE("Campfire"), DUNGEON_TAVERN("Tavern"), SPACE_STATION("Space station"),
    RAINY_WINDOW("Rainy window"), NEON_CITY("Neon city"), FOREST("Forest"), CLOUDSCAPE("Clouds");

    object Serializer : WireEnumSerializer<Scene>("Scene", entries, PLAIN_GRADIENT)
}

@Serializable(with = FrameStyle.Serializer::class)
enum class FrameStyle(val label: String) {
    CIRCLE("Circle"), SQUIRCLE("Squircle");

    object Serializer : WireEnumSerializer<FrameStyle>("FrameStyle", entries, SQUIRCLE)
}

/**
 * A user's avatar, assembled from deterministic layers. Eye and mouth shapes are derived from
 * [expression] (see [FaceSpec]) rather than stored separately, which keeps every expression
 * readable at widget size.
 */
@Serializable
data class AvatarConfig(
    val baseForm: BaseForm = BaseForm.HUMAN,
    val bodyColor: Int = AvatarPalette.body[0],
    val faceStyle: FaceStyle = FaceStyle.CLASSIC,
    val expression: Expression = Expression.NEUTRAL,
    val headAccessory: HeadAccessory = HeadAccessory.NONE,
    val faceAccessory: FaceAccessory = FaceAccessory.NONE,
    val bodyAccessory: BodyAccessory = BodyAccessory.NONE,
    val handProp: Prop = Prop.NONE,
    val scene: Scene = Scene.PLAIN_GRADIENT,
    val frameStyle: FrameStyle = FrameStyle.SQUIRCLE,
    val themeColor: Int = AvatarPalette.theme[0],
    val renderVersion: Int = 1,
) {
    /** Base form and colors only: what a viewer sees when avatar details are not shared. */
    fun minimal(): AvatarConfig = AvatarConfig(
        baseForm = baseForm,
        bodyColor = bodyColor,
        themeColor = themeColor,
        frameStyle = frameStyle,
    )
}

object AvatarPalette {
    val body: List<Int> = listOf(
        0xFFFFD45C.toInt(), // sunny
        0xFFF7B267.toInt(), // peach
        0xFFD9A07A.toInt(), // tan
        0xFF8D5B3E.toInt(), // cocoa
        0xFFFF9FB7.toInt(), // bubblegum
        0xFF9AD0EC.toInt(), // sky
        0xFFB8E0A8.toInt(), // mint
        0xFFC3B1E1.toInt(), // lavender
        0xFFF4F1EA.toInt(), // ghost
        0xFFB0B8C4.toInt(), // steel
        0xFFFF9F45.toInt(), // fox
        0xFF7FD48A.toInt(), // alien
    )
    val theme: List<Int> = listOf(
        0xFF7C6CF2.toInt(), // iris
        0xFF4FB3BF.toInt(), // lagoon
        0xFFF28C6C.toInt(), // coral
        0xFF6BBF59.toInt(), // fern
        0xFFE5B84B.toInt(), // honey
        0xFF5A6B8C.toInt(), // dusk
    )
}
