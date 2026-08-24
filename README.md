# focalboard-akka

Boards of cards, where each board decides which of its cards a view shows, in what order,
and in which column.

A port of [mattermost-community/focalboard](https://github.com/mattermost-community/focalboard)
onto **Akka**, built with **Akka Specify**.

---

## Where it came from

Focalboard is a project-tracking application: boards of cards, each board defining its own
card properties, and several views over the same cards. It was ported to derive a
specification format precise enough to regenerate a system on a different stack — the port
is the vehicle, the specification is the deliverable.

The specifications the port was generated from are in
[TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness) under
`focalboard-port/`.

---

## focalboard → this port

📉 684 lines of TypeScript and Go → **563 lines of Java**<br>
⚡ 6,509 nanoseconds to work out what a view shows → **1,530 nanoseconds**<br>
🔁 18 answers to one question over 24 delivery orders → **2 answers**<br>
🖥️ that answer worked out once in every open browser → **once, on the board**<br>
🎨 1,347 files of interface → **6 of them changed**<br>
🖼️ 2 screens compared against the original → **0 differing regions**<br>
✅ 35 sets of inputs put to both → **33 giving identical answers**<br>
⏱️ 3.2 seconds to start → **8.0 seconds**<br>
💾 18.7 MB in memory while idle → **398.8 MB**

Full method and the numbers that did *not* make this list:
[`bench/REPORT.md`](https://github.com/TylerJewell/akka-specify-harness/blob/main/focalboard-port/bench/REPORT.md).

---

## What it took to build

⏱️ **2.7 hours** from the first command to the published repository, **2.7** of them active<br>
💬 **640** exchanges with the model<br>
✍️ **585,140** tokens written by the model, **255,685,685** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **64** tests

```bash
python toolkit/tokens.py --port focalboard    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](https://github.com/TylerJewell/akka-specify-harness/tree/main/port-log).

---

## What it does

From the specification:

- **A view shows the cards its filter keeps, in the order its sort puts them, split into the
  columns its group-by property offers.** Two views over the same cards give two different
  answers, and neither changes the cards.
- **A column exists because the property offers that choice, not because a card made it.**
  A choice nobody has picked still gets a column, and it is empty.
- **A card with no choice made, or one naming a choice that has since been deleted, goes in
  the same place.** That is the leftmost column, and it is labelled "No" followed by the
  property's name.
- **Where a card sits belongs to the view, not to the card.** Dragging a card in one view
  leaves every other view's order exactly as it was.
- **Dragging a card writes two things at once: which column it is now in, and the whole new
  order.** Both land together or neither does.
- **Changing one field of a card replaces that field and leaves the rest alone.** Sending a
  new set of property values replaces the whole set, rather than merging into it.

---

## Design decisions

**One board, one place.** Everything a view needs to answer — the property definitions, the
cards and the views themselves — is held together, because working out what a view shows
reads all three at the same moment. Nothing has to be fetched from elsewhere mid-answer, and
a drag that changes two of them cannot land half-finished.

**Answered on the server.** The original works out what a view shows inside each open
browser, so five people looking at one board work it out five times. Here the board works it
out once and everyone is told the answer, so nobody can be looking at a stale one.

**Pushed, not asked for.** The page is told when something changes instead of asking every
few seconds whether anything has. Nothing is sent while nothing happens, and a change
appears in about a fifth of a second.

**Told the whole answer after a break.** When the connection drops and comes back, the page
is sent the board as it stands rather than the pieces it missed. It cannot end up half
right, and there is nothing to keep in case somebody reconnects.

**The original's own screens.** The interface is focalboard's, running against this
backend — six files of it changed, all of them the part that talks to the server. Comparing
the two side by side then means something, because only one thing was swapped.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/focalboard-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Open** http://localhost:9079.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once
- Node 18 or newer, to build the interface

### Build the interface

The interface is built once and served from the project. It is not rebuilt by `mvn`.

```bash
cd webapp && npm install --legacy-peer-deps && cd ..
python build-webapp.py
```

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9079**.

### Make a board to look at

```bash
curl -X POST http://localhost:9079/api/v2/boards -H 'Content-Type: application/json' -d '{
  "id": "myboard", "teamId": "0", "title": "My board",
  "cardProperties": [{"id": "status", "name": "Status", "type": "select", "options": [
    {"id": "todo", "value": "To Do", "color": "propColorGray"},
    {"id": "doing", "value": "Doing", "color": "propColorYellow"}]}]}'

curl -X POST http://localhost:9079/api/v2/boards/myboard/blocks -H 'Content-Type: application/json' -d '[
  {"id": "c1", "type": "card", "title": "First", "createAt": 1700000000000,
   "fields": {"properties": {"status": "todo"}}},
  {"id": "v1", "type": "view", "title": "By status",
   "fields": {"viewType": "board", "groupById": "status", "sortOptions": [], "cardOrder": ["c1"],
              "visibleOptionIds": [], "hiddenOptionIds": [], "visiblePropertyIds": ["status"],
              "filter": {"operation": "and", "filters": []}}}]'
```

Then open http://localhost:9079/team/0/myboard/v1.

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `akka.javasdk.dev-mode.http-port` | `9079` | Set in `src/main/resources/application.conf`. |

No model provider is used. Nothing here calls a language model.

---

## Where it differs from focalboard

Everything not listed here behaves the same way on purpose, including the parts that look
like mistakes.

- **When only some cards have been given a position by hand.** Focalboard compares two such
  cards by asking twice which goes first, and gets "the other one" both times; what comes
  out is whatever the sorting code makes of that, and over the same four cards delivered
  twenty-four different ways it produced eighteen different orders. This port puts the cards
  that have a position at that position and the rest behind them, ordered by name and then
  by when they were made, because that is a rule that can be stated and the same cards then
  always come out the same way. When every card has been given a position — which is the
  only kind the interface itself ever writes — the two agree exactly.
- **Asking whether a value starts or ends with something, when the card holds several
  values.** Focalboard stops there and the whole board shows nothing. This port joins the
  values together and asks the question of the result, so one unanswerable question hides
  one card rather than every card.
- **Dragging a card writes both halves together.** Focalboard sends two separate messages —
  the card's new column, and the view's new order — and nothing joins them, so one can
  arrive without the other. This port sends one, because holding a board in one place makes
  that free.
- **The page is told about changes instead of asking for them.** Both systems push rather
  than poll; the difference is how. Focalboard keeps a two-way connection open, this port
  uses a one-way stream. What a page misses while disconnected differs as a result: neither
  replays, and both re-read the whole board on reconnecting, which this port measured at 2.4
  seconds from the connection returning to the page being right again.
- **Identifiers are kept as given.** Focalboard replaces every identifier it is sent when
  cards and views are created, and does not update a view's card order to match, so a view
  created alongside its cards can come back pointing at cards that no longer exist. This
  port stores what it was given, because making up identifiers is not part of what it was
  built to do.
- **A view with no grouping chosen.** Focalboard shows one column labelled "No undefined".
  This port shows one column with no label, because a label made by writing out the absence
  of a name is not a name.
- **Teams, users, permissions, comments, attachments, templates, categories, the calendar
  and gallery layouts, undo, and date properties.** Not attempted. The endpoints the
  interface needs in order to draw anything are answered with fixed replies; everything
  else returns nothing. What is in scope and what is not is in
  [`docs/scope.md`](https://github.com/TylerJewell/akka-specify-harness/blob/main/focalboard-port/docs/scope.md).
- **Sorting by who made or last changed a card.** Not checked. Both systems have the
  setting; neither was run against it here, because it needs a directory of people and this
  port does not have one.
- **Everything a card holds beyond its name, its dates and its property values.** Not
  checked. Comments, attachments and the card's body are stored by focalboard and are not
  stored here at all.

---

## Licence

Focalboard's source is offered under the GNU AGPL v3.0 or a commercial licence from
Mattermost, Inc., © 2015–present Mattermost, Inc. This port vendors and modifies
focalboard's own interface, so it is a derived work under that licence and this repository
is private. See [`ACKNOWLEDGEMENTS.md`](ACKNOWLEDGEMENTS.md), which lists what was copied
and what publishing this would require.
