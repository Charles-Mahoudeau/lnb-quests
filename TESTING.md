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
9. **Season.** Start a new season on the site, then `/quests sync`: online players are told, counters reset, and the
   Nether advancement is taken back (A can be ranked again). B, offline at the time, is reset at login.
10. **Site down.** Stop the site, reach a tier, claim: "the site doesn't answer". Start it again: the tier and the claim
    go through within a few minutes, once each.
