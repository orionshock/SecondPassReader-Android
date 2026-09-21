# Second Pass Reader

Second Pass Reader presents reader-authorized Library content while preserving the ownership and
history semantics supplied by Second Pass Library.

## Language

**Shelf scope**:
Exactly one of Personal, Shared, or Group; it identifies an independent Shelf collection.
_Avoid_: All Shelves, non-personal, shared-and-group

**Personal Shelf**:
A Shelf owned by the authenticated reader's user identity.
_Avoid_: Primary Shelf

**Shared Shelf**:
A Shelf owned by another user and visible to the authenticated reader.
_Avoid_: Non-personal Shelf

**Group Shelf**:
A Shelf owned by a Library Group rather than a user.
_Avoid_: Shared Shelf

**Shelf preview**:
An ordered, bounded projection of a Shelf's Books embedded in the Shelf response.
_Avoid_: Preview lookup
