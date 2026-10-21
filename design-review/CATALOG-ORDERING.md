# Admin search and catalog ordering

- Admin search filters titles locally and retains query on returning from edit/delete and saved-state recreation. Persian/Arabic yeh/kaf and whitespace are normalized. Edit/delete target the filtered Podcast object, not the original list position.
- Catalog lists use creation time descending. Home's eight trending items and the full trending grid use trending-entry time descending, with creation/key tie breakers. Home scrolls to the first item when the leading trend changes.
- New admin saves atomically update the podcast, version and `db_config/podcast_order/<id>` timestamps using Firebase server timestamps. Creation time is recorded only on creation; trending time only on false-to-true transitions. Ordinary edits preserve both. Deletion removes ordering metadata.
- The metadata lives under the already admin-writable `db_config` node, compatible with the checked-in rules without adding properties to strictly validated podcast records. No production rules or records were changed during development.
- SQLite version 7 adds default-zero timestamp columns without deleting existing data. A schema marker triggers one catalog re-fetch even if the remote version has not changed. Older Firebase push IDs provide a fallback creation timestamp; arbitrary legacy IDs have no recoverable creation date, so their key is the stable fallback. Historical trending-entry dates cannot be reconstructed; turn a trend off/save then on/save to explicitly give it a new entry date.
- Direct Firebase console edits do not run admin-save timestamp logic: update the relevant metadata and version as well, or use the app admin panel.

Validation: resource/reference checks passed. Added JVM ordering/search tests, not executed because no Gradle build was requested. Device verification still required: search, edit and return, delete confirmation/cancellation, creation, trending an older podcast, cache upgrade, and refreshed Home order.
