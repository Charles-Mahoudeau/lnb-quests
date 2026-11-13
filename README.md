# LNB Quests

Quests paid in LNBits on LNB's Minecraft server, for NeoForge 1.21.1 (Kotlin for Forge). The server counts and
reports; LNB's site decides who gets what, prices every reward and pays it. The contract is `docs/quests.md` on the
site.

- **Personal quests**: cumulative tiers each player climbs (mine 1,000 / 10,000 / 100,000 stone…), claimed in the game
  or on the site.
- **Server quests**: advancements paid by rank across the network (1st, 2nd, 3rd, then the rest).
- **Community quests**: one goal for the whole server, planned on the site, paid by tier when it ends.
- **Flash quests**: random races when enough players are online; the first to finish wins.

Rewards follow the economy spec: a nightly index of active players' wealth, a damped server multiplier, a catch-up
bonus for poorer players, and a 30-day cap. All of it is set on the site (`/admin/economy/quests`).

## Install

Required on the server and on every client, with [LNB Warden](https://github.com/Dimitri-Collineau/lnb-warden), which
holds the connection to the site: set `api.url` and `api.token` in `config/lnbwarden-server.toml`. LNB Economy is not
required, but items given by quests wait in its mailbox (`/mailbox`).

The catalogue starts from `config/lnbquests/quests.json`, written from the mod's default the first time. At every start
the server sends it to the site, which adds the quests it doesn't know; after that, edits on the site win.

## Playing

- The book button left of the inventory, or `/quests` (`/quetes`), opens the quest screen.
- `/quests deliver <quest>` hands items over for a delivery quest (the screen has a button too).
- Counting rules (from the site's settings): blocks placed by players, mobs from spawners and blocks broken by machines
  don't count; blocks made by fluids (stone generators) do; creative and spectator players don't progress.

Operators: `/quests flash start <quest> [goal]`, `/quests flash stop`, `/quests sync`, `/quests status`, and
`/quests reward copy`, which copies the held item as a quest reward to paste in the site's quest editor.

## Building

```
./gradlew build
```

Needs Java 21 (`JAVA_HOME`) and `libs/lnbwarden-*.jar` (not committed: build LNB Warden and copy its jar). Every call
that pays goes through a journal (`<world>/data/lnbquests_journal.json`) and is sent again with the same idempotency
key until the site answers, after a restart too.

## Licence

AGPL-3.0-or-later.
