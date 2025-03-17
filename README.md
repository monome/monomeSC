# monomeSC

*monome + SuperCollider*

Communication and management for monome serialosc devices within the open-source audio synthesis platform SuperCollider.

Contains:

- `Monome` class manages serialosc server relay
- `MonomeGrid` subclass connects monome grids (all editions)
- `MonomeArc` subclass connects monome arcs (all editions)

Created by Raja Das, Ezra Buchla, Dani Derks, and Joseph Rangel.

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
