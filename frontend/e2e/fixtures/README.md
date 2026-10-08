# Wikipedia Table Fixtures

`wikipedia-tables.json` contains table excerpts captured manually on 2026-10-08 from the official English MediaWiki `action=parse` API. Automated tests read this local file only and never fetch Wikipedia.

| Article | Revision | Shapes |
| --- | --- | --- |
| [Richard Burton](https://en.wikipedia.org/w/index.php?title=Richard_Burton&oldid=1376704316) | 1376704316 | Biography infobox, nested navigation table, cell lists |
| [Academy Award for Best Actor](https://en.wikipedia.org/w/index.php?title=Academy_Award_for_Best_Actor&oldid=1378696624) | 1378696624 | Award columns, grouped years with rowspan, headings with colspan |
| [Summer Olympic Games](https://en.wikipedia.org/w/index.php?title=Summer_Olympic_Games&oldid=1373247608) | 1373247608 | Wide sports/statistics and host-history tables, merged headers |

Source text is by Wikipedia contributors under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). Each source URL links to the article/history for attribution. Changes: selected tables were extracted and serialized as HTML; tests apply WikiRace's frontend sanitizer and table styles. Original inline styles and classes remain in the input fixtures to exercise filtering. The additional unclassified/long-text fixture in `tables.spec.ts` is synthetic.

These are structural rendering fixtures, not complete articles or authoritative gameplay link snapshots. Actual navigation validation remains exclusively on the backend. Recognizing `sortable` styles does not introduce a client sorting feature.
