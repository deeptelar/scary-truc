package dev.deeptelar.scarytruc

/**
 * The little drawings of the notebook, in the pixels of a phone of 2003: what the walls of each
 * place look like, the caller as an arrow, and the things they can pick up. In each, `#` is ink,
 * `+` half ink and `-` paper.
 */
object NotebookArt {
    /** A drawing that stands along a wall of [plan], twice on each wall, always upright. */
    fun wall(plan: Plan): List<String> =
        when (plan) {
            // Rock that rises from the floor.
            Plan.CAVE ->
                listOf(
                    ".#....",
                    ".#..#.",
                    "##..#.",
                    "###.##",
                    "######",
                    "##+###",
                )
            // A fir.
            Plan.WOODS ->
                listOf(
                    "..##..",
                    ".####.",
                    "..##..",
                    ".####.",
                    "######",
                    "..##..",
                )
            // A hatch in a metal wall.
            Plan.CUBE ->
                listOf(
                    "######",
                    "#----#",
                    "#-##-#",
                    "#-##-#",
                    "#----#",
                    "######",
                )
            // The stones of a well.
            Plan.TAPE ->
                listOf(
                    "##.###",
                    "##.###",
                    "......",
                    "###.##",
                    "###.##",
                    "......",
                )
            // A hedge with snow on it.
            Plan.HEDGE ->
                listOf(
                    ".+++..",
                    "+++++.",
                    "#+#+##",
                    "######",
                    "#.##.#",
                    "######",
                )
            // A porthole in the hull.
            Plan.SHIP ->
                listOf(
                    ".####.",
                    "##++##",
                    "#+--+#",
                    "#+--+#",
                    "##++##",
                    ".####.",
                )
        }

    /** The caller, an arrow that points where they face; drawn pointing up. */
    val caller: List<String> =
        listOf(
            "...#...",
            "..###..",
            ".#####.",
            "#######",
            "..###..",
            "..###..",
            "..###..",
        )

    /** What [item] looks like, in the notebook and in the pocket. */
    fun item(item: Item): List<String> =
        when (item) {
            Item.BATTERY ->
                listOf(
                    ".###.",
                    "#####",
                    "#---#",
                    "#####",
                    "#---#",
                    "#####",
                )
            Item.FLARE ->
                listOf(
                    "..#..",
                    ".#+#.",
                    "..#..",
                    "..#..",
                    "..#..",
                    "..#..",
                )
            Item.STONE ->
                listOf(
                    ".....",
                    ".##..",
                    "####.",
                    "#####",
                    "#+###",
                    ".###.",
                )
        }

    /** [rows] turned a quarter to the right, [quarters] times. */
    fun turned(rows: List<String>, quarters: Int): List<String> {
        var turned = rows
        repeat(((quarters % 4) + 4) % 4) {
            val height = turned.size
            val width = turned.maxOf { it.length }
            turned = (0 until width).map { x -> (0 until height).map { y -> turned[height - 1 - y].getOrElse(x) { '.' } }.joinToString("") }
        }
        return turned
    }
}
