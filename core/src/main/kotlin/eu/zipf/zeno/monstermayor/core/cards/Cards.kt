package eu.zipf.zeno.monstermayor.core.cards

import kotlinx.serialization.Serializable

@Serializable
enum class CardType {
    /** Stays in front of the owner and grants a passive effect. */
    KEEP,

    /** Resolved immediately on purchase, then discarded. */
    DISCARD,
}

/** Every power card in the deck. The deck holds one copy of each. */
@Serializable
enum class CardId {
    // Discard cards
    SKYSCRAPER_SNACK,
    MONORAIL_MUNCH,
    POWER_GRID_FEAST,
    FIELD_MEDICS,
    REACTOR_SNACK,
    REFINERY_BLAST,
    MASS_EVACUATION,
    FRENZY,
    TANK_PARADE,

    // Keep cards
    EXTRA_CLAW,
    OVERCLOCKED_BRAIN,
    ARMOR_PLATING,
    SPIKED_TAIL,
    ENERGY_HOARDER,
    BATTERY_PACK,
    EVEN_BIGGER,
    METRO_MUNCHER,
    REGENERATING_HIDE,
    ALPHA_MONSTER,
    HERBIVORE,
    NEWS_TEAM,
    BARGAIN_HUNTER,
    COMPLETE_DESTRUCTION,
    GOURMET,
    UNDERDOG,
    TOKYO_TUNNELS,
}

data class CardDefinition(
    val id: CardId,
    val name: String,
    val cost: Int,
    val type: CardType,
    val description: String,
)

object CardCatalog {
    private val definitions: Map<CardId, CardDefinition> = listOf(
        discard(CardId.SKYSCRAPER_SNACK, "Skyscraper Snack", 6, "+4 ★"),
        discard(CardId.MONORAIL_MUNCH, "Monorail Munch", 3, "+2 ★"),
        discard(CardId.POWER_GRID_FEAST, "Power Grid Feast", 8, "+9 energy"),
        discard(CardId.FIELD_MEDICS, "Field Medic Drones", 3, "Heal 2, even in Tokyo"),
        discard(CardId.REACTOR_SNACK, "Reactor Snack", 6, "+2 ★ and heal 3, even in Tokyo"),
        discard(CardId.REFINERY_BLAST, "Refinery Blast", 6, "+2 ★. All other monsters take 3 damage"),
        discard(CardId.MASS_EVACUATION, "Mass Evacuation", 7, "All other monsters lose 5 ★"),
        discard(CardId.FRENZY, "Frenzy", 7, "Take another turn after this one"),
        discard(CardId.TANK_PARADE, "Tank Parade", 4, "+4 ★. You take 3 damage"),
        keep(CardId.EXTRA_CLAW, "Extra Claw", 7, "Roll one extra die"),
        keep(CardId.OVERCLOCKED_BRAIN, "Overclocked Brain", 5, "One extra reroll each turn"),
        keep(CardId.ARMOR_PLATING, "Armor Plating", 4, "Ignore any damage of exactly 1"),
        keep(CardId.SPIKED_TAIL, "Spiked Tail", 5, "Your attacks deal +1 damage"),
        keep(CardId.ENERGY_HOARDER, "Energy Hoarder", 3, "End of turn: +1 ★ per 6 energy you hold"),
        keep(CardId.BATTERY_PACK, "Battery Pack", 3, "+1 energy whenever you gain energy from dice"),
        keep(CardId.EVEN_BIGGER, "Even Bigger", 4, "+2 max health. Heal 2 when bought"),
        keep(CardId.METRO_MUNCHER, "Metro Muncher", 4, "+1 ★ when you start your turn in Tokyo"),
        keep(CardId.REGENERATING_HIDE, "Regenerating Hide", 4, "Heal 1 extra whenever you heal with dice"),
        keep(CardId.ALPHA_MONSTER, "Alpha Monster", 5, "+1 ★ whenever you attack"),
        keep(CardId.HERBIVORE, "Herbivore", 5, "End of turn: +1 ★ if you dealt no damage"),
        keep(CardId.NEWS_TEAM, "Dedicated News Team", 3, "+1 ★ whenever you buy a card"),
        keep(CardId.BARGAIN_HUNTER, "Bargain Hunter", 3, "Cards cost you 1 less energy"),
        keep(CardId.COMPLETE_DESTRUCTION, "Complete Destruction", 3, "Roll all six faces: +9 ★"),
        keep(CardId.GOURMET, "Gourmet", 4, "Scoring 1-1-1 gives +2 extra ★"),
        keep(CardId.UNDERDOG, "Underdog", 3, "End of turn: +1 ★ if you have the fewest ★"),
        keep(CardId.TOKYO_TUNNELS, "Tokyo Tunnels", 5, "+1 damage when attacking from Tokyo"),
    ).associateBy { it.id }

    init {
        check(definitions.keys == CardId.entries.toSet()) { "Every CardId needs a definition" }
    }

    operator fun get(id: CardId): CardDefinition = definitions.getValue(id)

    val all: List<CardDefinition> get() = CardId.entries.map(::get)

    private fun discard(id: CardId, name: String, cost: Int, text: String) =
        CardDefinition(id, name, cost, CardType.DISCARD, text)

    private fun keep(id: CardId, name: String, cost: Int, text: String) =
        CardDefinition(id, name, cost, CardType.KEEP, text)
}
