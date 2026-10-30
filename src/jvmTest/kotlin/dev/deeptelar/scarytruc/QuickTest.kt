package dev.deeptelar.scarytruc

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuickTest {
    private val plan = Plan.CUBE
    private val maze = plan.maze()
    private val empty = Haunt(emptyMap(), emptyMap(), emptyMap())
    private val fork = maze.cells.first { maze.exits(it).size >= 3 }

    private fun call(lang: Lang, at: Pos = fork, facing: Dir = Dir.NORTH) =
        Call.begin(plan, maze, empty, lang = lang).copy(at = at, facing = facing, hollow = null)

    @Test
    fun itOffersOnlyTheWaysThatAreOpen() {
        for (lang in Lang.entries) {
            val here = call(lang)
            val offered = Quick.of(here, maze, empty)
            // Every way, from a cell open on all sides or not, reads as itself with no model.
            for (dir in Dir.entries) {
                val each = Quick.of(call(lang, facing = dir), maze, empty)
                assertEquals(maze.exits(fork).size, each.count { Keywords.bare(it, lang)?.turn != null }, "$lang $dir in $each")
            }
            for (way in listOf(Order.AHEAD, Order.LEFT, Order.RIGHT, Order.BACK)) {
                val open = maze.canGo(here.at, here.facing.turned(way.turn!!))
                assertEquals(open, offered.any { Keywords.bare(it, lang) == way }, "$lang $way in $offered")
            }
        }
    }

    @Test
    fun everyOptionMeansWhatItSaysInBothLanguages() = runBlocking {
        val rock = Haunt(emptyMap(), emptyMap(), emptyMap(), items = mapOf(fork to Item.STONE))
        for (lang in Lang.entries) {
            val here = call(lang).copy(carried = Item.entries.toList())
            val offered = Quick.of(here, maze, rock)
            suspend fun heard(words: String) = Keywords.listen(here.copy(said = words))
            for (words in offered) assertFalse(heard(words).foreign, "$lang \"$words\" reads as another language")
            // Orders need no model: a tap answers at once.
            assertNotNull(offered.firstOrNull { Keywords.bare(it, lang) == Order.HIDE }, "$lang hide in $offered")
            assertNotNull(offered.firstOrNull { Keywords.bare(it, lang) == Order.MARK }, "$lang mark in $offered")
            assertNotNull(offered.firstOrNull { Keywords.bare(it, lang) == Order.TAKE }, "$lang take in $offered")
            for (item in Item.entries) {
                val use = heard(item.use(lang))
                assertEquals(Order.USE, use.order, item.use(lang))
                assertEquals(item, use.item, item.use(lang))
            }
            val calm = offered.first { Keywords.bare(it, lang) == null && heard(it).order == Order.NONE }
            assertTrue(heard(calm).calm > 0, "$lang \"$calm\" calms")
        }
    }

    @Test
    fun itOffersOnlyWhatCanBeDoneHere() {
        val offered = Quick.of(call(Lang.EN), maze, empty)
        assertFalse("Take it" in offered, "nothing here to take")
        assertTrue(offered.none { it in Item.entries.map { item -> item.use(Lang.EN) } }, "nothing carried")
        val marked = call(Lang.EN).copy(marks = listOf(Mark(fork, 1)))
        assertFalse("Mark the wall" in Quick.of(marked, maze, empty), "already marked")
        assertTrue("Don't move" in Quick.of(call(Lang.EN).copy(hidden = true), maze, empty))
    }
}
