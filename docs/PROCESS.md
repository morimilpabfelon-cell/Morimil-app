# Process Guard

## Core rule

A component is not complete just because the class exists. It is complete only when it is connected to the real app flow.

Before merge, every change must answer:

1. Who creates it?
2. Who calls it?
3. What state does it read?
4. What state does it change?
5. How is failure shown?

If one answer is missing, the component is not ready.

## Required checks

- Composition root reviewed.
- Runtime caller identified.
- User path identified.
- Tests updated when contracts changed.
- Old path removed when architecture is retired.

## Merge rule

Do not merge a component that is only reachable from tests.

Do not merge a cleanup that removes a backend path but leaves old UI or tests calling the removed contract.
