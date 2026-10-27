package dev.deeptelar.scarytruc

/**
 * The nights of the game. The first four each teach one thing, like the levels of Lemmings bring
 * one skill at a time; the fifth is the whole game, and [ENDLESS] is the fifth on a new maze each
 * time. A night with a [seed] is always the same maze.
 */
enum class Plan(
    val title: String,
    val crew: List<Role>,
    val width: Int,
    val height: Int,
    val dooms: Int,
    val hollow: Boolean,
    val candy: Int,
    val lostCandy: Int,
    val marigolds: Int,
    val dawn: Int,
    val seed: Long?,
    val hint: String,
) {
    FIRST(
        "Night 1: the Scout",
        listOf(Role.SCOUT), 6, 4, dooms = 1, hollow = false, candy = 4, lostCandy = 0, marigolds = 0, dawn = 24, seed = 2,
        hint = "The Scout can't see the skull. You can. Right click a cell to put a trick there (2 candies): nobody walks into a scream.",
    ),
    SECOND(
        "Night 2: the Coder",
        listOf(Role.SCOUT, Role.CODER), 7, 5, dooms = 2, hollow = false, candy = 3, lostCandy = 2, marigolds = 0, dawn = 28, seed = 1078,
        hint = "Left click is a treat (1 candy): whoever stands next to it goes to eat it. The Coder smells one two steps away. Lost candy lies in dead ends: walk someone there to refill your bag.",
    ),
    THIRD(
        "Night 3: the Hollow",
        listOf(Role.PLANNER, Role.SCOUT, Role.CODER), 8, 6, dooms = 2, hollow = true, candy = 5, lostCandy = 2, marigolds = 0, dawn = 38, seed = 2104,
        hint = "Something hunts them: the Hollow. You see it only in their light; the edges redden as it nears. A treat lures it and keeps it eating. A trick is a jack-o'-lantern: it never walks into one.",
    ),
    FOURTH(
        "Night 4: the Critic",
        Role.entries, 9, 6, dooms = 3, hollow = true, candy = 6, lostCandy = 3, marigolds = 0, dawn = 42, seed = 3292,
        hint = "The Critic does the opposite of everyone: it walks into a scream next to it, and runs back from a treat.",
    ),
    FIFTH(
        "Night 5: Tots Sants",
        Role.entries, 10, 7, dooms = 4, hollow = true, candy = 6, lostCandy = 3, marigolds = 1, dawn = 46, seed = 4248,
        hint = "One marigold tonight: after a death, it brings the night back to an earlier turn.",
    ),
    ENDLESS(
        "Endless nights",
        Role.entries, 10, 7, dooms = 4, hollow = true, candy = 6, lostCandy = 3, marigolds = 1, dawn = 46, seed = null,
        hint = "A new maze every night. Its number is its seed: share it.",
    ),
    ;

    val next: Plan? get() = entries.getOrNull(ordinal + 1)

    fun maze(seed: Long = this.seed ?: 1): Maze = Maze.of(seed, width, height, dooms, lostCandy, crew.size)
}
