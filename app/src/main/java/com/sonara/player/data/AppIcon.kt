package com.sonara.player.data

import com.sonara.player.R

/** The headings used on the App icon screen. */
enum class AppIconGroup(val label: String) {
    BRAND("Signature"),
    DARK("Dark"),
    LIGHT("Light"),
    COLORFUL("Colorful"),
    PREMIUM("Premium"),
}

/**
 * Every launcher icon the user can pick.
 *
 * Each entry matches one `<activity-alias>` in AndroidManifest.xml (same [alias] name) and one
 * adaptive icon file in res/mipmap-anydpi-v26 (see [fileName]). To add an icon: add a file, an
 * alias and an entry here. AppIconTest checks that all three stay in sync.
 */
enum class AppIcon(
    val label: String,
    val group: AppIconGroup,
    /** Name of the manifest alias, e.g. ".IconAurora" is written as "IconAurora" here. */
    val alias: String,
    val iconRes: Int,
) {
    AURORA("Aurora", AppIconGroup.BRAND, "IconAurora", R.mipmap.ic_launcher),
    NEBULA("Nebula", AppIconGroup.BRAND, "IconNebula", R.mipmap.ic_launcher_nebula),
    OBSIDIAN("Obsidian", AppIconGroup.DARK, "IconObsidian", R.mipmap.ic_launcher_obsidian),
    GRAPHITE("Graphite", AppIconGroup.DARK, "IconGraphite", R.mipmap.ic_launcher_graphite),
    NEON("Neon", AppIconGroup.DARK, "IconNeon", R.mipmap.ic_launcher_neon),
    MIDNIGHT("Midnight", AppIconGroup.DARK, "IconMidnight", R.mipmap.ic_launcher_midnight),
    SLATE("Slate", AppIconGroup.DARK, "IconSlate", R.mipmap.ic_launcher_slate),
    PEARL("Pearl", AppIconGroup.LIGHT, "IconPearl", R.mipmap.ic_launcher_pearl),
    CLOUD("Cloud", AppIconGroup.LIGHT, "IconCloud", R.mipmap.ic_launcher_cloud),
    MINT("Mint", AppIconGroup.LIGHT, "IconMint", R.mipmap.ic_launcher_mint),
    CANDY("Candy", AppIconGroup.COLORFUL, "IconCandy", R.mipmap.ic_launcher_candy),
    SUNSET("Sunset", AppIconGroup.COLORFUL, "IconSunset", R.mipmap.ic_launcher_sunset),
    OCEAN("Ocean", AppIconGroup.COLORFUL, "IconOcean", R.mipmap.ic_launcher_ocean),
    FOREST("Forest", AppIconGroup.COLORFUL, "IconForest", R.mipmap.ic_launcher_forest),
    CRIMSON("Crimson", AppIconGroup.COLORFUL, "IconCrimson", R.mipmap.ic_launcher_crimson),
    GOLD("Gold", AppIconGroup.PREMIUM, "IconGold", R.mipmap.ic_launcher_gold);

    /** The icon file name (without .xml) inside res/mipmap-anydpi-v26. */
    val fileName: String
        get() = if (this == DEFAULT) "ic_launcher" else "ic_launcher_" + name.lowercase()

    companion object {
        /** The icon that is enabled when the app is first installed. */
        val DEFAULT: AppIcon = AURORA
    }
}
