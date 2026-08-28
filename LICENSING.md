# Licensing

This repository holds two kinds of code and they are not under the same licence.

## The rebuild — Apache-2.0

Everything outside the vendored trees listed below was written for this project. It is a
clean-room implementation: written against a specification derived by running
[mattermost-community/focalboard](https://github.com/mattermost-community/focalboard) and recording what it does, not by translating its
source. See `../focalboard-port/specs/SPEC-001-focalboard.md` for the rules it was built to, and `ACKNOWLEDGEMENTS.md` for the
places any text was carried across and why.

It is licensed under the Apache License 2.0, Copyright 2026 Tyler Jewell. See `LICENSE`
and `NOTICE`.

## The interface — AGPL-3.0, as mattermost-community/focalboard licensed it

- `src/main/resources/static-resources/`
- `webapp/`

That code was written by the mattermost-community/focalboard project, Copyright the mattermost-community/focalboard authors, and is shipped
here **verbatim**, reused rather than rebuilt, per this harness's RENDERING.md R3. It
remains under the licence its authors chose, unmodified, in `LICENSE-focalboard` and beside
the code itself. Nothing about this repository relicenses it.

## Why the split

One licence over both halves would be wrong in one direction or the other: it would either
claim this project's terms over somebody else's work, or impose the original's terms on
code they did not write. The boundary is the `.vendored` manifest at the root of this
repository, which is the same file `toolkit/source_hygiene.py` and
`toolkit/copied_strings.py` read when they check what this port wrote — so the licence
split and the provenance scan cannot drift apart.

## A note on the runtime

The rebuild runs on the Akka SDK, which is distributed under the Business Source License
1.1 and converts to Apache-2.0 three years after each release. Apache-2.0 on this
repository's own code does not grant any right to Akka; running this in production needs
whatever Akka's licence requires at the time.
