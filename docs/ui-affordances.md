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

### Preview cover rows

- Preview covers are lightweight navigation surfaces; an eligible cover may open Book Detail.
- Do not add overflow menus, long-press actions, footers, Reader actions, or Session actions to
  preview art.
- Derive visible preview capacity from available layout width where returned data allows it.
- Preserve cover proportions and minimum spacing without crowding primary metadata.

### Decorative cover stacks

- Decorative stacks, including Home Shelf previews, are not independently interactive.
- The owning card or tile provides the primary navigation action.
- Tiny shelf previews do not receive full-card overflow controls.

## Segmented toggles

- Represent mutually exclusive binary presentation modes with one segmented control.
- Use semantic icons and accessible labels for each segment.
- Make the selected state visually and semantically clear.
- Avoid adjacent independent buttons for one binary choice.

## Entity browse context

- Selected Author or Series identity may extend the Library title context.
- Do not repeat entity name or result count in content when the title already owns them.
- Render optional descriptions only when meaningful, collapsed by default with More and Less
  affordances when the text actually overflows.
