# In-game test plan

With the site running (`feat/quests`), the flag `economy.quests` on, LNB Warden linked, and two players (A and B).

1. **Start.** The server log has no quest warning. On the site, `/admin/economy/quests` lists the catalogue from
   `quests.json`. `/quests status` says "from the site".
2. **Screen.** The book button left of the inventory and `/quests` open the screen. Tabs switch; categories switch.
3. **Personal tier.** Mine 1,000 stone by hand (or lower the tier on the site, then `/quests sync`). A chat line says
   the tier is reached. The screen shows "Claim"; claim it: the balance moves, the site shows the tier claimed. Claim
   another tier on the site instead: the screen shows it claimed after a refresh.
4. **Anti-cheat.** Place stone and mine it back: the counter doesn't move. Stone from a lava and water generator counts.
   Creative mode: nothing counts. Mobs from a spawner don't count.
5. **Delivery.** Hold white wool, open the screen, Personal, Deliveries, "Deliver": the wool is used up and the counter
   moves. A renamed stack is kept.
6. **Server quest.** A enters the Nether: everyone sees "A is 1st…", A is paid. B enters: 2nd. On the site, the podium
   shows both. A's second Minecraft account (same LNB account) gets "already ranked".
7. **Flash.** `/quests flash start flash_fish 3`: the boss bar shows; the first to catch 3 fish wins, everyone is told,
   the winner is paid, the site's Flash tab lists the race. Start another and let it expire.
8. **Community.** Plan a community quest on the site (mining, small goal) starting now. Log in again: the reminder
   shows. Mine; after `/quests sync` the site's progress moves. End it on the site (set the end in the past and wait for
   the task): qualified players are paid.
9. **Reset.** Complete a tier and claim it. Reset the quests on the site (`/admin/economy/quests`, Settings tab):
   within a second online players are told, counters (individual, daily, flash) go back to zero, the screen and badge
   refresh, and the Nether advancement is taken back (A can be ranked again). B, offline at the time, is reset at
   login. The next `/quests sync` doesn't bring the old progress back on `/minecraft/quests`. Restart the server: no
   second reset. First start of this version on an existing world: nothing is reset, `/quests status` shows the date.
10. **Site down.** Stop the site, reach a tier, claim: "the site doesn't answer". Start it again: the tier and the claim
    go through within a few minutes, once each.
11. **Categories.** On the site, Categories tab: add an individual category "Début de partie" with a subcategory
    "Outils", put a personal quest in it with that subcategory. Within a second, the book shows the new chapter with
    the quest under an "Outils" heading. Put a server quest in the Nether group: Records shows it there. With many
    chapters, the sidebar scrolls with the wheel. Deleting a category a quest uses is refused.
12. **Daily.** Settings, daily count 3, and at least three daily quests on. The book's Daily page lists three quests,
    the same for A and B, with the time left. Do one: the action bar counts, then a chat line and a toast say it's paid,
    and the page shows it done; doing more pays nothing more. A's second account sees it done too. At midnight (or
    after deleting today's `quest_daily_draw` rows on the site, then `/quests sync`), new quests come and counters
    start over.
