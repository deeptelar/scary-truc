# Scary Truc

A phone rings at midnight. One of the crew woke up in a maze, alone, with no idea how they got
there, and you are the only one who picked up. You can't see the maze. You only hear them: what
they see, what they find on the walls, and the slow, wet footsteps of the Hollow, which hunts by
sound. Tell them where to go, and keep them calm: fear is loud.

Six calls, any of them, each a maze drawn by hand after a horror film (The Descent, The Blair
Witch Project, Cube, Ring, The Shining, Alien), and each won its own way. When the line goes dead,
you learn how many steps they were from the way out.

**Play it: <https://deeptelar.github.io/scary-truc/>**, on a phone too. Without a model the caller
knows only a few words (left, right, ahead, back, stay, hide, mark). Add a model under **Model
settings**: Gemma 4 on [OpenRouter](https://openrouter.ai) with your own key, or your
[Ollama](https://ollama.com). The key stays in your browser and goes from it straight to the
server. A friend's proxy works too: its address, and your token as the key.

On the phone you can talk instead of typing: tap the microphone, speak, and what you said goes to
the caller when you pause. The speaker button says the caller's words out loud. Both use the
browser's own speech; most browsers turn speech into text on their maker's servers (Chrome on
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
