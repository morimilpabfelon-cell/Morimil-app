# Pull Request

## Summary

-

## Flow guard

- [ ] Main was green before this branch.
- [ ] This is the only active implementation branch.
- [ ] The changed component is connected before the next component starts.

## Process guard

- [ ] Who creates the changed component is known.
- [ ] Who calls the changed component is known.
- [ ] The real app path can reach it.
- [ ] Tests were updated when contracts changed.
- [ ] Removed architecture has no remaining callers.

## Local verification

- [ ] `./gradlew :app:testDebugUnitTest`
- [ ] `./gradlew :app:assembleDebug`
