# Second Pass Reader language

The reader connects to an SPL server to browse a Library and keep reader-owned records.

## Language

**Server ID**:
A stable UUID for one SPL server and its database. A change in address does not change this identity.
_Avoid_: Installation ID, server URL

**Library base URL**:
An HTTP or HTTPS address at the root of an SPL Library. One server may have several Library base URLs.
_Avoid_: API base URL, server identity

**Server URLs**:
The ordered Library base URLs reported by a server to an authenticated reader.
_Avoid_: Network classes such as LAN or public URL
