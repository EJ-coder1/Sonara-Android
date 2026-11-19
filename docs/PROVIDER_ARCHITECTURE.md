# Sonara music-provider architecture

## Current provider roles

| Source | What Sonara uses it for | In-app playback? |
|---|---|---:|
| Local MediaStore library | Music files already on the device | Yes |
| Jamendo API | Independent-artist catalog, track streams, permitted downloads, moods and country charts | Yes, subject to each track's rights and Jamendo flags |
| Apple/iTunes Search API | Worldwide song metadata and official Apple Music/iTunes links; the current query aggregates the device storefront plus PH, US, GB and KR | No; tapping a result opens Apple Music/iTunes |
| YouTube Music | Search handoff to the official app/site | No Sonara stream extraction |
| Spotify | Search handoff to the official app/site | No Sonara stream extraction |

## Provider contract

`MusicCatalogSearchProvider` is the extension point for future catalog metadata providers. `WorldwideMusicCatalog` owns storefront fan-out, query aggregation, duplicate suppression and error handling. A provider can be added without changing Sonara's Media3 player. A catalog hit is not automatically a playable track: it must have a permitted playback URL/provider SDK integration before it can be converted to a Sonara playback item.

## Playback policy

Do not use undocumented/private endpoints to extract audio, bypass ads, bypass account entitlements, or enable background playback that the source provider does not authorize. Do not use an API's preview audio as general entertainment when its terms restrict previews to store promotion. Do not provide downloads unless the provider and the particular track authorize them.

The Apple/iTunes search integration intentionally uses track metadata and the returned official store URL only. It ignores `previewUrl` and does not use Apple artwork in the results list. The YouTube Music, Spotify and Apple Music chips open official service search URLs.

## Reference projects

ArchiveTune and LastWave Native were inspected for architectural ideas: provider separation, paging, discovery sections, query debounce, and a single queue/player. Their source code was not copied into Sonara. Both supplied projects identify themselves as GPL-3.0, so a future decision to incorporate code rather than independently reproduce an idea requires a deliberate GPL compliance review and preserving the upstream notices/source obligations.
