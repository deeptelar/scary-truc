# Scary Truc

A phone rings at midnight. One of the crew woke up in a maze, alone, with no idea how they got
there, and you are the only one who picked up. You can't see the maze. You only hear them: what
they see, what they find on the walls, and the slow, wet footsteps of the Hollow, which hunts by
sound. Tell them where to go, and keep them calm: fear is loud.

Six calls, any of them, each a maze drawn by hand after a horror film (The Descent, The Blair
Witch Project, Cube, Ring, The Shining, Alien), and each won its own way. When the line goes dead,
you learn how many steps they were from the way out.

Off the safe way, in side corridors and dead ends, lie things worth the detour: tell them to take
it, then to use it. A phone battery buys time, a flare sends the Hollow running, and a stone thrown
down a corridor draws it to the noise.

New here? The phone opens on **How to save them**: seven pages, each a moving picture, a line from
whoever built the mazes and what you could type. It is under **Menu** later.

**Play it: <https://deeptelar.github.io/scary-truc/>**, on a phone too. Without a model the caller
knows only a few words (left, right, ahead, back, stay, hide, mark). Add a model under **Menu >
Model**: Gemma 4 on [OpenRouter](https://openrouter.ai) with your own key, or your
[Ollama](https://ollama.com). The key stays in your browser and goes from it straight to the
server. A friend's proxy works too: its address, and your token as the key.

On the phone you can talk instead of typing: tap Mic, speak, and what you said goes to the caller
when you pause. It uses the browser's own speech recognition; most browsers turn speech into text on their maker's servers (Chrome on
Google's), and the page only gets the text.

On the desktop: `./gradlew run`, or `./gradlew run --args='--call ship'` (with Ollama running and `gemma4:12b` pulled, Gemma plays the
caller; `--no-gemma` without it).

## How it works

A call is a [Telar](https://github.com/deeptelar/telar) graph that pauses for your words:

| Node | What it does |
|---|---|
| `speak` | Gemma voices the caller, from the facts `walk` gave it and nothing else |
| `listen` | Gemma reads your words: the way you said to go, and how they made the caller feel |
| `walk` | The maze answers in code: the caller obeys, refuses or bolts, walks a corridor, the Hollow hunts |
| `whisper` | When the Hollow is close, it speaks on the line, to you |

Gemma never decides what is true: the maze does, so the caller can't invent a door that isn't
there.

## Build

```bash
./gradlew jvmTest                      # the tests
./gradlew run                          # the desktop game
./gradlew wasmJsBrowserDistribution    # the page, in build/dist/wasmJs/productionExecutable
```

Pixelify Sans is under the SIL Open Font License ([licenses/](licenses/PixelifySans-OFL.txt)).

The sounds are cut from sound effects on [Pixabay](https://pixabay.com/sound-effects/):

- Dark ambient loop: [freesound_community](https://pixabay.com/users/freesound_community-46691455/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=31306)
- Old mine ambience in The Cave: [Joel Fazhari](https://pixabay.com/users/joelfazhari-16466931/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=200677)
- Metallic ambience after midnight: [Jurij](https://pixabay.com/users/soundreality-31074404/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=593615)
- Phone ring: [Andre Gautawa](https://pixabay.com/users/gautawa-47468133/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=272648)
- Caller's message: [Elio](https://pixabay.com/users/son_duquotidient-44285139/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=391098)
- Footsteps: [KRNBEATZ](https://pixabay.com/users/krnbeatz-52298700/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=422586)
- Running in a panic: [freesound_community](https://pixabay.com/users/freesound_community-46691455/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=47595)
- Woman breathing on the line: [Hasin Amanda](https://pixabay.com/users/hasin2004-46173687/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=247449)
- Man breathing on the line: [freesound_community](https://pixabay.com/users/freesound_community-46691455/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=83103)
- Flare: [freesound_community](https://pixabay.com/users/freesound_community-46691455/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=19895)
- Thrown stone: [LoLa Joy](https://pixabay.com/users/u_xg7ssi08yr-48039129/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=572428)
- Radio interference: [freesound_community](https://pixabay.com/users/freesound_community-46691455/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=7122)
- The Hollow waking: [DRAGON-STUDIO](https://pixabay.com/users/dragon-studio-38165424/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=472380)
- The Hollow's breath: [Aman Kumar](https://pixabay.com/users/tanweraman-29554143/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=392363)
- The Hollow sniffing at a hidden caller: [AlesiaDavina](https://pixabay.com/users/alesiadavina-32608554/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=541144)
- Jump scare: [Gavin Mogensen](https://pixabay.com/users/fronbondi_skegs-23154649/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=503610)
- Midnight bell: [freesound_community](https://pixabay.com/users/freesound_community-46691455/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=33827)
- Midnight horn: [Alexander Jauk](https://pixabay.com/users/alex_jauk-16800354/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=228913)
- Sobbing: [DRAGON-STUDIO](https://pixabay.com/users/dragon-studio-38165424/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=372482)
- Woman's screams: [Dilip](https://pixabay.com/users/virtual_vibes-51361309/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=379381) and [Dilip](https://pixabay.com/users/virtual_vibes-51361309/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=379382)
- Man's screams: [Official Profile](https://pixabay.com/users/universfield-28281460/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=123078) and [Hiran Vinodya](https://pixabay.com/users/vinodadora-30315691/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=128357)
- Line goes dead: [freesound_community](https://pixabay.com/users/freesound_community-46691455/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=103906)
- Door to the way out: [freesound_community](https://pixabay.com/users/freesound_community-46691455/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=37271)
- Way out: [Neo Theone](https://pixabay.com/users/mrstokes302-48032194/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=423626)
