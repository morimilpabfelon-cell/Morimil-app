# Pull Request

## Summary

-

## Process guard

- [ ] Who creates the changed component is known.
- [ ] Who calls the changed component is known.
- [ ] The real app path can reach it.
- [ ] Tests were updated when contracts changed.
- [ ] Removed architecture has no remaining callers.

## Local verification

- [ ] `./gradlew :app:testDebugUnitTest`
- [ ] `./gradlew :app:assembleDebug`
