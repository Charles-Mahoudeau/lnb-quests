# LNB Quests

Quests paid in LNBits on LNB's Minecraft server, for NeoForge 1.21.1 (Kotlin for Forge). The server counts and
reports; LNB's site decides who gets what, prices every reward and pays it. The contract is `docs/quests.md` on the
site.

- **Personal quests**: cumulative tiers each player climbs (mine 1,000 / 10,000 / 100,000 stone…), claimed in the game
  or on the site.
- **Server quests**: advancements paid by rank across the network (1st, 2nd, 3rd, then the rest).
- **Community quests**: one goal for the whole server, planned on the site, paid by tier when it ends.
- **Flash quests**: random races when enough players are online; the first to finish wins.
- **Daily quests**: a few quests drawn every day at midnight (Paris), the same for everyone; each player who finishes
  one before midnight is paid once.

Personal quests are sorted in chapters and server quests in Records groups: the site's categories, each with optional
subcategories shown as headings inside it.

Rewards follow the economy spec: a nightly index of active players' wealth, a damped server multiplier, a catch-up
bonus for poorer players, and a 30-day cap. All of it is set on the site (`/admin/economy/quests`).

## Install

Required on the server and on every client, with [LNB Warden](https://github.com/Dimitri-Collineau/lnb-warden), which
holds the connection to the site: set `api.url` and `api.token` in `config/lnbwarden-server.toml`. LNB Economy is not
required, but items given by quests wait in its mailbox (`/mailbox`).

The catalogue starts from `config/lnbquests/quests.json`, written from the mod's default the first time. At every start
the server sends it to the site, which adds the categories and quests it doesn't know; after that, edits on the site
win. The file is `{ "categories": [...], "quests": [...] }`: a category is
`{ "id", "family": "individual" | "server", "name": { "fr", "en" }, "icon", "siteIcon"?, "subcategories"?: [{ "id", "name" }] }`,
and a quest names its `category` and, optionally, its `subcategory`. A server quest without a category is placed from
its advancement. An existing `quests.json` doesn't pick up a new default: replace it, or import the file on the site.

## Playing

- The book button left of the inventory, or `/quests` (`/quetes`), opens the quest screen.
- `/quests deliver <quest>` hands items over for a delivery quest (the screen has a button too).
- Counting rules (from the site's settings): blocks placed by players, mobs from spawners and blocks broken by machines
  don't count; blocks made by fluids (stone generators) do; creative and spectator players don't progress.

The server keeps the site's quest event stream open (with Warden's `api.url` and `api.token`): a quest, a setting,
the season or the community quest edited on the site, and a reward claimed there, show in the game within a second.
The poll (`sync.pollSeconds`) still runs in case the stream is down, and `/quests sync` forces one.

Operators: `/quests flash start <quest> [goal]`, `/quests flash stop`, `/quests sync`, `/quests status`, and
`/quests reward copy`, which copies the held item as a quest reward to paste in the site's quest editor.

## Building

```
./gradlew build
```

Needs Java 21 (`JAVA_HOME`) and `libs/lnbwarden-*.jar` (not committed: build LNB Warden and copy its jar). Every call
that pays goes through a journal (`<world>/data/lnbquests_journal.json`) and is sent again with the same idempotency
key until the site answers, after a restart too.

## License

AGPL-3.0-or-later.
