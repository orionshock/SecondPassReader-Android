# UI affordances

This document records established Second Pass Reader presentation conventions. Keep it concise,
current, and focused on reusable product behavior rather than implementation history.

## General visual language

- Use a dark, dense, tablet-first utility presentation.
- Keep accent color restrained; book covers provide most of the screen's color.
- Prefer compact controls with touch-safe targets over oversized Material defaults.
- Use muted secondary metadata and preserve clear information hierarchy.

## App bars

- Root routes use a hamburger and current destination title.
- Pushed routes use Back, one muted immediate context, and the current title.
- Keep app bars to one line and one context level, with ellipsis for constrained widths.
- Normal shell routes expose the root drawer through a deliberate left-edge swipe.
- A future Reader route may disable the shell drawer gesture when reading gestures own the edge.

## Search fields

- Use a compact rounded field with a leading search icon.
- Let the contextual placeholder carry concise explanatory copy.
- Put the submit affordance inside the trailing edge of the field.
- IME Search and the trailing affordance perform the same action.
- Avoid redundant headings, explanations, and external Search buttons.

## Full Book cards

- Keep cover and metadata/footer geometry consistent across a grid.
- Allow a two-line title and one-line author with ellipsis where applicable.
- Actionable full cards expose a semantic top-right overflow affordance.
- Do not add full-card action chrome to passive preview or decorative imagery.

## Context menus

- Three-dot tap and long press open the same action model.
- Anchor menus to the actual overflow affordance or a sensible local press position.
- Menus are content-width and use framework edge-aware placement.
- Do not stretch a popup to card width.
- Use a modal or sheet only when the interaction is too complex for a compact menu.
- Omit unavailable actions rather than showing dead clutter.

## Book actions

- Current actions include Book details, Reading sessions, and truthful contextual Author or Series
  navigation.
- Use the wording **Read book** when a Reader action exists.
- Do not expose Read book before the Reader route is implemented.
- Card owners emit typed navigation intents and do not duplicate the target feature workflow.

## Loading and refresh

- Keep existing content and layout stable during background refresh.
- Show a compact inline spinner beside the relevant section title.
- Reserve larger loading treatment for a no-content initial load.
- Retain cached content during recoverable refresh failure.

## Semantic icons

- Feature code uses the `AppIcon` semantic vocabulary.
- Raw Material Symbol names remain inside the design mapping.
- Preserve meaningful owner, group, person, and sharing distinctions.

## Preview imagery

### Browse preview covers

- Author and Series preview covers are non-interactive supporting imagery; the containing entity
  row owns the touch action.
- Avoid nested preview-cover touch targets on touch-first layouts.
- Do not add overflow menus, long-press actions, footers, Reader actions, or Session actions to
  preview art.
- Derive visible preview capacity from available layout width where returned data allows it.
- Keep browse preview covers separated; do not stack them.
- Preserve the full one-line entity name before allocating preview space. Preview count may vary by
  row and may fall to zero rather than wrapping, truncating, or displacing the name.
- Preserve cover proportions and minimum spacing while using the available preview region fully.

### Selected axis context

- When a Library Author or Series is selected, the active axis pill gains a subtle trailing return
  indicator.
- Tapping that selected axis returns to its index.
- Do not add redundant Back rows for selected browse context.

### Decorative cover stacks

- Decorative shelf-icon stacks, including Home Shelf previews, may overlap and are not
  independently interactive.
- The owning card or tile provides the primary navigation action.
- Tiny shelf previews do not receive full-card overflow controls.

### Shelf collection rows

- Give the Shelf name its full one-line width before allocating preview space; never wrap or
  ellipsize it.
- Owner/context is prominent supporting metadata and the count is quieter supporting metadata.
- Separated, non-interactive preview covers consume only genuine leftover width.
- Preview count may vary by row or fall to zero rather than displacing Shelf identity.

### Shelf ownership and context

- My Shelves may show Private or Listed because that visibility is user-controlled.
- Shared by Others shows the authoritative owner and omits redundant visibility.
- Group Shelves shows the group identity and omits Private or Listed because access follows group
  membership.
- Use semantic person, sharing, and group icons for those distinctions.

### Shelf-icon previews

- Reserve overlapping decorative cover stacks for shelf-icon treatments such as Home Shelf cards.
- Decorative covers never become individual navigation targets.

## Segmented toggles

- Represent mutually exclusive binary presentation modes with one segmented control.
- Use semantic icons and accessible labels for each segment.
- Make the selected state visually and semantically clear.
- Avoid adjacent independent buttons for one binary choice.

## Collection mode controls

- Peer browse perspectives use one compact segmented mode control.
- Feature-specific filters may sit beside the mode control in the app bar when width allows.
- Keep the root mode control in one stable position when mode-specific filters appear or disappear.
- Authoritative result counts remain muted right-side app-bar metadata.

## Collection-row notes

- Show nonblank secondary record notes as bounded, muted quote-style excerpts where width allows.
- Protect the primary record identity before allocating horizontal space to a note excerpt.
- Omit excerpts in compact layouts rather than making collection rows excessively tall.

## Entity browse context

- Selected Author or Series identity may extend the Library title context.
- Do not repeat entity name or result count in content when the title already owns them.
- Render optional descriptions only when meaningful, collapsed by default with More and Less
  affordances when the text actually overflows.

## Metadata fields

- Use a muted compact field label with a stronger readable value.
- Interactive values use the established accent and underline treatment with a sensible touch
  target; non-interactive values must not imitate links.
- Omit missing fields instead of rendering empty placeholders.

## Object action tiles

- Important object-level actions may use bordered icon-above-label buttons.
- Keep actions in the same group equal in size.
- Wide layouts may use a vertical action rail; medium and narrow layouts reposition the same
  actions instead of shrinking their touch targets.
- A disabled future action may remain visible when it communicates an intended product capability.
- Avoid large placeholder or explanation containers for unavailable actions.

## Session detail actions

- Keep lifecycle actions such as Edit and Close with the Session metadata they modify.
- Related Book navigation may use a separate object-action rail.
- Visually distinguish modifying the current record from navigating to a related object.

## Read-only annotation cards

- Communicate annotation type with one semantic icon in a stable leading rail rather than a
  repetitive type heading.
- Treat quote, note, or useful location label as the primary content.
- Never expose raw durable location identifiers in history presentation.
- Read-only history surfaces omit edit and delete affordances.

## Adaptive detail heroes

- Keep artwork prominent without crowding useful metadata or actions.
- Tablet portrait should not default to a giant vertically stacked cover.
- Choose detail composition from available width rather than orientation alone.

## Management surfaces

- Keep normal detail surfaces focused on viewing and consolidate administration behind a clear
  Manage action.
- Metadata editing may retain focused modal forms opened from the management surface.
- Keep destructive actions with administration rather than competing with normal viewing actions.

## Settings

- Default Settings surfaces useful library, account, device, and authority state rather than
  protocol diagnostics.
- Put support-oriented identifiers behind collapsed Technical details and never expose secrets.
- Repair connection, Log out, and Forget are distinct lifecycle actions: Repair preserves local data
  when verified identity is unchanged; Logout attempts remote revocation and always resets locally;
  Forget resets locally without requiring server contact.
- Confirm destructive account-local cleanup explicitly.
