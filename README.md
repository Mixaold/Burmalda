# Burmalda

Fabric mod for Minecraft 1.21 – 1.21.11.

A debuff randomizer. Every three minutes everyone gets handed something at random — one player flies
off into the sky, another burns, a third just suffers for no reason. 69 of them: 46 personal ones and
23 that hit the whole server at once, like a military base with a commander shouting drill orders,
Pandora's box, a drone hunting you down, or an interrogation room built around you while you stand
there.

Best played with friends — a lot of the debuffs tie players to each other.

## Install

Needs **Fabric API**. Put the jar on **both the client and the server** — the server runs the
debuffs, the client draws the panel and the timers.

Take the jar for your version:

| Jar | Covers |
|---|---|
| `burmalda-1.2.0-1.21.1.jar` | 1.21, 1.21.1 |
| `burmalda-1.2.0-1.21.3.jar` | 1.21.2, 1.21.3 |
| `burmalda-1.2.0-1.21.5.jar` | 1.21.4, 1.21.5 |
| `burmalda-1.2.0-1.21.8.jar` | 1.21.6, 1.21.7, 1.21.8 |
| `burmalda-1.2.0-1.21.11.jar` | 1.21.9, 1.21.10, 1.21.11 |

## Start it

Type `start` in chat (or `пошла возня`), then confirm with `burmalda` (or `бурмалда`).

Best to start once everybody has joined, but latecomers get caught up anyway.

`/burmalda menu` opens a menu for handing out debuffs by hand — the host and whoever is in the
whitelist only.

## Bugs

Open an issue: https://github.com/Mixaold/Burmalda/issues

Say which Minecraft version and which jar you're on, what you were doing and which debuff was
running. If the game crashed or kicked you, attach `.minecraft/logs/latest.log`.

## Build

One source tree, five build targets through Stonecutter:

```
./gradlew :1.21.1:build :1.21.3:build :1.21.5:build :1.21.8:build :1.21.11:build
```

The jars land in `versions/<version>/build/libs/` — the plain one, not `-sources`.

## License

MIT.

---

# Burmalda (Русский)

Мод под Fabric для Minecraft 1.21 – 1.21.11.

Рандомайзер дебаффов. Каждые три минуты всем выдаётся что-то случайное — кто-то улетает в небо,
кто-то горит, кто-то просто страдает без причины. Всего 69: 46 личных и 23 групповых, которые
накрывают весь сервер разом — военная часть с командиром и его командами, ящик Пандоры, дрон который
за тобой охотится, допросная которую строят прямо вокруг тебя.

Лучше играть с друзьями — куча дебаффов завязана на связки между игроками.

## Установка

Нужен **Fabric API**. Ставить jar **и на клиент, и на сервер** — сервер крутит дебаффы, клиент рисует
панель и таймеры.

Бери jar под свою версию:

| Jar | Под какие версии |
|---|---|
| `burmalda-1.2.0-1.21.1.jar` | 1.21, 1.21.1 |
| `burmalda-1.2.0-1.21.3.jar` | 1.21.2, 1.21.3 |
| `burmalda-1.2.0-1.21.5.jar` | 1.21.4, 1.21.5 |
| `burmalda-1.2.0-1.21.8.jar` | 1.21.6, 1.21.7, 1.21.8 |
| `burmalda-1.2.0-1.21.11.jar` | 1.21.9, 1.21.10, 1.21.11 |

## Запуск

Пишешь в чат `пошла возня` (или `start`), потом подтверждаешь словом `бурмалда` (или `burmalda`).

Лучше запускать когда все уже зашли, но и опоздавших мод подхватит.

`/burmalda menu` — меню чтобы выдавать дебаффы руками, доступно хосту и тем кто в вайтлисте.

## Баги

Пиши в issues: https://github.com/Mixaold/Burmalda/issues

Укажи версию Minecraft и какой jar стоит, что делал и какой дебафф был активен. Если игру выкинуло
или крашнуло — приложи `.minecraft/logs/latest.log`.

## Сборка

Один исходник, пять сборок через Stonecutter:

```
./gradlew :1.21.1:build :1.21.3:build :1.21.5:build :1.21.8:build :1.21.11:build
```

Готовые jar лежат в `versions/<версия>/build/libs/` — обычный, не `-sources`.

## Лицензия

MIT.
