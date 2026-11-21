# Daybreak design source

These files are the source for the Daybreak redesign. The spec that explains them is
`docs/superpowers/specs/2026-11-21-daybreak-redesign.md`.

- One `.dc.html` file per screen, 390 × 844 px (treat px as dp). `canvas.json` lists every
  artboard with its title.
- Exact values are the inline `style="…"` attributes: sizes, colours, radii, copy.
- State logic (what changes when a habit is ticked, which outcome shows what) is in the
  `class Component` script at the bottom of each file: `renderVals()` returns every value the
  markup uses, so read it top to bottom.
- `{{name}}` in the markup is a value from `renderVals()`. `<sc-for>` repeats, `<sc-if>` shows
  a block conditionally, `<dc-import name="SprigA" …>` places a Sprig with those props.
- `SprigA.dc.html` is the Sprig reference: geometry in a 120-unit box, the animation loop in
  `componentDidMount`, and the pose/outfit/interaction logic in `renderVals()`.
- Files ending in `-settings`, `-last`, `-slipped`, `-shield`, `-lockoff`, `-late`,
  `-writtenoff` only set a prop on the base screen; read the base file.

These files need the design canvas runtime to render, so they do not open in a browser on
their own. The owner can view and click through them on the live canvas linked from the spec.
