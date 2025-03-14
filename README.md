# monomeSC

*monome + SuperCollider*

Communication and management for monome serialosc devices within the open-source audio synthesis platform SuperCollider.

Contains:

- `Monome` class (by Raja Das, Ezra Buchla, and Dani Derks) manages serialosc server relay
- `MonomeGrid` subclass (Ibid.) connects monome grids (all editions)
- `MonomeArc` subclass (by Joseph Rangel and Dani Derks) connects monome arcs (all editions)

## installation

To install the SuperCollider library for monome grid devices:

- download + unzip the [latest release](https://github.com/monome/monomeSC/releases)
- in SuperCollider, select `File > Open user support directory`
- move or copy the `monomeSC` folder into the `Extensions` folder
  - if `Extensions` does not exist, please create it
- in SuperCollider, recompile the class library (`Language > Recompile Class Library`)
  - macOS: <kbd>Command</kbd> + <kbd>Shift</kbd> + <kbd>L</kbd>
  - Windows / Linux: <kbd>Ctrl</kbd> + <kbd>Shift</kbd> + <kbd>L</kbd>

## study

To learn these libraries, please refer to the SuperCollider studies on monome's website:

- [grid](https://monome.org/docs/grid/studies/sc/)
- [arc](https://monome.org/docs/arc/studies/sc/)