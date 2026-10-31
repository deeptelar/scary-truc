package dev.deeptelar.scarytruc

/**
 * The language of the game: what the phone shows, what the caller says and understands. Spoken to
 * in another one, the caller doesn't understand.
 */
enum class Lang(val code: String, val title: String) {
    EN("en", "English"),
    ES("es", "Español"),
    ;

    /** [en] in English, [es] in Spanish. */
    fun t(en: String, es: String): String = if (this == ES) es else en

    companion object {
        /** The language of a browser's or a system's [code], such as `es-ES`: English unless it is one of ours. */
        fun of(code: String?): Lang = entries.firstOrNull { code?.trim()?.lowercase()?.startsWith(it.code) == true } ?: EN
    }
}

/** What is written about a call, in a language: see [Plan]. */
class PlanText(
    val title: String,
    val film: String,
    val setting: String,
    val hint: String,
    val notes: List<String>,
    val oddities: List<String>,
)

/** What is written about this call in [lang]. */
fun Plan.text(lang: Lang): PlanText =
    if (lang == Lang.EN) PlanText(title, film, setting, hint, notes, oddities) else SPANISH.getValue(this)

/** [Plan.haunt] with its notes and oddities in [lang]. */
fun Plan.haunt(lang: Lang): Haunt = text(lang).let { Haunt.of(rows, it.notes, it.oddities) }

/** What kills, in [lang]. */
fun DoomKind.word(lang: Lang): String =
    lang.t(
        word,
        when (this) {
            DoomKind.PIT -> "un pozo sin fondo"
            DoomKind.MOUTH -> "una puerta que es una boca"
            DoomKind.MIRROR -> "un espejo que te traga"
        },
    )

/** What the caller sees of it from next to it, in [lang]. */
fun DoomKind.sign(lang: Lang): String =
    lang.t(
        sign,
        when (this) {
            DoomKind.PIT -> "el suelo se vuelve negro y húmedo, y se traga la luz de mi móvil"
            DoomKind.MOUTH -> "una puerta, tibia, que se mueve un poco, como si respirara"
            DoomKind.MIRROR -> "un espejo alto, y mi reflejo no sostiene ningún móvil"
        },
    )

/** What a note teaches about it, in [lang]. */
fun DoomKind.lesson(lang: Lang): String =
    lang.t(
        lesson,
        when (this) {
            DoomKind.PIT -> "El suelo negro que se come tu luz no es suelo."
            DoomKind.MOUTH -> "Las puertas no respiran. Si una respira, no la cruces."
            DoomKind.MIRROR -> "Si tu reflejo no te imita, no es un espejo. No entres en él."
        },
    )

/** The name of a thing, in [lang]: all three are feminine in Spanish. */
fun Item.word(lang: Lang): String =
    lang.t(
        word,
        when (this) {
            Item.BATTERY -> "batería"
            Item.FLARE -> "bengala"
            Item.STONE -> "piedra"
        },
    )

/** A thing as the caller sees it, in [lang]. */
fun Item.thing(lang: Lang): String =
    lang.t(
        thing,
        when (this) {
            Item.BATTERY -> "una batería de móvil, del mismo modelo que la mía"
            Item.FLARE -> "una bengala de carretera, sin encender"
            Item.STONE -> "una piedra que me cabe en la mano"
        },
    )

/** What took the caller, kept in [Call.death] in English, in [lang]. */
fun death(death: String?, lang: Lang): String =
    when {
        lang == Lang.EN -> death.orEmpty()
        death == "the Hollow" -> "el Hueco"
        death == "the dark" -> "la oscuridad"
        else -> DoomKind.entries.firstOrNull { it.word == death }?.word(lang) ?: death.orEmpty()
    }

/** A way from where the caller faces, as they say they went or didn't, in [lang]. */
fun Order.way(lang: Lang): String =
    lang.t(
        word,
        when (this) {
            Order.AHEAD -> "hacia delante"
            Order.RIGHT -> "a la derecha"
            Order.BACK -> "hacia atrás"
            Order.LEFT -> "a la izquierda"
            else -> word
        },
    )

private val SPANISH: Map<Plan, PlanText> =
    mapOf(
        Plan.CAVE to
            PlanText(
                "La Cueva",
                "El descenso",
                "Es una cueva. Roca mojada por todas partes, tan estrecha, y agua goteando en algún sitio.",
                "Caza a ciegas, por el sonido, y duerme justo al lado de la salida. Haz que pasen a su lado en calma.",
                listOf(
                    "Bajamos seis. Soy la última. No ve. Oye. Si está cerca, no te muevas. No respires.",
                    DoomKind.PIT.lesson(Lang.ES),
                    DoomKind.MOUTH.lesson(Lang.ES),
                ),
                listOf("una cuerda de escalada, cortada limpia", "un casco, con la lámpara rota y aún encendida"),
            ),
        Plan.WOODS to
            PlanText(
                "El Bosque",
                "El proyecto de la bruja de Blair",
                "Es un bosque. Árboles tan juntos que son como paredes, y las ramas crujen.",
                "Todos los árboles parecen iguales y es fácil andar en círculos. Pide marcas, y recuérdalas.",
                listOf(
                    "Caminamos hacia el sur todo el día y volvimos al mismo tronco. Tres veces. Marca los árboles.",
                    DoomKind.PIT.lesson(Lang.ES),
                    DoomKind.MOUTH.lesson(Lang.ES),
                ),
                listOf(
                    "figuritas hechas de palos, colgando de una rama",
                    "un círculo de pájaros muertos, colocados con cuidado",
                    "un hombre de pie, de cara a un árbol, sin moverse",
                    "una tienda de campaña, rasgada desde dentro",
                ),
            ),
        Plan.CUBE to
            PlanText(
                "El Cubo",
                "Cube",
                "Es una habitación. Cuadrada, de metal, con una escotilla en cada pared, y todo zumba.",
                "Todas las salas parecen iguales, y hay cosas raras por todas partes. Solo tres matan. Escucha.",
                listOf(
                    "Cada sala se parece a la anterior. Algunas matan. Mira antes de entrar.",
                    DoomKind.MOUTH.lesson(Lang.ES),
                    DoomKind.PIT.lesson(Lang.ES),
                    DoomKind.MIRROR.lesson(Lang.ES),
                ),
                listOf(
                    "números grabados en el marco de la escotilla",
                    "una bota. Solo una bota",
                    "una mancha en el suelo con forma de persona",
                    "una escotilla que da a una pared",
                ),
            ),
        Plan.TAPE to
            PlanText(
                "La Cinta",
                "The Ring",
                "Es piedra vieja, mojada, redonda como el interior de un pozo. Hay una tele en el suelo, con estática.",
                "No se fían de ti, y las notas les dan la razón. El camino corto es una trampa. Las flechas de tiza pueden darte la razón a ti.",
                listOf(
                    "No te fíes de la voz del teléfono.",
                    DoomKind.MOUTH.lesson(Lang.ES),
                    DoomKind.PIT.lesson(Lang.ES),
                    "Siete días, dijo. Siempre vuelve a llamar.",
                ),
                listOf("una cinta de vídeo sin etiqueta", "una tele, desenchufada, que sigue mostrando estática"),
            ),
        Plan.HEDGE to
            PlanText(
                "El Seto",
                "El resplandor",
                "Estoy fuera, en la nieve. Setos helados más altos que yo, y un hotel enorme a lo lejos con todas las ventanas a oscuras.",
                "Los setos se mueven a medianoche, y la medianoche llega pronto. Tu libreta mentirá. El frío no.",
                listOf(
                    "Los setos se mueven cuando da la hora. Sigue el frío, no tu mapa.",
                    DoomKind.PIT.lesson(Lang.ES),
                    DoomKind.MOUTH.lesson(Lang.ES),
                ),
                listOf("huellas en la nieve que se acaban en mitad del camino", "un hacha, clavada hondo en el seto"),
            ),
        Plan.SHIP to
            PlanText(
                "La Nave",
                "Alien, el octavo pasajero",
                "Es un pasillo de metal. Tuberías, vapor, y una luz de alarma roja girando en algún sitio.",
                "Está despierto desde el primer segundo. Que no paren, y que se escondan cuando esté cerca.",
                listOf(
                    "Está despierto. Siempre está despierto. No pares. Escóndete en los callejones sin salida.",
                    DoomKind.PIT.lesson(Lang.ES),
                    DoomKind.MOUTH.lesson(Lang.ES),
                ),
                listOf("un transportín de gato, vacío, con la puerta abierta", "un agujero fundido en el suelo, con los bordes aún humeando"),
            ),
    )
