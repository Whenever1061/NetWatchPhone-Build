# NetWatch Control Hub update channel

This directory temporarily hosts the public update manifest and verified patch files for NetWatch Control Hub.

- `update.json` is the stable-channel manifest checked by the Hub Updater.
- `patches/` contains versioned update patches.
- Each manifest publishes a SHA-256 hash that the Control Hub verifies before staging an update.

The Control Hub can later be moved to a dedicated NetWatch release repository without changing the update format.
