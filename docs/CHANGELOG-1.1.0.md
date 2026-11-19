# Sonara 1.1.0 — Worldwide discovery update

- Added an expandable `MusicCatalogSearchProvider` contract and `WorldwideMusicCatalog` registry.
- Added Apple/iTunes song metadata search across the user's storefront plus PH, US, GB and KR storefronts (duplicate titles/artists are merged).
- Added official-service search handoffs for YouTube Music, Spotify and Apple Music.
- Kept Jamendo as the in-app streaming provider for independent music, with its existing per-track license/download checks.
- Did not add stream extraction, unauthorized downloads, or pirated catalog sources. Apple search results open their official store link; they are not added to Sonara's play queue.
- Updated privacy disclosures and provider architecture documentation.
- Added unit tests for storefront selection and catalog-result deduplication.
