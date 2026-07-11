# Barista Pro — Pour Lab

Portrait Android/LibGDX prototype for testing a simplified latte-art pour.

## Controls (build 0.2.0)

- Drag around the outer ring to choose the milk-flow direction.
- Touch inside the cup to position the spout and pour; hold longer for more foam.
- Move sideways while holding to jiggle, or move against/with the arrow to pull back/push forward.
- Make a fast forward stroke to trigger the narrow, high-momentum cut-through.
- Tap **Reset Cup** to start again.

The surface uses a lightweight 2D foam-and-velocity grid. Momentum decays as the cup fills, and resistance increases towards the rim.

Open the project in Android Studio, allow Gradle sync to finish, then run the `android` configuration on an emulator or Android device.
