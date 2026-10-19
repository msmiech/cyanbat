# The game needs no keep rules of its own: nothing in it is reached by reflection. Every class it
# uses is referenced from code, the activities are kept from the manifest, and the libraries -
# Compose, DataStore and coroutines - bring their own rules. Enums read back by name (Difficulty,
# InputMethod, DisplayMode and the other settings) go through entries and Enum.name, which R8 keeps
# working as they are.
